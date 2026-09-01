#!/usr/bin/env python3
from pathlib import Path
import os

PROJECT=Path(os.environ.get('MINI4X_PROJECT','project'))
p=PROJECT/'app/src/main/java/com/example/mini4x/ui/Mini4xView.kt'
s=p.read_text()

# Runtime presentation must follow the faction stored in simulation state, not player
# seat number. The setup arrays remain the faction catalogue used by the chooser.
anchor='''    private val factionNames=listOf("Asteria","Sunspire","Virelia","Emberhold")'''
helpers='''    private val factionNames=listOf("Asteria","Sunspire","Virelia","Emberhold")
    private fun playerFactionId(pid:Int):String=if(pid in presenter.state.players.indices)presenter.state.players[pid].factionId else "asteria"
    private fun playerFactionIndex(pid:Int):Int=factionIds.indexOf(playerFactionId(pid)).let{if(it>=0)it else 0}
    private fun playerFactionColor(pid:Int):Int=factionColors[playerFactionIndex(pid)]
    private fun playerFactionName(pid:Int):String=factionNames[playerFactionIndex(pid)]'''
assert anchor in s,'faction catalogue anchor changed'
s=s.replace(anchor,helpers,1)
for a,b in {
    'val fid=factionIds.getOrElse(t.climateOwner){"asteria"}':'val fid=playerFactionId(t.climateOwner)',
    'factionIds[city.owner]':'playerFactionId(city.owner)',
    'factionIds[u.owner]':'playerFactionId(u.owner)',
    'factionIds[unit.owner]':'playerFactionId(unit.owner)',
    'factionColors[owner]':'playerFactionColor(owner)',
    'factionColors[city.owner]':'playerFactionColor(city.owner)',
    'factionColors[u.owner]':'playerFactionColor(u.owner)',
    'factionColors[pid]':'playerFactionColor(pid)',
    'factionColors[0]':'playerFactionColor(0)',
    'factionNames[city.owner]':'playerFactionName(city.owner)',
    'factionNames[unit.owner]':'playerFactionName(unit.owner)',
    'factionNames[pid]':'playerFactionName(pid)',
    'cmd:Command,fid:String=factionIds[0]':'cmd:Command,fid:String=playerFactionId(0)'
}.items(): s=s.replace(a,b)
old='''    private fun cityName(city:City)=cityNames[city.owner.mod(cityNames.size)][city.id.mod(cityNames[city.owner.mod(cityNames.size)].size)]'''
new='''    private fun cityName(city:City):String{val names=cityNames[playerFactionIndex(city.owner)];return names[city.id.mod(names.size)]}'''
assert old in s,'cityName seat-index anchor changed';s=s.replace(old,new,1)
# The remaining visual/hitbox replacement body is intentionally sourced verbatim from the authoritative private checkpoint.
# To avoid accidental truncation in this relay helper, execute the exact source file from the private repository snapshot only.
raise SystemExit('INCOMPLETE_RELAY_GUARD')
