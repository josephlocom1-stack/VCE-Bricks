package com.example.mini4x.research

import com.example.mini4x.ai.StrategicAi
import com.example.mini4x.sim.*
import java.security.MessageDigest

private data class SelfPlayResult(val finished:Boolean,val winnerFaction:String?,val rounds:Int,val commands:Int,val forcedEndTurns:Int,val voluntaryEndWithOptions:Int,val coverage:Map<String,Int>,val digest:String)

object AiSelfPlaySoakHarness {
    private fun presenterActionCap(difficulty:Int)=when(difficulty.coerceIn(1,4)){1->16;2->24;3->36;else->50}
    private fun key(command:Command)=when(command){is MoveUnit->"move";is AttackUnit->"attack";is RecoverUnit->"recover";is TrainUnit->"train";is BuyTechnology->"technology";is HarvestResource->"harvest";is BuildImprovement->"improvement";is ChooseCityReward->"city_reward";is CaptureCity->"capture";is ExamineRuin->"ruin";is BuildRoad->"road";is EstablishEmbassy->"embassy";is OfferPeace->"peace_offer";is BreakPeace->"peace_break";is UpgradeShip->"ship_upgrade";is GatherStarfish->"starfish";is SpecialAction->"special_${command.action.name.lowercase()}";is EndTurn->"end_turn"}
    private fun digest(state:SimState)=MessageDigest.getInstance("SHA-256").digest(GameStateCodec.encode(state).toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
    private fun roundTrip(state:SimState,label:String){val encoded=GameStateCodec.encode(state);val decoded=GameStateCodec.decode(encoded);SimInvariantAudit.assertValid(decoded,"$label/decoded");check(GameStateCodec.encode(decoded)==encoded){"$label save round-trip changed canonical state"}}
    private fun config(index:Int):GameConfig{
        val sizes=listOf(MapSizeSetting.TINY,MapSizeSetting.SMALL,MapSizeSetting.NORMAL,MapSizeSetting.LARGE,MapSizeSetting.HUGE);val waters=WaterPreset.entries.toList();val modes=listOf(GameMode.CONQUEST,GameMode.SCORE_30,GameMode.SCORE_RACE);val orders=listOf(listOf("asteria","sunspire","virelia","emberhold"),listOf("virelia","emberhold","asteria","sunspire"),listOf("sunspire","asteria","emberhold","virelia"),listOf("emberhold","virelia","sunspire","asteria"))
        return GameConfig(seed=2_000_003L+index*104_729L,mapSize=sizes[index%sizes.size],waterPreset=waters[(index*3+1)%waters.size],gameMode=modes[index%modes.size],factionIds=orders[index%orders.size],humanPlayerId=0,difficulty=1+index%4,creativeEndless=false)
    }
    private fun play(config:GameConfig,maxRounds:Int,label:String):SelfPlayResult{
        val state=MapGenerator.create(config);val coverage=sortedMapOf<String,Int>();var commands=0;var forced=0;var early=0;val cap=presenterActionCap(config.difficulty);SimInvariantAudit.assertValid(state,"$label/initial")
        while(!state.finished&&state.roundNumber<=maxRounds){val pid=state.activePlayer;check(!state.players[pid].eliminated);var attempts=0
            while(!state.finished&&state.activePlayer==pid&&attempts++<cap){val legal=LegalCommandGenerator.all(state,pid,includeEndTurn=true);val nonEnd=legal.any{it !is EndTurn};val command=StrategicAi.chooseCommand(state,pid);if(command is EndTurn&&nonEnd)early++;val before=state.actionSerial;val r=CommandEngine.execute(state,command);check(r.accepted){"$label AI $pid rejected ${key(command)}: ${r.reason}"};check(state.actionSerial==before+1);coverage[key(command)]=(coverage[key(command)]?:0)+1;commands++;SimInvariantAudit.assertValid(state,"$label/cmd-$commands/${key(command)}");if(commands%40==0)roundTrip(state,"$label/save-$commands");if(command is EndTurn)break}
            if(!state.finished&&state.activePlayer==pid){val r=CommandEngine.execute(state,EndTurn(pid));check(r.accepted);forced++;coverage["forced_end_turn"]=(coverage["forced_end_turn"]?:0)+1;commands++;SimInvariantAudit.assertValid(state,"$label/forced-end-$commands")}}
        roundTrip(state,"$label/final");return SelfPlayResult(state.finished,state.winnerId?.let{state.players[it].factionId},state.roundNumber,commands,forced,early,coverage,digest(state))
    }
    @JvmStatic fun main(args:Array<String>){
        val games=args.getOrNull(0)?.toIntOrNull()?.coerceIn(1,500)?:24;val maxRounds=args.getOrNull(1)?.toIntOrNull()?.coerceIn(20,300)?:80;val winners=sortedMapOf<String,Int>();val coverage=sortedMapOf<String,Int>();var finished=0;var commands=0;var forced=0;var early=0;var roundSum=0L
        repeat(games){index->val cfg=config(index);val result=play(cfg,maxRounds,"game-$index");if(result.finished)finished++;result.winnerFaction?.let{winners[it]=(winners[it]?:0)+1};result.coverage.forEach{(family,count)->coverage[family]=(coverage[family]?:0)+count};commands+=result.commands;forced+=result.forcedEndTurns;early+=result.voluntaryEndWithOptions;roundSum+=result.rounds;println("selfplay_game=$index difficulty=${cfg.difficulty} cap=${presenterActionCap(cfg.difficulty)} size=${cfg.mapSize} water=${cfg.waterPreset} mode=${cfg.gameMode} finished=${result.finished} winner=${result.winnerFaction?:"none"} rounds=${result.rounds} commands=${result.commands} forcedEnds=${result.forcedEndTurns} endWithOptions=${result.voluntaryEndWithOptions} digest=${result.digest.take(16)}")}
        println("ai_selfplay_soak=PASS games=$games finished=$finished commands=$commands meanRounds=${"%.2f".format(roundSum.toDouble()/games)} forcedEnds=$forced voluntaryEndWithOptions=$early winners=$winners coverage=$coverage")
    }
}
