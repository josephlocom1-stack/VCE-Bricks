package com.example.mini4x.sim

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64

object GameStateCodec {
    private const val MAGIC = 0x4D345831
    private const val VERSION = 1
    fun encode(s:SimState):String {
        val bytes=ByteArrayOutputStream()
        DataOutputStream(bytes).use { o ->
            o.writeInt(MAGIC);o.writeInt(VERSION);o.writeLong(s.seed);o.writeLong(s.rngCounter);o.writeInt(s.roundNumber);o.writeInt(s.activePlayer);o.writeUTF(s.gameMode.name);o.writeUTF(s.waterPreset.name);o.writeInt(s.size);o.writeInt(s.humanPlayerId);o.writeInt(s.difficulty);o.writeBoolean(s.finished);o.writeInt(s.winnerId ?: -1);o.writeUTF(s.victoryReason);o.writeInt(s.nextCityId);o.writeInt(s.nextUnitId);o.writeLong(s.actionSerial);o.writeBoolean(s.creativeEndless)
            o.writeInt(s.players.size);for(p in s.players){o.writeInt(p.id);o.writeUTF(p.factionId);o.writeInt(p.stars);o.writeBoolean(p.eliminated);o.writeInt(p.capitalCityId?:-1);writeStrings(o,p.technologies);writeStrings(o,p.startingTechnologies);writeInts(o,p.metPlayers);writeInts(o,p.embassies);o.writeInt(p.kills);o.writeInt(p.losses);o.writeInt(p.opponentsDefeated)}
            o.writeInt(s.cities.size);for(c in s.cities){o.writeInt(c.id);o.writeInt(c.owner);writePos(o,c.pos);o.writeInt(c.level);o.writeInt(c.basePopulation);o.writeInt(c.spentPopulation);o.writeInt(c.buildingPopulation);o.writeInt(c.connectionPopulation);o.writeBoolean(c.workshop);o.writeBoolean(c.park);o.writeBoolean(c.wall);o.writeBoolean(c.isCapital);o.writeInt(c.originalOwner);o.writeBoolean(c.borderExpanded);o.writeInt(c.pendingRewardLevel?:-1);o.writeInt(c.besiegedByUnitId?:-1);writeInts(o,c.supportedUnitIds)}
            o.writeInt(s.units.size);for(u in s.units){o.writeInt(u.id);o.writeInt(u.owner);writePos(o,u.pos);o.writeUTF(u.kind.name);o.writeInt(u.hp);o.writeInt(u.supportCityId?:-1);o.writeUTF(u.carriedKind?.name?:"");o.writeBoolean(u.veteran);o.writeBoolean(u.poisoned);o.writeInt(u.frozenTurns);o.writeBoolean(u.moved);o.writeBoolean(u.attacked);o.writeBoolean(u.tookAction);o.writeInt(u.captureReadyOnRound?:-1);o.writeBoolean(u.independent);o.writeInt(u.kills)}
            for(x in 0 until s.size)for(y in 0 until s.size){val t=s.map[x][y];o.writeUTF(t.terrain.name);o.writeBoolean(t.flooded);o.writeInt(t.climateOwner);o.writeInt(t.territoryOwner?:-1);o.writeInt(t.cityId?:-1);o.writeBoolean(t.village);o.writeInt(t.workCityId?:-1);o.writeUTF(t.resource?.name?:"");o.writeUTF(t.improvement?.name?:"");o.writeBoolean(t.road);o.writeBoolean(t.ruin);o.writeBoolean(t.lighthouse);o.writeBoolean(t.starfish);o.writeInt(t.occupantUnitId?:-1);o.writeInt(t.improvementBuiltTurn)}
            for(i in s.players.indices)for(j in s.players.indices)o.writeUTF(s.diplomacy[i][j].name)
            o.writeInt(s.discoveredTiles.size);for(set in s.discoveredTiles){o.writeInt(set.size);set.sortedWith(compareBy<Pos>{it.x}.thenBy{it.y}).forEach{writePos(o,it)}}
            o.writeInt(s.score.size);s.score.forEach(o::writeInt)
        };return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }
    fun decode(encoded:String):SimState {
        val raw=Base64.getDecoder().decode(encoded);DataInputStream(ByteArrayInputStream(raw)).use { i ->
            require(i.readInt()==MAGIC){"Invalid Mini4X save"};require(i.readInt()==VERSION){"Unsupported save version"}
            val seed=i.readLong();val rng=i.readLong();val round=i.readInt();val active=i.readInt();val mode=GameMode.valueOf(i.readUTF());val water=WaterPreset.valueOf(i.readUTF());val size=i.readInt();val human=i.readInt();val difficulty=i.readInt();val finished=i.readBoolean();val winner=i.readInt().takeIf{it>=0};val reason=i.readUTF();val nextCity=i.readInt();val nextUnit=i.readInt();val serial=i.readLong();val creative=i.readBoolean()
            val players=MutableList(i.readInt()){val id=i.readInt();val faction=i.readUTF();val stars=i.readInt();val eliminated=i.readBoolean();val cap=i.readInt().takeIf{it>=0};val tech=readStrings(i);val starting=readStrings(i);val met=readInts(i);val emb=readInts(i);val kills=i.readInt();val losses=i.readInt();val defeated=i.readInt();PlayerState(id,faction,stars,eliminated,cap,tech,starting,met,emb,kills,losses,defeated)}
            val cities=MutableList(i.readInt()){val id=i.readInt();val owner=i.readInt();val pos=readPos(i);val level=i.readInt();val base=i.readInt();val spent=i.readInt();val build=i.readInt();val conn=i.readInt();val workshop=i.readBoolean();val park=i.readBoolean();val wall=i.readBoolean();val capital=i.readBoolean();val original=i.readInt();val border=i.readBoolean();val pending=i.readInt().takeIf{it>=0};val siege=i.readInt().takeIf{it>=0};val supported=readInts(i);City(id,owner,pos,level,base,spent,build,conn,workshop,park,wall,capital,original,border,pending,siege,supported)}
            val units=MutableList(i.readInt()){val id=i.readInt();val owner=i.readInt();val pos=readPos(i);val kind=UnitKind.valueOf(i.readUTF());val hp=i.readInt();val support=i.readInt().takeIf{it>=0};val carried=i.readUTF().takeIf{it.isNotEmpty()}?.let(UnitKind::valueOf);val veteran=i.readBoolean();val poisoned=i.readBoolean();val frozen=i.readInt();val moved=i.readBoolean();val attacked=i.readBoolean();val action=i.readBoolean();val capture=i.readInt().takeIf{it>=0};val independent=i.readBoolean();val kills=i.readInt();UnitState(id,owner,pos,kind,hp,support,carried,veteran,poisoned,frozen,moved,attacked,action,capture,independent,kills)}
            val map=Array(size){x->Array(size){y->val terrain=Terrain.valueOf(i.readUTF());val flooded=i.readBoolean();val climate=i.readInt();val territory=i.readInt().takeIf{it>=0};val city=i.readInt().takeIf{it>=0};val village=i.readBoolean();val work=i.readInt().takeIf{it>=0};val res=i.readUTF().takeIf{it.isNotEmpty()}?.let(Resource::valueOf);val imp=i.readUTF().takeIf{it.isNotEmpty()}?.let(Improvement::valueOf);val road=i.readBoolean();val ruin=i.readBoolean();val light=i.readBoolean();val starfish=i.readBoolean();val occ=i.readInt().takeIf{it>=0};val built=i.readInt();Tile(Pos(x,y),terrain,flooded,climate,territory,city,village,work,res,imp,road,ruin,light,starfish,occ,built)}}
            val dip=Array(players.size){Array(players.size){Relation.NEUTRAL}};for(a in players.indices)for(b in players.indices)dip[a][b]=Relation.valueOf(i.readUTF());val discovered=MutableList(i.readInt()){mutableSetOf<Pos>().also{set->repeat(i.readInt()){set+=readPos(i)}}};val score=MutableList(i.readInt()){i.readInt()};return SimState(seed,rng,round,active,mode,water,size,human,difficulty,map,players,cities,units,dip,discovered,score,finished,winner,reason,nextCity,nextUnit,serial,creative)
        }
    }
    private fun writePos(o:DataOutputStream,p:Pos){o.writeInt(p.x);o.writeInt(p.y)}
    private fun readPos(i:DataInputStream)=Pos(i.readInt(),i.readInt())
    private fun writeStrings(o:DataOutputStream,s:Collection<String>){o.writeInt(s.size);s.sorted().forEach(o::writeUTF)}
    private fun readStrings(i:DataInputStream)=mutableSetOf<String>().also{s->repeat(i.readInt()){s+=i.readUTF()}}
    private fun writeInts(o:DataOutputStream,s:Collection<Int>){o.writeInt(s.size);s.sorted().forEach(o::writeInt)}
    private fun readInts(i:DataInputStream)=mutableSetOf<Int>().also{s->repeat(i.readInt()){s+=i.readInt()}}
}
