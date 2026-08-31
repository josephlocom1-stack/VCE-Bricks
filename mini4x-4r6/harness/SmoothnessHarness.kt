package com.example.mini4x.research

import com.example.mini4x.presentation.*
import com.example.mini4x.sim.*

object SmoothnessHarness {
    private fun state(): SimState = MapGenerator.create(
        GameConfig(
            seed = 313_337L,
            mapSize = MapSizeSetting.TINY,
            waterPreset = WaterPreset.CONTINENTS,
            gameMode = GameMode.CONQUEST,
            factionIds = listOf("asteria", "sunspire"),
            humanPlayerId = 0,
            difficulty = 2,
        )
    )

    @JvmStatic
    fun main(args: Array<String>) {
        val before = state()

        val invisible = AnimationPlanner.plan(
            before,
            EndTurn(0),
            listOf(
                SimEvent(EventType.STARS_CHANGED, playerId = 1, amount = 7, message = "Turn income"),
                SimEvent(EventType.TECH_RESEARCHED, playerId = 1, message = "roads"),
            ),
        )
        check(invisible.none { it.type == ClipType.INCOME || it.type == ClipType.RESEARCH }) {
            "non-rendered INCOME/RESEARCH clip survived: $invisible"
        }

        val aiDiscovery = AnimationPlanner.plan(
            before,
            EndTurn(1),
            listOf(SimEvent(EventType.TILES_DISCOVERED, playerId = 1, from = Pos(4, 4), amount = 3)),
        )
        check(aiDiscovery.none { it.type == ClipType.FOG_REVEAL }) { "AI discovery leaked into human animation queue" }

        val humanDiscovery = AnimationPlanner.plan(
            before,
            EndTurn(0),
            listOf(SimEvent(EventType.TILES_DISCOVERED, playerId = 0, from = Pos(4, 4), amount = 3)),
        )
        check(humanDiscovery.any { it.type == ClipType.FOG_REVEAL }) { "human discovery animation was removed" }

        val damage = AnimationPlanner.plan(
            before,
            EndTurn(0),
            listOf(
                SimEvent(
                    EventType.UNIT_DAMAGED,
                    playerId = 0,
                    subjectId = before.ownedUnits(0).first().id,
                    amount = 4,
                    to = before.ownedUnits(0).first().pos,
                )
            ),
        )
        check(damage.any { it.type == ClipType.DAMAGE }) { "visible damage clip was accidentally removed" }

        val queue = AnimationQueue()
        check(!queue.isBusy()) { "fresh queue unexpectedly busy" }
        queue.enqueue(listOf(AnimationClip(ClipType.DAMAGE, durationMs = 10)))
        check(queue.current == null) { "enqueue unexpectedly promoted current clip" }
        check(queue.isBusy()) { "queued-before-first-draw clip not reported busy" }
        check(queue.update(1_000L)) { "queued clip did not start" }
        check(queue.current != null && queue.isBusy()) { "current clip not reported busy" }
        check(!queue.update(1_011L)) { "finished single clip left queue busy" }
        check(!queue.isBusy()) { "finished queue still busy" }

        println("animation_visibility=PASS invisible_removed=true ai_fog_filtered=true human_fog_preserved=true damage_preserved=true")
        println("animation_queue_busy_contract=PASS queued_before_current=true current=true finished=false")
        println("phase4r6_smoothness_jvm=PASS")
    }
}
