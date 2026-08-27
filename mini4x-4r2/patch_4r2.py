#!/usr/bin/env python3
from pathlib import Path
import sys

root=Path(sys.argv[1])
model=root/'Model.kt'; derived=root/'DerivedState.kt'; command=root/'CommandEngine.kt'
m=model.read_text(); d=derived.read_text(); c=command.read_text()

def one(text,old,new,label):
    if text.count(old)!=1: raise SystemExit(f'{label}: expected one anchor, got {text.count(old)}')
    return text.replace(old,new,1)

m=one(m,'fun capital(playerId: Int): City? = players[playerId].capitalCityId?.let(::city)','fun capital(playerId: Int): City? = players[playerId].capitalCityId?.let(::city)?.takeIf{it.owner==playerId}','owned capital')

c=one(c,'if(Skill.CONVERT in UnitCatalog.skills(a)){val old=d.owner;d.owner=a.owner;d.supportCityId=null;d.independent=true;a.attacked=true;a.tookAction=true;',
      'if(Skill.CONVERT in UnitCatalog.skills(a)){val old=d.owner;d.supportCityId?.let{state.city(it)?.supportedUnitIds?.remove(d.id)};d.owner=a.owner;d.supportCityId=null;d.independent=true;a.attacked=true;a.tookAction=true;','conversion support cleanup')

c=one(c,'2->{state.capital(c.playerId)?.basePopulation=(state.capital(c.playerId)?.basePopulation?:0)+3;"+3 capital population"}',
      '2->{val target=state.capital(c.playerId)?:state.ownedCities(c.playerId).firstOrNull();if(target!=null)target.basePopulation+=3;if(target?.isCapital==true)"+3 capital population" else "+3 city population"}','lost capital ruin fallback')

d=one(d,'if(u==null || u.pos!=c.pos || u.owner==c.owner) c.besiegedByUnitId=null','if(u==null || u.pos!=c.pos || u.owner==c.owner || state.diplomacy[c.owner][u.owner]==Relation.PEACE) c.besiegedByUnitId=null','peace siege cleanup')

d=one(d,'val doomed=state.ownedUnits(pid).toList()\n                for(u in doomed) { state.tile(u.pos)?.takeIf{it.occupantUnitId==u.id}?.occupantUnitId=null; state.units.remove(u) }',
'''val doomed=state.ownedUnits(pid).toList()\n                val doomedIds=doomed.map { it.id }.toSet()\n                for(u in doomed) { state.tile(u.pos)?.takeIf{it.occupantUnitId==u.id}?.occupantUnitId=null;u.supportCityId?.let{state.city(it)?.supportedUnitIds?.remove(u.id)};state.units.remove(u) }\n                for(city in state.cities){city.supportedUnitIds.removeAll(doomedIds);if(city.besiegedByUnitId in doomedIds)city.besiegedByUnitId=null}''','elimination references')

d=one(d,'winner=state.players.indices.maxByOrNull { state.score[it] }; reason="30-turn score"','winner=state.players.indices.filter { !state.players[it].eliminated }.maxByOrNull { state.score[it] }; reason="30-turn score"','score30 living winner')
d=one(d,'winner=state.players.indices.maxByOrNull { state.score[it] }; reason="20-turn weekly challenge"','winner=state.players.indices.filter { !state.players[it].eliminated }.maxByOrNull { state.score[it] }; reason="20-turn weekly challenge"','weekly living winner')

d=one(d,'recalculateBuildingPopulation(state)\n        recalculateConnections(state)\n        recalculateCityUpgrades(state,events)\n        recalculateScores(state,events)\n        checkElimination(state,events)\n        checkVictory(state,events)',
'''recalculateBuildingPopulation(state)\n        recalculateConnections(state)\n        recalculateCityUpgrades(state,events)\n        checkElimination(state,events)\n        refreshSieges(state)\n        recalculateScores(state,events)\n        checkVictory(state,events)''','elimination before scoring')

model.write_text(m);derived.write_text(d);command.write_text(c)
print('phase4r2_candidate_patch=APPLIED defects=7')
