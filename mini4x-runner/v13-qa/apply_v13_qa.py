#!/usr/bin/env python3
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter
import json, os, re, math

PROJECT=Path(os.environ.get('MINI4X_PROJECT','project'))
ASSETS=PROJECT/'app/src/main/assets/mini4x'
ANIM=ASSETS/'anim'; RIG=ASSETS/'rig'; RIG.mkdir(parents=True,exist_ok=True)
factions=['asteria','sunspire','virelia','emberhold']
humanoids=['warrior','archer','defender','knight']
all_kinds=humanoids+['rider','catapult']

# V1.3.1 turns the accepted AI-authored 192px idle sprites into real articulated
# layers. Every source pixel belongs to exactly one body part, so the neutral
# composition reconstructs the AI sprite without doubled seams.
def blank(): return Image.new('RGBA',(192,192),(0,0,0,0))

def nearest_partition(im, centers, equipment_rule=None):
    src=im.convert('RGBA'); px=src.load(); layers={k:blank() for k in centers}; out={k:v.load() for k,v in layers.items()}
    if equipment_rule: layers['equipment']=blank();out['equipment']=layers['equipment'].load()
    for y in range(192):
        for x in range(192):
            rgba=px[x,y]
            if rgba[3]<8: continue
            if equipment_rule and equipment_rule(x,y,rgba): out['equipment'][x,y]=rgba; continue
            key=min(centers,key=lambda k:(x-centers[k][0])**2+(y-centers[k][1])**2)
            out[key][x,y]=rgba
    return layers

def humanoid_layers(im,kind):
    centers={
      'head':(96,49),'torso':(96,103),
      'left_upper_arm':(68,91),'left_forearm':(54,121),
      'right_upper_arm':(124,91),'right_forearm':(140,121),
      'left_thigh':(82,146),'left_shin':(78,174),
      'right_thigh':(110,146),'right_shin':(114,174)}
    def equip(x,y,c):
        r,g,b,a=c;mx=max(r,g,b);mn=min(r,g,b);sat=mx-mn
        outlier=(x<39 or x>153) and 35<y<164
        metallic=sat<45 and mx>125 and y>52
        bow=kind=='archer' and r>90 and r>g*1.12 and g>b*1.12 and outlier
        sword=kind in ('warrior','knight') and metallic and outlier
        shield=kind in ('warrior','defender','knight') and outlier and y>78
        return bow or sword or shield
    return nearest_partition(im,centers,equip)

def rider_layers(im):
    centers={'head':(93,42),'torso':(91,76),'left_upper_arm':(71,77),'left_forearm':(61,98),'right_upper_arm':(111,77),'right_forearm':(125,98),'mount_body':(105,128),'mount_front_leg':(131,164),'mount_back_leg':(78,164),'rider_leg':(88,112)}
    return nearest_partition(im,centers,lambda x,y,c:(x<40 or x>157) and 45<y<130)

def catapult_layers(im):
    centers={'chassis':(96,126),'left_wheel':(59,153),'right_wheel':(137,153),'throw_arm':(96,73),'payload':(111,42),'support_left':(67,111),'support_right':(126,111)}
    return nearest_partition(im,centers,None)

def alpha_bbox(layers):
    base=blank()
    for im in layers.values(): base.alpha_composite(im)
    return base.getbbox() or (45,28,148,184)

def save_layers(fid,kind,layers):
    root=RIG/f'{fid}_{kind}';root.mkdir(parents=True,exist_ok=True)
    for name,im in layers.items(): im.save(root/f'{name}.webp','WEBP',lossless=True,method=5)
    sh=blank();d=ImageDraw.Draw(sh);d.ellipse((60,166,134,184),fill=(0,0,0,78));sh=sh.filter(ImageFilter.GaussianBlur(3));sh.save(root/'shadow.webp','WEBP',lossless=True,method=5)

def transform(piece, angle=0, pivot=(96,96), dx=0, dy=0):
    p=piece.rotate(angle,Image.Resampling.BICUBIC,center=pivot,expand=False)
    if dx or dy: p=p.transform(p.size,Image.Transform.AFFINE,(1,0,-dx,0,1,-dy),resample=Image.Resampling.BICUBIC)
    return p

def compose(layers, order, pose, kind):
    out=blank();shadow=blank();ImageDraw.Draw(shadow).ellipse((60,166,134,184),fill=(0,0,0,70));shadow=shadow.filter(ImageFilter.GaussianBlur(3));out.alpha_composite(shadow)
    params={k:(0,(96,96),0,0) for k in layers}
    def setp(k,a=0,p=(96,96),dx=0,dy=0):
        if k in params: params[k]=(a,p,dx,dy)
    if kind in humanoids:
        if pose=='idle': setp('head',-1,(96,66),0,-1);setp('torso',1,(96,128))
        elif pose=='move1':
            setp('left_upper_arm',-14,(76,78));setp('left_forearm',-17,(63,104));setp('right_upper_arm',14,(116,78));setp('right_forearm',17,(131,104));setp('left_thigh',10,(86,133));setp('left_shin',-9,(80,157));setp('right_thigh',-10,(106,133));setp('right_shin',9,(112,157));setp('torso',2,(96,128),0,-2);setp('head',-2,(96,66),0,-3)
        elif pose=='move2':
            setp('left_upper_arm',14,(76,78));setp('left_forearm',17,(63,104));setp('right_upper_arm',-14,(116,78));setp('right_forearm',-17,(131,104));setp('left_thigh',-10,(86,133));setp('left_shin',9,(80,157));setp('right_thigh',10,(106,133));setp('right_shin',-9,(112,157));setp('torso',-2,(96,128),0,-1);setp('head',2,(96,66),0,-2)
        elif pose=='attack1': setp('right_upper_arm',-27,(116,78),3,-3);setp('right_forearm',-38,(132,102),5,-5);setp('equipment',-34,(135,105),5,-5);setp('torso',-5,(96,128));setp('head',-3,(96,66))
        elif pose=='attack2': setp('right_upper_arm',38,(116,78),7,3);setp('right_forearm',52,(132,102),10,5);setp('equipment',48,(135,105),10,5);setp('torso',7,(96,128),4,0);setp('head',4,(96,66),4,0)
        elif pose=='hit':
            for k in params:setp(k,-7,(96,145),-5,0)
        elif pose=='death':
            for k in params:setp(k,66,(96,170),15,8)
    elif kind=='rider':
        if pose=='move1':setp('mount_front_leg',18,(129,143));setp('mount_back_leg',-18,(81,143));setp('torso',-3,(94,103),0,-3);setp('head',-3,(94,61),0,-4)
        elif pose=='move2':setp('mount_front_leg',-18,(129,143));setp('mount_back_leg',18,(81,143));setp('torso',3,(94,103),0,-2);setp('head',3,(94,61),0,-3)
        elif pose=='attack1':setp('right_upper_arm',-25,(109,67));setp('right_forearm',-35,(122,89));setp('equipment',-30,(126,91));setp('torso',-4,(93,103))
        elif pose=='attack2':setp('right_upper_arm',38,(109,67));setp('right_forearm',48,(122,89));setp('equipment',45,(126,91));setp('torso',6,(93,103),3,0)
        elif pose=='hit':
            for k in params:setp(k,-6,(100,145),-5,0)
        elif pose=='death':
            for k in params:setp(k,60,(100,172),14,9)
    else:
        if pose=='move1':setp('left_wheel',16,(59,153));setp('right_wheel',16,(137,153));setp('chassis',0,(96,126),2,0)
        elif pose=='move2':setp('left_wheel',-16,(59,153));setp('right_wheel',-16,(137,153));setp('chassis',0,(96,126),-2,0)
        elif pose=='attack1':setp('throw_arm',-30,(92,112));setp('payload',-30,(92,112))
        elif pose=='attack2':setp('throw_arm',42,(92,112));setp('payload',42,(92,112),9,-9)
        elif pose=='hit':
            for k in params:setp(k,-5,(96,150),-5,0)
        elif pose=='death':
            for k in params:setp(k,50,(96,170),12,8)
    for name in order:
        if name not in layers:continue
        a,p,dx,dy=params[name];out.alpha_composite(transform(layers[name],a,p,dx,dy))
    return out

manifest={'version':'1.3.1','source':'AI-authored V1.3 idle sprites','coordinate_space':[192,192],'units':{}}
for fid in factions:
    for kind in all_kinds:
        src=ANIM/f'{fid}_{kind}_idle.webp'
        if not src.exists(): raise SystemExit(f'missing AI animation source {src}')
        im=Image.open(src).convert('RGBA')
        layers=humanoid_layers(im,kind) if kind in humanoids else rider_layers(im) if kind=='rider' else catapult_layers(im)
        save_layers(fid,kind,layers)
        if kind in humanoids: order=['left_shin','right_shin','left_thigh','right_thigh','torso','left_upper_arm','right_upper_arm','left_forearm','right_forearm','equipment','head']
        elif kind=='rider': order=['mount_back_leg','mount_front_leg','mount_body','rider_leg','torso','left_upper_arm','right_upper_arm','left_forearm','right_forearm','equipment','head']
        else: order=['left_wheel','right_wheel','support_left','support_right','chassis','throw_arm','payload']
        for pose in ['idle','move1','move2','attack1','attack2','hit','death']:
            compose(layers,order,pose,kind).save(ANIM/f'{fid}_{kind}_{pose}.webp','WEBP',quality=90,method=5)
        manifest['units'][f'{fid}_{kind}']={'parts':sorted(layers.keys())+['shadow'],'poses':['idle','move1','move2','attack1','attack2','hit','death'],'bbox':alpha_bbox(layers)}

(RIG/'rig_manifest.json').write_text(json.dumps(manifest,indent=2))
(ASSETS/'v13_1_qa_manifest.txt').write_text('Mini4X V1.3.1 articulated QA patch\narticulated_units=24\ncore_pose_frames=168\nrig_manifest=rig/rig_manifest.json\n')

gradle=PROJECT/'app/build.gradle.kts'
s=gradle.read_text();s=re.sub(r'versionCode\s*=\s*\d+','versionCode = 14',s);s=re.sub(r'versionName\s*=\s*"[^"]+"','versionName = "1.3.1"',s);gradle.write_text(s)
print((ASSETS/'v13_1_qa_manifest.txt').read_text())
