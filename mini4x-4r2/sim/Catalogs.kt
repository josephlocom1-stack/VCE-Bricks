package com.example.mini4x.sim

import kotlin.math.ceil

data class Technology(
    val id: String,
    val label: String,
    val tier: Int,
    val requires: String?,
    val branch: String,
    val effect: String
)

object TechnologyCatalog {
    val all = listOf(
        Technology("climbing","Climbing",1,null,"climbing","Mountains, mountain defence, reveal ore"),
        Technology("mining","Mining",2,"climbing","climbing","Mines"),
        Technology("smithery","Smithery",3,"mining","climbing","Swordsmen and forges"),
        Technology("meditation","Meditation",2,"climbing","climbing","Mountain temples"),
        Technology("philosophy","Philosophy",3,"meditation","climbing","Mind Benders and 33% research discount"),

        Technology("fishing","Fishing",1,null,"fishing","Fish, ports, rafts, shallow water"),
        Technology("sailing","Sailing",2,"fishing","fishing","Scouts and ocean movement"),
        Technology("navigation","Navigation",3,"sailing","fishing","Bombers and starfish gathering"),
        Technology("ramming","Ramming",2,"fishing","fishing","Rammers"),
        Technology("aquatism","Aquatism",3,"ramming","fishing","Aquatic defence and water temples"),

        Technology("hunting","Hunting",1,null,"hunting","Animals"),
        Technology("archery","Archery",2,"hunting","hunting","Archers and forest defence"),
        Technology("spiritualism","Spiritualism",3,"archery","hunting","Forest temples"),
        Technology("forestry","Forestry",2,"hunting","hunting","Lumber huts and clear forest"),
        Technology("mathematics","Mathematics",3,"forestry","hunting","Catapults and sawmills"),

        Technology("organization","Organization",1,null,"organization","Fruit"),
        Technology("farming","Farming",2,"organization","organization","Farms"),
        Technology("construction","Construction",3,"farming","organization","Windmills and burn forest"),
        Technology("strategy","Strategy",2,"organization","organization","Defenders and peace treaties"),
        Technology("diplomacy","Diplomacy",3,"strategy","organization","Cloaks, embassies, capital vision"),

        Technology("riding","Riding",1,null,"riding","Riders"),
        Technology("roads","Roads",2,"riding","riding","Roads and bridges"),
        Technology("trade","Trade",3,"roads","riding","Markets"),
        Technology("free_spirit","Free Spirit",2,"riding","riding","Temples and disband"),
        Technology("chivalry","Chivalry",3,"free_spirit","riding","Knights and destroy building")
    )
    private val byId = all.associateBy { it.id }
    operator fun get(id: String): Technology = byId[id] ?: error("Unknown tech $id")

    fun descendantsOf(id: String): Set<String> {
        val result = mutableSetOf<String>()
        var changed = true
        while (changed) {
            changed = false
            for (t in all) {
                if (t.id in result) continue
                if (t.requires == id || t.requires in result) { result += t.id; changed = true }
            }
        }
        return result
    }

    fun isAncestor(candidate: String, child: String): Boolean {
        var cur: Technology? = byId[child]
        while (cur?.requires != null) {
            if (cur.requires == candidate) return true
            cur = byId[cur.requires]
        }
        return false
    }

    fun researchCost(state: SimState, playerId: Int, techId: String): Int {
        val tech = get(techId)
        val cities = state.ownedCities(playerId).size.coerceAtLeast(1)
        val base = tech.tier * cities + 4
        return if ("philosophy" in state.players[playerId].technologies) ceil(base * .67).toInt() else base
    }

    fun canResearch(state: SimState, playerId: Int, techId: String): Boolean {
        val p = state.players[playerId]
        if (techId in p.technologies) return false
        val tech = get(techId)
        if (tech.requires == null) return true
        if (tech.requires in p.technologies) return true
        return p.startingTechnologies.any { owned -> isAncestor(techId, owned) }
    }
}

data class UnitType(
    val kind: UnitKind,
    val cost: Int?,
    val maxHP: Int,
    val attack: Double,
    val defense: Double,
    val movement: Int,
    val range: Int,
    val skills: Set<Skill>
)

object UnitCatalog {
    private val u = mapOf(
        UnitKind.WARRIOR to UnitType(UnitKind.WARRIOR,2,10,2.0,2.0,1,1,setOf(Skill.DASH,Skill.FORTIFY)),
        UnitKind.RIDER to UnitType(UnitKind.RIDER,3,10,2.0,1.0,2,1,setOf(Skill.DASH,Skill.ESCAPE)),
        UnitKind.ARCHER to UnitType(UnitKind.ARCHER,3,10,2.0,1.0,1,2,setOf(Skill.DASH)),
        UnitKind.DEFENDER to UnitType(UnitKind.DEFENDER,3,15,1.0,3.0,1,1,setOf(Skill.FORTIFY)),
        UnitKind.SWORDSMAN to UnitType(UnitKind.SWORDSMAN,5,15,3.0,3.0,1,1,setOf(Skill.DASH,Skill.FORTIFY)),
        UnitKind.MIND_BENDER to UnitType(UnitKind.MIND_BENDER,5,10,0.0,1.0,1,1,setOf(Skill.CONVERT,Skill.HEAL)),
        UnitKind.CATAPULT to UnitType(UnitKind.CATAPULT,8,10,4.0,0.0,1,3,setOf(Skill.STIFF)),
        UnitKind.CLOAK to UnitType(UnitKind.CLOAK,8,5,2.0,.5,2,1,setOf(Skill.DASH,Skill.SURPRISE,Skill.CREEP)),
        UnitKind.KNIGHT to UnitType(UnitKind.KNIGHT,8,10,3.5,1.0,3,1,setOf(Skill.DASH,Skill.PERSIST)),
        UnitKind.GIANT to UnitType(UnitKind.GIANT,null,40,5.0,4.0,1,1,setOf(Skill.FORTIFY)),
        UnitKind.RAFT to UnitType(UnitKind.RAFT,null,10,0.0,1.0,2,0,setOf(Skill.CARRY)),
        UnitKind.RAMMER to UnitType(UnitKind.RAMMER,null,10,3.0,3.0,3,1,setOf(Skill.CARRY,Skill.DASH)),
        UnitKind.SCOUT to UnitType(UnitKind.SCOUT,null,10,2.0,1.0,3,2,setOf(Skill.CARRY,Skill.SCOUT,Skill.DASH)),
        UnitKind.BOMBER to UnitType(UnitKind.BOMBER,null,10,3.0,2.0,2,3,setOf(Skill.CARRY,Skill.SPLASH,Skill.STIFF)),
        UnitKind.JUGGERNAUT to UnitType(UnitKind.JUGGERNAUT,null,40,4.0,4.0,2,1,setOf(Skill.CARRY,Skill.STOMP,Skill.DASH)),
        UnitKind.GUERRILLA to UnitType(UnitKind.GUERRILLA,null,10,2.0,2.0,1,1,setOf(Skill.DASH,Skill.SURPRISE,Skill.INDEPENDENT))
    )
    operator fun get(kind: UnitKind) = u[kind] ?: error("Unknown unit $kind")
    fun maxHP(unit: UnitState): Int = when {
        unit.kind in setOf(UnitKind.RAFT,UnitKind.RAMMER,UnitKind.SCOUT,UnitKind.BOMBER) && unit.carriedKind != null -> get(unit.carriedKind!!).maxHP
        else -> get(unit.kind).maxHP
    }
    fun skills(unit: UnitState): Set<Skill> = get(unit.kind).skills + (if (unit.independent) setOf(Skill.INDEPENDENT) else emptySet())
}

object FactionCatalog {
    val all: List<FactionDefinition> = listOf(
        FactionDefinition(id="asteria", displayName="Asteria", initialStars=5, initialCityLevel=1, startingTechnology=setOf("organization"), startingUnit=UnitKind.WARRIOR, resourceMultipliers=mapOf(Resource.FRUIT to 2.0), terrainMultipliers=mapOf(Terrain.FOREST to 1.05)),
        FactionDefinition(id="sunspire", displayName="Sunspire", initialStars=6, initialCityLevel=1, startingTechnology=setOf("riding"), startingUnit=UnitKind.RIDER, terrainMultipliers=mapOf(Terrain.FOREST to .45), resourceMultipliers=mapOf(Resource.CROP to 1.25)),
        FactionDefinition(id="virelia", displayName="Virelia", initialStars=7, initialCityLevel=1, startingTechnology=setOf("hunting"), startingUnit=UnitKind.ARCHER, terrainMultipliers=mapOf(Terrain.FOREST to 1.55), resourceMultipliers=mapOf(Resource.ANIMAL to 1.5)),
        FactionDefinition(id="emberhold", displayName="Emberhold", initialStars=7, initialCityLevel=1, startingTechnology=setOf("climbing"), startingUnit=UnitKind.WARRIOR, terrainMultipliers=mapOf(Terrain.MOUNTAIN to 1.5), resourceMultipliers=mapOf(Resource.ORE to 1.5))
    )
    private val byId = all.associateBy { it.id }
    operator fun get(id: String) = byId[id] ?: error("Unknown faction $id")
}

object ImprovementRules {
    data class Rule(val cost: Int, val tech: String?, val terrain: Set<Terrain>, val basePopulation: Int)
    val rules = mapOf(
        Improvement.LUMBER_HUT to Rule(3,"forestry",setOf(Terrain.FOREST),1),
        Improvement.FARM to Rule(5,"farming",setOf(Terrain.FIELD),2),
        Improvement.MINE to Rule(5,"mining",setOf(Terrain.MOUNTAIN),2),
        Improvement.PORT to Rule(7,"fishing",setOf(Terrain.WATER),1),
        Improvement.SAWMILL to Rule(5,"mathematics",setOf(Terrain.FIELD,Terrain.FOREST),0),
        Improvement.WINDMILL to Rule(5,"construction",setOf(Terrain.FIELD),0),
        Improvement.FORGE to Rule(5,"smithery",setOf(Terrain.FIELD,Terrain.MOUNTAIN),0),
        Improvement.TEMPLE to Rule(20,"free_spirit",setOf(Terrain.FIELD),1),
        Improvement.FOREST_TEMPLE to Rule(15,"spiritualism",setOf(Terrain.FOREST),1),
        Improvement.MONUMENT to Rule(0,null,setOf(Terrain.FIELD),3),
        Improvement.MARKET to Rule(5,"trade",setOf(Terrain.FIELD),0),
        Improvement.BRIDGE to Rule(5,"roads",setOf(Terrain.WATER),0)
    )
}
