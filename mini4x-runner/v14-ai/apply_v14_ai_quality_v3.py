#!/usr/bin/env python3
from pathlib import Path
import os

P=Path(os.environ.get('MINI4X_PROJECT','project'))/'app/src/main/java/com/example/mini4x/ai/StrategicAi.kt'
s=P.read_text()
old='''                    val local=threatAt(state,pid,city.pos)
                    if(local>8.0) v+=when(c.kind){UnitKind.DEFENDER->30.0;UnitKind.ARCHER->20.0;UnitKind.SWORDSMAN->15.0;else->4.0}
                    val nearby=state.units.filter{it.owner!=pid && state.diplomacy[pid][it.owner]!=Relation.PEACE && it.pos.chebyshev(city.pos)<=4}
                    if(nearby.any{UnitCatalog[it.kind].defense>=3.0} && c.kind==UnitKind.CATAPULT) v+=22
                    if(nearby.any{UnitCatalog[it.kind].movement>=3.0} && c.kind==UnitKind.DEFENDER) v+=14
'''
new='''                    val local=threatAt(state,pid,city.pos)
                    val nearby=state.units.filter{it.owner!=pid && state.diplomacy[pid][it.owner]!=Relation.PEACE && it.pos.chebyshev(city.pos)<=4}
                    val immediate=nearby.filter{it.pos.chebyshev(city.pos)<=1}
                    if(local>8.0) v+=when(c.kind){
                        UnitKind.DEFENDER->72.0
                        UnitKind.SWORDSMAN->20.0
                        UnitKind.ARCHER->10.0
                        UnitKind.CATAPULT->-18.0
                        else->4.0
                    }
                    // Adjacent attackers require a body that can hold the city now. A ranged glass
                    // cannon is useful only after the city has a blocker; otherwise it is usually
                    // captured before the Catapult can create value.
                    if(immediate.isNotEmpty()) v+=when(c.kind){
                        UnitKind.DEFENDER->110.0
                        UnitKind.SWORDSMAN->24.0
                        UnitKind.ARCHER->-35.0
                        UnitKind.CATAPULT->-80.0
                        else->0.0
                    }
                    if(immediate.isEmpty() && nearby.any{UnitCatalog[it.kind].defense>=3.0} && c.kind==UnitKind.CATAPULT) v+=22
                    if(nearby.any{UnitCatalog[it.kind].movement>=3.0} && c.kind==UnitKind.DEFENDER) v+=18
'''
if old not in s:
    raise SystemExit('V1.4 AI v3 patch anchor missing: threat-aware training block')
P.write_text(s.replace(old,new,1))
print('V1.4 AI quality patch v3 applied')
