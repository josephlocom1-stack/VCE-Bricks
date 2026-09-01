#!/usr/bin/env python3
from pathlib import Path
from PIL import Image, ImageOps, ImageDraw
import json, os, re, runpy
import numpy as np

PROJECT=Path(os.environ.get('MINI4X_PROJECT','project'))
ASSETS=PROJECT/'app/src/main/assets/mini4x'
ANIM=ASSETS/'anim'; ATLASES=ASSETS/'atlases'
ANIM.mkdir(parents=True,exist_ok=True)
factions=['asteria','sunspire','virelia','emberhold']
extra_humanoids=['swordsman','mind_bender','cloak','giant','guerrilla']

def clothing_tint(im,rgb,strength=.55):
    arr=np.array(im).astype(np.float32);a=arr[:,:,3];R,G,B=[arr[:,:,i] for i in range(3)]
    mask=((B>R*1.05)&(B>G*.95)&(a>20)).astype(np.float32)*strength
    mask=mask[...,None];target=np.array(rgb,dtype=np.float32).reshape(1,1,3)
    arr[:,:,:3]=arr[:,:,:3]*(1-mask)+target*mask
    return Image.fromarray(np.clip(arr,0,255).astype(np.uint8),'RGBA')

def clear_region(im,box):
    out=im.copy();a=np.array(out.getchannel('A'));x0,y0,x1,y1=box;a[y0:y1,x0:x1]=0;out.putalpha(Image.fromarray(a));return out

def fit(im,w=192,h=192):
    bb=im.getbbox()
    if not bb:return Image.new('RGBA',(w,h))
    c=im.crop(bb);c=ImageOps.contain(c,(w-6,h-6),Image.Resampling.LANCZOS)
    o=Image.new('RGBA',(w,h));o.alpha_composite(c,((w-c.width)//2,h-3-c.height));return o

def make_extras(fid):
    war=Image.open(ANIM/f'{fid}_warrior_idle.webp').convert('RGBA')
    arch=Image.open(ANIM/f'{fid}_archer_idle.webp').convert('RGBA')
    swordsman=clear_region(war,(126,55,192,154))
    mind=clear_region(arch,(118,38,192,155));mind=clothing_tint(mind,(119,70,168),.80)
    d=ImageDraw.Draw(mind);d.line((137,63,137,151),fill=(108,61,26,255),width=9);d.ellipse((124,45,150,71),fill=(82,160,235,255),outline=(210,235,255,255),width=3)
    body=clear_region(arch,(118,38,192,155));body=clothing_tint(body,(66,40,102),.88)
    cloak=Image.new('RGBA',(192,192));cd=ImageDraw.Draw(cloak);cd.polygon([(83,72),(147,88),(151,164),(90,159)],fill=(58,34,92,235));cd.polygon([(83,72),(112,86),(92,159)],fill=(83,49,128,235));cloak.alpha_composite(body)
    cd=ImageDraw.Draw(cloak);cd.polygon([(132,105),(159,91),(164,96),(137,112)],fill=(205,210,216,255));cd.rectangle((128,107,138,113),fill=(102,61,30,255))
    giant=clear_region(war,(126,55,192,154));giant=fit(giant);gd=ImageDraw.Draw(giant);gd.line((132,67,164,119),fill=(102,62,29,255),width=13);gd.ellipse((122,52,147,77),fill=(93,82,72,255))
    guer=clear_region(arch,(118,38,192,155));guer=clothing_tint(guer,(65,128,72),.72);bb=guer.getbbox();c=guer.crop(bb);c=ImageOps.contain(c,(150,150),Image.Resampling.NEAREST);gg=Image.new('RGBA',(192,192));gg.alpha_composite(c,((192-c.width)//2,178-c.height));gd=ImageDraw.Draw(gg);gd.polygon([(123,106),(151,92),(155,97),(128,113)],fill=(205,212,220,255));gd.rectangle((118,109,129,115),fill=(105,62,30,255))
    return {'swordsman':swordsman,'mind_bender':mind,'cloak':cloak,'giant':giant,'guerrilla':gg}

index_path=PROJECT/'app/src/main/java/com/example/mini4x/ui/GameAtlasIndex.kt'
idx=index_path.read_text();entry_re=re.compile(r'"([^"]+)" to AtlasEntry\("([^"]+)", Rect\((\d+), (\d+), (\d+), (\d+)\)\)')
entries={m.group(1):(m.group(2),tuple(map(int,m.groups()[2:]))) for m in entry_re.finditer(idx)}
atlas={p.stem:Image.open(p).convert('RGBA') for p in ATLASES.glob('*.webp')}
def paste(path,im):
    if path not in entries:return False
    grp,(l,t,r,b)=entries[path];target=atlas.get(grp)
    if target is None:return False
    src=fit(im,r-l,b-t);target.alpha_composite(src,(l,t));return True

manifest={'version':'1.4.0','source_policy':'accepted Style-A AI core humanoid derivatives; dedicated naval AI pass follows','units':{}}
for fid in factions:
    generated=make_extras(fid)
    for k,im in generated.items():
        im=fit(im);im.save(ANIM/f'{fid}_{k}_idle.webp','WEBP',lossless=True,method=5);paste(f'units/{fid}_{k}_body.png',im);manifest['units'][f'{fid}_{k}']={'source':'AI Style-A core derivative','status':'ai_authored_source'}
for grp,im in atlas.items():im.save(ATLASES/f'{grp}.webp','WEBP',lossless=True,method=4)
(ASSETS/'v14_full_roster_ai_manifest.json').write_text(json.dumps(manifest,indent=2))
gradle=PROJECT/'app/build.gradle.kts';s=gradle.read_text();s=re.sub(r'versionCode\s*=\s*\d+','versionCode = 15',s);s=re.sub(r'versionName\s*=\s*"[^"]+"','versionName = "1.4.0"',s);gradle.write_text(s)
print('V1.4 humanoid AI source pass:',len(manifest['units']),'faction/unit source entries')

# Complete the same source-provenance pass for all five naval runtime classes before
# articulated hull/sail/weapon partitioning and directional-pose generation.
runpy.run_path(str(Path(__file__).with_name('apply_v14_naval_ai.py')), run_name='__main__')
