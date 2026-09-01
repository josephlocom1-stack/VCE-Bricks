#!/usr/bin/env python3
from pathlib import Path
import os

PROJECT=Path(os.environ.get('MINI4X_PROJECT','project'))
presenter=PROJECT/'app/src/main/java/com/example/mini4x/presentation/GamePresenter.kt'
g=presenter.read_text()
assert 'reconcileSelectionAfterCommand' not in g,'selection lifecycle patch already applied'

old='''    fun legalActions():List<Command>{val out=mutableListOf<Command>();selection.unitId?.let{state.unit(it)?.let{u->out+=LegalCommandGenerator.forUnit(state,u)}};selection.cityId?.let{state.city(it)?.let{c->out+=LegalCommandGenerator.forCity(state,c)}};selection.tile?.let{out+=LegalCommandGenerator.forTile(state,it,0)};return out.distinct()}
    fun execute(command:Command):Boolean {val before=state.deepCopy();val r=CommandEngine.execute(state,command);message=if(r.accepted)r.events.lastOrNull()?.message?.ifBlank{"Action complete."}?:"Action complete." else r.reason;if(r.accepted)animations.enqueue(AnimationPlanner.plan(before,command,r.events));if(state.finished)screen=ScreenMode.RESULT;return r.accepted}'''
new='''    fun legalActions():List<Command>{val out=mutableListOf<Command>();selection.unitId?.let{state.unit(it)?.let{u->out+=LegalCommandGenerator.forUnit(state,u)}};selection.cityId?.let{state.city(it)?.let{c->out+=LegalCommandGenerator.forCity(state,c)}};selection.tile?.let{out+=LegalCommandGenerator.forTile(state,it,0)};return out.distinct()}
    private fun reconcileSelectionAfterCommand(){
        selection.unitId?.let{id->
            val unit=state.unit(id)
            selection=if(unit?.owner==0)Selection(unitId=unit.id,tile=unit.pos) else Selection()
            return
        }
        selection.cityId?.let{id->
            val city=state.city(id)
            selection=if(city?.owner==0)Selection(cityId=city.id,tile=city.pos) else Selection()
            return
        }
        selection.tile?.let{pos->selection=if(state.inBounds(pos))Selection(tile=pos)else Selection()}
    }
    fun execute(command:Command):Boolean {val before=state.deepCopy();val r=CommandEngine.execute(state,command);message=if(r.accepted)r.events.lastOrNull()?.message?.ifBlank{"Action complete."}?:"Action complete." else r.reason;if(r.accepted){animations.enqueue(AnimationPlanner.plan(before,command,r.events));reconcileSelectionAfterCommand()};if(state.finished)screen=ScreenMode.RESULT;return r.accepted}'''
assert old in g,'execute/selection anchor changed'
g=g.replace(old,new,1)

old='''        if(!execute(EndTurn(0)))return false
        while(!state.finished&&state.activePlayer!=0){'''
new='''        if(!execute(EndTurn(0)))return false
        // Turn ownership changes can invalidate the selected entity while the AI acts.
        // Start the next human turn from a neutral selection instead of retaining stale context.
        selection=Selection()
        while(!state.finished&&state.activePlayer!=0){'''
assert old in g,'end-turn selection anchor changed'
g=g.replace(old,new,1)

for required in [
    'private fun reconcileSelectionAfterCommand()',
    'Selection(unitId=unit.id,tile=unit.pos)',
    'Selection(cityId=city.id,tile=city.pos)',
    'if(r.accepted){animations.enqueue(AnimationPlanner.plan(before,command,r.events));reconcileSelectionAfterCommand()}',
    'selection=Selection()\n        while(!state.finished&&state.activePlayer!=0)',
]:
    assert required in g,required

presenter.write_text(g)
print('V1.4 selection lifecycle: selected unit/city positions reconciled after commands; invalid entities cleared; turn handoff clears selection')
