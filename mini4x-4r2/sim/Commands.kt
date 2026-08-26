package com.example.mini4x.sim

sealed interface Command { val playerId: Int }
data class MoveUnit(override val playerId:Int,val unitId:Int,val destination:Pos):Command
data class AttackUnit(override val playerId:Int,val attackerId:Int,val defenderId:Int):Command
data class RecoverUnit(override val playerId:Int,val unitId:Int):Command
data class TrainUnit(override val playerId:Int,val cityId:Int,val kind:UnitKind):Command
data class BuyTechnology(override val playerId:Int,val technologyId:String):Command
data class HarvestResource(override val playerId:Int,val pos:Pos):Command
data class BuildImprovement(override val playerId:Int,val pos:Pos,val improvement:Improvement):Command
data class ChooseCityReward(override val playerId:Int,val cityId:Int,val reward:CityRewardType):Command
data class CaptureCity(override val playerId:Int,val unitId:Int):Command
data class ExamineRuin(override val playerId:Int,val unitId:Int):Command
data class BuildRoad(override val playerId:Int,val pos:Pos):Command
data class EstablishEmbassy(override val playerId:Int,val targetPlayerId:Int):Command
data class OfferPeace(override val playerId:Int,val targetPlayerId:Int):Command
data class BreakPeace(override val playerId:Int,val targetPlayerId:Int):Command
data class UpgradeShip(override val playerId:Int,val unitId:Int,val target:UnitKind):Command
data class GatherStarfish(override val playerId:Int,val unitId:Int):Command
data class SpecialAction(override val playerId:Int,val unitId:Int,val action:SpecialActionType,val targetPos:Pos?=null):Command
data class EndTurn(override val playerId:Int):Command
