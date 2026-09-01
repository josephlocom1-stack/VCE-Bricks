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

# All in-game art/name/colour lookups use the authoritative player faction. Setup-card
# lookups deliberately keep catalogue index i/selectedFaction and are not replaced.
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
}.items():
    s=s.replace(a,b)

old='''    private fun cityName(city:City)=cityNames[city.owner.mod(cityNames.size)][city.id.mod(cityNames[city.owner.mod(cityNames.size)].size)]'''
new='''    private fun cityName(city:City):String{val names=cityNames[playerFactionIndex(city.owner)];return names[city.id.mod(names.size)]}'''
assert old in s,'cityName seat-index anchor changed'
s=s.replace(old,new,1)

# World HUD: supplied reference floats Score / Stars / Turn directly on the black
# starfield, with four primary controls spread across the portrait width.
old='''    private fun drawHud(c:Canvas){
        val s=presenter.state;val portrait=height>width
        val panelW=if(portrait) width*.66f else width*.31f;val panelH=if(portrait)64*d else 58*d;val l=width/2-panelW/2;val top=if(portrait)10*d else -3*d
        paint.color=Color.argb(215,18,27,33);c.drawRoundRect(l,top,l+panelW,top+panelH,22*d,22*d,paint)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=.7f*d;paint.color=Color.argb(100,115,130,140)
        for(k in 1..2){val sx=l+panelW*k/3f;c.drawLine(sx,top+8*d,sx,top+panelH-8*d,paint)};paint.style=Paint.Style.FILL
        val income=DerivedState.totalIncome(s,0);val vals=listOf("Score" to "%,d".format(s.score[0]),"Stars (+$income)" to s.players[0].stars.toString(),"Turn" to s.roundNumber.toString())
        for(i in vals.indices){
            val x=l+panelW*(i+.5f)/3;text.textSize=7.5f*d;text.typeface=Typeface.create("sans-serif",Typeface.NORMAL);text.color=Color.rgb(235,239,241);c.drawText(vals[i].first,x,top+17*d,text);text.textSize=13.5f*d;text.color=Color.WHITE
            if(i==1){drawStar(c,x-9*d,top+36*d,5.8f*d,Color.rgb(247,194,45));c.drawText(vals[i].second,x+5*d,top+39*d,text)} else c.drawText(vals[i].second,x,top+39*d,text)
        }
        // top-left chevron/help
        drawRoundControl(c,36*d,47*d,22*d,0,"Menu")
        // target-like tech shortcut at top right with a small badge
        drawRoundControl(c,width-36*d,47*d,22*d,2,"")
        paint.color=Color.rgb(36,151,231);c.drawCircle(width-23*d,20*d,9*d,paint);paint.style=Paint.Style.STROKE;paint.strokeWidth=1.2f*d;paint.color=Color.WHITE;c.drawCircle(width-23*d,20*d,9*d,paint);paint.style=Paint.Style.FILL;text.typeface=Typeface.DEFAULT_BOLD;text.textSize=7*d;text.color=Color.WHITE;c.drawText("3",width-23*d,22.5f*d,text);text.typeface=Typeface.DEFAULT
        val by=height-56*d;val xs=floatArrayOf(width*.31f,width*.43f,width*.55f,width*.67f);val labs=arrayOf("Menu","Game Stats","Tech Tree","Next Turn")
        for(i in xs.indices)drawRoundControl(c,xs[i],by,27*d,i,labs[i])
        // green rank ribbon
        paint.color=Color.rgb(27,194,53);c.drawRoundRect(xs[1]-29*d,by-37*d,xs[1]-10*d,by-24*d,1*d,1*d,paint);text.typeface=Typeface.DEFAULT_BOLD;text.textSize=6*d;text.color=Color.WHITE;c.drawText("1st",xs[1]-19.5f*d,by-27*d,text);text.typeface=Typeface.DEFAULT
    }'''
new='''    private fun drawHud(c:Canvas){
        val s=presenter.state;val portrait=height>width;val income=DerivedState.totalIncome(s,0)
        val vals=listOf("Score" to "%,d".format(s.score[0]),"Stars (+$income)" to s.players[0].stars.toString(),"Turn" to s.roundNumber.toString())
        val hx=if(portrait)floatArrayOf(width*.34f,width*.52f,width*.70f) else floatArrayOf(width*.42f,width*.50f,width*.58f)
        val labelY=if(portrait)62*d else 24*d;val valueY=if(portrait)86*d else 48*d
        for(i in vals.indices){val x=hx[i];text.typeface=Typeface.create("sans-serif",Typeface.NORMAL);text.textSize=9.5f*d;text.color=Color.rgb(242,244,245);c.drawText(vals[i].first,x,labelY,text);text.typeface=Typeface.create("sans-serif-light",Typeface.NORMAL);text.textSize=18*d;text.color=Color.WHITE;if(i==1){drawStar(c,x-12*d,valueY-6*d,6.8f*d,Color.rgb(247,194,45));c.drawText(vals[i].second,x+7*d,valueY,text)}else c.drawText(vals[i].second,x,valueY,text)}
        val by=height-56*d;val xs=if(portrait)floatArrayOf(width*.18f,width*.40f,width*.61f,width*.82f)else floatArrayOf(width*.31f,width*.44f,width*.57f,width*.70f);val labs=arrayOf("Settings","Game Stats","Tech Tree","End Turn")
        for(i in xs.indices)drawRoundControl(c,xs[i],by,27*d,i,labs[i])
        val rank=1+s.players.indices.count{pid->s.score[pid]>s.score[0]};val rankText=when(rank){1->"1st";2->"2nd";3->"3rd";else->"${rank}th"};paint.color=Color.rgb(27,194,53);c.drawRoundRect(xs[1]-29*d,by-37*d,xs[1]-10*d,by-24*d,1*d,1*d,paint);text.typeface=Typeface.DEFAULT_BOLD;text.textSize=6.5f*d;text.color=Color.WHITE;c.drawText(rankText,xs[1]-19.5f*d,by-27*d,text);text.typeface=Typeface.DEFAULT
    }'''
assert old in s,'original world HUD block changed'
s=s.replace(old,new,1)

# Visible/touch geometry must agree; no invisible top-corner world shortcuts.
old='''        // top controls
        if(hypot(x-36*d,y-47*d)<30*d){presenter.setScreen(ScreenMode.MENU);return}
        if(hypot(x-(width-36*d),y-47*d)<30*d){presenter.setScreen(ScreenMode.TECH);techScale=1f;techPanX=0f;techPanY=0f;return}
        val by=height-56*d;val xs=floatArrayOf(width*.31f,width*.43f,width*.55f,width*.67f)
        for(i in xs.indices)if(hypot(x-xs[i],y-by)<38*d){when(i){0->presenter.setScreen(ScreenMode.MENU);1->presenter.setScreen(ScreenMode.STATS);2->{presenter.setScreen(ScreenMode.TECH);techScale=1f;techPanX=0f;techPanY=0f};3->{sfx(sndClick);presenter.endTurn();tutorialStage=max(tutorialStage,4)}};return}'''
new='''        val by=height-56*d;val portrait=height>width;val xs=if(portrait)floatArrayOf(width*.18f,width*.40f,width*.61f,width*.82f)else floatArrayOf(width*.31f,width*.44f,width*.57f,width*.70f)
        for(i in xs.indices)if(hypot(x-xs[i],y-by)<38*d){when(i){0->presenter.setScreen(ScreenMode.MENU);1->presenter.setScreen(ScreenMode.STATS);2->{presenter.setScreen(ScreenMode.TECH);techScale=1f;techPanX=0f;techPanY=0f};3->{sfx(sndClick);presenter.endTurn();tutorialStage=max(tutorialStage,4)}};return}'''
assert old in s,'world tap controls anchor changed'
s=s.replace(old,new,1)

# Replace the tech-screen hierarchy with the supplied reference structure: same top
# stats/back control as the info screens, central faction marker, green completed / blue
# researchable / dark locked nodes, visible star costs, and the cost rule at the bottom.
old='''    private fun drawTech(c:Canvas){val layout=techLayout();techNodes.clear();techNodes.putAll(layout);text.textSize=20*d;text.typeface=Typeface.DEFAULT_BOLD;text.color=Color.WHITE;c.drawText("TECHNOLOGY",width/2f,38*d,text);text.typeface=Typeface.DEFAULT;text.textSize=8.5f*d;text.color=Color.rgb(151,171,182);c.drawText("Drag and pinch · costs increase with cities",width/2f,59*d,text);for(t in TechnologyCatalog.all){val q=layout[t.id]?:continue;t.requires?.let{r->layout[r]?.let{p->paint.style=Paint.Style.STROKE;paint.strokeWidth=2.3f*d;paint.color=Color.rgb(91,107,117);c.drawLine(p.x,p.y,q.x,q.y,paint);paint.style=Paint.Style.FILL}}};val owned=presenter.state.players[0].technologies;for(t in TechnologyCatalog.all){val q=layout[t.id]?:continue;val available=TechnologyCatalog.canResearch(presenter.state,0,t.id)&&presenter.state.players[0].stars>=TechnologyCatalog.researchCost(presenter.state,0,t.id);val col=when{t.id in owned->Color.rgb(40,195,91);available->Color.rgb(44,157,221);else->Color.rgb(28,36,42)};val r=min(27*d,min(width,height)*.055f)*techScale.coerceIn(.80f,1.22f);paint.color=col;c.drawCircle(q.x,q.y,r,paint);paint.style=Paint.Style.STROKE;paint.strokeWidth=2*d;paint.color=if(t.id in owned||available)Color.WHITE else Color.rgb(75,87,95);c.drawCircle(q.x,q.y,r,paint);paint.style=Paint.Style.FILL;bmp("tech/${t.id}.png")?.let{ic->val sz=r*1.20f;c.drawBitmap(ic,null,RectF(q.x-sz/2,q.y-sz/2-3*d,q.x+sz/2,q.y+sz/2-3*d),paint)};text.textSize=7.5f*d;text.color=if(t.id in owned||available)Color.WHITE else Color.rgb(128,142,150);c.drawText(t.label,q.x,q.y+r+13*d,text)};circleButton(c,36*d,45*d,21*d,"×","Close")}'''
new='''    private fun drawTech(c:Canvas){
        val layout=techLayout();techNodes.clear();techNodes.putAll(layout);drawOverlayTopBar(c,"")
        for(t in TechnologyCatalog.all){val q=layout[t.id]?:continue;t.requires?.let{req->layout[req]?.let{p->paint.style=Paint.Style.STROKE;paint.strokeWidth=2.0f*d;paint.color=Color.rgb(215,222,225);c.drawLine(p.x,p.y,q.x,q.y,paint);paint.style=Paint.Style.FILL}}}
        val state=presenter.state;val owned=state.players[0].technologies
        for(t in TechnologyCatalog.all){
            val q=layout[t.id]?:continue;val researchable=TechnologyCatalog.canResearch(state,0,t.id);val cost=TechnologyCatalog.researchCost(state,0,t.id)
            val col=when{t.id in owned->Color.rgb(40,195,91);researchable->Color.rgb(44,157,221);else->Color.rgb(12,15,17)}
            val r=min(27*d,min(width,height)*.055f)*techScale.coerceIn(.80f,1.22f);paint.color=col;c.drawCircle(q.x,q.y,r,paint);paint.style=Paint.Style.STROKE;paint.strokeWidth=if(researchable||t.id in owned)1.7f*d else 1.1f*d;paint.color=if(researchable||t.id in owned)Color.WHITE else Color.rgb(70,76,80);c.drawCircle(q.x,q.y,r,paint);paint.style=Paint.Style.FILL
            bmp("tech/${t.id}.png")?.let{ic->val sz=r*1.03f;c.drawBitmap(ic,null,RectF(q.x-sz/2,q.y-sz/2+1*d,q.x+sz/2,q.y+sz/2+1*d),paint)}
            if(t.id in owned){paint.style=Paint.Style.STROKE;paint.strokeWidth=max(1.8f*d,r*.09f);paint.strokeCap=Paint.Cap.SQUARE;paint.color=Color.WHITE;c.drawLine(q.x+r*.42f,q.y-r*.58f,q.x+r*.55f,q.y-r*.43f,paint);c.drawLine(q.x+r*.55f,q.y-r*.43f,q.x+r*.79f,q.y-r*.75f,paint);paint.style=Paint.Style.FILL}
            else if(researchable){drawStar(c,q.x-r*.36f,q.y-r*.68f,max(2.8f*d,r*.105f),Color.rgb(247,194,45));text.typeface=Typeface.create("sans-serif",Typeface.NORMAL);text.textSize=5.9f*d;text.color=Color.WHITE;c.drawText(cost.toString(),q.x-r*.05f,q.y-r*.60f,text)}
            else{val lx=q.x+r*.58f;val ly=q.y-r*.63f;paint.style=Paint.Style.STROKE;paint.strokeWidth=max(1.2f*d,r*.06f);paint.color=Color.rgb(158,166,170);c.drawArc(RectF(lx-r*.13f,ly-r*.16f,lx+r*.13f,ly+r*.10f),180f,-180f,false,paint);paint.style=Paint.Style.FILL;paint.color=Color.rgb(158,166,170);c.drawRoundRect(lx-r*.16f,ly,lx+r*.16f,ly+r*.22f,r*.04f,r*.04f,paint)}
            text.typeface=Typeface.create("sans-serif",Typeface.NORMAL);text.textSize=8.1f*d;text.color=if(t.id in owned||researchable)Color.WHITE else Color.rgb(125,133,138);c.drawText(t.label,q.x,q.y+r+14*d,text)
        }
        val portrait=height>width;val center=PointF(width/2f+techPanX,height*(if(portrait).53f else .54f)+techPanY);val fid=playerFactionId(0);val sz=42*d*techScale.coerceIn(.82f,1.18f);uiBitmap(c,"cities/${fid}_city_l1.png",RectF(center.x-sz/2,center.y-sz*.68f,center.x+sz/2,center.y+sz*.32f))
        text.typeface=Typeface.create("sans-serif",Typeface.NORMAL);text.textSize=7.8f*d;text.color=Color.rgb(237,239,240);c.drawText("Tech costs increase for each city in your empire.",width/2f,height-25*d,text)
    }'''
assert old in s,'original tech screen block changed'
s=s.replace(old,new,1)

# Reference back controls are icon-only circles, not labelled mini-buttons.
s=s.replace('circleButton(c,34*d,42*d,21*d,"‹","Back")','circleButton(c,34*d,42*d,21*d,"‹","")')

# Keep the underlying tech branches visible around the tech-detail overlay, as in the
# supplied completed-technology screenshot.
anchor='''        val cost=TechnologyCatalog.researchCost(presenter.state,0,id);val legal=TechnologyCatalog.canResearch(presenter.state,0,id);val affordable=presenter.state.players[0].stars>=cost
        drawOverlayTopBar(c,"")'''
replacement='''        val cost=TechnologyCatalog.researchCost(presenter.state,0,id);val legal=TechnologyCatalog.canResearch(presenter.state,0,id);val affordable=presenter.state.players[0].stars>=cost
        drawTech(c);drawOverlayTopBar(c,"")'''
assert anchor in s,'tech detail background anchor changed'
s=s.replace(anchor,replacement,1)

# Slightly improve compact bottom-control label legibility.
s=s.replace('text.textSize=7*d;text.color=Color.rgb(245,247,248);c.drawText(label,x,y+r+12*d,text)','text.textSize=7.8f*d;text.color=Color.rgb(245,247,248);c.drawText(label,x,y+r+12*d,text)',1)

p.write_text(s)
print('UI review fixes: authoritative faction presentation, reference world HUD/controls, reference tech hierarchy/costs, accessibility cues')
