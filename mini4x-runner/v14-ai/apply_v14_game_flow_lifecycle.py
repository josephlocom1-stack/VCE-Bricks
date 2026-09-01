#!/usr/bin/env python3
from pathlib import Path
import os

PROJECT=Path(os.environ.get('MINI4X_PROJECT','project'))
view=PROJECT/'app/src/main/java/com/example/mini4x/ui/Mini4xView.kt'
s=view.read_text()
assert 'presenter.screen!=ScreenMode.RESULT' not in s,'game flow lifecycle patch already applied'

# GamePresenter.execute() owns the terminal transition. Callers may return to their normal
# screen only when execution did not finish the game.
old='''        if(modalPrimaryRect?.contains(x,y)==true){val id=selectedTechId?:return;sfx(sndResearch);if(presenter.execute(BuyTechnology(0,id)))presenter.setScreen(ScreenMode.TECH)}'''
new='''        if(modalPrimaryRect?.contains(x,y)==true){val id=selectedTechId?:return;sfx(sndResearch);if(presenter.execute(BuyTechnology(0,id))&&presenter.screen!=ScreenMode.RESULT)presenter.setScreen(ScreenMode.TECH)}'''
assert old in s,'tech-detail terminal transition anchor changed'
s=s.replace(old,new,1)

old='''        actionRects.firstOrNull{it.first.contains(x,y)}?.let{sfx(soundFor(it.second));presenter.execute(it.second);presenter.setScreen(ScreenMode.WORLD)}'''
new='''        actionRects.firstOrNull{it.first.contains(x,y)}?.let{sfx(soundFor(it.second));presenter.execute(it.second);if(presenter.screen!=ScreenMode.RESULT)presenter.setScreen(ScreenMode.WORLD)}'''
assert old in s,'action-sheet terminal transition anchor changed'
s=s.replace(old,new,1)

for required in [
    'presenter.execute(BuyTechnology(0,id))&&presenter.screen!=ScreenMode.RESULT',
    'presenter.execute(it.second);if(presenter.screen!=ScreenMode.RESULT)presenter.setScreen(ScreenMode.WORLD)',
]:
    assert required in s,required

view.write_text(s)
print('V1.4 game-flow lifecycle: command/technology callers preserve GamePresenter terminal RESULT transitions')
