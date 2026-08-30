#!/usr/bin/env python3
from pathlib import Path
import sys

root=Path(sys.argv[1])
codec=root/'GameStateCodec.kt'
c=codec.read_text()

def one(text,old,new,label):
    count=text.count(old)
    if count!=1: raise SystemExit(f'{label}: expected one anchor, got {count}')
    return text.replace(old,new,1)

c=one(c,
'''    private const val MAGIC = 0x4D345831
    private const val VERSION = 1''',
'''    private const val MAGIC = 0x4D345831
    private const val VERSION = 1
    private const val MAX_ENCODED_CHARS = 1_500_000
    private const val MAX_RAW_BYTES = 1_000_000
    private const val MAX_MAP_SIZE = 30
    private const val MAX_PLAYERS = 4
    private const val MAX_TECH_ENTRIES = 64
    private const val MAX_REFERENCE_ENTRIES = MAX_MAP_SIZE * MAX_MAP_SIZE''','bounds constants')

c=one(c,
'''    fun decode(encoded:String):SimState {
        val raw=Base64.getDecoder().decode(encoded);DataInputStream(ByteArrayInputStream(raw)).use { i ->''',
'''    fun decode(encoded:String):SimState {
        require(encoded.length<=MAX_ENCODED_CHARS){"Save payload too large"}
        val raw=Base64.getDecoder().decode(encoded)
        require(raw.size<=MAX_RAW_BYTES){"Decoded save payload too large"}
        DataInputStream(ByteArrayInputStream(raw)).use { i ->''','payload bounds')

c=one(c,
'val size=i.readInt();val human=i.readInt();val difficulty=i.readInt();',
'val size=readCount(i,1,MAX_MAP_SIZE,"map size");val human=i.readInt();val difficulty=i.readInt();','map count')

c=one(c,
'''            val players=MutableList(i.readInt()){val id=i.readInt();val faction=i.readUTF();val stars=i.readInt();val eliminated=i.readBoolean();val cap=i.readInt().takeIf{it>=0};val tech=readStrings(i);val starting=readStrings(i);val met=readInts(i);val emb=readInts(i);val kills=i.readInt();val losses=i.readInt();val defeated=i.readInt();PlayerState(id,faction,stars,eliminated,cap,tech,starting,met,emb,kills,losses,defeated)}''',
'''            val playerCount=readCount(i,2,MAX_PLAYERS,"players")
            val players=MutableList(playerCount){val id=i.readInt();val faction=i.readUTF();val stars=i.readInt();val eliminated=i.readBoolean();val cap=i.readInt().takeIf{it>=0};val tech=readStrings(i);val starting=readStrings(i);val met=readInts(i);val emb=readInts(i);val kills=i.readInt();val losses=i.readInt();val defeated=i.readInt();PlayerState(id,faction,stars,eliminated,cap,tech,starting,met,emb,kills,losses,defeated)}''','player count')

c=one(c,
'''            val cities=MutableList(i.readInt()){val id=i.readInt();val owner=i.readInt();val pos=readPos(i);val level=i.readInt();val base=i.readInt();val spent=i.readInt();val build=i.readInt();val conn=i.readInt();val workshop=i.readBoolean();val park=i.readBoolean();val wall=i.readBoolean();val capital=i.readBoolean();val original=i.readInt();val border=i.readBoolean();val pending=i.readInt().takeIf{it>=0};val siege=i.readInt().takeIf{it>=0};val supported=readInts(i);City(id,owner,pos,level,base,spent,build,conn,workshop,park,wall,capital,original,border,pending,siege,supported)}''',
'''            val cityCount=readCount(i,0,size*size,"cities")
            val cities=MutableList(cityCount){val id=i.readInt();val owner=i.readInt();val pos=readPos(i);val level=i.readInt();val base=i.readInt();val spent=i.readInt();val build=i.readInt();val conn=i.readInt();val workshop=i.readBoolean();val park=i.readBoolean();val wall=i.readBoolean();val capital=i.readBoolean();val original=i.readInt();val border=i.readBoolean();val pending=i.readInt().takeIf{it>=0};val siege=i.readInt().takeIf{it>=0};val supported=readInts(i);City(id,owner,pos,level,base,spent,build,conn,workshop,park,wall,capital,original,border,pending,siege,supported)}''','city count')

c=one(c,
'''            val units=MutableList(i.readInt()){val id=i.readInt();val owner=i.readInt();val pos=readPos(i);val kind=UnitKind.valueOf(i.readUTF());val hp=i.readInt();val support=i.readInt().takeIf{it>=0};val carried=i.readUTF().takeIf{it.isNotEmpty()}?.let(UnitKind::valueOf);val veteran=i.readBoolean();val poisoned=i.readBoolean();val frozen=i.readInt();val moved=i.readBoolean();val attacked=i.readBoolean();val action=i.readBoolean();val capture=i.readInt().takeIf{it>=0};val independent=i.readBoolean();val kills=i.readInt();UnitState(id,owner,pos,kind,hp,support,carried,veteran,poisoned,frozen,moved,attacked,action,capture,independent,kills)}''',
'''            val unitCount=readCount(i,0,size*size,"units")
            val units=MutableList(unitCount){val id=i.readInt();val owner=i.readInt();val pos=readPos(i);val kind=UnitKind.valueOf(i.readUTF());val hp=i.readInt();val support=i.readInt().takeIf{it>=0};val carried=i.readUTF().takeIf{it.isNotEmpty()}?.let(UnitKind::valueOf);val veteran=i.readBoolean();val poisoned=i.readBoolean();val frozen=i.readInt();val moved=i.readBoolean();val attacked=i.readBoolean();val action=i.readBoolean();val capture=i.readInt().takeIf{it>=0};val independent=i.readBoolean();val kills=i.readInt();UnitState(id,owner,pos,kind,hp,support,carried,veteran,poisoned,frozen,moved,attacked,action,capture,independent,kills)}''','unit count')

c=one(c,
'''            val dip=Array(players.size){Array(players.size){Relation.NEUTRAL}};for(a in players.indices)for(b in players.indices)dip[a][b]=Relation.valueOf(i.readUTF());val discovered=MutableList(i.readInt()){mutableSetOf<Pos>().also{set->repeat(i.readInt()){set+=readPos(i)}}};val score=MutableList(i.readInt()){i.readInt()};return SimState(seed,rng,round,active,mode,water,size,human,difficulty,map,players,cities,units,dip,discovered,score,finished,winner,reason,nextCity,nextUnit,serial,creative)''',
'''            val dip=Array(players.size){Array(players.size){Relation.NEUTRAL}};for(a in players.indices)for(b in players.indices)dip[a][b]=Relation.valueOf(i.readUTF())
            val discoveredCount=readCount(i,playerCount,playerCount,"discovered player sets")
            val discovered=MutableList(discoveredCount){mutableSetOf<Pos>().also{set->val count=readCount(i,0,size*size,"discovered tiles");repeat(count){val pos=readPos(i);require(set.add(pos)){"Duplicate discovered tile $pos"}}}}
            val scoreCount=readCount(i,playerCount,playerCount,"score entries")
            val score=MutableList(scoreCount){i.readInt()}
            require(i.available()==0){"Trailing save data"}
            val state=SimState(seed,rng,round,active,mode,water,size,human,difficulty,map,players,cities,units,dip,discovered,score,finished,winner,reason,nextCity,nextUnit,serial,creative)
            validateDecodedState(state)
            return state''','bounded tail and semantic validation')

c=one(c,
'''    private fun readStrings(i:DataInputStream)=mutableSetOf<String>().also{s->repeat(i.readInt()){s+=i.readUTF()}}
    private fun writeInts(o:DataOutputStream,s:Collection<Int>){o.writeInt(s.size);s.sorted().forEach(o::writeInt)}
    private fun readInts(i:DataInputStream)=mutableSetOf<Int>().also{s->repeat(i.readInt()){s+=i.readInt()}}''',
'''    private fun readStrings(i:DataInputStream)=mutableSetOf<String>().also{s->val count=readCount(i,0,MAX_TECH_ENTRIES,"technology set");repeat(count){val value=i.readUTF();require(s.add(value)){"Duplicate technology entry $value"}}}
    private fun writeInts(o:DataOutputStream,s:Collection<Int>){o.writeInt(s.size);s.sorted().forEach(o::writeInt)}
    private fun readInts(i:DataInputStream)=mutableSetOf<Int>().also{s->val count=readCount(i,0,MAX_REFERENCE_ENTRIES,"reference set");repeat(count){val value=i.readInt();require(s.add(value)){"Duplicate reference entry $value"}}}''','bounded set reads')

validator='''    private fun readCount(i:DataInputStream,min:Int,max:Int,label:String):Int {
        val count=i.readInt()
        require(count in min..max){"Invalid $label count: $count"}
        return count
    }
    private fun validateDecodedState(s:SimState) {
        require(MapSizeSetting.entries.any{it.n==s.size}){"Unsupported map size ${s.size}"}
        require(s.roundNumber>=1){"Invalid round ${s.roundNumber}"}
        require(s.rngCounter>=0){"Invalid RNG counter ${s.rngCounter}"}
        require(s.actionSerial>=0){"Invalid action serial ${s.actionSerial}"}
        require(s.humanPlayerId in s.players.indices){"Invalid human player ${s.humanPlayerId}"}
        require(s.activePlayer in s.players.indices){"Invalid active player ${s.activePlayer}"}
        if(!s.finished) require(!s.players[s.activePlayer].eliminated){"Active player is eliminated"}
        val factionIds=FactionCatalog.all.map{it.id}.toSet();val techIds=TechnologyCatalog.all.map{it.id}.toSet()
        val cityIds=s.cities.map{it.id};val unitIds=s.units.map{it.id}
        require(cityIds.size==cityIds.toSet().size){"Duplicate city IDs"};require(unitIds.size==unitIds.toSet().size){"Duplicate unit IDs"}
        require(s.cities.map{it.pos}.toSet().size==s.cities.size){"Duplicate city positions"};require(s.units.map{it.pos}.toSet().size==s.units.size){"Duplicate unit positions"}
        val cityById=s.cities.associateBy{it.id};val unitById=s.units.associateBy{it.id}
        s.players.forEachIndexed{index,p->
            require(p.id==index){"Player ID/index mismatch"};require(p.factionId in factionIds){"Unknown faction ${p.factionId}"};require(p.stars>=0){"Negative stars"}
            require(p.technologies.all{it in techIds}){"Unknown technology"};require(p.startingTechnologies.all{it in techIds}){"Unknown starting technology"}
            require(p.metPlayers.all{it in s.players.indices&&it!=p.id}){"Invalid met-player reference"};require(p.embassies.all{it in s.players.indices&&it!=p.id}){"Invalid embassy reference"}
            p.capitalCityId?.let{id->val city=cityById[id];require(city!=null&&city.isCapital){"Invalid original capital reference"}}
        }
        s.cities.forEach{city->
            require(city.owner in s.players.indices&&city.originalOwner in s.players.indices){"Invalid city owner"};require(s.inBounds(city.pos)){"City out of bounds"};require(s.tile(city.pos)?.cityId==city.id){"City/tile reference mismatch"};require(city.level>=1){"Invalid city level"}
            require(city.basePopulation>=0&&city.spentPopulation>=0&&city.buildingPopulation>=0&&city.connectionPopulation>=0){"Negative city population component"}
            city.supportedUnitIds.forEach{id->val unit=unitById[id];require(unit!=null&&unit.supportCityId==city.id){"Invalid city support reference"}}
            city.besiegedByUnitId?.let{id->val unit=unitById[id];require(unit!=null&&unit.pos==city.pos&&unit.owner!=city.owner){"Invalid siege reference"};require(s.diplomacy[city.owner][unit.owner]!=Relation.PEACE){"Allied siege reference"}}
        }
        s.units.forEach{unit->
            require(unit.owner in s.players.indices){"Invalid unit owner"};require(s.inBounds(unit.pos)){"Unit out of bounds"};require(unit.hp>0){"Non-positive live unit HP"};require(unit.frozenTurns>=0){"Negative frozen turns"};require(s.tile(unit.pos)?.occupantUnitId==unit.id){"Unit/tile reference mismatch"}
            unit.supportCityId?.let{id->val city=cityById[id];require(city!=null&&unit.id in city.supportedUnitIds){"Invalid unit support reference"}}
        }
        for(x in 0 until s.size)for(y in 0 until s.size){val tile=s.map[x][y];require(tile.pos==Pos(x,y)){"Tile coordinate mismatch"};require(tile.climateOwner in s.players.indices){"Invalid climate owner"};tile.territoryOwner?.let{require(it in s.players.indices){"Invalid territory owner"}};tile.cityId?.let{id->val city=cityById[id];require(city!=null&&city.pos==tile.pos){"Invalid tile city reference"}};tile.workCityId?.let{id->require(id in cityById){"Invalid work-city reference"}};tile.occupantUnitId?.let{id->val unit=unitById[id];require(unit!=null&&unit.pos==tile.pos){"Invalid tile occupant reference"}}}
        for(a in s.players.indices)for(b in s.players.indices){require(s.diplomacy[a][b]==s.diplomacy[b][a]){"Asymmetric diplomacy"};if(a==b)require(s.diplomacy[a][b]==Relation.PEACE){"Invalid self diplomacy"}}
        s.discoveredTiles.forEach{set->require(set.all(s::inBounds)){"Out-of-bounds discovered tile"}};require(s.score.all{it>=0}){"Negative score"}
        require(s.nextCityId>(cityIds.maxOrNull()?:0)){"Invalid next city ID"};require(s.nextUnitId>(unitIds.maxOrNull()?:0)){"Invalid next unit ID"}
        if(s.finished)require(s.winnerId in s.players.indices){"Finished state has invalid winner"}else require(s.winnerId==null){"Unfinished state has winner"}
    }
'''
anchor='    private fun writePos(o:DataOutputStream,p:Pos){o.writeInt(p.x);o.writeInt(p.y)}\n'
if c.count(anchor)!=1: raise SystemExit('helper insertion anchor changed')
c=c.replace(anchor,validator+anchor,1)
codec.write_text(c)
print('phase4r3_codec_hardening=APPLIED legacy_version=1')
