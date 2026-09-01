#!/usr/bin/env python3
from pathlib import Path
from PIL import Image
import base64, hashlib, io, json, os, re
import numpy as np

PROJECT=Path(os.environ.get('MINI4X_PROJECT','project'))
ROOT=Path(__file__).parent
ASSETS=PROJECT/'app/src/main/assets/mini4x'
ANIM=ASSETS/'anim'; ATLASES=ASSETS/'atlases'
MASTER_SOURCE=ROOT/'naval-safe-sources/production_naval_master_compact.webp.b64'
RAFT_SOURCE=ROOT/'naval-safe-sources/raft_styleA_production.png.b64'

factions={'asteria':(47,169,217),'sunspire':(240,196,49),'virelia':(164,104,216),'emberhold':(223,103,80)}
naval=['raft','rammer','scout','bomber','juggernaut']
CELL=128

def decode_image(path):
    if not path.exists(): raise SystemExit(f'missing naval AI source {path}')
    encoded=path.read_text().strip()
    if len(encoded)%4: raise SystemExit(f'naval AI source base64 is truncated: {path} ({len(encoded)} chars)')
    raw=base64.b64decode(encoded,validate=True)
    return Image.open(io.BytesIO(raw)).convert('RGBA'),raw

master,master_raw=decode_image(MASTER_SOURCE)
if master.size!=(CELL*5,CELL): raise SystemExit(f'unexpected naval master size {master.size}')
master.save(ASSETS/'v14_naval_ai_master.webp','WEBP',quality=90,method=6)
base={kind:master.crop((i*CELL,0,(i+1)*CELL,CELL)) for i,kind in enumerate(naval)}

# Recovery lock: use the accepted standalone Style-A raft instead of continuing an
# open-ended refinement loop. It is a transparent 128x128 gameplay-scale asset and
# remains the canonical Raft source until a deliberate future art pass replaces it.
raft,raft_raw=decode_image(RAFT_SOURCE)
if raft.size!=(CELL,CELL): raise SystemExit(f'unexpected locked raft size {raft.size}; expected {(CELL,CELL)}')
if raft.getbbox() is None: raise SystemExit('locked raft source is fully transparent')
base['raft']=raft
raft.save(ASSETS/'v14_raft_styleA_locked.png','PNG',optimize=True)
raft_sha256=hashlib.sha256(raft_raw).hexdigest()

def tint_sail(im,rgb):
    arr=np.array(im).astype(np.float32);a=arr[:,:,3];R,G,B=[arr[:,:,i] for i in range(3)]
    mask=((B>110)&(B>R*1.06)&(B>G*.93)&(a>20)).astype(np.float32)*.68
    mask=mask[...,None];target=np.array(rgb,dtype=np.float32).reshape(1,1,3)
    arr[:,:,:3]=arr[:,:,:3]*(1-mask)+target*mask
    return Image.fromarray(np.clip(arr,0,255).astype(np.uint8),'RGBA')

def fit(im,w,h):
    bb=im.getbbox()
    if not bb:return Image.new('RGBA',(w,h))
    c=im.crop(bb);scale=min((w-6)/c.width,(h-6)/c.height)
    c=c.resize((max(1,int(c.width*scale)),max(1,int(c.height*scale))),Image.Resampling.LANCZOS)
    out=Image.new('RGBA',(w,h));out.alpha_composite(c,((w-c.width)//2,h-3-c.height));return out

index_path=PROJECT/'app/src/main/java/com/example/mini4x/ui/GameAtlasIndex.kt'
idx=index_path.read_text();entry_re=re.compile(r'"([^"]+)" to AtlasEntry\("([^"]+)", Rect\((\d+), (\d+), (\d+), (\d+)\)\)')
entries={m.group(1):(m.group(2),tuple(map(int,m.groups()[2:]))) for m in entry_re.finditer(idx)}
atlas={p.stem:Image.open(p).convert('RGBA') for p in ATLASES.glob('*.webp')}
def paste(path,im):
    if path not in entries:return False
    grp,(l,t,r,b)=entries[path];target=atlas.get(grp)
    if target is None:return False
    target.alpha_composite(fit(im,r-l,b-t),(l,t));return True

manifest_path=ASSETS/'v14_full_roster_ai_manifest.json'
manifest=json.loads(manifest_path.read_text())
manifest['source_policy']='accepted Style-A AI humanoid derivatives; locked accepted Style-A raft; dedicated AI-authored low-poly master for remaining naval units'
manifest['naval_source']='v14_naval_ai_master.webp'
manifest['naval_source_dimensions']=list(master.size)
manifest['raft_source']='v14_raft_styleA_locked.png'
manifest['raft_source_dimensions']=list(raft.size)
manifest['raft_source_sha256']=raft_sha256
manifest['raft_acceptance']='locked_after_recovery_review_no_further_refinement_required'
manifest['naval_animation_contract']='directional pass partitions each source into hull_back,hull_mid,deck,mast,sail,hull_front,weapon,equipment,shadow and generates seven poses plus left-facing variants'

for fid,rgb in factions.items():
    for kind in naval:
        im=tint_sail(base[kind],rgb)
        im=fit(im,192,192)
        im.save(ANIM/f'{fid}_{kind}_idle.webp','WEBP',quality=91,method=6)
        if not paste(f'units/{fid}_{kind}_body.png',im):
            raise SystemExit(f'missing atlas entry for naval unit {fid}/{kind}')
        locked=(kind=='raft')
        manifest['units'][f'{fid}_{kind}']={
            'source':'locked accepted Style-A raft' if locked else 'dedicated AI-authored low-poly naval master',
            'status':'accepted_locked_source' if locked else 'ai_authored_source',
            'animation_source':'modular_naval_body_parts',
            'master_cell':None if locked else naval.index(kind)
        }

for grp,im in atlas.items(): im.save(ATLASES/f'{grp}.webp','WEBP',lossless=True,method=4)
manifest_path.write_text(json.dumps(manifest,indent=2))
print('V1.4 naval AI source pass:',len(factions)*len(naval),'faction/unit source entries; locked raft sha256=',raft_sha256,'; total manifest entries=',len(manifest['units']))
