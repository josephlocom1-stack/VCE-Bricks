#!/usr/bin/env python3
from pathlib import Path
import os

PROJECT = Path(os.environ.get('MINI4X_PROJECT', 'project'))
map_generator = PROJECT / 'app/src/main/java/com/example/mini4x/sim/MapGenerator.kt'
s = map_generator.read_text()

old = '        WaterPreset.LAKES -> .72'
new = '        WaterPreset.LAKES -> .87'
count = s.count(old)
assert count == 1, f'LAKES threshold anchor changed: expected 1, found {count}'
assert new not in s, 'LAKES threshold candidate already applied'
s = s.replace(old, new, 1)
assert old not in s and s.count(new) == 1, 'LAKES threshold promotion verification failed'
map_generator.write_text(s)

print('V1.4 Phase 4R.5 LAKES map fix applied: threshold=0.87 scope=single_constant')
