package com.example.mini4x.research

import com.example.mini4x.ai.StrategicAi
import com.example.mini4x.sim.*
import java.security.MessageDigest

private data class PlannedAiCommand(val command:Command,val events:List<SimEvent>)

object AiTurnPlanReplayHarness {
    private fun presenterActionCap(difficulty:Int)=when(difficulty.coerceIn(1,4)){1->16;2->24;3->36;else->50}
    private fun digest(state:SimState)=MessageDigest.getInstance("SHA-256").digest(GameStateCodec.encode(state).toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}

    private fun planOpponentCycle(start:SimState):Pair<SimState,List<PlannedAiCommand>>{
        val worker=start.deepCopy();val plan=mutableListOf<PlannedAiCommand>()
        while(!worker.finished&&worker.activePlayer!=worker.humanPlayerId){
            val pid=worker.activePlayer;val cap=presenterActionCap(worker.difficulty);var actions=0
            while(!worker.finished&&worker.activePlayer==pid&&actions++<cap){
                val command=StrategicAi.chooseCommand(worker,pid);val result=CommandEngine.execute(worker,command);check(result.accepted){"worker rejected $command :: ${result.reason}"};plan+=PlannedAiCommand(command,result.events.toList());if(command is EndTurn)break
            }
            if(!worker.finished&&worker.activePlayer==pid){val command=EndTurn(pid);val result=CommandEngine.execute(worker,command);check(result.accepted);plan+=PlannedAiCommand(command,result.events.toList())}
        }
        return worker to plan
    }
    private fun replay(start:SimState,plan:List<PlannedAiCommand>):SimState{
        val authoritative=start.deepCopy();plan.forEachIndexed{index,p->val r=CommandEngine.execute(authoritative,p.command);check(r.accepted){"replay rejected step=$index ${p.command}"};check(r.events==p.events){"event divergence step=$index command=${p.command}"}};return authoritative
    }
    private fun scenario(index:Int):GameConfig{
        val sizes=listOf(MapSizeSetting.TINY,MapSizeSetting.SMALL,MapSizeSetting.NORMAL,MapSizeSetting.LARGE,MapSizeSetting.HUGE,MapSizeSetting.MASSIVE);val waters=WaterPreset.entries.toList();val modes=listOf(GameMode.CONQUEST,GameMode.SCORE_30,GameMode.SCORE_RACE)
        return GameConfig(seed=1_700_001L+index*65_537L,mapSize=sizes[index%sizes.size],waterPreset=waters[(index*5+2)%waters.size],gameMode=modes[index%modes.size],factionIds=listOf("asteria","sunspire","virelia","emberhold"),humanPlayerId=0,difficulty=1+index%4,creativeEndless=false)
    }
    @JvmStatic fun main(args:Array<String>){
        val scenarios=args.getOrNull(0)?.toIntOrNull()?.coerceIn(1,64)?:12;var total=0
        repeat(scenarios){index->val state=MapGenerator.create(scenario(index));check(CommandEngine.execute(state,EndTurn(state.humanPlayerId)).accepted);val start=digest(state);val(worker,plan)=planOpponentCycle(state);val replayed=replay(state,plan);val wd=digest(worker);val rd=digest(replayed);check(wd==rd){"scenario-$index plan/replay mismatch start=$start worker=$wd replay=$rd"};check(worker.activePlayer==replayed.activePlayer&&worker.finished==replayed.finished&&worker.winnerId==replayed.winnerId);total+=plan.size;println("ai_plan_replay_scenario=$index difficulty=${state.difficulty} cap=${presenterActionCap(state.difficulty)} size=${state.size} commands=${plan.size} finished=${worker.finished} digest=${wd.take(16)}")}
        println("ai_turn_plan_replay=PASS scenarios=$scenarios commands=$total")
    }
}
