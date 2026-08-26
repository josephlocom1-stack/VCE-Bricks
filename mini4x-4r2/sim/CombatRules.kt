package com.example.mini4x.sim

import kotlin.math.floor
import kotlin.math.roundToInt

object CombatRules {
    data class Preview(val damageToDefender:Int,val retaliation:Int,val defenseBonus:Double,val canRetaliate:Boolean)

    fun defenseBonus(state:SimState, defender:UnitState):Double {
        var bonus=1.0
        val tile=state.tile(defender.pos) ?: return bonus
        val skills=UnitCatalog.skills(defender)
        val tech=state.players[defender.owner].technologies
        if(Skill.FORTIFY in skills && tile.cityId!=null) {
            val c=state.city(tile.cityId!!)
            if(c?.owner==defender.owner) bonus=if(c.wall) 4.0 else 1.5
        }
        if(bonus<4.0) {
            if(tile.terrain==Terrain.MOUNTAIN && "climbing" in tech) bonus=maxOf(bonus,1.5)
            if(tile.terrain==Terrain.FOREST && "archery" in tech) bonus=maxOf(bonus,1.5)
            if(tile.terrain in setOf(Terrain.WATER,Terrain.OCEAN) && "aquatism" in tech) bonus=maxOf(bonus,1.5)
        }
        if(defender.poisoned) bonus*=.7
        return bonus
    }

    fun canAttack(state:SimState, attacker:UnitState, defender:UnitState):String? {
        if(attacker.owner==defender.owner) return "Cannot attack an ally"
        if(state.diplomacy[attacker.owner][defender.owner]==Relation.PEACE) return "You are at peace"
        if(attacker.frozenTurns>0) return "Unit is frozen"
        if(attacker.attacked) return "Unit already attacked"
        val at=UnitCatalog[attacker.kind]
        if(at.attack<=0 && Skill.CONVERT !in UnitCatalog.skills(attacker)) return "Unit cannot attack"
        if(attacker.moved && Skill.DASH !in UnitCatalog.skills(attacker)) return "Unit cannot attack after moving"
        if(attacker.pos.chebyshev(defender.pos)>at.range) return "Target is out of range"
        return null
    }

    fun preview(state:SimState, attacker:UnitState, defender:UnitState):Preview {
        val at=UnitCatalog[attacker.kind]; val dt=UnitCatalog[defender.kind]
        val aMax=UnitCatalog.maxHP(attacker).coerceAtLeast(1); val dMax=UnitCatalog.maxHP(defender).coerceAtLeast(1)
        val bonus=defenseBonus(state,defender)
        val af=at.attack*attacker.hp.toDouble()/aMax
        val df=dt.defense*defender.hp.toDouble()/dMax*bonus
        val total=(af+df).coerceAtLeast(.0001)
        val dmg=(af/total*at.attack*4.5).roundToInt().coerceAtLeast(if(at.attack>0)1 else 0)
        val retaliationAllowed=defender.hp-dmg>0 && Skill.STIFF !in UnitCatalog.skills(defender) && Skill.SURPRISE !in UnitCatalog.skills(attacker) && dt.attack>0 && defender.pos.chebyshev(attacker.pos)<=dt.range
        val ret=if(retaliationAllowed) (df/total*dt.defense*4.5).roundToInt().coerceAtLeast(1) else 0
        return Preview(dmg,ret,bonus,retaliationAllowed)
    }

    fun splashDamage(primary:Int)=floor(primary/2.0).toInt()
}
