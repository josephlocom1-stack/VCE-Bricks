#!/usr/bin/env python3
from pathlib import Path
import base64, os, re
from PIL import Image, ImageDraw

PROJECT=Path(os.environ.get('MINI4X_PROJECT','project'))
ROOT=Path(__file__).parent
SOURCE_DIR=ROOT/'safe-sources'
ASSETS=PROJECT/'app/src/main/assets/mini4x'
ATLASES=ASSETS/'atlases'; ANIM=ASSETS/'anim'; UI=ASSETS/'ui'
ANIM.mkdir(parents=True,exist_ok=True); UI.mkdir(parents=True,exist_ok=True)
TMP=Path('/tmp/mini4x-v13-ai'); TMP.mkdir(parents=True,exist_ok=True)

def decode_master(name):
    parts=sorted(SOURCE_DIR.glob(name+'.b64.*'))
    if not parts: raise SystemExit(f'missing AI source chunks for {name}')
    raw=base64.b64decode(''.join(x.read_text().strip() for x in parts))
    out=TMP/name; out.write_bytes(raw); return out

# One compact AI-authored master is the visual source of truth. Layout is documented below.
master=Image.open(decode_master('production_master.webp')).convert('RGBA')
CELL=56
unit_kinds=['warrior','archer','rider','defender','catapult','knight']
# row 0 = idle, row 1 = authored attack, six unit columns
unit_idle={k:master.crop((i*CELL,0,(i+1)*CELL,CELL)) for i,k in enumerate(unit_kinds)}
unit_attack={k:master.crop((i*CELL,CELL,(i+1)*CELL,2*CELL)) for i,k in enumerate(unit_kinds)}
# rows 2-3 = world cells. Row 2: field forest mountain water ocean.
# Row 3: capital city small-city metropolis village fish.
world_names=['field','forest','mountain','water','ocean','city_l5','city_l1','city_l3','village','fish']
world_cells={name:master.crop(((i%5)*CELL,(2+i//5)*CELL,(i%5+1)*CELL,(3+i//5)*CELL)) for i,name in enumerate(world_names)}
# Keep the actual accepted AI master inside the APK for provenance/QA.
master.save(ASSETS/'ai_production_master.webp','WEBP',quality=82,method=6)

index_path=PROJECT/'app/src/main/java/com/example/mini4x/ui/GameAtlasIndex.kt'
index=index_path.read_text()
entry_re=re.compile(r'"([^"]+)" to AtlasEntry\("([^"]+)", Rect\((\d+), (\d+), (\d+), (\d+)\)\)')
entries={m.group(1):(m.group(2),tuple(map(int,m.groups()[2:]))) for m in entry_re.finditer(index)}
if not entries: raise SystemExit('could not parse GameAtlasIndex')
atlas={p.stem:Image.open(p).convert('RGBA') for p in ATLASES.glob('*.webp')}

factions={'asteria':(47,169,217),'sunspire':(240,196,49),'virelia':(164,104,216),'emberhold':(223,103,80)}

def recolor(im,rgb,strength=.34):
    im=im.convert('RGBA'); px=im.load(); tr,tg,tb=rgb
    for y in range(im.height):
        for x in range(im.width):
            r,g,b,a=px[x,y]
            if a<12: continue
            mx=max(r,g,b); mn=min(r,g,b); sat=mx-mn
            s=strength*(.40+.60*min(1.0,sat/90.0))
            if mx>218 and sat<28: s*=.15
            if r>g*1.25 and r>b*1.18 and g>50: s*=.28
            px[x,y]=(int(r*(1-s)+tr*s),int(g*(1-s)+tg*s),int(b*(1-s)+tb*s),a)
    return im

def fit(im,w,h,bottom=.96,scale=.92):
    box=im.getbbox()
    if not box:return Image.new('RGBA',(w,h))
    crop=im.crop(box); k=min(w*scale/crop.width,h*scale/crop.height)
    nw=max(1,int(crop.width*k)); nh=max(1,int(crop.height*k)); crop=crop.resize((nw,nh),Image.Resampling.LANCZOS)
    out=Image.new('RGBA',(w,h)); x=(w-nw)//2; y=min(h-nh,max(0,int(h*bottom-nh))); out.alpha_composite(crop,(x,y)); return out

def paste_entry(path,im):
    e=entries.get(path)
    if not e:return False
    group,(l,t,r,b)=e; target=atlas.get(group)
    if target is None:return False
    target.alpha_composite(fit(im,r-l,b-t),(l,t)); return True

# Phase 2/3 — core AI units and core environment replace matching indexed runtime sprites.
for faction,rgb in factions.items():
    for kind in unit_kinds: paste_entry(f'units/{faction}_{kind}_body.png',recolor(unit_idle[kind],rgb))
    for terrain in ('field','forest','mountain','water','ocean','village','fish'):
        paste_entry(f'tiles/{faction}_{terrain}.png',recolor(world_cells[terrain],rgb,.08 if terrain not in ('village',) else .24))
    for level in range(1,6):
        key='city_l1' if level<=2 else ('city_l3' if level<=4 else 'city_l5')
        paste_entry(f'cities/{faction}_city_l{level}.png',recolor(world_cells[key],rgb,.28))
for name,im in atlas.items(): im.save(ATLASES/f'{name}.webp','WEBP',lossless=True,method=4)

# Phase 7 — joint-aware conservative motion from the authored idle silhouette, plus authored attack.
def segmented_pose(base,pose):
    base=fit(base,192,192)
    if pose=='idle': return base
    if pose=='death': return base.rotate(70,Image.Resampling.BICUBIC,center=(96,176))
    if pose=='hit': return base.rotate(-7,Image.Resampling.BICUBIC,center=(96,146))
    # Split at neck/hips so movement is not a single rigid-card rotation.
    top=Image.new('RGBA',(192,192)); mid=Image.new('RGBA',(192,192)); low=Image.new('RGBA',(192,192))
    top.alpha_composite(base.crop((0,0,192,82)),(0,0)); mid.alpha_composite(base.crop((0,82,192,132)),(0,82)); low.alpha_composite(base.crop((0,132,192,192)),(0,132))
    if pose=='move1': ta,ma,dx,dy=-2,3,-2,-2
    else: ta,ma,dx,dy=2,-3,2,-1
    out=Image.new('RGBA',(192,192)); out.alpha_composite(low,(dx,0)); out.alpha_composite(mid.rotate(ma,Image.Resampling.BICUBIC,center=(96,126)),(0,dy)); out.alpha_composite(top.rotate(ta,Image.Resampling.BICUBIC,center=(96,78)),(0,dy)); return out

for faction,rgb in factions.items():
    for kind in unit_kinds:
        idle=recolor(unit_idle[kind],rgb); attack=recolor(unit_attack[kind],rgb)
        frames={
            'idle':segmented_pose(idle,'idle'),'move1':segmented_pose(idle,'move1'),'move2':segmented_pose(idle,'move2'),
            'attack1':fit(attack,192,192),'attack2':fit(attack.rotate(5,Image.Resampling.BICUBIC,center=(attack.width//2,int(attack.height*.82))),192,192),
            'hit':segmented_pose(idle,'hit'),'death':segmented_pose(idle,'death')}
        for pose,frame in frames.items(): frame.save(ANIM/f'{faction}_{kind}_{pose}.webp','WEBP',quality=86,method=4)

# Phase 4/5/6 — preserve stable V1.2 start/tribe/HUD/tech logic, but make title/world visually coherent.
W,H=1536,1024; bg=Image.new('RGBA',(W,H),(2,7,13,255))
world_cycle=['field','forest','mountain','water','ocean']
for y in range(7):
    for x in range(9):
        tile=fit(world_cells[world_cycle[(x*3+y*5)%5]],250,210,1.0,.88)
        bg.alpha_composite(tile,(560+(x-y)*108,15+(x+y)*66))
for kind,x,y in [('warrior',120,620),('archer',300,660),('rider',465,650)]: bg.alpha_composite(fit(unit_idle[kind],250,300,1.0,.92),(x,y))
ov=Image.new('RGBA',(W,H)); od=ImageDraw.Draw(ov)
for x in range(W):
    a=max(0,int(220*(1-x/(W*.72))))
    if a: od.line((x,0,x,H),fill=(0,4,10,a))
for y in range(H):
    if y>H*.58: od.line((0,y,W,y),fill=(0,3,8,int(145*(y-H*.58)/(H*.42))))
Image.alpha_composite(bg,ov).convert('RGB').save(UI/'title_background.webp','WEBP',quality=88,method=6)

# Phase 8 — better strategic search and less premature aggression, without rewriting simulation rules.
ai_path=PROJECT/'app/src/main/java/com/example/mini4x/ai/StrategicAi.kt'
if ai_path.exists():
    s=ai_path.read_text()
    for a,b in {
        'maxActions:Int=80':'maxActions:Int=96','1->3;2->5;3->8;else->11':'1->4;2->6;3->9;else->12',
        '1->0;2->2;3->3;else->5':'1->1;2->2;3->4;else->6','ownPower<enemyPower*.42':'ownPower<enemyPower*.50',
        'state.ownedCities(pid).size<max(3,state.size/5)':'state.ownedCities(pid).size<max(4,state.size/4)',
        'ownPower>enemyPower*.62 || state.ownedCities(pid).size>=4':'ownPower>enemyPower*.72 || state.ownedCities(pid).size>=5',
        'round>4 -> StrategicGoal.ATTACK':'round>5 -> StrategicGoal.ATTACK','.sortedBy(::unitPriority).take(8)':'.sortedBy(::unitPriority).take(10)','}.take(24)':'}.take(30)'
    }.items(): s=s.replace(a,b)
    ai_path.write_text(s)

gradle=PROJECT/'app/build.gradle.kts'
if gradle.exists():
    s=gradle.read_text(); s=re.sub(r'versionCode\s*=\s*\d+','versionCode = 13',s); s=re.sub(r'versionName\s*=\s*"[^"]+"','versionName = "1.3.0"',s); gradle.write_text(s)

(ASSETS/'v13_ai_manifest.txt').write_text('\n'.join([
    'Mini4X V1.3 AI production override','source=ai_production_master.webp','unit_master_kinds='+','.join(unit_kinds),
    'factions='+','.join(factions),'world_master_cells='+','.join(world_cells),'core_animation_overrides=168']))
print((ASSETS/'v13_ai_manifest.txt').read_text())
