#!/usr/bin/env python3
from pathlib import Path
import zlib,base64,re,math,os,wave,struct
from PIL import Image,ImageDraw,ImageFilter
import numpy as np

PROJECT=Path(os.environ.get("MINI4X_PROJECT","project"))
PAYLOAD=Path(__file__).parent/'payloads'
PATCHES={
    'Mini4xView.kt':'app/src/main/java/com/example/mini4x/ui/Mini4xView.kt',
    'GamePresenter.kt':'app/src/main/java/com/example/mini4x/presentation/GamePresenter.kt',
    'GameAssetAtlas.kt':'app/src/main/java/com/example/mini4x/ui/GameAssetAtlas.kt',
    'MainActivity.kt':'app/src/main/java/com/example/mini4x/MainActivity.kt',
    'app_build.gradle.kts':'app/build.gradle.kts',
}
for stem,rel in PATCHES.items():
    parts=sorted(PAYLOAD.glob(stem+'.part*'))
    if not parts: raise SystemExit(f'missing payload for {stem}')
    payload=''.join(p.read_text().strip() for p in parts)
    raw=zlib.decompress(base64.b64decode(payload))
    out=PROJECT/rel;out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(raw)
    print('patched',rel,len(raw))

assets=PROJECT/'app/src/main/assets/mini4x'
atlases=assets/'atlases'
index=(PROJECT/'app/src/main/java/com/example/mini4x/ui/GameAtlasIndex.kt').read_text()
entry_re=re.compile(r'"([^"]+)" to AtlasEntry\("([^"]+)", Rect\((\d+), (\d+), (\d+), (\d+)\)\)')
entries={m.group(1):(m.group(2),tuple(map(int,m.groups()[2:]))) for m in entry_re.finditer(index)}
opened={}
def sprite(path):
    group,rect=entries[path]
    if group not in opened: opened[group]=Image.open(atlases/f'{group}.webp').convert('RGBA')
    return opened[group].crop(rect)

W,H=1536,1024
bg=Image.new('RGBA',(W,H),(2,9,17,255));d=ImageDraw.Draw(bg)
for i in range(110):
    x=(i*977+113)%W;y=(i*619+77)%H;r=1+(i%7==0)
    d.ellipse((x-r,y-r,x+r,y+r),fill=(220,235,245,145 if r==1 else 210))
tw,th=112,58;scale=1.45;ox,oy=W*.58,H*.16
terrain_cycle=['field','forest','field','mountain','field','water','field','forest']
for diag in range(0,22):
    for x in range(11):
        y=diag-x
        if y<0 or y>=11: continue
        kind=terrain_cycle[(x*3+y*5)%len(terrain_cycle)]
        if x in (0,10) or y in (0,10): kind='water' if (x+y)%3==0 else kind
        sp=sprite(f'tiles/asteria_{kind}.png')
        sp=sp.resize((int(192*scale),int(176*scale)),Image.Resampling.LANCZOS)
        cx=ox+(x-y)*tw/2*scale;cy=oy+(x+y)*th/2*scale
        bg.alpha_composite(sp,(int(cx-96*scale),int(cy-104*scale)))
for path,x,y,sc in [('cities/asteria_city_l5.png',7,5,1.10),('cities/virelia_city_l4.png',3,6,1.0),('cities/sunspire_city_l3.png',7,2,.92),('units/asteria_warrior_body.png',6,5,.68),('units/asteria_rider_body.png',5,4,.68),('units/virelia_archer_body.png',3,5,.66)]:
    sp=sprite(path);sp=sp.resize((int(192*scale*sc),int(192*scale*sc)),Image.Resampling.LANCZOS)
    cx=ox+(x-y)*tw/2*scale;cy=oy+(x+y)*th/2*scale
    bg.alpha_composite(sp,(int(cx-sp.width/2),int(cy-sp.height*.88)))
over=Image.new('RGBA',(W,H),(0,0,0,0));od=ImageDraw.Draw(over)
for x in range(W):
    a=int(max(0,205*(1-x/(W*.72))))
    if a: od.line((x,0,x,H),fill=(0,5,12,a))
for y in range(H):
    if y>H*.55:
        a=int(150*((y-H*.55)/(H*.45)))
        od.line((0,y,W,y),fill=(0,3,8,a))
bg=Image.alpha_composite(bg,over)
ui=assets/'ui';ui.mkdir(parents=True,exist_ok=True)
bg.convert('RGB').save(ui/'title_background.webp','WEBP',lossless=True,method=4)
print('generated title background', (ui/'title_background.webp').stat().st_size)

unit_entries={p:(g,r) for p,(g,r) in entries.items() if p.startswith('units/') and p.endswith('_body.png')}
humanoid={'archer','cloak','defender','giant','guerrilla','knight','swordsman','warrior'}
mounted={'rider'};siege={'catapult'};naval={'bomber','juggernaut','raft','rammer','scout'}
anim=assets/'anim';anim.mkdir(parents=True,exist_ok=True)
def mk_layer(im,mode,part):
    arr=np.array(im);y=np.arange(192,dtype=np.float32)[:,None]
    if mode=='humanoid':
        head=np.clip((88-y)/18,0,1);lower=np.clip((y-118)/22,0,1);upper=np.clip(1-head-lower,0,1);total=head+upper+lower
        weights=[head/np.maximum(total,1e-6),upper/np.maximum(total,1e-6),lower/np.maximum(total,1e-6)]
    else:
        top=np.clip((126-y)/24,0,1);weights=[top,1-top]
    arr[:,:,3]=(arr[:,:,3].astype(np.float32)*weights[part]).clip(0,255).astype(np.uint8)
    return Image.fromarray(arr,'RGBA')
def tf(im,angle=0,center=(96,120),dx=0,dy=0):
    out=im.rotate(angle,resample=Image.Resampling.BICUBIC,center=center,expand=False)
    if dx or dy:
        c=Image.new('RGBA',(192,192));c.alpha_composite(out,(int(dx),int(dy)));return c
    return out
def rig_pose(im,kind,pose):
    if kind in humanoid:
        if pose=='idle': return im
        if pose=='death': return im.rotate(72,resample=Image.Resampling.BICUBIC,center=(96,176),expand=False)
        h,u,l=[mk_layer(im,'humanoid',i) for i in range(3)]
        prm={'move1':(-2,3,-2,1,-2,-2),'move2':(2,-3,2,-1,-1,-1),'attack1':(-4,-14,-1,-3,-2,-1),'attack2':(5,15,2,5,0,0),'hit':(-6,-9,-3,-4,2,2)}[pose]
        ha,ua,ldx,udx,udy,hdy=prm
        c=Image.new('RGBA',(192,192));c.alpha_composite(tf(l,0,(96,176),ldx,0));c.alpha_composite(tf(u,ua,(96,126),udx,udy));c.alpha_composite(tf(h,ha,(96,78),0,hdy));return c
    if pose=='idle': return im
    if pose=='death': return im.rotate(58 if kind in naval else 68,resample=Image.Resampling.BICUBIC,center=(96,176),expand=False)
    top,base=[mk_layer(im,'two',i) for i in range(2)]
    if kind in mounted: prm={'move1':(-2,-3),'move2':(2,-1),'attack1':(-7,-2),'attack2':(8,0),'hit':(-7,2)}
    elif kind in siege: prm={'move1':(-2,-1),'move2':(2,0),'attack1':(-12,-2),'attack2':(13,0),'hit':(-5,2)}
    elif kind in naval: prm={'move1':(-2,-2),'move2':(2,-1),'attack1':(-5,-1),'attack2':(5,0),'hit':(-5,2)}
    else: prm={'move1':(-2,-2),'move2':(2,-1),'attack1':(-7,-1),'attack2':(8,0),'hit':(-6,2)}
    a,dy=prm[pose];c=Image.new('RGBA',(192,192));c.alpha_composite(base);c.alpha_composite(tf(top,a,(96,122),0,dy));return c
poses=['idle','move1','move2','attack1','attack2','hit','death']
units_atlas=opened.get('units') or Image.open(atlases/'units.webp').convert('RGBA')
for path,(group,rect) in unit_entries.items():
    key=path[len('units/'):-len('_body.png')];kind=key.split('_',1)[1]
    im=units_atlas.crop(rect)
    for pose in poses:
        out=anim/f'{key}_{pose}.webp'
        rig_pose(im,kind,pose).save(out,'WEBP',lossless=True,method=3)
print('animation frames',len(list(anim.glob('*.webp'))))
raw=PROJECT/'app/src/main/res/raw';raw.mkdir(parents=True,exist_ok=True);SR=22050
def env(t,d): return min(1,t/.012)*max(0,1-t/d)**2
def write_wav(name,dur,fn):
    n=int(SR*dur);frames=[]
    for i in range(n):
        t=i/SR;v=max(-1,min(1,fn(t,dur)));frames.append(struct.pack('<h',int(v*32767)))
    with wave.open(str(raw/name),'wb') as w:
        w.setnchannels(1);w.setsampwidth(2);w.setframerate(SR);w.writeframes(b''.join(frames))
write_wav('ui_click.wav',.075,lambda t,d:.24*env(t,d)*math.sin(2*math.pi*(900-300*t/d)*t))
write_wav('move_step.wav',.13,lambda t,d:.19*env(t,d)*(math.sin(2*math.pi*180*t)+.35*math.sin(2*math.pi*360*t)))
write_wav('attack_hit.wav',.20,lambda t,d:.25*env(t,d)*(math.sin(2*math.pi*(120+50*t/d)*t)+.45*math.sin(2*math.pi*330*t)))
write_wav('research.wav',.34,lambda t,d:.18*env(t,d)*(math.sin(2*math.pi*660*t)+.7*math.sin(2*math.pi*880*t)+.4*math.sin(2*math.pi*990*t)))
write_wav('capture.wav',.38,lambda t,d:.20*env(t,d)*(math.sin(2*math.pi*523.25*t)+.7*math.sin(2*math.pi*659.25*t)+.5*math.sin(2*math.pi*783.99*t)))
print('V1.2 patch/assets complete')
