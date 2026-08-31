#!/usr/bin/env python3
from __future__ import annotations
import argparse
from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected one anchor, found {count}')
    return text.replace(old, new, 1)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('--animation-system', type=Path, required=True)
    parser.add_argument('--view-contract', type=Path, required=True)
    args = parser.parse_args()

    anim = args.animation_system.read_text()
    view = args.view_contract.read_text()

    anim = replace_once(
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
    anim = replace_once(
        anim,
        '''    fun enqueue(newClips:List<AnimationClip>){clips.addAll(newClips)}
    fun update(nowMs:Long):Boolean {''',
        '''    fun enqueue(newClips:List<AnimationClip>){clips.addAll(newClips)}
    fun isBusy():Boolean=current!=null||clips.isNotEmpty()
    fun update(nowMs:Long):Boolean {''',
        'animation queue busy state',
    )

    view = replace_once(
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
    view = replace_once(
        view,
        '''    private fun sfx(id:Int,vol:Float=.72f){soundPool.play(id,vol,vol,1,0,1f)}''',
        '''    private fun sfx(id:Int,vol:Float=.72f){if(id!=0&&id in readySounds)soundPool.play(id,vol,vol,1,0,1f)}''',
        'SoundPool playback readiness gate',
    )
    view = replace_once(
        view,
        '''    override fun onDetachedFromWindow(){super.onDetachedFromWindow();soundPool.release();bitmaps.evictAll();atlas.clear()}''',
        '''    override fun onDetachedFromWindow(){super.onDetachedFromWindow();soundPool.setOnLoadCompleteListener(null);soundPool.release();readySounds.clear();bitmaps.evictAll();atlas.clear()}''',
        'SoundPool detach cleanup',
    )
    view = replace_once(
        view,
        '''        override fun onSingleTapUp(e:MotionEvent):Boolean{handleTap(e.x,e.y);return true}''',
        '''        override fun onSingleTapUp(e:MotionEvent):Boolean{
            if(presenter.screen==ScreenMode.WORLD && presenter.animations.isBusy())return true
            handleTap(e.x,e.y);return true
        }''',
        'WORLD animation input gate',
    )

    required_anim = [
        'EventType.TECH_RESEARCHED,EventType.STARS_CHANGED -> Unit',
        'e.playerId==before.humanPlayerId',
        'fun isBusy():Boolean=current!=null||clips.isNotEmpty()',
    ]
    required_view = [
        'ConcurrentHashMap.newKeySet<Int>()',
        'setOnLoadCompleteListener{_,sampleId,status->if(status==0)readySounds+=sampleId}',
        'id!=0&&id in readySounds',
        'soundPool.setOnLoadCompleteListener(null);soundPool.release();readySounds.clear()',
        'presenter.screen==ScreenMode.WORLD && presenter.animations.isBusy()',
    ]
    for marker in required_anim:
        if marker not in anim:
            raise SystemExit(f'missing animation marker: {marker}')
    for marker in required_view:
        if marker not in view:
            raise SystemExit(f'missing view marker: {marker}')

    args.animation_system.write_text(anim)
    args.view_contract.write_text(view)
    print('phase4r6_smoothness_candidate=APPLIED issues=4')


if __name__ == '__main__':
    main()
