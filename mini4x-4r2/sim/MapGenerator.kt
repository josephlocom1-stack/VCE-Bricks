package com.example.mini4x.sim

import kotlin.math.*
import kotlin.random.Random

object MapGenerator {
    private fun hash(seed: Long, x: Int, y: Int, salt: Long = 0L): Double {
        var z = seed xor (x.toLong() * -7046029254386353131L) xor (y.toLong() * -4658895280553007687L) xor salt
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        z = z xor (z ushr 31)
        return ((z ushr 11) and ((1L shl 53)-1)).toDouble() / (1L shl 53).toDouble()
    }

    private fun smoothNoise(seed: Long, x: Int, y: Int, cell: Int, salt: Long): Double {
        val gx = floor(x.toDouble()/cell).toInt(); val gy = floor(y.toDouble()/cell).toInt()
        val fx = (x % cell).toDouble()/cell; val fy = (y % cell).toDouble()/cell
        fun s(t: Double) = t*t*(3-2*t)
        fun lerp(a:Double,b:Double,t:Double)=a+(b-a)*t
        val a=hash(seed,gx,gy,salt); val b=hash(seed,gx+1,gy,salt); val c=hash(seed,gx,gy+1,salt); val d=hash(seed,gx+1,gy+1,salt)
        return lerp(lerp(a,b,s(fx)),lerp(c,d,s(fx)),s(fy))
    }

    private fun landScore(config: GameConfig, x: Int, y: Int): Double {
        val n=config.mapSize.n; val nx=(x-(n-1)/2.0)/(n/2.0); val ny=(y-(n-1)/2.0)/(n/2.0)
        val noise=.65*smoothNoise(config.seed,x,y,max(3,n/5),101)+.35*smoothNoise(config.seed,x,y,max(2,n/9),102)
        return when(config.waterPreset) {
            WaterPreset.DRYLANDS -> .92 + .08*noise
            WaterPreset.LAKES -> .80 + .22*noise
            WaterPreset.PANGEA -> .92 - .58*sqrt(nx*nx+ny*ny) + .22*(noise-.5)
            WaterPreset.CONTINENTS -> {
                val centers=listOf(-.48 to -.18, .42 to .22, -.12 to .55)
                val best=centers.maxOf { (cx,cy) -> 1.0 - sqrt((nx-cx)*(nx-cx)+(ny-cy)*(ny-cy)) }
                .34 + .72*best + .18*(noise-.5)
            }
            WaterPreset.ARCHIPELAGO -> {
                val centers=(0 until 10).map { i ->
                    val a=hash(config.seed,i,7,201)*Math.PI*2; val r=.15+.75*hash(config.seed,i,8,202)
                    (cos(a)*r) to (sin(a)*r)
                }
                val best=centers.maxOf { (cx,cy) -> 1.0 - sqrt((nx-cx)*(nx-cx)+(ny-cy)*(ny-cy))*2.0 }
                .10 + .60*best + .22*(noise-.5)
            }
            WaterPreset.WATER_WORLD -> .12 + .28*noise
        }
    }

    private fun waterThreshold(p: WaterPreset) = when(p) {
        WaterPreset.DRYLANDS -> .08
        WaterPreset.LAKES -> .72
        WaterPreset.CONTINENTS -> .52
        WaterPreset.PANGEA -> .48
        WaterPreset.ARCHIPELAGO -> .55
        WaterPreset.WATER_WORLD -> .34
    }

    private fun chooseSeparated(seed: Long, n: Int, count: Int, occupied: List<Pos> = emptyList(), minSep: Int): List<Pos> {
        val candidates=(1 until n-1).flatMap { x -> (1 until n-1).map { y -> Pos(x,y) } }.toMutableList()
        candidates.sortBy { hash(seed,it.x,it.y,301) }
        val out=occupied.toMutableList(); val added=mutableListOf<Pos>()
        repeat(count) {
            val best=candidates.filter { c -> out.none { it.chebyshev(c) < minSep } }
                .maxByOrNull { c -> if(out.isEmpty()) hash(seed,c.x,c.y,302) else out.minOf { it.chebyshev(c) }.toDouble() + hash(seed,c.x,c.y,303)*.15 }
                ?: candidates.maxByOrNull { c -> out.minOfOrNull { it.chebyshev(c) }?.toDouble() ?: 0.0 } ?: return@repeat
            out += best; added += best; candidates.remove(best)
        }
        return added
    }

    fun create(config: GameConfig): SimState {
        require(config.factionIds.size in 2..4) { "2-4 players supported" }
        val n=config.mapSize.n; val tiles=Array(n){x->Array(n){y->Tile(Pos(x,y))}}; val pcount=config.factionIds.size
        val sep=max(4,(n.toDouble()/sqrt(pcount.toDouble())*.58).roundToInt())
        val capitals=chooseSeparated(config.seed,n,pcount,minSep=sep)
        val villageTarget=max(pcount+3,(n*n/22.0).roundToInt())
        val villages=chooseSeparated(config.seed xor 0x51f15eL,n,villageTarget,capitals,minSep=2)
        val threshold=waterThreshold(config.waterPreset); val land=Array(n){BooleanArray(n)}
        for(x in 0 until n) for(y in 0 until n) land[x][y]=landScore(config,x,y) > threshold
        (capitals+villages).forEach { land[it.x][it.y]=true }
        capitals.forEach { c -> c.neighbours8().filter { it.x in 0 until n && it.y in 0 until n }.forEach { land[it.x][it.y]=true } }

        fun nearestCapital(pos: Pos): Int = capitals.indices.minByOrNull { capitals[it].chebyshev(pos)*1000 + it } ?: 0
        for(x in 0 until n) for(y in 0 until n) {
            val t=tiles[x][y]; val pos=t.pos; t.climateOwner=nearestCapital(pos)
            if(!land[x][y]) {
                val adjacentLand=pos.neighbours8().any { q -> q.x in 0 until n && q.y in 0 until n && land[q.x][q.y] }
                t.terrain=if(adjacentLand) Terrain.WATER else Terrain.OCEAN
                if((y==0 || y==n-1) && hash(config.seed,x,y,401)<.18) t.terrain=Terrain.ICE
            } else {
                val fac=FactionCatalog[config.factionIds[t.climateOwner]]
                val forestMult=fac.terrainMultipliers[Terrain.FOREST] ?: 1.0; val mountainMult=fac.terrainMultipliers[Terrain.MOUNTAIN] ?: 1.0
                val v=hash(config.seed,x,y,402); val m=hash(config.seed,x,y,403)
                t.terrain = when { m < .085*mountainMult -> Terrain.MOUNTAIN; v < .24*forestMult -> Terrain.FOREST; else -> Terrain.FIELD }
            }
        }
        capitals.forEach { tiles[it.x][it.y].terrain=Terrain.FIELD }
        villages.forEach { tiles[it.x][it.y].terrain=Terrain.FIELD; tiles[it.x][it.y].village=true }

        for(x in 0 until n) for(y in 0 until n) {
            val t=tiles[x][y]; if(t.pos in capitals || t.village) continue
            val fac=FactionCatalog[config.factionIds[t.climateOwner]]; fun mult(r:Resource)=fac.resourceMultipliers[r]?:1.0; val r=hash(config.seed,x,y,501)
            t.resource=when(t.terrain) {
                Terrain.FIELD -> when { r < .10*mult(Resource.FRUIT) -> Resource.FRUIT; r < .10*mult(Resource.FRUIT)+.075*mult(Resource.CROP) -> Resource.CROP; else -> null }
                Terrain.FOREST -> if(r<.14*mult(Resource.ANIMAL)) Resource.ANIMAL else null
                Terrain.MOUNTAIN -> if(r<.22*mult(Resource.ORE)) Resource.ORE else null
                Terrain.WATER, Terrain.OCEAN -> if(r<.18*mult(Resource.FISH)) Resource.FISH else null
                Terrain.ICE -> null
            }
        }

        val ruinCandidates=(0 until n).flatMap{x->(0 until n).map{y->tiles[x][y]}}.filter { it.pos !in capitals && !it.village && it.terrain != Terrain.ICE }.sortedBy { hash(config.seed,it.pos.x,it.pos.y,601) }
        var ruins=0
        for(t in ruinCandidates) { if(ruins>=config.mapSize.ruinCount) break; if(capitals.all { it.chebyshev(t.pos)>=2 } && villages.all { it.chebyshev(t.pos)>=1 }) { t.ruin=true; t.resource=null; ruins++ } }
        val waterTiles=ruinCandidates.filter { it.terrain==Terrain.WATER || it.terrain==Terrain.OCEAN }; val starCount=max(0,waterTiles.size/25)
        waterTiles.sortedBy { hash(config.seed,it.pos.x,it.pos.y,602) }.take(starCount).forEach { it.starfish=true }
        waterTiles.sortedBy { hash(config.seed,it.pos.x,it.pos.y,603) }.take(max(1,waterTiles.size/55)).forEach { it.lighthouse=true }

        val players=config.factionIds.mapIndexed { id,fid -> val f=FactionCatalog[fid]; PlayerState(id,fid,f.initialStars,technologies=f.startingTechnology.toMutableSet(),startingTechnologies=f.startingTechnology.toMutableSet()) }.toMutableList()
        val cities=mutableListOf<City>(); val units=mutableListOf<UnitState>(); var cid=1; var uid=1
        capitals.forEachIndexed { pid,pos ->
            val f=FactionCatalog[config.factionIds[pid]]; val c=City(cid++,pid,pos,level=f.initialCityLevel,isCapital=true)
            cities += c; players[pid].capitalCityId=c.id
            val tile=tiles[pos.x][pos.y]; tile.cityId=c.id; tile.village=false; tile.territoryOwner=pid
            for(dx in -1..1) for(dy in -1..1) { val q=Pos(pos.x+dx,pos.y+dy); if(q.x in 0 until n && q.y in 0 until n && tiles[q.x][q.y].territoryOwner==null) tiles[q.x][q.y].territoryOwner=pid }
            val type=UnitCatalog[f.startingUnit]; val u=UnitState(uid++,pid,pos,f.startingUnit,type.maxHP,supportCityId=c.id)
            units+=u;c.supportedUnitIds+=u.id;tile.occupantUnitId=u.id
        }
        val dip=Array(pcount){i->Array(pcount){j->if(i==j) Relation.PEACE else Relation.NEUTRAL}}
        val state=SimState(seed=config.seed,rngCounter=0,roundNumber=1,activePlayer=0,gameMode=config.gameMode,waterPreset=config.waterPreset,size=n,humanPlayerId=config.humanPlayerId,difficulty=config.difficulty,map=tiles,players=players,cities=cities,units=units,diplomacy=dip,discoveredTiles=MutableList(pcount){mutableSetOf()},score=MutableList(pcount){0},nextCityId=cid,nextUnitId=uid,creativeEndless=config.creativeEndless)
        DerivedState.recalculateAll(state, mutableListOf())
        players.indices.forEach { pid -> VisionRules.revealAllSources(state,pid,mutableListOf()) }
        DerivedState.recalculateScores(state, mutableListOf())
        return state
    }
}
