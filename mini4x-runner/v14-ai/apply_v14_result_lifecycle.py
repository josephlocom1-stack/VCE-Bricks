#!/usr/bin/env python3
from pathlib import Path
import os

PROJECT=Path(os.environ.get('MINI4X_PROJECT','project'))
view=PROJECT/'app/src/main/java/com/example/mini4x/ui/Mini4xView.kt'
s=view.read_text()
assert 'private var resultNewGameRect:' not in s,'result lifecycle patch already applied'

anchor='''    private var titleRects=emptyList<RectF>()
    private var helpReturn=ScreenMode.MENU'''
replacement='''    private var titleRects=emptyList<RectF>()
    private var resultNewGameRect:RectF?=null
    private var helpReturn=ScreenMode.MENU'''
assert anchor in s,'result rect state anchor changed'
s=s.replace(anchor,replacement,1)

old='''    private fun drawResult(c:Canvas){val s=presenter.state;val win=s.winnerId==0;text.typeface=Typeface.DEFAULT_BOLD;text.textSize=31*d;text.color=if(win)Color.rgb(90,225,105) else Color.rgb(235,95,74);c.drawText(if(win)"VICTORY" else "DEFEAT",width/2f,height*.36f,text);text.typeface=Typeface.DEFAULT;text.textSize=13*d;text.color=Color.WHITE;c.drawText(s.victoryReason,width/2f,height*.41f,text);c.drawText("Score ${s.score[0]} · Round ${s.roundNumber}",width/2f,height*.46f,text);paint.color=playerFactionColor(0);c.drawRoundRect(width*.28f,height*.56f,width*.72f,height*.62f,25*d,25*d,paint);text.color=Color.WHITE;c.drawText("NEW GAME",width/2f,height*.595f,text)}'''
new='''    private fun leaveFinishedGame(){
        saveStore.clear();savedGame=null;activeGame=false;resultNewGameRect=null
        presenter=GamePresenter();selectedMode=0;presenter.setScreen(ScreenMode.TITLE)
    }
    private fun drawResult(c:Canvas){
        val s=presenter.state;val win=s.winnerId==0
        text.typeface=Typeface.DEFAULT_BOLD;text.textSize=31*d;text.color=if(win)Color.rgb(90,225,105) else Color.rgb(235,95,74);c.drawText(if(win)"VICTORY" else "DEFEAT",width/2f,height*.36f,text)
        text.typeface=Typeface.DEFAULT;text.textSize=13*d;text.color=Color.WHITE;c.drawText(s.victoryReason,width/2f,height*.41f,text);c.drawText("Score ${s.score[0]} · Round ${s.roundNumber}",width/2f,height*.46f,text)
        resultNewGameRect=RectF(width*.28f,height*.56f,width*.72f,height*.62f);paint.color=playerFactionColor(0);c.drawRoundRect(resultNewGameRect!!,25*d,25*d,paint)
        text.color=Color.WHITE;c.drawText("NEW GAME",resultNewGameRect!!.centerX(),resultNewGameRect!!.centerY()+4*d,text)
    }'''
assert old in s,'drawResult anchor changed'
s=s.replace(old,new,1)

old='''            ScreenMode.RESULT->{saveStore.clear();savedGame=null;activeGame=false;presenter=GamePresenter();selectedMode=0;presenter.setScreen(ScreenMode.TITLE)}'''
new='''            ScreenMode.RESULT->{if(resultNewGameRect?.contains(x,y)==true)leaveFinishedGame()}'''
assert old in s,'result tap anchor changed'
s=s.replace(old,new,1)

old='''            ScreenMode.RESULT->{presenter.setScreen(ScreenMode.TITLE)}'''
new='''            ScreenMode.RESULT->{leaveFinishedGame()}'''
assert old in s,'result back anchor changed'
s=s.replace(old,new,1)

for required in [
    'private var resultNewGameRect:RectF?=null',
    'private fun leaveFinishedGame()',
    'if(resultNewGameRect?.contains(x,y)==true)leaveFinishedGame()',
    'ScreenMode.RESULT->{leaveFinishedGame()}',
    'saveStore.clear();savedGame=null;activeGame=false',
]:
    assert required in s,required

view.write_text(s)
print('V1.4 result lifecycle: real New Game hit target; Back and button both clear finished save before title')
