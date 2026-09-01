#!/usr/bin/env python3
from pathlib import Path
import os

PROJECT=Path(os.environ.get('MINI4X_PROJECT','project'))
view=PROJECT/'app/src/main/java/com/example/mini4x/ui/Mini4xView.kt'
s=view.read_text()
assert 'preserve current save until START GAME' not in s,'safe new-game patch already applied'

old='''            3->{saveStore.clear();savedGame=null;activeGame=false;presenter=GamePresenter();selectedMode=0;selectedFaction=0;presenter.setScreen(ScreenMode.TITLE)}'''
new='''            // New Game is recoverable until the player explicitly presses START GAME.
            // Keep the current persisted save/Continue option rather than deleting it on one menu tap.
            3->{activeGame=false;presenter=GamePresenter();selectedMode=0;selectedFaction=0;presenter.setScreen(ScreenMode.TITLE)} // preserve current save until START GAME'''
assert old in s,'menu New Game destructive-clear anchor changed'
s=s.replace(old,new,1)

# Starting a configured replacement game already occurs inside handleTap; persistIfActive()
# then immediately writes the new deterministic state, which is the deliberate replacement point.
assert 'if(setupStartRect?.contains(x,y)==true){sfx(sndClick);activeGame=true;' in s
assert '};persistIfActive();invalidate()' in s
assert 'saveStore.clear();savedGame=null' not in s[s.index('private fun tapMenu'):s.index('private fun tapHelp')], 'menu New Game still deletes save eagerly'

view.write_text(s)

# Keep the production sequence at 16 stages: the final V1.4 completion stage applies companion
# transforms after all earlier UI/lifecycle patches have settled.
for name in ['apply_v14_landscape_overlay_ui.py','apply_v14_landscape_tech_tree.py']:
    companion=Path(__file__).with_name(name)
    assert companion.is_file(),f'V1.4 responsive companion missing: {name}'
    code=compile(companion.read_text(),str(companion),'exec')
    exec(code,{'__name__':'__main__'})

# Phase 4R.2 is a verified product promotion, not a parallel reconstruction stage.
integrity=Path(__file__).with_name('apply_v14_sim_integrity.py')
assert integrity.is_file(),'V1.4 simulation-integrity companion missing'
code=compile(integrity.read_text(),str(integrity),'exec')
exec(code,{'__name__':'__main__'})

# Phase 4R.3 keeps the V1 wire format and async persistence path while hardening
# mutation gating and malformed-save decoding.
save_hardening=Path(__file__).with_name('apply_v14_save_hardening.py')
assert save_hardening.is_file(),'V1.4 save-hardening companion missing'
code=compile(save_hardening.read_text(),str(save_hardening),'exec')
exec(code,{'__name__':'__main__'})

# Phase 4R.5 restores LAKES as a distinct deterministic water preset. The candidate
# threshold was accepted only after all-size/all-preset Kotlin properties and exact
# non-LAKES full-state digest equivalence passed.
map_lakes=Path(__file__).with_name('apply_v14_map_lakes.py')
assert map_lakes.is_file(),'V1.4 LAKES map companion missing'
code=compile(map_lakes.read_text(),str(map_lakes),'exec')
exec(code,{'__name__':'__main__'})

# Phase 4R.6 removes invisible animation delay/AI fog leakage, blocks WORLD single taps
# while prior-state animation work is queued/current, and gates SoundPool playback on load readiness.
smoothness=Path(__file__).with_name('apply_v14_smoothness.py')
assert smoothness.is_file(),'V1.4 smoothness companion missing'
code=compile(smoothness.read_text(),str(smoothness),'exec')
exec(code,{'__name__':'__main__'})

print('V1.4 safe new game: responsive UI + verified Phase 4R.2 simulation integrity + Phase 4R.3 save hardening + Phase 4R.5 LAKES map fix + Phase 4R.6 smoothness applied')
