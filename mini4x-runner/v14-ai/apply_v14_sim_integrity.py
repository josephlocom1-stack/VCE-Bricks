#!/usr/bin/env python3
from pathlib import Path
import os

PROJECT = Path(os.environ.get('MINI4X_PROJECT', 'project'))
SIM = PROJECT / 'app/src/main/java/com/example/mini4x/sim'
model = SIM / 'Model.kt'
derived = SIM / 'DerivedState.kt'
command = SIM / 'CommandEngine.kt'

m = model.read_text()
d = derived.read_text()
c = command.read_text()


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    assert count == 1, f'{label}: expected one anchor, found {count}'
    return text.replace(old, new, 1)


# 1. Current-capital lookup is ownership-sensitive while capitalCityId remains durable
# for original-capital history/recapture semantics.
m = replace_once(
    m,
    'fun capital(playerId: Int): City? = players[playerId].capitalCityId?.let(::city)',
    'fun capital(playerId: Int): City? = players[playerId].capitalCityId?.let(::city)?.takeIf { it.owner == playerId }',
    'owned current capital',
)

# 2. Converted units must detach from their former city's support roster.
c = replace_once(
    c,
    'val old=d.owner;d.owner=a.owner;d.supportCityId=null;d.independent=true;a.attacked=true;a.tookAction=true',
    'val old=d.owner;d.supportCityId?.let{state.city(it)?.supportedUnitIds?.remove(d.id)};d.owner=a.owner;d.supportCityId=null;d.independent=true;a.attacked=true;a.tookAction=true',
    'conversion support cleanup',
)

# 3. Lost-capital ruin population reward uses an owned-city fallback and never mutates
# an enemy-owned former capital.
c = replace_once(
    c,
    '2 -> {state.capital(c.playerId)?.basePopulation=(state.capital(c.playerId)?.basePopulation?:0)+3;"+3 capital population"}',
    '2 -> {val target=state.capital(c.playerId)?:state.ownedCities(c.playerId).firstOrNull();if(target!=null)target.basePopulation+=3;if(target?.isCapital==true)"+3 capital population" else "+3 city population"}',
    'lost-capital ruin population fallback',
)

# 4. Peace cannot leave a now-allied unit recorded as a besieger.
d = replace_once(
    d,
    'if(u==null || u.pos!=c.pos || u.owner==c.owner) c.besiegedByUnitId=null',
    'if(u==null || u.pos!=c.pos || u.owner==c.owner || state.diplomacy[c.owner][u.owner]==Relation.PEACE) c.besiegedByUnitId=null',
    'peace siege cleanup',
)

# 5. Eliminated units leave no support/siege references.
d = replace_once(
    d,
    '''                val doomed=state.ownedUnits(pid).toList()
                for(u in doomed) { state.tile(u.pos)?.takeIf{it.occupantUnitId==u.id}?.occupantUnitId=null; state.units.remove(u) }''',
    '''                val doomed=state.ownedUnits(pid).toList()
                val doomedIds=doomed.map { it.id }.toSet()
                for(u in doomed) { state.tile(u.pos)?.takeIf{it.occupantUnitId==u.id}?.occupantUnitId=null;u.supportCityId?.let{state.city(it)?.supportedUnitIds?.remove(u.id)};state.units.remove(u) }
                for(city in state.cities) { city.supportedUnitIds.removeAll(doomedIds);if(city.besiegedByUnitId in doomedIds)city.besiegedByUnitId=null }''',
    'elimination reference cleanup',
)

# 6. Timed-score modes can only award living players.
d = replace_once(
    d,
    'winner=state.players.indices.maxByOrNull { state.score[it] }; reason="30-turn score"',
    'winner=state.players.indices.filter { !state.players[it].eliminated }.maxByOrNull { state.score[it] }; reason="30-turn score"',
    'SCORE_30 living winner',
)
d = replace_once(
    d,
    'winner=state.players.indices.maxByOrNull { state.score[it] }; reason="20-turn weekly challenge"',
    'winner=state.players.indices.filter { !state.players[it].eliminated }.maxByOrNull { state.score[it] }; reason="20-turn weekly challenge"',
    'WEEKLY_20 living winner',
)

# 7. Recalculation reaches a fixed point in one pass: eliminate/clean references before
# the final siege refresh + score/victory calculation.
d = replace_once(
    d,
    '''        refreshSieges(state)
        recalculateBuildingPopulation(state)
        recalculateConnections(state)
        recalculateCityUpgrades(state,events)
        recalculateScores(state,events)
        checkElimination(state,events)
        checkVictory(state,events)''',
    '''        recalculateBuildingPopulation(state)
        recalculateConnections(state)
        recalculateCityUpgrades(state,events)
        checkElimination(state,events)
        refreshSieges(state)
        recalculateScores(state,events)
        checkVictory(state,events)''',
    'derived-state elimination/fixed-point order',
)

model.write_text(m)
derived.write_text(d)
command.write_text(c)

print('V1.4 Phase 4R.2 simulation integrity fixes applied: defects=7')
