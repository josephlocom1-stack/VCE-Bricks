#!/usr/bin/env python3
from pathlib import Path
import os

PROJECT=Path(os.environ.get('MINI4X_PROJECT','project'))

presenter=PROJECT/'app/src/main/java/com/example/mini4x/presentation/GamePresenter.kt'
g=presenter.read_text()
assert 'val directAttack=AttackUnit(0,selected.id,unit.id)' not in g,'command reachability patch already applied'
old='''        val selected=selection.unitId?.let(state::unit)
        if(selected!=null && pos in MovementRules.legalDestinations(state,selected)) return execute(MoveUnit(0,selected.id,pos))'''
new='''        val selected=selection.unitId?.let(state::unit)
        if(selected!=null && unit!=null && unit.owner!=0){
            val directAttack=AttackUnit(0,selected.id,unit.id)
            if(CommandEngine.validate(state,directAttack)==null)return execute(directAttack)
        }
        if(selected!=null && pos in MovementRules.legalDestinations(state,selected)) return execute(MoveUnit(0,selected.id,pos))'''
assert old in g,'selected-unit movement anchor changed'
g=g.replace(old,new,1)
presenter.write_text(g)

view=PROJECT/'app/src/main/java/com/example/mini4x/ui/Mini4xView.kt'
s=view.read_text()
assert 'private fun isConvertAttack(' not in s,'command reachability UI patch already applied'

anchor='''    private fun commandIconPath(cmd:Command,fid:String=playerFactionId(0)):String?=when(cmd){'''
helpers='''    private fun isConvertAttack(cmd:AttackUnit):Boolean=presenter.state.unit(cmd.attackerId)?.let{Skill.CONVERT in UnitCatalog.skills(it)}==true
    private fun attackActionLabel(cmd:AttackUnit):String{
        val verb=if(isConvertAttack(cmd))"Convert" else "Attack"
        val target=presenter.state.unit(cmd.defenderId)?:return verb
        return "$verb ${unitLabel(target.kind)}"
    }
    private fun specialActionLabel(cmd:SpecialAction):String=when(cmd.action){
        SpecialActionType.HEAL_ADJACENT->"Heal adjacent"
        SpecialActionType.INFILTRATE->cmd.targetPos?.let(presenter.state::cityAt)?.let{"Infiltrate ${cityName(it)}"}?:"Infiltrate"
        SpecialActionType.DISBAND->"Disband"
        SpecialActionType.DESTROY_BUILDING->presenter.state.unit(cmd.unitId)?.let{"Destroy · ${unitLabel(it.kind)}"}?:"Destroy building"
    }

'''
assert anchor in s,'command icon anchor changed'
s=s.replace(anchor,helpers+anchor,1)

old='''        is AttackUnit->"units/weapon_sword.png"'''
new='''        is AttackUnit->if(isConvertAttack(cmd))"tech/philosophy.png" else "units/weapon_sword.png"'''
assert old in s,'attack icon anchor changed'
s=s.replace(old,new,1)

old='''    private fun commandLabel(cmd:Command)=when(cmd){is MoveUnit->"Move";is AttackUnit->"Attack";is RecoverUnit->"Recover";is TrainUnit->"Train ${cmd.kind.name.lowercase().replaceFirstChar{it.uppercase()}}";is BuyTechnology->"Research ${TechnologyCatalog[cmd.technologyId].label}";is HarvestResource->"Harvest resource";is BuildImprovement->"Build ${cmd.improvement.name.lowercase().replace('_',' ')}";is ChooseCityReward->cmd.reward.name.lowercase().replace('_',' ');is CaptureCity->"Capture";is ExamineRuin->"Examine ruin";is BuildRoad->"Build road";is EstablishEmbassy->"Establish embassy";is OfferPeace->"Offer peace";is BreakPeace->"Break peace";is UpgradeShip->"Upgrade to ${cmd.target}";is GatherStarfish->"Gather starfish";is SpecialAction->cmd.action.name.lowercase().replace('_',' ');is EndTurn->"End turn"}'''
new='''    private fun commandLabel(cmd:Command)=when(cmd){is MoveUnit->"Move";is AttackUnit->attackActionLabel(cmd);is RecoverUnit->"Recover";is TrainUnit->"Train ${cmd.kind.name.lowercase().replaceFirstChar{it.uppercase()}}";is BuyTechnology->"Research ${TechnologyCatalog[cmd.technologyId].label}";is HarvestResource->"Harvest resource";is BuildImprovement->"Build ${cmd.improvement.name.lowercase().replace('_',' ')}";is ChooseCityReward->cmd.reward.name.lowercase().replace('_',' ');is CaptureCity->"Capture";is ExamineRuin->"Examine ruin";is BuildRoad->"Build road";is EstablishEmbassy->"Establish embassy";is OfferPeace->"Offer peace";is BreakPeace->"Break peace";is UpgradeShip->"Upgrade to ${cmd.target}";is GatherStarfish->"Gather starfish";is SpecialAction->specialActionLabel(cmd);is EndTurn->"End turn"}'''
assert old in s,'command label anchor changed'
s=s.replace(old,new,1)

old='''        val actions=presenter.legalActions().filter{it !is MoveUnit}.take(12);actionRects=emptyList()'''
new='''        val legalActions=presenter.legalActions().filter{it !is MoveUnit}
        // Keep non-target utility actions reachable even when a ranged unit has many attack targets.
        // Any attack omitted by the compact sheet remains directly targetable on the world map.
        val actions=(legalActions.filter{it !is AttackUnit}+legalActions.filterIsInstance<AttackUnit>()).take(12);actionRects=emptyList()'''
assert old in s,'action list anchor changed'
s=s.replace(old,new,1)

for required in [
    'val directAttack=AttackUnit(0,selected.id,unit.id)',
    'CommandEngine.validate(state,directAttack)==null',
]:
    assert required in g,required
for required in [
    'private fun isConvertAttack(cmd:AttackUnit)',
    'return "$verb ${unitLabel(target.kind)}"',
    '"tech/philosophy.png"',
    'is AttackUnit->attackActionLabel(cmd)',
    'is SpecialAction->specialActionLabel(cmd)',
    'legalActions.filter{it !is AttackUnit}',
    'legalActions.filterIsInstance<AttackUnit>()',
]:
    assert required in s,required

view.write_text(s)
print('V1.4 command reachability: direct enemy targeting, conversion-aware labels/icons, target-aware special labels, utility-action priority')
