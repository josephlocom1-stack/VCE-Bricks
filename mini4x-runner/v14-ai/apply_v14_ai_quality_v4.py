#!/usr/bin/env python3
from pathlib import Path
import os

P=Path(os.environ.get('MINI4X_PROJECT','project'))/'app/src/main/java/com/example/mini4x/ai/StrategicAi.kt'
s=P.read_text()
old='''        val plan=plan(state,playerId)
        // Strategic diplomacy transition: once conquest/attack mode is chosen, peace must not
'''
new='''        val plan=plan(state,playerId)
        // Immediate city survival is a tactical obligation, not an economy preference. When an
        // adjacent hostile unit can take a threatened city, train the best legal blocker before
        // spending stars or moving elsewhere. This preemption is intentionally narrow: only a
        // DEFEND plan, an actually threatened owned city, and an adjacent hostile unit qualify.
        if(plan.goal==StrategicGoal.DEFEND) {
            for(cityId in plan.threatenedCityIds) {
                val city=state.city(cityId) ?: continue
                val adjacentHostile=state.units.any { enemy ->
                    enemy.owner!=playerId && state.diplomacy[playerId][enemy.owner]!=Relation.PEACE &&
                        enemy.pos.chebyshev(city.pos)<=1
                }
                if(adjacentHostile) {
                    for(kind in listOf(UnitKind.DEFENDER,UnitKind.SWORDSMAN,UnitKind.WARRIOR)) {
                        val train=TrainUnit(playerId,city.id,kind)
                        if(CommandEngine.validate(state,train)==null) return train
                    }
                }
            }
        }
        // Strategic diplomacy transition: once conquest/attack mode is chosen, peace must not
'''
if old not in s: raise SystemExit('V1.4 AI v4 patch anchor missing: chooseCommand plan')
P.write_text(s.replace(old,new,1))
print('V1.4 AI quality patch v4 applied')
