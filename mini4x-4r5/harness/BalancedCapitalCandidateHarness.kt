package com.example.mini4x.research

import com.example.mini4x.sim.*
import java.util.PriorityQueue
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

private data class ReachNode(val pos: Pos, val cost: Int, val terminal: Boolean)

private data class Row(
    val influenceCv: Double,
    val influenceGapFraction: Double,
    val influence: IntArray,
    val largestLandShare: Double,
    val capitalsConnected: Boolean,
    val openingVillage: BooleanArray,
)

object BalancedCapitalCandidateHarness {
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
        check(capitals.toSet().size == capitals.size) { "$label duplicate capitals: $capitals" }
        val minSep = requiredSeparation(state.size, state.players.size)
        for (a in capitals.indices) for (b in a + 1 until capitals.size) {
            check(capitals[a].chebyshev(capitals[b]) >= minSep) {
                "$label capital separation ${capitals[a].chebyshev(capitals[b])} < $minSep"
            }
        }
        capitals.forEach { pos ->
            check(pos.x in 1 until state.size - 1 && pos.y in 1 until state.size - 1) { "$label capital on outer edge: $pos" }
            check(state.tile(pos)?.terrain == Terrain.FIELD) { "$label capital not FIELD: $pos" }
            check(pos.neighbours8().filter(state::inBounds).none { q -> isWater(state.tile(q)!!.terrain) }) {
                "$label forced capital ring contains water at $pos"
            }
        }
    }

    private fun influence(capitals: List<Pos>, n: Int): IntArray {
        val counts = IntArray(capitals.size)
        for (x in 0 until n) for (y in 0 until n) {
            val p = Pos(x, y)
            val owner = capitals.indices.minByOrNull { i -> capitals[i].chebyshev(p) * 1000 + i } ?: 0
            counts[owner]++
        }
        return counts
    }

    private fun cv(values: IntArray): Double {
        val mean = values.average()
        if (mean == 0.0) return 0.0
        val variance = values.sumOf { value -> val d = value - mean; d * d } / values.size
        return sqrt(variance) / mean
    }

    private fun roadLike(tile: Tile) = tile.road || tile.cityId != null || tile.village || tile.improvement == Improvement.BRIDGE

    private fun roadUsable(state: SimState, playerId: Int, tile: Tile): Boolean {
        val owner = tile.territoryOwner
        return owner == null || owner == playerId || state.diplomacy[playerId][owner] == Relation.PEACE
    }

    private fun edgeCost(state: SimState, playerId: Int, from: Tile, to: Tile): Int =
        if (roadLike(from) && roadLike(to) && roadUsable(state, playerId, from) && roadUsable(state, playerId, to)) 1 else 2

    /** Terrain-aware opportunity envelope, not an AI policy. */
    private fun oneTurnEnds(state: SimState, playerId: Int, kind: UnitKind, start: Pos): Set<Pos> {
        val player = state.players[playerId]
        val type = UnitCatalog[kind]
        val allowance = type.movement * 2
        val creep = Skill.CREEP in type.skills
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
                val occupied = target.occupantUnitId?.let(state::unit)
                if (occupied != null && occupied.owner != playerId) continue
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

    private fun villageByTurn3(state: SimState, playerId: Int): Boolean {
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

    private fun landStats(state: SimState): Pair<Double, Boolean> {
        val n = state.size
        val labels = Array(n) { IntArray(n) { -1 } }
        val sizes = mutableListOf<Int>()
        var label = 0
        for (sx in 0 until n) for (sy in 0 until n) {
            if (isWater(state.map[sx][sy].terrain) || labels[sx][sy] >= 0) continue
            val queue = ArrayDeque<Pos>()
            queue += Pos(sx, sy)
            labels[sx][sy] = label
            var count = 0
            while (queue.isNotEmpty()) {
                val p = queue.removeFirst()
                count++
                for (q in p.neighbours4()) {
                    if (!state.inBounds(q) || isWater(state.tile(q)!!.terrain) || labels[q.x][q.y] >= 0) continue
                    labels[q.x][q.y] = label
                    queue += q
                }
            }
            sizes += count
            label++
        }
        val capitalLabels = state.players.map { player ->
            val p = state.capital(player.id)?.pos ?: error("missing capital ${player.id}")
            labels[p.x][p.y]
        }.toSet()
        val largest = (sizes.maxOrNull() ?: 0).toDouble() / (n * n)
        return largest to (capitalLabels.size == 1)
    }

    private fun row(state: SimState): Row {
        val capitals = state.players.map { state.capital(it.id)?.pos ?: error("missing capital ${it.id}") }
        val areas = influence(capitals, state.size)
        val equalShare = state.size * state.size.toDouble() / areas.size
        val gapFraction = (areas.maxOrNull()!! - areas.minOrNull()!!) / equalShare
        val (largestLand, connected) = landStats(state)
        val opening = BooleanArray(state.players.size) { pid -> villageByTurn3(state, pid) }
        return Row(cv(areas), gapFraction, areas, largestLand, connected, opening)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0)?.lowercase() ?: "candidate"
        require(mode == "baseline" || mode == "candidate")
        val seeds = args.getOrNull(1)?.toIntOrNull()?.coerceIn(12, 200) ?: 30
        var maps = 0
        var deterministicChecks = 0

        for (size in MapSizeSetting.entries) for (preset in WaterPreset.entries) {
            val rows = mutableListOf<Row>()
            for (seed in 0 until seeds) {
                val cfg = config(7_000_001L + size.ordinal * 1_000_003L + preset.ordinal * 100_003L + seed, size, preset)
                val state = MapGenerator.create(cfg)
                val label = "$mode/$size/$preset/$seed"
                basicContracts(state, label)
                val encoded = GameStateCodec.encode(state)
                if (seed < 2) {
                    val again = MapGenerator.create(cfg)
                    check(GameStateCodec.encode(again) == encoded) { "$label nondeterministic generation" }
                    deterministicChecks++
                }
                rows += row(state)
                maps++
            }

            val meanCv = rows.sumOf { it.influenceCv } / rows.size
            val meanGap = rows.sumOf { it.influenceGapFraction } / rows.size
            val slotMeans = DoubleArray(4) { slot -> rows.sumOf { it.influence[slot].toDouble() } / rows.size }
            val equalShare = size.n * size.n.toDouble() / 4.0
            val slotSpread = (slotMeans.maxOrNull()!! - slotMeans.minOrNull()!!) / equalShare
            val largestLand = rows.sumOf { it.largestLandShare } / rows.size
            val connected = rows.count { it.capitalsConnected }.toDouble() / rows.size
            val openingSlots = DoubleArray(4) { slot -> rows.count { it.openingVillage[slot] }.toDouble() / rows.size }
            val openingMean = openingSlots.average()

            if (mode == "candidate") {
                // Absolute within-map geometry bound. Persistent slot-order bias is assessed
                // by the paired baseline-vs-candidate comparator over the identical corpus.
                check(meanCv <= 0.20) { "$size/$preset candidate mean influence CV too high: $meanCv" }
            }

            println(
                "capital_quality mode=$mode size=$size preset=$preset seeds=$seeds " +
                    "meanCV=${"%.5f".format(meanCv)} meanGapFraction=${"%.5f".format(meanGap)} " +
                    "slotSpread=${"%.5f".format(slotSpread)} largestLand=${"%.5f".format(largestLand)} " +
                    "connected=${"%.5f".format(connected)} openingMean=${"%.5f".format(openingMean)} " +
                    "openingSlots=${openingSlots.joinToString(",") { "%.5f".format(it) }}"
            )
        }

        println("balanced_capital_harness=PASS mode=$mode maps=$maps deterministicChecks=$deterministicChecks sizes=${MapSizeSetting.entries.size} presets=${WaterPreset.entries.size}")
    }
}
