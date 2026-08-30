package com.example.mini4x.research

import com.example.mini4x.sim.*
import java.security.MessageDigest
import java.util.PriorityQueue
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

private data class MapQuality(
    val waterShare: Double,
    val largestLandShare: Double,
    val waterComponents: Int,
    val capitalsConnected: Boolean,
    val openingVillageRate: Double,
)

object LakesThresholdPropertyHarness {
    private val factions = listOf("asteria", "sunspire", "virelia", "emberhold")

    private fun isWater(t: Terrain) = t == Terrain.WATER || t == Terrain.OCEAN

    private fun config(seed: Long, size: MapSizeSetting, water: WaterPreset) = GameConfig(
        seed = seed,
        mapSize = size,
        waterPreset = water,
        gameMode = GameMode.CONQUEST,
        factionIds = factions,
        humanPlayerId = 0,
        difficulty = 2,
        creativeEndless = false,
    )

    private fun requiredSeparation(n: Int, players: Int): Int =
        max(4, (n.toDouble() / sqrt(players.toDouble()) * .58).roundToInt())

    private fun basicContracts(state: SimState, label: String) {
        SimInvariantAudit.assertValid(state, label)
        val capitals = state.players.map { player -> state.capital(player.id)?.pos ?: error("$label missing capital ${player.id}") }
        check(capitals.toSet().size == capitals.size) { "$label duplicate capital positions: $capitals" }
        val minSep = requiredSeparation(state.size, state.players.size)
        for (a in capitals.indices) for (b in a + 1 until capitals.size) {
            check(capitals[a].chebyshev(capitals[b]) >= minSep) {
                "$label capital separation ${capitals[a].chebyshev(capitals[b])} < $minSep"
            }
        }
        capitals.forEach { pos ->
            check(state.tile(pos)?.terrain == Terrain.FIELD) { "$label capital $pos not FIELD" }
            check(pos.neighbours8().filter(state::inBounds).none { q -> isWater(state.tile(q)!!.terrain) }) {
                "$label capital $pos has water in forced start ring"
            }
        }
    }

    private fun roadLike(tile: Tile) = tile.road || tile.cityId != null || tile.village || tile.improvement == Improvement.BRIDGE

    private fun roadUsable(state: SimState, playerId: Int, tile: Tile): Boolean {
        val owner = tile.territoryOwner
        return owner == null || owner == playerId || state.diplomacy[playerId][owner] == Relation.PEACE
    }

    private fun edgeCost(state: SimState, playerId: Int, from: Tile, to: Tile): Int =
        if (roadLike(from) && roadLike(to) && roadUsable(state, playerId, from) && roadUsable(state, playerId, to)) 1 else 2

    private data class ReachNode(val pos: Pos, val cost: Int, val terminal: Boolean)

    /**
     * Terrain-aware one-turn opening envelope using the current production movement costs.
     * Starting factions have no Fishing/Port, so water is intentionally not an opening path.
     * Occupied tiles remain blocked; rough terrain can be entered but terminates continuation.
     */
    private fun oneTurnEnds(state: SimState, playerId: Int, kind: UnitKind, start: Pos): Set<Pos> {
        val player = state.players[playerId]
        val unitType = UnitCatalog[kind]
        val allowance = unitType.movement * 2
        val creep = Skill.CREEP in unitType.skills
        val out = mutableSetOf(start)
        val best = mutableMapOf(start to 0)
        val queue = PriorityQueue<ReachNode>(compareBy<ReachNode> { it.cost }.thenBy { it.pos.x }.thenBy { it.pos.y })
        queue += ReachNode(start, 0, false)

        while (queue.isNotEmpty()) {
            val node = queue.poll()
            if (node.cost != best[node.pos] || node.terminal) continue
            val from = state.tile(node.pos) ?: continue
            for (next in node.pos.neighbours8()) {
                if (!state.inBounds(next)) continue
                val target = state.tile(next)!!
                if (isWater(target.terrain)) continue
                if (target.terrain == Terrain.MOUNTAIN && "climbing" !in player.technologies && !creep) continue
                if (target.occupantUnitId != null && next != start) continue
                val nextCost = node.cost + edgeCost(state, playerId, from, target)
                if (nextCost > allowance) continue
                val terminal = !creep && target.terrain in setOf(Terrain.FOREST, Terrain.MOUNTAIN)
                if (nextCost < (best[next] ?: Int.MAX_VALUE)) {
                    best[next] = nextCost
                    out += next
                    queue += ReachNode(next, nextCost, terminal)
                }
            }
        }
        return out
    }

    private fun openingVillageReachableByTurn3(state: SimState, playerId: Int): Boolean {
        val capital = state.capital(playerId) ?: return false
        val unit = state.ownedUnits(playerId).firstOrNull { it.pos == capital.pos } ?: return false
        var positions: Set<Pos> = setOf(capital.pos)
        repeat(3) {
            val next = mutableSetOf<Pos>()
            positions.forEach { start -> next += oneTurnEnds(state, playerId, unit.kind, start) }
            positions = next
            if (positions.any { state.tile(it)?.village == true }) return true
        }
        return false
    }

    private fun quality(state: SimState): MapQuality {
        val n = state.size
        val total = n * n
        var water = 0
        val landLabels = Array(n) { IntArray(n) { -1 } }
        val landSizes = mutableListOf<Int>()
        var landLabel = 0
        for (x in 0 until n) for (y in 0 until n) if (isWater(state.map[x][y].terrain)) water++

        for (sx in 0 until n) for (sy in 0 until n) {
            if (isWater(state.map[sx][sy].terrain) || landLabels[sx][sy] >= 0) continue
            val queue = ArrayDeque<Pos>()
            queue += Pos(sx, sy)
            landLabels[sx][sy] = landLabel
            var count = 0
            while (queue.isNotEmpty()) {
                val p = queue.removeFirst()
                count++
                for (q in p.neighbours4()) {
                    if (!state.inBounds(q) || isWater(state.tile(q)!!.terrain) || landLabels[q.x][q.y] >= 0) continue
                    landLabels[q.x][q.y] = landLabel
                    queue += q
                }
            }
            landSizes += count
            landLabel++
        }

        val seenWater = Array(n) { BooleanArray(n) }
        var waterComponents = 0
        for (sx in 0 until n) for (sy in 0 until n) {
            if (!isWater(state.map[sx][sy].terrain) || seenWater[sx][sy]) continue
            waterComponents++
            val queue = ArrayDeque<Pos>()
            queue += Pos(sx, sy)
            seenWater[sx][sy] = true
            while (queue.isNotEmpty()) {
                val p = queue.removeFirst()
                for (q in p.neighbours4()) {
                    if (!state.inBounds(q) || !isWater(state.tile(q)!!.terrain) || seenWater[q.x][q.y]) continue
                    seenWater[q.x][q.y] = true
                    queue += q
                }
            }
        }

        val capitalLabels = state.players.map { player ->
            val pos = state.capital(player.id)?.pos ?: error("missing capital ${player.id}")
            landLabels[pos.x][pos.y]
        }.toSet()
        val openingRate = state.players.indices.count { openingVillageReachableByTurn3(state, it) }.toDouble() / state.players.size
        return MapQuality(
            waterShare = water.toDouble() / total,
            largestLandShare = (landSizes.maxOrNull() ?: 0).toDouble() / total,
            waterComponents = waterComponents,
            capitalsConnected = capitalLabels.size == 1,
            openingVillageRate = openingRate,
        )
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0)?.lowercase() ?: "candidate"
        require(mode == "baseline" || mode == "candidate") { "mode must be baseline or candidate" }
        val seeds = args.getOrNull(1)?.toIntOrNull()?.coerceIn(12, 300) ?: 40
        val digest = MessageDigest.getInstance("SHA-256")
        var nonLakesMaps = 0
        var totalMaps = 0
        var deterministicChecks = 0

        for (size in MapSizeSetting.entries) {
            val lakes = mutableListOf<MapQuality>()
            for (preset in WaterPreset.entries) {
                for (seed in 0 until seeds) {
                    val cfg = config(4_000_003L + size.ordinal * 1_000_003L + preset.ordinal * 100_003L + seed, size, preset)
                    val state = MapGenerator.create(cfg)
                    val label = "$mode/$size/$preset/$seed"
                    basicContracts(state, label)
                    val encoded = GameStateCodec.encode(state)
                    if (preset != WaterPreset.LAKES) {
                        digest.update("$size/$preset/$seed\n".toByteArray())
                        digest.update(encoded.toByteArray())
                        nonLakesMaps++
                    } else {
                        lakes += quality(state)
                    }
                    if (seed < 2) {
                        val again = MapGenerator.create(cfg)
                        check(GameStateCodec.encode(again) == encoded) { "$label generation is nondeterministic" }
                        deterministicChecks++
                    }
                    totalMaps++
                }
            }

            val meanWater = lakes.sumOf { it.waterShare } / lakes.size
            val nonzeroWater = lakes.count { it.waterShare > 0.0 }.toDouble() / lakes.size
            val meanLargestLand = lakes.sumOf { it.largestLandShare } / lakes.size
            val meanComponents = lakes.sumOf { it.waterComponents }.toDouble() / lakes.size
            val connectedRate = lakes.count { it.capitalsConnected }.toDouble() / lakes.size
            val openingRate = lakes.sumOf { it.openingVillageRate } / lakes.size

            if (mode == "baseline") {
                check(lakes.all { it.waterShare == 0.0 }) { "$size baseline LAKES unexpectedly contains natural water" }
            } else {
                check(meanWater in 0.05..0.20) { "$size LAKES mean water share out of broad band: $meanWater" }
                check(nonzeroWater >= 0.85) { "$size LAKES too often has no water: $nonzeroWater" }
                check(meanLargestLand >= 0.75) { "$size LAKES fragments main landmass too strongly: $meanLargestLand" }
                check(meanComponents >= 1.0) { "$size LAKES does not form distinct water bodies often enough: $meanComponents" }
                check(connectedRate >= 0.80) { "$size LAKES disconnects capitals too often: $connectedRate" }
                check(openingRate >= 0.50) { "$size LAKES three-turn village opportunity too low: $openingRate" }
            }

            println(
                "lakes_quality mode=$mode size=$size seeds=$seeds " +
                    "meanWater=${"%.4f".format(meanWater)} nonzero=${"%.3f".format(nonzeroWater)} " +
                    "largestLand=${"%.3f".format(meanLargestLand)} components=${"%.2f".format(meanComponents)} " +
                    "capitalsConnected=${"%.3f".format(connectedRate)} villageByTurn3=${"%.3f".format(openingRate)}"
            )
        }

        if (mode == "baseline") println("lakes_threshold_defect_reproduced=PASS all_sizes_no_natural_water=true")
        else println("lakes_threshold_candidate_properties=PASS threshold_candidate=0.870")
        println("map_preset_smoke=PASS mode=$mode maps=$totalMaps deterministicChecks=$deterministicChecks sizes=${MapSizeSetting.entries.size} presets=${WaterPreset.entries.size}")
        println("non_lakes_digest=${hex(digest.digest())} maps=$nonLakesMaps")
    }
}
