package com.example.mini4x.sim

object VisionRules {
    fun unitSight(state: SimState, unit: UnitState): Int {
        var range=1
        if(Skill.SCOUT in UnitCatalog.skills(unit)) range += 1
        if(state.tile(unit.pos)?.terrain==Terrain.MOUNTAIN) range += 1
        return range
    }

    fun revealFrom(state: SimState, playerId: Int, center: Pos, range: Int, events: MutableList<SimEvent>): Int {
        val newly=mutableListOf<Pos>()
        for(x in center.x-range..center.x+range) for(y in center.y-range..center.y+range) {
            val p=Pos(x,y); if(!state.inBounds(p)) continue
            if(center.chebyshev(p)<=range && state.discoveredTiles[playerId].add(p)) newly+=p
        }
        if(newly.isNotEmpty()) {
            events += SimEvent(EventType.TILES_DISCOVERED,playerId,from=center,amount=newly.size,message="${newly.size} tiles explored")
            checkMeetings(state,playerId,newly,events)
        }
        return newly.size
    }

    private fun checkMeetings(state: SimState, playerId: Int, newTiles: List<Pos>, events: MutableList<SimEvent>) {
        val p=state.players[playerId]
        for(other in state.players.indices) {
            if(other==playerId || other in p.metPlayers || state.players[other].eliminated) continue
            val seen=newTiles.any { q ->
                val t=state.tile(q) ?: return@any false
                t.territoryOwner==other || t.cityId?.let { state.city(it)?.owner==other }==true || t.occupantUnitId?.let { state.unit(it)?.owner==other }==true
            }
            if(seen) {
                p.metPlayers += other; state.players[other].metPlayers += playerId
                val reward=(3 + state.score.getOrElse(other){0}/600).coerceAtMost(11)
                p.stars += reward
                events += SimEvent(EventType.CIVILIZATION_MET,playerId,targetId=other,amount=reward,message="Met ${FactionCatalog[state.players[other].factionId].displayName}: +$reward stars")
                events += SimEvent(EventType.STARS_CHANGED,playerId,amount=reward,message="First contact")
            }
        }
    }

    fun revealUnit(state: SimState, unit: UnitState, events: MutableList<SimEvent>) = revealFrom(state,unit.owner,unit.pos,unitSight(state,unit),events)

    fun revealAllSources(state: SimState, playerId:Int, events:MutableList<SimEvent>) {
        state.ownedCities(playerId).forEach { revealFrom(state,playerId,it.pos,1,events) }
        state.ownedUnits(playerId).forEach { revealUnit(state,it,events) }
        if("diplomacy" in state.players[playerId].technologies) {
            state.players.indices.filter { it!=playerId && it in state.players[playerId].metPlayers }.forEach { other ->
                state.capital(other)?.let { revealFrom(state,playerId,it.pos,1,events) }
            }
        }
    }
}
