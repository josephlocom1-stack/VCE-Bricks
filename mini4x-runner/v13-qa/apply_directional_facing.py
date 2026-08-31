#!/usr/bin/env python3
from pathlib import Path
from PIL import Image, ImageOps, ImageDraw, ImageFilter
import os, json, math

PROJECT=Path(os.environ.get('MINI4X_PROJECT','project'))
ASSETS=PROJECT/'app/src/main/assets/mini4x'
ANIM=ASSETS/'anim';RIG=ASSETS/'rig';RIG.mkdir(parents=True,exist_ok=True)
factions=['asteria','sunspire','virelia','emberhold']
core=['warrior','archer','defender','knight','rider','catapult']
extra_humanoids=['swordsman','mind_bender','cloak','giant','guerrilla']
naval=['raft','rammer','scout','bomber','juggernaut']
all_units=core+extra_humanoids+naval
poses=['idle','move1','move2','attack1','attack2','hit','death']

def blank(): return Image.new('RGBA',(192,192),(0,0,0,0))

def nearest_partition(im,centers,equipment_rule=None):
    src=im.convert('RGBA');px=src.load();layers={k:blank() for k in centers};out={k:v.load() for k,v in layers.items()}
    if equipment_rule:
        layers['equipment']=blank();out['equipment']=layers['equipment'].load()
    for y in range(192):
        for x in range(192):
            rgba=px[x,y]
            if rgba[3]<8: continue
            if equipment_rule and equipment_rule(x,y,rgba):
                out['equipment'][x,y]=rgba;continue
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
        outlier=(x<43 or x>149) and 28<y<169
        metallic=sat<52 and mx>118
        staff=kind=='mind_bender' and outlier
        blade=kind in ('swordsman','guerrilla') and outlier and metallic
        cloak_tool=kind=='cloak' and outlier and y>62
        giant_tool=kind=='giant' and outlier and metallic
        return staff or blade or cloak_tool or giant_tool
    return nearest_partition(im,centers,equip)

def naval_layers(im,kind):
    centers={
      'hull_front':(137,139),'hull_mid':(98,144),'hull_back':(56,139),
      'deck':(96,120),'mast':(96,82),'sail':(116,76),'weapon':(132,104)}
    def equip(x,y,c):
        return (x>153 and 45<y<145) or (kind=='bomber' and y<58 and x>111)
    return nearest_partition(im,centers,equip)

def save_layers(fid,kind,layers,water=False):
    root=RIG/f'{fid}_{kind}';root.mkdir(parents=True,exist_ok=True)
    for name,im in layers.items(): im.save(root/f'{name}.webp','WEBP',lossless=True,method=5)
    sh=blank();d=ImageDraw.Draw(sh)
    if water:d.ellipse((53,166,140,182),fill=(22,76,105,58))
    else:d.ellipse((60,166,134,184),fill=(0,0,0,72))
    sh=sh.filter(ImageFilter.GaussianBlur(3));sh.save(root/'shadow.webp','WEBP',lossless=True,method=5)

def transform(piece,angle=0,pivot=(96,96),dx=0,dy=0):
    p=piece.rotate(angle,Image.Resampling.BICUBIC,center=pivot,expand=False)
    if dx or dy:p=p.transform(p.size,Image.Transform.AFFINE,(1,0,-dx,0,1,-dy),resample=Image.Resampling.BICUBIC)
    return p

def shadow(water=False):
    sh=blank();d=ImageDraw.Draw(sh)
    d.ellipse((53 if water else 60,166,140 if water else 134,182 if water else 184),fill=(22,76,105,52) if water else (0,0,0,66))
    return sh.filter(ImageFilter.GaussianBlur(3))

def compose_humanoid(layers,pose):
    order=['left_shin','right_shin','left_thigh','right_thigh','torso','left_upper_arm','right_upper_arm','left_forearm','right_forearm','equipment','head']
    prm={k:(0,(96,96),0,0) for k in layers}
    def setp(k,a=0,p=(96,96),dx=0,dy=0):
        if k in prm:prm[k]=(a,p,dx,dy)
    if pose=='idle':setp('head',-1,(96,66),0,-1);setp('torso',1,(96,128))
    elif pose=='move1':
        setp('left_upper_arm',-13,(76,78));setp('left_forearm',-16,(63,104));setp('right_upper_arm',13,(116,78));setp('right_forearm',16,(131,104));setp('left_thigh',9,(86,133));setp('left_shin',-8,(80,157));setp('right_thigh',-9,(106,133));setp('right_shin',8,(112,157));setp('torso',2,(96,128),0,-2);setp('head',-2,(96,66),0,-3)
    elif pose=='move2':
        setp('left_upper_arm',13,(76,78));setp('left_forearm',16,(63,104));setp('right_upper_arm',-13,(116,78));setp('right_forearm',-16,(131,104));setp('left_thigh',-9,(86,133));setp('left_shin',8,(80,157));setp('right_thigh',9,(106,133));setp('right_shin',-8,(112,157));setp('torso',-2,(96,128),0,-1);setp('head',2,(96,66),0,-2)
    elif pose=='attack1':setp('right_upper_arm',-28,(116,78),3,-3);setp('right_forearm',-37,(132,102),5,-5);setp('equipment',-34,(135,105),5,-5);setp('torso',-5,(96,128));setp('head',-3,(96,66))
    elif pose=='attack2':setp('right_upper_arm',39,(116,78),7,3);setp('right_forearm',51,(132,102),10,5);setp('equipment',47,(135,105),10,5);setp('torso',7,(96,128),4,0);setp('head',4,(96,66),4,0)
    elif pose=='hit':
        for k in prm:setp(k,-7,(96,145),-5,0)
    elif pose=='death':
        for k in prm:setp(k,66,(96,170),15,8)
    out=shadow(False)
    for name in order:
        if name in layers:
            a,p,dx,dy=prm[name];out.alpha_composite(transform(layers[name],a,p,dx,dy))
    return out

def compose_naval(layers,pose):
    order=['hull_back','hull_mid','deck','mast','sail','hull_front','weapon','equipment']
    prm={k:(0,(96,130),0,0) for k in layers}
    def setp(k,a=0,p=(96,130),dx=0,dy=0):
        if k in prm:prm[k]=(a,p,dx,dy)
    if pose=='move1':
        setp('hull_front',0,(96,145),2,-1);setp('hull_mid',0,(96,145),2,-1);setp('hull_back',0,(96,145),2,-1);setp('mast',-2,(96,132),2,-3);setp('sail',-3,(96,116),2,-3)
    elif pose=='move2':
        setp('hull_front',0,(96,145),-2,0);setp('hull_mid',0,(96,145),-2,0);setp('hull_back',0,(96,145),-2,0);setp('mast',2,(96,132),-2,-1);setp('sail',3,(96,116),-2,-1)
    elif pose=='attack1':setp('weapon',-18,(122,120),-2,-3);setp('equipment',-18,(122,120),-2,-3);setp('mast',-2,(96,132))
    elif pose=='attack2':setp('weapon',27,(122,120),7,-5);setp('equipment',27,(122,120),7,-5);setp('mast',3,(96,132))
    elif pose=='hit':
        for k in prm:setp(k,-5,(96,155),-5,1)
    elif pose=='death':
        for k in prm:setp(k,42,(96,172),12,9)
    out=shadow(True)
    for name in order:
        if name in layers:
            a,p,dx,dy=prm[name];out.alpha_composite(transform(layers[name],a,p,dx,dy))
    return out

rig_path=RIG/'rig_manifest.json';manifest=json.loads(rig_path.read_text())
for fid in factions:
    for kind in extra_humanoids+naval:
        src=ANIM/f'{fid}_{kind}_idle.webp'
        if not src.exists():raise SystemExit(f'missing full-roster animation source {src}')
        im=Image.open(src).convert('RGBA')
        water=kind in naval
        layers=naval_layers(im,kind) if water else humanoid_layers(im,kind)
        save_layers(fid,kind,layers,water)
        for pose in poses:
            frame=compose_naval(layers,pose) if water else compose_humanoid(layers,pose)
            frame.save(ANIM/f'{fid}_{kind}_{pose}.webp','WEBP',quality=90,method=5)
        manifest['units'][f'{fid}_{kind}']={
            'parts':sorted(layers.keys())+['shadow'],
            'poses':poses,
            'source':'AI-derived standard-roster idle sprite',
            'domain':'water' if water else 'land'
        }

count=0
for fid in factions:
    for unit in all_units:
        for pose in poses:
            p=ANIM/f'{fid}_{unit}_{pose}.webp'
            if not p.exists():raise SystemExit(f'missing articulated pose {p}')
            im=Image.open(p).convert('RGBA')
            ImageOps.mirror(im).save(ANIM/f'{fid}_{unit}_{pose}_left.webp','WEBP',quality=90,method=5)
            count+=1

view=PROJECT/'app/src/main/java/com/example/mini4x/ui/Mini4xView.kt';s=view.read_text()
old='''        val animPath="anim/${fid}_${kind}_${pose}.webp"\n        val fallback="units/${fid}_${kind}_body.png"\n        val chosen=if(bmp(animPath)!=null)animPath else fallback'''
new='''        val facingLeft=ownsClip && clip?.from!=null && clip.to!=null && worldCenter(clip.to).x < worldCenter(clip.from).x\n        val animPath=if(facingLeft) "anim/${fid}_${kind}_${pose}_left.webp" else "anim/${fid}_${kind}_${pose}.webp"\n        val fallback="units/${fid}_${kind}_body.png"\n        val chosen=if(bmp(animPath)!=null)animPath else fallback'''
if old in s:view.write_text(s.replace(old,new,1))
elif 'val facingLeft=ownsClip' not in s:raise SystemExit('drawUnit animation block not found')

manifest['version']='1.3.1-full-roster-rig'
manifest['directional_views']=['right/base','left/mirrored']
manifest['runtime_facing']='movement and attack vector'
manifest['full_roster_units']=all_units
rig_path.write_text(json.dumps(manifest,indent=2))

qa=ASSETS/'v13_1_qa_manifest.txt'
lines=[line for line in qa.read_text().splitlines() if not line.startswith(('articulated_units=','core_pose_frames=','directional_pose_variants=','facing_policy=','full_roster_unit_kinds='))]
lines += [
    'articulated_units=64',
    'core_pose_frames=448',
    f'directional_pose_variants={count}',
    'facing_policy=movement_attack_vector',
    'full_roster_unit_kinds='+','.join(all_units)
]
qa.write_text('\n'.join(lines)+'\n')
print(f'full roster: 64 articulated faction/unit rigs, 448 base poses, {count} directional variants')
