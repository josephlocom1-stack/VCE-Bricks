package com.example.mini4x.sim

import java.util.ArrayDeque
import kotlin.math.max

object DerivedState {
    fun cityForTile(state:SimState,playerId:Int,pos:Pos):City? {
        val t=state.tile(pos)?:return null
        t.workCityId?.let { id -> state.city(id)?.takeIf { it.owner==playerId }?.let { return it } }
        if(t.territoryOwner!=playerId) return null
        return state.ownedCities(playerId).minByOrNull { it.pos.chebyshev(pos) }
    }

    fun recalculateAll(state:SimState, events:MutableList<SimEvent>) {
        refreshSieges(state)
        recalculateBuildingPopulation(state)
        recalculateConnections(state)
        recalculateCityUpgrades(state,events)
        recalculateScores(state,events)
        checkElimination(state,events)
        checkVictory(state,events)
    }

    fun refreshSieges(state:SimState) {
        for(c in state.cities) {
            val uid=c.besiegedByUnitId ?: continue
            val u=state.unit(uid)
            if(u==null || u.pos!=c.pos || u.owner==c.owner) c.besiegedByUnitId=null
        }
    }

    fun recalculateBuildingPopulation(state:SimState) {
        state.cities.forEach { it.buildingPopulation=0 }
        for(city in state.cities) {
            val tiles=(0 until state.size).flatMap { x -> (0 until state.size).map { y -> state.map[x][y] } }
                .filter { it.workCityId==city.id && it.territoryOwner==city.owner }
            var total=0
            for(t in tiles) {
                total += when(t.improvement) {
                    Improvement.LUMBER_HUT -> 1
                    Improvement.FARM -> 2
                    Improvement.MINE -> 2
                    Improvement.PORT -> 1
                    Improvement.TEMPLE -> 1
                    Improvement.FOREST_TEMPLE -> 1
                    Improvement.MONUMENT -> 3
                    Improvement.SAWMILL -> t.pos.neighbours8().count { q -> state.tile(q)?.let { it.workCityId==city.id && it.improvement==Improvement.LUMBER_HUT }==true }
                    Improvement.WINDMILL -> t.pos.neighbours8().count { q -> state.tile(q)?.let { it.workCityId==city.id && it.improvement==Improvement.FARM }==true }
                    Improvement.FORGE -> 2*t.pos.neighbours8().count { q -> state.tile(q)?.let { it.workCityId==city.id && it.improvement==Improvement.MINE }==true }
                    else -> 0
                }
            }
            city.buildingPopulation=total
        }
    }

    private fun roadLike(t:Tile)=t.road || t.cityId!=null || t.village || t.improvement==Improvement.BRIDGE || t.improvement==Improvement.PORT
    private fun networkUsable(state:SimState,playerId:Int,t:Tile):Boolean {
        val owner=t.territoryOwner
        return owner==null || owner==playerId || state.diplomacy[playerId][owner]==Relation.PEACE
    }

    fun recalculateConnections(state:SimState) {
        state.cities.forEach { it.connectionPopulation=0 }
        for(player in state.players.indices) {
            val cap=state.capital(player)?.takeIf { it.owner==player } ?: continue
            val start=cap.pos
            val visited=mutableSetOf<Pos>(); val q=ArrayDeque<Pos>(); visited+=start;q+=start
            while(q.isNotEmpty()) {
                val p=q.removeFirst()
                for(n in p.neighbours8()) {
                    val t=state.tile(n)?:continue
                    if(n in visited || !roadLike(t) || !networkUsable(state,player,t)) continue
                    visited+=n;q+=n
                }
            }
            for(c in state.ownedCities(player)) {
                if(c.id==cap.id) continue
                if(c.pos in visited) { cap.connectionPopulation+=1; c.connectionPopulation+=1 }
            }
        }
    }

    fun recalculateCityUpgrades(state:SimState,events:MutableList<SimEvent>) {
        for(c in state.cities) {
            if(c.pendingRewardLevel!=null) continue
            val need=c.populationNeeded
            if(c.population>=need) {
                c.spentPopulation += need
                c.level += 1
                c.pendingRewardLevel=c.level
                events += SimEvent(EventType.CITY_UPGRADED,c.owner,subjectId=c.id,amount=c.level,message="City reached level ${c.level}")
            }
        }
    }

    fun cityIncome(state:SimState,playerId:Int):Int = state.ownedCities(playerId).sumOf { it.income(state.humanPlayerId) }

    fun marketIncome(state:SimState,playerId:Int):Int {
        var stars=0
        for(x in 0 until state.size) for(y in 0 until state.size) {
            val t=state.map[x][y]
            if(t.territoryOwner!=playerId || t.improvement!=Improvement.MARKET) continue
            val links=t.pos.neighbours8().count { q -> state.tile(q)?.let { n -> n.improvement==Improvement.PORT || n.road || n.cityId!=null }==true }
            stars += max(1,links.coerceAtMost(3))
        }
        return stars
    }

    fun embassyIncome(state:SimState,playerId:Int):Int {
        var total=0
        for(a in state.players.indices) for(b in state.players.indices) {
            if(a==b || b !in state.players[a].embassies) continue
            if(playerId==a || playerId==b) total += if(state.diplomacy[a][b]==Relation.PEACE) 4 else 2
        }
        return total
    }

    fun totalIncome(state:SimState,playerId:Int)=cityIncome(state,playerId)+marketIncome(state,playerId)+embassyIncome(state,playerId)

    fun recalculateScores(state:SimState,events:MutableList<SimEvent>) {
        for(pid in state.players.indices) {
            var s=0
            val p=state.players[pid]
            s += state.ownedUnits(pid).sumOf { u -> 5*(UnitCatalog[u.carriedKind ?: u.kind].cost ?: 8) }
            for(x in 0 until state.size) for(y in 0 until state.size) {
                val t=state.map[x][y]
                if(t.territoryOwner==pid) s+=20
                if(t.improvement==Improvement.MONUMENT && t.territoryOwner==pid) s+=400
                if(t.improvement in setOf(Improvement.TEMPLE,Improvement.FOREST_TEMPLE) && t.territoryOwner==pid) {
                    val growth=((state.roundNumber-t.improvementBuiltTurn).coerceAtLeast(0)/2).coerceAtMost(4)
                    s += 100 + growth*100
                }
            }
            s += state.discoveredTiles[pid].size*5
            s += state.ownedCities(pid).sumOf { c -> 100+50*(c.level-1)+(if(c.park)250 else 0) }
            s += p.technologies.sumOf { TechnologyCatalog[it].tier*100 }
            if(state.score[pid]!=s) {
                val delta=s-state.score[pid];state.score[pid]=s
                if(delta!=0) events += SimEvent(EventType.SCORE_CHANGED,pid,amount=delta,message="Score $s")
            }
        }
    }

    fun checkElimination(state:SimState,events:MutableList<SimEvent>) {
        for(pid in state.players.indices) {
            val p=state.players[pid]
            if(!p.eliminated && state.ownedCities(pid).isEmpty()) {
                p.eliminated=true
                val doomed=state.ownedUnits(pid).toList()
                for(u in doomed) { state.tile(u.pos)?.takeIf{it.occupantUnitId==u.id}?.occupantUnitId=null; state.units.remove(u) }
                events += SimEvent(EventType.PLAYER_ELIMINATED,pid,message="${FactionCatalog[p.factionId].displayName} eliminated")
            }
        }
    }

    fun checkVictory(state:SimState,events:MutableList<SimEvent>) {
        if(state.finished || state.creativeEndless || state.gameMode==GameMode.CREATIVE) return
        var winner:Int?=null; var reason=""
        when(state.gameMode) {
            GameMode.CONQUEST -> { val living=state.livingPlayers(); if(living.size==1) { winner=living.first().id; reason="Conquest" } }
            GameMode.SCORE_RACE -> { winner=state.players.indices.filter{!state.players[it].eliminated}.firstOrNull { state.score[it]>=10_000 }; if(winner!=null) reason="10,000 score reached" }
            GameMode.SCORE_30 -> if(state.roundNumber>30) { winner=state.players.indices.maxByOrNull { state.score[it] }; reason="30-turn score" }
            GameMode.WEEKLY_20 -> if(state.roundNumber>20) { winner=state.players.indices.maxByOrNull { state.score[it] }; reason="20-turn weekly challenge" }
            else -> Unit
        }
        if(winner!=null) {
            state.finished=true;state.winnerId=winner;state.victoryReason=reason
            events += SimEvent(EventType.VICTORY,winner,message=reason)
        }
    }

    fun claimCityTerritory(state:SimState,city:City) {
        val radius=if(city.borderExpanded)2 else 1
        for(dx in -radius..radius) for(dy in -radius..radius) {
            val q=Pos(city.pos.x+dx,city.pos.y+dy); val t=state.tile(q)?:continue
            val competitor=state.cities.filter { it.id!=city.id && it.owner!=city.owner }.minByOrNull { it.pos.chebyshev(q) }
            if(competitor==null || city.pos.chebyshev(q)<competitor.pos.chebyshev(q) || t.territoryOwner==city.owner) t.territoryOwner=city.owner
        }
    }
}
