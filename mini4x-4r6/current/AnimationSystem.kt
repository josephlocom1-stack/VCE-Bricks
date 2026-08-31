package com.example.mini4x.presentation

import com.example.mini4x.sim.*
import kotlin.math.PI
import kotlin.math.sin

enum class ClipType { MOVE, MELEE, ARROW, PROJECTILE, DAMAGE, DEATH, CAPTURE, CITY_GROW, RESEARCH, INCOME, FOG_REVEAL }

data class AnimationClip(
    val type:ClipType,
    val subjectId:Int?=null,
    val targetId:Int?=null,
    val from:Pos?=null,
    val to:Pos?=null,
    val amount:Int?=null,
    val label:String="",
    val durationMs:Int
) {
    fun progress(elapsedMs:Int)= (elapsedMs.toFloat()/durationMs.coerceAtLeast(1)).coerceIn(0f,1f)
    fun hop(elapsedMs:Int):Float { val p=progress(elapsedMs);return sin(p*PI).toFloat() }
}

object AnimationPlanner {
    fun plan(before:SimState, command:Command, events:List<SimEvent>):List<AnimationClip> {
        val clips=mutableListOf<AnimationClip>()
        when(command) {
            is MoveUnit -> {
                val u=before.unit(command.unitId)
                if(u!=null) clips += AnimationClip(ClipType.MOVE,u.id,from=u.pos,to=command.destination,durationMs=250)
            }
            is AttackUnit -> {
                val a=before.unit(command.attackerId);val d=before.unit(command.defenderId)
                if(a!=null&&d!=null) {
                    val type=when(a.kind){UnitKind.ARCHER,UnitKind.SCOUT->ClipType.ARROW;UnitKind.CATAPULT,UnitKind.BOMBER->ClipType.PROJECTILE;else->ClipType.MELEE}
                    clips += AnimationClip(type,a.id,d.id,a.pos,d.pos,durationMs=if(type==ClipType.MELEE)320 else 430)
                }
            }
            else -> Unit
        }
        for(e in events) when(e.type) {
            EventType.UNIT_MOVED -> if(clips.none{it.type==ClipType.MOVE && it.subjectId==e.subjectId}) clips += AnimationClip(ClipType.MOVE,e.subjectId,from=e.from,to=e.to,durationMs=250)
            EventType.UNIT_DAMAGED -> clips += AnimationClip(ClipType.DAMAGE,e.subjectId,amount=e.amount,to=e.to,durationMs=260)
            EventType.UNIT_KILLED -> clips += AnimationClip(ClipType.DEATH,e.subjectId,to=e.to,durationMs=300)
            EventType.CITY_CAPTURED,EventType.VILLAGE_CAPTURED -> clips += AnimationClip(ClipType.CAPTURE,e.subjectId,to=e.to,durationMs=420)
            EventType.CITY_UPGRADED -> clips += AnimationClip(ClipType.CITY_GROW,e.subjectId,amount=e.amount,durationMs=340)
            EventType.TECH_RESEARCHED -> clips += AnimationClip(ClipType.RESEARCH,label=e.message,durationMs=360)
            EventType.STARS_CHANGED -> if((e.amount?:0)>0) clips += AnimationClip(ClipType.INCOME,amount=e.amount,label=e.message,durationMs=380)
            EventType.TILES_DISCOVERED -> clips += AnimationClip(ClipType.FOG_REVEAL,from=e.from,amount=e.amount,durationMs=300)
            else -> Unit
        }
        return clips.distinct()
    }
}

class AnimationQueue {
    private val clips=ArrayDeque<AnimationClip>()
    var current:AnimationClip?=null; private set
    private var startMs:Long=0
    fun enqueue(newClips:List<AnimationClip>){clips.addAll(newClips)}
    fun update(nowMs:Long):Boolean {
        if(current==null && clips.isNotEmpty()){current=clips.removeFirst();startMs=nowMs}
        val c=current?:return false
        if(nowMs-startMs>=c.durationMs){current=null;if(clips.isNotEmpty()){current=clips.removeFirst();startMs=nowMs}}
        return current!=null||clips.isNotEmpty()
    }
    fun elapsed(nowMs:Long)=(nowMs-startMs).toInt().coerceAtLeast(0)
    fun clear(){clips.clear();current=null}
}
