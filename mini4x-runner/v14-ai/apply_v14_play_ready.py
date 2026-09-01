#!/usr/bin/env python3
from pathlib import Path
import os,re
P=Path(os.environ.get('MINI4X_PROJECT','project'))
root=P/'build.gradle.kts'; app=P/'app/build.gradle.kts'
r=root.read_text();a=app.read_text()
# API 36 requires AGP >=8.9.1. Use stable 8.10.1; workflow uses Gradle 8.11.1.
r2=re.sub(r'(com\.android\.application[^\n]*version\s+")[^"]+("[^\n]*apply\s+false)',r'\g<1>8.10.1\2',r)
if r2==r:
    r2=re.sub(r'(id\("com\.android\.application"\)\s+version\s+")[^"]+(")',r'\g<1>8.10.1\2',r)
if '8.10.1' not in r2: raise SystemExit('AGP version anchor not found')
a2=re.sub(r'compileSdk\s*=\s*\d+','compileSdk = 36',a)
a2=re.sub(r'targetSdk\s*=\s*\d+','targetSdk = 36',a2)
if 'compileSdk = 36' not in a2 or 'targetSdk = 36' not in a2: raise SystemExit('SDK anchors not found')
root.write_text(r2);app.write_text(a2)
print('Play readiness: AGP 8.10.1 compileSdk 36 targetSdk 36')
