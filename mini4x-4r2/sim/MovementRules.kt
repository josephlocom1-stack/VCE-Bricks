package com.example.mini4x.sim

import java.util.PriorityQueue

object MovementRules {
    data class StepState(val pos: Pos, val naval: Boolean)
    data class MovementPath(val path: List<Pos>, val halfCost: Int, val finalNaval: Boolean, val embarked: Boolean, val disembarked: Boolean)

    private data class Node(val state: StepState,val cost:Int,val path:List<Pos>,val terminal:Boolean,val embarked:Boolean,val disembarked:Boolean)

    fun isNaval(kind: UnitKind)=kind in setOf(UnitKind.RAFT,UnitKind.RAMMER,UnitKind.SCOUT,UnitKind.BOMBER,UnitKind.JUGGERNAUT)

    fun enemyAdjacent(state: SimState, playerId:Int, pos:Pos):Boolean = pos.neighbours8().any { q ->
        val u=state.tile(q)?.occupantUnitId?.let(state::unit) ?: return@any false
        u.owner!=playerId && state.diplomacy[playerId][u.owner]!=Relation.PEACE
    }

    private fun roadUsable(state:SimState, playerId:Int, tile:Tile):Boolean {
        val owner=tile.territoryOwner
        return owner==null || owner==playerId || state.diplomacy[playerId][owner]==Relation.PEACE
    }

    private fun roadLike(tile:Tile)=tile.road || tile.cityId!=null || tile.village || tile.improvement==Improvement.BRIDGE

    private fun edgeCost(state:SimState,playerId:Int,from:Tile,to:Tile):Int {
        return if(roadLike(from)&&roadLike(to)&&roadUsable(state,playerId,from)&&roadUsable(state,playerId,to)) 1 else 2
    }

    private fun canEnterLand(state:SimState,playerId:Int,unit:UnitState,tile:Tile):Boolean {
        if(tile.terrain==Terrain.MOUNTAIN && "climbing" !in state.players[playerId].technologies && Skill.CREEP !in UnitCatalog.skills(unit)) return false
        if(tile.territoryOwner!=null && tile.territoryOwner!=playerId && state.diplomacy[playerId][tile.territoryOwner!!]==Relation.PEACE) return true
        return tile.terrain in setOf(Terrain.FIELD,Terrain.FOREST,Terrain.MOUNTAIN, Terrain.ICE)
    }

    private fun canEnterWater(state:SimState,playerId:Int,tile:Tile):Boolean {
        if(tile.terrain==Terrain.WATER) return "fishing" in state.players[playerId].technologies
        if(tile.terrain==Terrain.OCEAN) return "sailing" in state.players[playerId].technologies
        return false
    }

    private fun hasFriendlyPort(state:SimState,playerId:Int,pos:Pos):Boolean {
        val t=state.tile(pos)?:return false
        return t.improvement==Improvement.PORT && t.territoryOwner==playerId
    }

    fun findPath(state:SimState, unit:UnitState, destination:Pos):MovementPath? {
        if(!state.inBounds(destination) || destination !in state.discoveredTiles[unit.owner]) return null
        if(destination==unit.pos) return MovementPath(emptyList(),0,isNaval(unit.kind),false,false)
        val skills=UnitCatalog.skills(unit)
        val currentlyNaval=isNaval(unit.kind)
        val baseMove=UnitCatalog[unit.kind].movement
        // A land unit embarks by ENTERING a friendly Port water tile. It does not need to
        // somehow begin its turn on water. Once embarked, later moves use the naval wrapper's movement.
        val allowance=baseMove*2
        val pq=PriorityQueue<Node>(compareBy<Node>{it.cost}.thenBy{it.path.size})
        val start=StepState(unit.pos,currentlyNaval)
        pq += Node(start,0,emptyList(),false,false,false)
        val best=mutableMapOf(start to 0)
        while(pq.isNotEmpty()) {
            val n=pq.poll(); if(n.cost!=best[n.state]) continue
            if(n.state.pos==destination) return MovementPath(n.path,n.cost,n.state.naval,n.embarked,n.disembarked)
            if(n.terminal) continue
            for(q in n.state.pos.neighbours8()) {
                if(!state.inBounds(q) || q !in state.discoveredTiles[unit.owner]) continue
                val target=state.tile(q)!!
                if(target.occupantUnitId!=null && q!=unit.pos) continue
                var naval=n.state.naval; var embarked=n.embarked; var disembarked=n.disembarked
                val from=state.tile(n.state.pos)!!
                val allowed=if(naval) {
                    if(target.terrain in setOf(Terrain.WATER,Terrain.OCEAN)) canEnterWater(state,unit.owner,target)
                    else if(canEnterLand(state,unit.owner,unit,target)) { naval=false; disembarked=true; true } else false
                } else {
                    if(target.terrain in setOf(Terrain.WATER,Terrain.OCEAN)) {
                        if(hasFriendlyPort(state,unit.owner,q) && canEnterWater(state,unit.owner,target)) { naval=true; embarked=true; true } else false
                    } else canEnterLand(state,unit.owner,unit,target)
                }
                if(!allowed) continue
                val step=edgeCost(state,unit.owner,from,target)
                val nc=n.cost+step; if(nc>allowance) continue
                val rough=!naval && target.terrain in setOf(Terrain.FOREST,Terrain.MOUNTAIN) && Skill.CREEP !in skills
                val zoc=Skill.CREEP !in skills && enemyAdjacent(state,unit.owner,q)
                val terminal=rough||zoc
                val st=StepState(q,naval)
                if(nc < (best[st]?:Int.MAX_VALUE)) {
                    best[st]=nc; pq += Node(st,nc,n.path+q,terminal,embarked,disembarked)
                }
            }
        }
        return null
    }

    fun legalDestinations(state:SimState, unit:UnitState):Set<Pos> {
        if(unit.owner!=state.activePlayer || unit.moved || unit.frozenTurns>0) return emptySet()
        val out=mutableSetOf<Pos>()
        for(p in state.discoveredTiles[unit.owner]) {
            if(p!=unit.pos && findPath(state,unit,p)!=null) out+=p
        }
        return out
    }
}
