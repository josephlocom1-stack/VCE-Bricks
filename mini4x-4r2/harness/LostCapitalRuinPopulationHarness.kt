package com.example.mini4x.research

import com.example.mini4x.sim.*

private data class RuinPopulationResult(val rngCounter: Long,val capturedDelta: Int,val ownedDelta: Int,val message: String)
object LostCapitalRuinPopulationHarness {
    private data class Fixture(val state: SimState,val capturedCapitalId: Int,val ownedCityId: Int,val examiningUnitId: Int)
    private fun firstEmptyLand(state: SimState, around: Pos, forbidden: Set<Pos> = emptySet()): Pos = (around.neighbours8() + (0 until state.size).flatMap { x -> (0 until state.size).map { y -> Pos(x, y) } }).first { p -> state.inBounds(p) && p !in forbidden && state.tile(p)?.let { t -> t.terrain !in setOf(Terrain.WATER, Terrain.OCEAN, Terrain.ICE) && t.cityId == null && t.occupantUnitId == null } == true }
    private fun fixture(): Fixture {
        val state = MapGenerator.create(GameConfig(seed = 8_401_337L,mapSize = MapSizeSetting.NORMAL,waterPreset = WaterPreset.DRYLANDS,gameMode = GameMode.CONQUEST,factionIds = listOf("asteria", "sunspire"),humanPlayerId = 0,difficulty = 2))
        val pid = 0;val captured = state.players[pid].capitalCityId?.let(state::city) ?: error("missing player-0 original capital")
        val secondPos = firstEmptyLand(state, captured.pos);val secondTile = state.tile(secondPos)!!;secondTile.terrain = Terrain.FIELD;secondTile.territoryOwner = pid;secondTile.cityId = state.nextCityId;secondTile.village = false;secondTile.resource = null;secondTile.improvement = null;secondTile.workCityId = null
        val second = City(id = state.nextCityId++,owner = pid,pos = secondPos,level = 1,originalOwner = pid,isCapital = false);state.cities += second
        captured.owner = 1;state.tile(captured.pos)!!.territoryOwner = 1
        val unit = state.ownedUnits(pid).first();state.tile(unit.pos)?.takeIf { it.occupantUnitId == unit.id }?.occupantUnitId = null
        val ruinPos = firstEmptyLand(state, second.pos, setOf(captured.pos, second.pos));val ruinTile = state.tile(ruinPos)!!;ruinTile.terrain = Terrain.FIELD;ruinTile.territoryOwner = pid;ruinTile.ruin = true;ruinTile.occupantUnitId = unit.id;unit.pos = ruinPos;unit.moved = false;unit.attacked = false;unit.tookAction = false;unit.captureReadyOnRound = null;state.discoveredTiles[pid] += ruinPos;state.activePlayer = pid;state.finished = false;state.winnerId = null
        return Fixture(state, captured.id, second.id, unit.id)
    }
    private fun findPopulationOutcome(): RuinPopulationResult {
        val base = fixture();for (counter in 0L..4096L) {val state = base.state.deepCopy();state.rngCounter = counter;val captured = state.city(base.capturedCapitalId)!!;val owned = state.city(base.ownedCityId)!!;val capturedBefore = captured.basePopulation;val ownedBefore = owned.basePopulation;val result = CommandEngine.execute(state, ExamineRuin(0, base.examiningUnitId));check(result.accepted) { "ExamineRuin rejected at rngCounter=$counter: ${result.reason}" };val capturedDelta = captured.basePopulation - capturedBefore;val ownedDelta = owned.basePopulation - ownedBefore;if (capturedDelta != 0 || ownedDelta != 0) {val message = result.events.lastOrNull { it.type == EventType.RUIN_EXAMINED }?.message.orEmpty();return RuinPopulationResult(counter, capturedDelta, ownedDelta, message)}};error("did not encounter deterministic +3 population ruin outcome")
    }
    @JvmStatic fun main(args: Array<String>) {val expectOwnedFallback = args.firstOrNull() == "expect-owned-fallback";val row = findPopulationOutcome();println("ruin_rng_counter=${row.rngCounter}");println("captured_capital_population_delta=${row.capturedDelta}");println("owned_city_population_delta=${row.ownedDelta}");println("ruin_message=${row.message}");if (expectOwnedFallback) {check(row.capturedDelta == 0);check(row.ownedDelta == 3);check(row.message == "+3 city population");println("lost_capital_ruin_population=PASS")}}
}
