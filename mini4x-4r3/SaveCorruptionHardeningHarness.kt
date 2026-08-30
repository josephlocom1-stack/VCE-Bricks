package com.example.mini4x.research

import com.example.mini4x.sim.*
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.util.Base64

object SaveCorruptionHardeningHarness {
    private data class CountOffsets(
        val mapSize: Int,
        val players: Int,
        val firstTechnologySet: Int,
        val cities: Int,
        val units: Int,
    )

    private fun consumed(raw: ByteArray, input: ByteArrayInputStream): Int = raw.size - input.available()
    private fun skipPos(i: DataInputStream) { i.readInt(); i.readInt() }
    private fun skipStrings(i: DataInputStream) { repeat(i.readInt()) { i.readUTF() } }
    private fun skipInts(i: DataInputStream) { repeat(i.readInt()) { i.readInt() } }

    private fun offsets(encoded: String): CountOffsets {
        val raw = Base64.getDecoder().decode(encoded)
        val bytes = ByteArrayInputStream(raw)
        val i = DataInputStream(bytes)
        i.readInt(); i.readInt(); i.readLong(); i.readLong(); i.readInt(); i.readInt(); i.readUTF(); i.readUTF()
        val mapSize = consumed(raw, bytes); i.readInt()
        i.readInt(); i.readInt(); i.readBoolean(); i.readInt(); i.readUTF(); i.readInt(); i.readInt(); i.readLong(); i.readBoolean()
        val players = consumed(raw, bytes)
        val playerCount = i.readInt()
        var firstTech = -1
        repeat(playerCount) { p ->
            i.readInt(); i.readUTF(); i.readInt(); i.readBoolean(); i.readInt()
            if (p == 0) firstTech = consumed(raw, bytes)
            skipStrings(i); skipStrings(i); skipInts(i); skipInts(i); i.readInt(); i.readInt(); i.readInt()
        }
        check(firstTech >= 0)
        val cities = consumed(raw, bytes)
        val cityCount = i.readInt()
        repeat(cityCount) {
            i.readInt(); i.readInt(); skipPos(i)
            i.readInt(); i.readInt(); i.readInt(); i.readInt(); i.readInt()
            i.readBoolean(); i.readBoolean(); i.readBoolean(); i.readBoolean(); i.readInt(); i.readBoolean(); i.readInt(); i.readInt(); skipInts(i)
        }
        val units = consumed(raw, bytes)
        return CountOffsets(mapSize, players, firstTech, cities, units)
    }

    private fun replaceInt(encoded: String, offset: Int, value: Int): String {
        val raw = Base64.getDecoder().decode(encoded).copyOf()
        ByteBuffer.wrap(raw).putInt(offset, value)
        return Base64.getEncoder().encodeToString(raw)
    }

    private fun appendTrailing(encoded: String): String {
        val raw = Base64.getDecoder().decode(encoded)
        return Base64.getEncoder().encodeToString(raw + byteArrayOf(1, 2, 3, 4))
    }

    private fun expectReject(label: String, encoded: String) {
        val failure = runCatching { GameStateCodec.decode(encoded) }.exceptionOrNull()
        check(failure != null) { "$label malformed save unexpectedly decoded" }
        println("corrupt_rejected=$label type=${failure::class.simpleName}")
    }

    private fun baseState(): SimState = MapGenerator.create(
        GameConfig(
            seed = 4_303_003L,
            mapSize = MapSizeSetting.NORMAL,
            waterPreset = WaterPreset.CONTINENTS,
            gameMode = GameMode.CONQUEST,
            factionIds = listOf("asteria", "sunspire", "virelia", "emberhold"),
            humanPlayerId = 0,
            difficulty = 2,
        )
    )

    @JvmStatic
    fun main(args: Array<String>) {
        val state = baseState()
        val encoded = GameStateCodec.encode(state)
        val restored = GameStateCodec.decode(encoded)
        check(GameStateCodec.encode(restored) == encoded) { "valid legacy V1 save no longer round-trips canonically" }
        println("legacy_v1_roundtrip=PASS")

        val o = offsets(encoded)
        expectReject("map_size_before_allocation", replaceInt(encoded, o.mapSize, 31))
        expectReject("player_count_before_allocation", replaceInt(encoded, o.players, 5))
        expectReject("technology_count_before_repeat", replaceInt(encoded, o.firstTechnologySet, 65))
        expectReject("city_count_before_allocation", replaceInt(encoded, o.cities, state.size * state.size + 1))
        expectReject("unit_count_before_allocation", replaceInt(encoded, o.units, state.size * state.size + 1))
        expectReject("trailing_payload", appendTrailing(encoded))
        expectReject("invalid_base64", "not-valid-%%%")
        expectReject("encoded_payload_limit", "A".repeat(1_500_001))
        expectReject("raw_payload_limit", Base64.getEncoder().encodeToString(ByteArray(1_000_001)))

        val badSupport = state.deepCopy()
        val supported = badSupport.units.first { it.supportCityId != null }
        badSupport.city(supported.supportCityId!!)!!.supportedUnitIds.remove(supported.id)
        expectReject("broken_support_relationship", GameStateCodec.encode(badSupport))

        val badDiplomacy = state.deepCopy()
        badDiplomacy.diplomacy[0][1] = Relation.WAR
        badDiplomacy.diplomacy[1][0] = Relation.NEUTRAL
        expectReject("asymmetric_diplomacy", GameStateCodec.encode(badDiplomacy))

        val badTechnology = state.deepCopy()
        badTechnology.players[0].technologies += "not_a_real_technology"
        expectReject("unknown_technology", GameStateCodec.encode(badTechnology))

        val badDiscovery = state.deepCopy()
        badDiscovery.discoveredTiles[0] += Pos(state.size + 1, state.size + 1)
        expectReject("out_of_bounds_discovery", GameStateCodec.encode(badDiscovery))

        println("save_corruption_hardening=PASS")
    }
}
