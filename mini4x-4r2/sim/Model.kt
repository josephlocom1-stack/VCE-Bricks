package com.example.mini4x.sim

import kotlin.math.max

enum class Terrain { FIELD, FOREST, MOUNTAIN, WATER, OCEAN, ICE }
enum class Resource { FRUIT, ANIMAL, FISH, CROP, ORE }
enum class Improvement {
    LUMBER_HUT, FARM, MINE, PORT, SAWMILL, WINDMILL, FORGE,
    TEMPLE, FOREST_TEMPLE, MONUMENT, MARKET, BRIDGE
}
enum class Relation { WAR, NEUTRAL, PEACE }
enum class GameMode { CONQUEST, SCORE_30, SCORE_RACE, CREATIVE, WEEKLY_20 }
enum class MapSizeSetting(val n: Int, val ruinCount: Int) {
    TINY(11, 4), SMALL(14, 5), NORMAL(16, 7), LARGE(18, 9), HUGE(20, 11), MASSIVE(30, 23)
}
enum class WaterPreset { DRYLANDS, LAKES, CONTINENTS, PANGEA, ARCHIPELAGO, WATER_WORLD }
enum class UnitKind {
    WARRIOR, RIDER, ARCHER, DEFENDER, SWORDSMAN, MIND_BENDER, CATAPULT, CLOAK, KNIGHT, GIANT,
    RAFT, RAMMER, SCOUT, BOMBER, JUGGERNAUT, GUERRILLA
}
enum class Skill {
    DASH, ESCAPE, PERSIST, FORTIFY, STIFF, SURPRISE, SCOUT, CREEP, CONVERT, HEAL,
    STATIC, SPLASH, CARRY, INDEPENDENT, STOMP
}
enum class CityRewardType { WORKSHOP, EXPLORER, WALL, STARS_5, BORDER_GROWTH, POPULATION_3, PARK, SUPER_UNIT }
enum class SpecialActionType { HEAL_ADJACENT, INFILTRATE, DISBAND, DESTROY_BUILDING }

data class Pos(val x: Int, val y: Int) {
    fun chebyshev(other: Pos): Int = max(kotlin.math.abs(x - other.x), kotlin.math.abs(y - other.y))
    fun neighbours8(): List<Pos> = DIRECTIONS8.map { Pos(x + it.x, y + it.y) }
    fun neighbours4(): List<Pos> = DIRECTIONS4.map { Pos(x + it.x, y + it.y) }
    companion object {
        val DIRECTIONS8 = listOf(Pos(-1,-1), Pos(0,-1), Pos(1,-1), Pos(-1,0), Pos(1,0), Pos(-1,1), Pos(0,1), Pos(1,1))
        val DIRECTIONS4 = listOf(Pos(0,-1), Pos(-1,0), Pos(1,0), Pos(0,1))
    }
}

data class Tile(
    val pos: Pos,
    var terrain: Terrain = Terrain.FIELD,
    var flooded: Boolean = false,
    var climateOwner: Int = 0,
    var territoryOwner: Int? = null,
    var cityId: Int? = null,
    var village: Boolean = false,
    var workCityId: Int? = null,
    var resource: Resource? = null,
    var improvement: Improvement? = null,
    var road: Boolean = false,
    var ruin: Boolean = false,
    var lighthouse: Boolean = false,
    var starfish: Boolean = false,
    var occupantUnitId: Int? = null,
    var improvementBuiltTurn: Int = 0
)

data class FactionDefinition(
    val id: String,
    val displayName: String,
    val initialStars: Int,
    val initialCityLevel: Int,
    val startingTechnology: Set<String>,
    val startingUnit: UnitKind,
    val terrainMultipliers: Map<Terrain, Double> = emptyMap(),
    val resourceMultipliers: Map<Resource, Double> = emptyMap(),
    val unitReplacementMap: Map<UnitKind, UnitKind> = emptyMap(),
    val technologyReplacementMap: Map<String, String> = emptyMap(),
    val buildingReplacementMap: Map<Improvement, Improvement> = emptyMap(),
    val globalRules: Set<String> = emptySet()
)

data class PlayerState(
    val id: Int,
    var factionId: String,
    var stars: Int,
    var eliminated: Boolean = false,
    var capitalCityId: Int? = null,
    val technologies: MutableSet<String> = mutableSetOf(),
    val startingTechnologies: MutableSet<String> = mutableSetOf(),
    val metPlayers: MutableSet<Int> = mutableSetOf(),
    val embassies: MutableSet<Int> = mutableSetOf(),
    var kills: Int = 0,
    var losses: Int = 0,
    var opponentsDefeated: Int = 0
)

data class City(
    val id: Int,
    var owner: Int,
    val pos: Pos,
    var level: Int = 1,
    var basePopulation: Int = 0,
    var spentPopulation: Int = 0,
    var buildingPopulation: Int = 0,
    var connectionPopulation: Int = 0,
    var workshop: Boolean = false,
    var park: Boolean = false,
    var wall: Boolean = false,
    var isCapital: Boolean = false,
    var originalOwner: Int = owner,
    var borderExpanded: Boolean = false,
    var pendingRewardLevel: Int? = null,
    var besiegedByUnitId: Int? = null,
    val supportedUnitIds: MutableSet<Int> = mutableSetOf()
) {
    val totalPopulation: Int get() = max(0, basePopulation + buildingPopulation + connectionPopulation)
    val population: Int get() = max(0, totalPopulation - spentPopulation)
    val populationNeeded: Int get() = level + 1
    val capacity: Int get() = level + 1
    fun income(humanPlayerId: Int = 0): Int {
        if (besiegedByUnitId != null) return 0
        val capitalBonus = if (isCapital && originalOwner == owner && owner == humanPlayerId) 1 else 0
        return level + (if (workshop) 1 else 0) + (if (park) 1 else 0) + capitalBonus
    }
}

data class UnitState(
    val id: Int,
    var owner: Int,
    var pos: Pos,
    var kind: UnitKind,
    var hp: Int,
    var supportCityId: Int? = null,
    var carriedKind: UnitKind? = null,
    var veteran: Boolean = false,
    var poisoned: Boolean = false,
    var frozenTurns: Int = 0,
    var moved: Boolean = false,
    var attacked: Boolean = false,
    var tookAction: Boolean = false,
    var captureReadyOnRound: Int? = null,
    var independent: Boolean = false,
    var kills: Int = 0
)

data class GameConfig(
    val seed: Long,
    val mapSize: MapSizeSetting = MapSizeSetting.TINY,
    val waterPreset: WaterPreset = WaterPreset.CONTINENTS,
    val gameMode: GameMode = GameMode.CONQUEST,
    val factionIds: List<String> = listOf("asteria", "sunspire"),
    val humanPlayerId: Int = 0,
    val difficulty: Int = 2,
    val creativeEndless: Boolean = false
)

data class SimState(
    val seed: Long,
    var rngCounter: Long,
    var roundNumber: Int,
    var activePlayer: Int,
    val gameMode: GameMode,
    val waterPreset: WaterPreset,
    val size: Int,
    val humanPlayerId: Int,
    val difficulty: Int,
    val map: Array<Array<Tile>>,
    val players: MutableList<PlayerState>,
    val cities: MutableList<City>,
    val units: MutableList<UnitState>,
    val diplomacy: Array<Array<Relation>>,
    val discoveredTiles: MutableList<MutableSet<Pos>>,
    val score: MutableList<Int>,
    var finished: Boolean = false,
    var winnerId: Int? = null,
    var victoryReason: String = "",
    var nextCityId: Int = 1,
    var nextUnitId: Int = 1,
    var actionSerial: Long = 0,
    val creativeEndless: Boolean = false
) {
    fun inBounds(p: Pos) = p.x in 0 until size && p.y in 0 until size
    fun tile(p: Pos): Tile? = if (inBounds(p)) map[p.x][p.y] else null
    fun unit(id: Int): UnitState? = units.firstOrNull { it.id == id }
    fun city(id: Int): City? = cities.firstOrNull { it.id == id }
    fun cityAt(p: Pos): City? = tile(p)?.cityId?.let(::city)
    fun ownedCities(playerId: Int): List<City> = cities.filter { it.owner == playerId }
    fun ownedUnits(playerId: Int): List<UnitState> = units.filter { it.owner == playerId }
    fun capital(playerId: Int): City? = players[playerId].capitalCityId?.let(::city)
    fun livingPlayers(): List<PlayerState> = players.filter { !it.eliminated }

    fun deepCopy(): SimState {
        val newMap = Array(size) { x -> Array(size) { y -> map[x][y].copy(pos = map[x][y].pos) } }
        val newPlayers = players.map { p -> p.copy(
            technologies = p.technologies.toMutableSet(),
            startingTechnologies = p.startingTechnologies.toMutableSet(),
            metPlayers = p.metPlayers.toMutableSet(),
            embassies = p.embassies.toMutableSet()
        ) }.toMutableList()
        val newCities = cities.map { c -> c.copy(supportedUnitIds = c.supportedUnitIds.toMutableSet()) }.toMutableList()
        val newUnits = units.map { it.copy() }.toMutableList()
        val newDip = Array(players.size) { i -> Array(players.size) { j -> diplomacy[i][j] } }
        val newDisc = discoveredTiles.map { it.toMutableSet() }.toMutableList()
        return copy(
            map = newMap, players = newPlayers, cities = newCities, units = newUnits,
            diplomacy = newDip, discoveredTiles = newDisc, score = score.toMutableList()
        )
    }
}

enum class EventType {
    COMMAND_REJECTED, UNIT_MOVED, UNIT_DAMAGED, UNIT_HEALED, UNIT_KILLED, UNIT_CONVERTED,
    CITY_BESIEGED, CITY_CAPTURED, VILLAGE_CAPTURED, CITY_UPGRADED, CITY_REWARD,
    STARS_CHANGED, TECH_RESEARCHED, RESOURCE_USED, IMPROVEMENT_BUILT, ROAD_BUILT,
    TILES_DISCOVERED, CIVILIZATION_MET, RUIN_EXAMINED, STARFISH_GATHERED,
    SHIP_UPGRADED, EMBASSY_ESTABLISHED, PEACE_OFFERED, PEACE_BROKEN,
    TURN_STARTED, TURN_ENDED, PLAYER_ELIMINATED, SCORE_CHANGED, VICTORY
}

data class SimEvent(
    val type: EventType,
    val playerId: Int? = null,
    val subjectId: Int? = null,
    val targetId: Int? = null,
    val from: Pos? = null,
    val to: Pos? = null,
    val amount: Int? = null,
    val message: String = ""
)

data class CommandResult(val accepted: Boolean, val reason: String = "", val events: List<SimEvent> = emptyList())
