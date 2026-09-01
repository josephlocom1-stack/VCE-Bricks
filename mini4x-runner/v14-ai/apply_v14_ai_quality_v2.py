#!/usr/bin/env python3
from pathlib import Path
import os, re

P=Path(os.environ.get('MINI4X_PROJECT','project'))/'app/src/main/java/com/example/mini4x/ai/StrategicAi.kt'
s=P.read_text()

def rep(old,new,label):
    global s
    if old not in s:
        raise SystemExit(f'V1.4 AI v2 patch anchor missing: {label}')
    s=s.replace(old,new,1)

def insert_after_regex(pattern,addition,label):
    global s
    m=re.search(pattern,s,re.M)
    if not m:
        raise SystemExit(f'V1.4 AI v2 patch anchor missing: {label}')
    s=s[:m.end()]+addition+s[m.end():]

rep('''        if(plan.goal==StrategicGoal.ATTACK && "strategy" in state.players[playerId].technologies) {
            val peaceTargets=state.players.indices.filter { it!=playerId && !state.players[it].eliminated && state.diplomacy[playerId][it]==Relation.PEACE }
            if(peaceTargets.isNotEmpty() && state.roundNumber>=12) {
                val target=peaceTargets.minByOrNull { other -> state.ownedCities(other).size*20 + militaryPower(state,other).toInt() }
                if(target!=null) BreakPeace(playerId,target).let { if(CommandEngine.validate(state,it)==null) return it }
            }
        }
''','''        if(plan.goal==StrategicGoal.ATTACK && "strategy" in state.players[playerId].technologies) {
            // Never break peace while our units are still inside the ally's territory: the rules
            // delete those units immediately. Reposition first, then consider war on a later action.
            val peaceTargets=state.players.indices.filter { other ->
                other!=playerId && !state.players[other].eliminated && state.diplomacy[playerId][other]==Relation.PEACE &&
                    state.ownedUnits(playerId).none { state.tile(it.pos)?.territoryOwner==other }
            }
            if(peaceTargets.isNotEmpty() && state.roundNumber>=12) {
                val target=peaceTargets.minByOrNull { other -> state.ownedCities(other).size*20 + militaryPower(state,other).toInt() }
                if(target!=null && militaryPower(state,playerId)>=militaryPower(state,target)*.90)
                    BreakPeace(playerId,target).let { if(CommandEngine.validate(state,it)==null) return it }
            }
        }
''','peace safety')

# Insert local-danger preemption immediately after enemyPower is computed. This is deliberately
# structural rather than tied to a specific aggression-threshold line, because V1.3 legitimately
# retunes those thresholds. The local safety rule must stay before any later fast ATTACK path.
insert_after_regex(r'^\s*val enemyPower=.*\n','''        // Critical local danger preempts global aggression. This check happens before any
        // strong-empire ATTACK fast path so a winning empire cannot abandon its capital.
        val urgentThreatened=state.ownedCities(pid).filter { city -> threatAt(state,pid,city.pos)>8.5 }.map{it.id}.toSet()
        val urgentCapitalId=state.capital(pid)?.id
        val urgentMaxThreat=state.ownedCities(pid).maxOfOrNull { threatAt(state,pid,it.pos) } ?: 0.0
        val urgentCritical=(urgentCapitalId!=null && urgentCapitalId in urgentThreatened) || urgentMaxThreat>13.0
        if(urgentThreatened.isNotEmpty() && urgentCritical)
            return AiPlan(StrategicGoal.DEFEND,targetTech=bestTechTarget(state,pid,StrategicGoal.DEFEND),reserveStars=4,threatenedCityIds=urgentThreatened)
''','enemyPower / critical threat preemption')

rep('''        val threatened=state.ownedCities(pid).filter { city -> threatAt(state,pid,city.pos)>8.5 }.map{it.id}.toSet()
        // Small empires must save threatened cities. Large empires should counter-attack instead of
        // spending the rest of conquest in a perpetual defensive posture.
        if(threatened.isNotEmpty() && (state.ownedCities(pid).size<=2 || ownPower<enemyPower*.50))
            return AiPlan(StrategicGoal.DEFEND,targetTech=bestTechTarget(state,pid,StrategicGoal.DEFEND),reserveStars=3,threatenedCityIds=threatened)
''','''        val ownCities=state.ownedCities(pid)
        val threatened=ownCities.filter { city -> threatAt(state,pid,city.pos)>8.5 }.map{it.id}.toSet()
        val capitalId=state.capital(pid)?.id
        val maxLocalThreat=ownCities.maxOfOrNull { threatAt(state,pid,it.pos) } ?: 0.0
        val criticalThreat=(capitalId!=null && capitalId in threatened) || maxLocalThreat>13.0
        if(threatened.isNotEmpty() && (criticalThreat || ownCities.size<=2 || ownPower<enemyPower*.65))
            return AiPlan(StrategicGoal.DEFEND,targetTech=bestTechTarget(state,pid,StrategicGoal.DEFEND),reserveStars=4,threatenedCityIds=threatened)
''','threatened city planning')

rep('''            is MoveUnit -> {
                val u=state.unit(c.unitId)!!; val dest=c.destination
                val village=discoveredVillages(state,pid).minOfOrNull{it.chebyshev(dest)}?:99
                val enemyCity=enemyCitiesDiscovered(state,pid).minOfOrNull{it.pos.chebyshev(dest)}?:99
                val frontier=dest.neighbours8().count{state.inBounds(it)&&it !in state.discoveredTiles[pid]}
                if(plan.goal==StrategicGoal.EXPAND) v+=35-village*5+frontier*3
                if(plan.goal==StrategicGoal.ATTACK) v+=52-enemyCity*7
                if(plan.goal==StrategicGoal.DEFEND) {
                    val threatCity=plan.threatenedCityIds.mapNotNull(state::city).minOfOrNull{it.pos.chebyshev(dest)}?:99;v+=30-threatCity*5
                }
                if(MovementRules.enemyAdjacent(state,pid,dest) && UnitCatalog[u.kind].defense<2) v-=9
            }
''','''            is MoveUnit -> {
                val u=state.unit(c.unitId)!!; val dest=c.destination; val dt=state.tile(dest)
                val village=discoveredVillages(state,pid).minOfOrNull{it.chebyshev(dest)}?:99
                val enemyCity=enemyCitiesDiscovered(state,pid).minOfOrNull{it.pos.chebyshev(dest)}?:99
                val frontier=dest.neighbours8().count{state.inBounds(it)&&it !in state.discoveredTiles[pid]}
                if(plan.goal==StrategicGoal.EXPAND) v+=35-village*5+frontier*3
                if(plan.goal==StrategicGoal.ATTACK) v+=52-enemyCity*7
                if(plan.goal==StrategicGoal.DEFEND) {
                    val threatCity=plan.threatenedCityIds.mapNotNull(state::city).minOfOrNull{it.pos.chebyshev(dest)}?:99;v+=30-threatCity*5
                }
                if(dt?.village==true) v+=70
                if(dt?.cityId!=null && state.city(dt.cityId!!)?.owner!=pid) v+=90
                val localThreat=threatAt(state,pid,dest)
                val hpRatio=u.hp.toDouble()/UnitCatalog.maxHP(u).coerceAtLeast(1)
                v-=localThreat*(if(hpRatio<.45)3.8 else 2.0)
                val fromCity=state.tile(u.pos)?.cityId
                if(fromCity!=null && fromCity in plan.threatenedCityIds) v-=75
                if(u.kind in setOf(UnitKind.ARCHER,UnitKind.CATAPULT) && MovementRules.enemyAdjacent(state,pid,dest)) v-=22
                else if(MovementRules.enemyAdjacent(state,pid,dest) && UnitCatalog[u.kind].defense<2) v-=11
            }
''','movement survival/objective scoring')

rep('''            is TrainUnit -> {
                val cost=UnitCatalog[c.kind].cost?:0;spending(cost)
                v+=when(plan.goal){StrategicGoal.DEFEND->when(c.kind){UnitKind.DEFENDER->48.0;UnitKind.ARCHER->40.0;else->30.0};StrategicGoal.ATTACK->when(c.kind){UnitKind.KNIGHT,UnitKind.SWORDSMAN,UnitKind.CATAPULT->48.0;UnitKind.RIDER,UnitKind.ARCHER->40.0;else->28.0};StrategicGoal.EXPAND->if(c.kind==UnitKind.RIDER)46.0 else 29.0;else->28.0}
            }
''','''            is TrainUnit -> {
                val cost=UnitCatalog[c.kind].cost?:0;spending(cost)
                v+=when(plan.goal){StrategicGoal.DEFEND->when(c.kind){UnitKind.DEFENDER->48.0;UnitKind.ARCHER->40.0;else->30.0};StrategicGoal.ATTACK->when(c.kind){UnitKind.KNIGHT,UnitKind.SWORDSMAN,UnitKind.CATAPULT->48.0;UnitKind.RIDER,UnitKind.ARCHER->40.0;else->28.0};StrategicGoal.EXPAND->if(c.kind==UnitKind.RIDER)46.0 else 29.0;else->28.0}
                val city=state.city(c.cityId)
                if(city!=null) {
                    val local=threatAt(state,pid,city.pos)
                    if(local>8.0) v+=when(c.kind){UnitKind.DEFENDER->30.0;UnitKind.ARCHER->20.0;UnitKind.SWORDSMAN->15.0;else->4.0}
                    val nearby=state.units.filter{it.owner!=pid && state.diplomacy[pid][it.owner]!=Relation.PEACE && it.pos.chebyshev(city.pos)<=4}
                    if(nearby.any{UnitCatalog[it.kind].defense>=3.0} && c.kind==UnitKind.CATAPULT) v+=22
                    if(nearby.any{UnitCatalog[it.kind].movement>=3.0} && c.kind==UnitKind.DEFENDER) v+=14
                }
            }
''','threat-aware training')

rep('''                v+=p.damageToDefender*5-p.retaliation*3
                if(p.damageToDefender>=d.hp) v+=45+UnitCatalog[d.kind].defense*7
                if(p.retaliation>=a.hp) v-=55
                if(state.tile(d.pos)?.cityId!=null) v+=32
''','''                val targetCost=UnitCatalog[d.kind].cost ?: 8
                v+=p.damageToDefender*5-p.retaliation*3 + targetCost*1.6
                if(p.damageToDefender>=d.hp) v+=52+UnitCatalog[d.kind].defense*7+targetCost*2
                if(p.retaliation>=a.hp) v-=70
                if(state.tile(d.pos)?.cityId!=null) v+=42
''','attack target value')

P.write_text(s)
print('V1.4 AI quality patch v2 applied')
