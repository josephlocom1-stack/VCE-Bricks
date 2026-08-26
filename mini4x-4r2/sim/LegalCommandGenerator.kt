package com.example.mini4x.sim

object LegalCommandGenerator {
    private val trainable = listOf(UnitKind.WARRIOR,UnitKind.RIDER,UnitKind.ARCHER,UnitKind.DEFENDER,UnitKind.SWORDSMAN,UnitKind.MIND_BENDER,UnitKind.CATAPULT,UnitKind.CLOAK,UnitKind.KNIGHT)

    fun forUnit(state:SimState,unit:UnitState):List<Command> {
        if(unit.owner!=state.activePlayer) return emptyList()
        val pid=unit.owner;val out=mutableListOf<Command>()
        MovementRules.legalDestinations(state,unit).forEach { out += MoveUnit(pid,unit.id,it) }
        for(enemy in state.units) if(enemy.owner!=pid && unit.pos.chebyshev(enemy.pos)<=UnitCatalog[unit.kind].range) out+=AttackUnit(pid,unit.id,enemy.id)
        out+=RecoverUnit(pid,unit.id)
        out+=CaptureCity(pid,unit.id);out+=ExamineRuin(pid,unit.id);out+=GatherStarfish(pid,unit.id)
        out+=UpgradeShip(pid,unit.id,UnitKind.RAMMER);out+=UpgradeShip(pid,unit.id,UnitKind.SCOUT);out+=UpgradeShip(pid,unit.id,UnitKind.BOMBER)
        out+=SpecialAction(pid,unit.id,SpecialActionType.HEAL_ADJACENT)
        state.cities.filter { it.owner!=pid && unit.pos.chebyshev(it.pos)<=1 }.forEach { out+=SpecialAction(pid,unit.id,SpecialActionType.INFILTRATE,it.pos) }
        out+=SpecialAction(pid,unit.id,SpecialActionType.DISBAND)
        return out.filter { CommandEngine.validate(state,it)==null }
    }

    fun forCity(state:SimState,city:City):List<Command> {
        if(city.owner!=state.activePlayer) return emptyList();val pid=city.owner;val out=mutableListOf<Command>()
        trainable.forEach { out+=TrainUnit(pid,city.id,it) }
        city.pendingRewardLevel?.let { lvl ->
            val rewards=when(lvl){2->listOf(CityRewardType.WORKSHOP,CityRewardType.EXPLORER);3->listOf(CityRewardType.WALL,CityRewardType.STARS_5);4->listOf(CityRewardType.BORDER_GROWTH,CityRewardType.POPULATION_3);else->listOf(CityRewardType.PARK,CityRewardType.SUPER_UNIT)}
            rewards.forEach { out+=ChooseCityReward(pid,city.id,it) }
        }
        return out.filter { CommandEngine.validate(state,it)==null }
    }

    fun forTile(state:SimState,pos:Pos,playerId:Int=state.activePlayer):List<Command> {
        val t=state.tile(pos)?:return emptyList();val out=mutableListOf<Command>()
        out+=HarvestResource(playerId,pos);out+=BuildRoad(playerId,pos)
        Improvement.entries.forEach { out+=BuildImprovement(playerId,pos,it) }
        state.ownedUnits(playerId).filter { it.pos.chebyshev(pos)<=1 }.forEach { out+=SpecialAction(playerId,it.id,SpecialActionType.DESTROY_BUILDING,pos) }
        return out.filter { CommandEngine.validate(state,it)==null }
    }

    fun all(state:SimState,playerId:Int=state.activePlayer,includeEndTurn:Boolean=true):List<Command> {
        if(playerId!=state.activePlayer || state.players[playerId].eliminated) return emptyList()
        val out=mutableListOf<Command>()
        state.ownedUnits(playerId).forEach { out+=forUnit(state,it) }
        state.ownedCities(playerId).forEach { out+=forCity(state,it) }
        for(x in 0 until state.size) for(y in 0 until state.size) {
            val t=state.map[x][y]
            if(t.territoryOwner==playerId || (t.occupantUnitId?.let{state.unit(it)?.owner}==playerId)) out+=forTile(state,t.pos,playerId)
        }
        TechnologyCatalog.all.forEach { out+=BuyTechnology(playerId,it.id) }
        state.players.indices.filter{it!=playerId && !state.players[it].eliminated}.forEach { target -> out+=EstablishEmbassy(playerId,target);out+=OfferPeace(playerId,target);out+=BreakPeace(playerId,target) }
        if(includeEndTurn) out+=EndTurn(playerId)
        return out.distinct().filter { CommandEngine.validate(state,it)==null }
    }
}
