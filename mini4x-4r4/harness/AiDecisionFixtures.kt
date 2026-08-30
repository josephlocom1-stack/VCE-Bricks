package com.example.mini4x.research

import com.example.mini4x.ai.StrategicAi
import com.example.mini4x.ai.StrategicGoal
import com.example.mini4x.sim.*

object AiDecisionFixtures {
    private fun base(seed: Long, mode: GameMode = GameMode.CONQUEST, difficulty: Int = 4): SimState = MapGenerator.create(
        GameConfig(seed=seed,mapSize=MapSizeSetting.TINY,waterPreset=WaterPreset.DRYLANDS,gameMode=mode,factionIds=listOf("asteria","sunspire"),humanPlayerId=0,difficulty=difficulty)
    ).also { it.activePlayer=0;it.finished=false;it.winnerId=null;it.victoryReason="" }

    private fun removeUnits(state:SimState,owner:Int){
        val removed=state.units.filter{it.owner==owner}.map{it.id}.toSet()
        for(x in 0 until state.size)for(y in 0 until state.size)if(state.map[x][y].occupantUnitId in removed)state.map[x][y].occupantUnitId=null
        state.cities.forEach{it.supportedUnitIds.removeAll(removed)}
        state.units.removeAll{it.owner==owner}
    }
    private fun hideExpansionAndEnemyCities(state:SimState,pid:Int=0){
        state.discoveredTiles[pid].removeAll(state.cities.asSequence().filter{it.owner!=pid}.map{it.pos}.toSet())
        state.discoveredTiles[pid].forEach{state.tile(it)?.village=false}
    }
    private fun freeNeighbourCells(state:SimState,center:Pos)=center.neighbours8().filter(state::inBounds).filter{pos->
        val tile=state.tile(pos)!!;tile.terrain !in setOf(Terrain.WATER,Terrain.OCEAN,Terrain.ICE)&&tile.occupantUnitId==null
    }.sortedWith(compareBy<Pos>{it.x}.thenBy{it.y})
    private fun spawnUnit(state:SimState,owner:Int,pos:Pos,kind:UnitKind):UnitState{
        val id=state.nextUnitId++;val unit=UnitState(id,owner,pos,kind,UnitCatalog[kind].maxHP,independent=true);state.units+=unit;state.tile(pos)!!.occupantUnitId=id;return unit
    }

    private fun captureMustBeatRoutineDevelopment(){
        val state=base(1201L);removeUnits(state,1);val own=state.ownedUnits(0).first();val target=state.ownedCities(1).first()
        state.tile(own.pos)!!.occupantUnitId=null;state.tile(target.pos)!!.occupantUnitId=null;own.pos=target.pos;own.moved=false;own.attacked=false;own.tookAction=false;own.captureReadyOnRound=state.roundNumber;target.besiegedByUnitId=own.id;state.tile(target.pos)!!.occupantUnitId=own.id;state.discoveredTiles[0]+=target.pos
        val expected=CaptureCity(0,own.id);check(CommandEngine.validate(state,expected)==null);check(StrategicAi.chooseCommand(state,0)==expected){"AI ignored ready capture"}
    }
    private fun smallThreatenedEmpireMustDefend(){
        val state=base(1202L);removeUnits(state,1);hideExpansionAndEnemyCities(state);val capital=state.capital(0)!!;val cells=freeNeighbourCells(state,capital.pos);check(cells.size>=2);spawnUnit(state,1,cells[0],UnitKind.SWORDSMAN);spawnUnit(state,1,cells[1],UnitKind.SWORDSMAN)
        check(StrategicAi.threatAt(state,0,capital.pos)>8.5);val plan=StrategicAi.plan(state,0);check(plan.goal==StrategicGoal.DEFEND);check(capital.id in plan.threatenedCityIds)
    }
    private fun discoveredVillageMustTriggerExpansion(){
        val state=base(1203L);removeUnits(state,1);hideExpansionAndEnemyCities(state);val capital=state.capital(0)!!;val candidate=state.discoveredTiles[0].filter{it!=capital.pos&&state.tile(it)?.cityId==null}.sortedWith(compareBy<Pos>{it.x}.thenBy{it.y}).first();state.tile(candidate)!!.village=true;check(StrategicAi.plan(state,0).goal==StrategicGoal.EXPAND)
    }
    private fun quietEarlyPositionMustInvestInTech(){
        val state=base(1204L);removeUnits(state,1);hideExpansionAndEnemyCities(state);state.roundNumber=1;state.players[0].technologies.clear();state.players[0].startingTechnologies.clear();state.players[0].stars=20;val plan=StrategicAi.plan(state,0);check(plan.goal==StrategicGoal.TECH);check(plan.targetTech!=null);check(plan.reserveStars>0)
    }
    private fun maturePoorPositionMustPreferEconomy(){
        val state=base(1205L);removeUnits(state,1);hideExpansionAndEnemyCities(state);state.roundNumber=15;state.players[0].technologies.clear();state.players[0].technologies.addAll(listOf("organization","hunting","fishing","riding","climbing","roads","farming","forestry"));state.players[0].startingTechnologies.clear();check(DerivedState.totalIncome(state,0)<maxOf(5,state.ownedCities(0).size*3));check(StrategicAi.plan(state,0).goal==StrategicGoal.ECONOMY)
    }
    private fun exposedWeakEnemyCityMustTriggerAttack(){
        val state=base(1206L);removeUnits(state,1);hideExpansionAndEnemyCities(state);state.roundNumber=6;val enemyCity=state.ownedCities(1).first();state.discoveredTiles[0]+=enemyCity.pos;check(StrategicAi.militaryPower(state,0)>StrategicAi.militaryPower(state,1).coerceAtLeast(1.0)*.62);check(StrategicAi.plan(state,0).goal==StrategicGoal.ATTACK)
    }
    private fun attackPlanAtPeaceMustBreakTreatyBeforeLocalAction(){
        val state=base(1207L);removeUnits(state,1);hideExpansionAndEnemyCities(state);state.roundNumber=12;val enemyCity=state.ownedCities(1).first();state.discoveredTiles[0]+=enemyCity.pos;state.players[0].technologies+="strategy";state.diplomacy[0][1]=Relation.PEACE;state.diplomacy[1][0]=Relation.PEACE;check(StrategicAi.plan(state,0).goal==StrategicGoal.ATTACK);val expected=BreakPeace(0,1);check(CommandEngine.validate(state,expected)==null);check(StrategicAi.chooseCommand(state,0)==expected)
    }
    private fun lateScoreModeMustSwitchObjective(){val state=base(1208L,GameMode.SCORE_30);removeUnits(state,1);hideExpansionAndEnemyCities(state);state.roundNumber=13;check(StrategicAi.plan(state,0).goal==StrategicGoal.SCORE)}

    @JvmStatic fun main(args:Array<String>){captureMustBeatRoutineDevelopment();smallThreatenedEmpireMustDefend();discoveredVillageMustTriggerExpansion();quietEarlyPositionMustInvestInTech();maturePoorPositionMustPreferEconomy();exposedWeakEnemyCityMustTriggerAttack();attackPlanAtPeaceMustBreakTreatyBeforeLocalAction();lateScoreModeMustSwitchObjective();println("ai_decision_fixtures=PASS fixtures=8")}
}
