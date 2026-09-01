#!/usr/bin/env python3
from pathlib import Path
import os

PROJECT=Path(os.environ.get('MINI4X_PROJECT','project'))
PRESENTATION=PROJECT/'app/src/main/java/com/example/mini4x/presentation'
UI=PROJECT/'app/src/main/java/com/example/mini4x/ui'
anim_path=PRESENTATION/'AnimationSystem.kt'
view_path=UI/'Mini4xView.kt'
anim=anim_path.read_text()
view=view_path.read_text()


def replace_once(text:str,old:str,new:str,label:str)->str:
    count=text.count(old)
    assert count==1,f'{label}: expected one anchor, found {count}'
    return text.replace(old,new,1)


# 4R.6: do not spend queue time on event clips that have no renderer, and never
# turn another player's exploration into human fog feedback.
anim=replace_once(
    anim,
    '''            EventType.TECH_RESEARCHED -> clips += AnimationClip(ClipType.RESEARCH,label=e.message,durationMs=360)
            EventType.STARS_CHANGED -> if((e.amount?:0)>0) clips += AnimationClip(ClipType.INCOME,amount=e.amount,label=e.message,durationMs=380)
            EventType.TILES_DISCOVERED -> clips += AnimationClip(ClipType.FOG_REVEAL,from=e.from,amount=e.amount,durationMs=300)''',
    '''            // RESEARCH and INCOME are not rendered by Mini4xView. Do not add invisible queue time.
            EventType.TECH_RESEARCHED,EventType.STARS_CHANGED -> Unit
            // Fog feedback belongs only to the human viewer, never another player's exploration.
            EventType.TILES_DISCOVERED -> if(e.playerId==before.humanPlayerId) clips += AnimationClip(ClipType.FOG_REVEAL,from=e.from,amount=e.amount,durationMs=300)''',
    'visible animation event mapping',
)

# A queued clip is already a gameplay-input hazard even before the first draw promotes
# it to current. Expose queue busy state rather than checking current only.
anim=replace_once(
    anim,
    '''    fun enqueue(newClips:List<AnimationClip>){clips.addAll(newClips)}
    fun update(nowMs:Long):Boolean {''',
    '''    fun enqueue(newClips:List<AnimationClip>){clips.addAll(newClips)}
    fun isBusy():Boolean=current!=null||clips.isNotEmpty()
    fun update(nowMs:Long):Boolean {''',
    'animation queue busy state',
)

# SoundPool loading is asynchronous. Preserve eager preload, but only play samples after
# Android reports successful completion. This avoids unreliable first-interaction audio.
view=replace_once(
    view,
    '''    private val soundPool=SoundPool.Builder().setMaxStreams(4).build()
    private val sndClick=soundPool.load(context,R.raw.ui_click,1)''',
    '''    private val readySounds=java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
    private val soundPool=SoundPool.Builder().setMaxStreams(4).build().apply{
        setOnLoadCompleteListener{_,sampleId,status->if(status==0)readySounds+=sampleId}
    }
    private val sndClick=soundPool.load(context,R.raw.ui_click,1)''',
    'SoundPool readiness listener',
)
view=replace_once(
    view,
    '''    private fun sfx(id:Int,vol:Float=.72f){soundPool.play(id,vol,vol,1,0,1f)}''',
    '''    private fun sfx(id:Int,vol:Float=.72f){if(id!=0&&id in readySounds)soundPool.play(id,vol,vol,1,0,1f)}''',
    'SoundPool playback readiness gate',
)
view=replace_once(
    view,
    '''    override fun onDetachedFromWindow(){super.onDetachedFromWindow();soundPool.release();bitmaps.evictAll();atlas.clear()}''',
    '''    override fun onDetachedFromWindow(){super.onDetachedFromWindow();soundPool.setOnLoadCompleteListener(null);soundPool.release();readySounds.clear();bitmaps.evictAll();atlas.clear()}''',
    'SoundPool detach cleanup',
)

# Simulation may already have advanced while WORLD clips replay the prior state. Suppress
# only single-tap gameplay/HUD dispatch while visible work is queued/current; pinch, pan and
# double-tap camera gestures continue through their existing gesture paths.
view=replace_once(
    view,
    '''        override fun onSingleTapUp(e:MotionEvent):Boolean{handleTap(e.x,e.y);return true}''',
    '''        override fun onSingleTapUp(e:MotionEvent):Boolean{
            if(presenter.screen==ScreenMode.WORLD && presenter.animations.isBusy())return true
            handleTap(e.x,e.y);return true
        }''',
    'WORLD animation input gate',
)

for marker in [
    'EventType.TECH_RESEARCHED,EventType.STARS_CHANGED -> Unit',
    'EventType.TILES_DISCOVERED -> if(e.playerId==before.humanPlayerId)',
    'fun isBusy():Boolean=current!=null||clips.isNotEmpty()',
]:
    assert marker in anim,marker
for marker in [
    'ConcurrentHashMap.newKeySet<Int>()',
    'setOnLoadCompleteListener{_,sampleId,status->if(status==0)readySounds+=sampleId}',
    'id!=0&&id in readySounds',
    'soundPool.setOnLoadCompleteListener(null);soundPool.release();readySounds.clear()',
    'presenter.screen==ScreenMode.WORLD && presenter.animations.isBusy()',
]:
    assert marker in view,marker

anim_path.write_text(anim)
view_path.write_text(view)
print('V1.4 Phase 4R.6 smoothness applied: visible queue filtering + WORLD tap gate + SoundPool readiness')
