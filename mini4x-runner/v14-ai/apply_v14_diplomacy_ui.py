#!/usr/bin/env python3
from pathlib import Path
import os,re

PROJECT=Path(os.environ.get('MINI4X_PROJECT','project'))

presenter=PROJECT/'app/src/main/java/com/example/mini4x/presentation/GamePresenter.kt'
g=presenter.read_text()
old='enum class ScreenMode { TITLE, SETUP, WORLD, TECH, TECH_DETAIL, ACTIONS, UNIT_INFO, CITY_INFO, MENU, STATS, HELP, RESULT }'
new='enum class ScreenMode { TITLE, SETUP, WORLD, TECH, TECH_DETAIL, ACTIONS, UNIT_INFO, CITY_INFO, MENU, STATS, DIPLOMACY, HELP, RESULT }'
assert old in g,'ScreenMode anchor changed'
g=g.replace(old,new,1)
presenter.write_text(g)

view=PROJECT/'app/src/main/java/com/example/mini4x/ui/Mini4xView.kt'
s=view.read_text()

anchor='''    private var menuRects=emptyList<RectF>()
    private var setupFactionRects=emptyList<RectF>()'''
replacement='''    private var menuRects=emptyList<RectF>()
    private var diplomacyActionRects=emptyList<Pair<RectF,Command>>()
    private var diplomacyConfirmBreak:BreakPeace?=null
    private var diplomacyConfirmRect:RectF?=null
    private var diplomacyCancelRect:RectF?=null
    private var setupFactionRects=emptyList<RectF>()'''
assert anchor in s,'menu state anchor changed'
s=s.replace(anchor,replacement,1)

anchor='''            ScreenMode.STATS->drawStats(c)
            ScreenMode.HELP->drawHelp(c)'''
replacement='''            ScreenMode.STATS->drawStats(c)
            ScreenMode.DIPLOMACY->drawDiplomacy(c)
            ScreenMode.HELP->drawHelp(c)'''
assert anchor in s,'draw dispatch anchor changed'
s=s.replace(anchor,replacement,1)

diplomacy=r'''    private fun drawDiplomacy(c:Canvas){
        drawOverlayTopBar(c,"DIPLOMACY")
        diplomacyActionRects=emptyList();diplomacyConfirmRect=null;diplomacyCancelRect=null
        val portrait=height>width
        val r=modalPanel(c,if(portrait)height*.14f else height*.12f,if(portrait)height*.90f else height*.92f,if(portrait).92f else .72f)
        val state=presenter.state;val player=state.players[0]
        val rivals=state.players.indices.filter{it!=0 && !state.players[it].eliminated}
        text.textAlign=Paint.Align.LEFT;text.typeface=Typeface.create("sans-serif",Typeface.NORMAL);text.textSize=8.4f*d;text.color=Color.rgb(183,201,211)
        c.drawText("Treaties and embassies use normal game rules and costs.",r.left+18*d,r.top+31*d,text)
        val pairs=mutableListOf<Pair<RectF,Command>>()
        val top=r.top+49*d;val rowH=(r.bottom-top-15*d)/max(1,rivals.size)
        rivals.forEachIndexed{i,pid->
            val row=RectF(r.left+12*d,top+i*rowH,r.right-12*d,top+(i+1)*rowH-8*d)
            paint.color=Color.rgb(9,20,27);c.drawRoundRect(row,12*d,12*d,paint)
            paint.style=Paint.Style.STROKE;paint.strokeWidth=1f*d;paint.color=Color.rgb(47,66,77);c.drawRoundRect(row,12*d,12*d,paint);paint.style=Paint.Style.FILL
            val col=playerFactionColor(pid);paint.color=col;c.drawCircle(row.left+20*d,row.top+24*d,10*d,paint);drawCrown(c,row.left+20*d,row.top+24*d,5.5f*d,Color.WHITE)
            text.textAlign=Paint.Align.LEFT;text.typeface=Typeface.DEFAULT_BOLD;text.textSize=11*d;text.color=Color.WHITE;c.drawText(playerFactionName(pid),row.left+39*d,row.top+28*d,text)
            val relation=state.diplomacy[0][pid].name.lowercase().replaceFirstChar{it.uppercase()}
            val met=pid in player.metPlayers;val embassy=pid in player.embassies
            text.typeface=Typeface.create("sans-serif",Typeface.NORMAL);text.textSize=7.5f*d;text.color=Color.rgb(174,194,205)
            c.drawText("$relation · ${if(met)"Met" else "Not met"} · ${if(embassy)"Embassy active" else "No embassy"}",row.left+39*d,row.top+49*d,text)
            val inside=state.ownedUnits(0).count{u->state.tile(u.pos)?.territoryOwner==pid}
            if(state.diplomacy[0][pid]==Relation.PEACE && inside>0){text.textSize=7.2f*d;text.color=Color.rgb(244,148,126);c.drawText("Warning: breaking peace loses $inside unit${if(inside==1)"" else "s"} inside their territory.",row.left+18*d,row.top+72*d,text)}
            val candidates=listOf<Command>(EstablishEmbassy(0,pid),OfferPeace(0,pid),BreakPeace(0,pid))
            val legal=candidates.filter{CommandEngine.validate(state,it)==null}
            val buttonY=row.bottom-43*d;val gap=7*d;val count=max(1,legal.size);val buttonW=min(112*d,(row.width()-36*d-gap*(count-1))/count)
            if(legal.isEmpty()){
                val hint=when{
                    "strategy" !in player.technologies && "diplomacy" !in player.technologies->"Research Strategy for peace · Diplomacy for embassies"
                    "strategy" !in player.technologies->"Research Strategy to negotiate peace"
                    "diplomacy" !in player.technologies && !embassy->"Research Diplomacy to establish embassies"
                    else->"No diplomatic action is currently available"
                }
                text.textSize=7.4f*d;text.color=Color.rgb(134,153,164);c.drawText(hint,row.left+18*d,row.bottom-20*d,text)
            }else legal.forEachIndexed{j,cmd->
                val total=buttonW*legal.size+gap*(legal.size-1);val bx=row.centerX()-total/2+j*(buttonW+gap);val br=RectF(bx,buttonY,bx+buttonW,buttonY+32*d)
                paint.color=if(cmd is BreakPeace)Color.rgb(126,54,47) else Color.rgb(26,125,190);c.drawRoundRect(br,9*d,9*d,paint)
                val fid=playerFactionId(0);commandIconPath(cmd,fid)?.let{uiBitmap(c,it,RectF(br.left+7*d,br.centerY()-9*d,br.left+25*d,br.centerY()+9*d))}
                val label=when(cmd){is EstablishEmbassy->"EMBASSY";is OfferPeace->"OFFER PEACE";is BreakPeace->"BREAK PEACE";else->commandLabel(cmd).uppercase()}
                text.textAlign=Paint.Align.CENTER;text.typeface=Typeface.DEFAULT_BOLD;text.textSize=6.6f*d;text.color=Color.WHITE;c.drawText(label,br.centerX()+8*d,br.centerY()+2.5f*d,text)
                pairs+=br to cmd
            }
        }
        text.textAlign=Paint.Align.CENTER;diplomacyActionRects=pairs

        diplomacyConfirmBreak?.let{cmd->
            diplomacyActionRects=emptyList()
            paint.color=Color.argb(185,0,0,0);c.drawRect(0f,0f,width.toFloat(),height.toFloat(),paint)
            val q=modalPanel(c,height*.36f,height*.64f,if(portrait).84f else .52f);val target=cmd.targetPlayerId
            val inside=state.ownedUnits(0).count{u->state.tile(u.pos)?.territoryOwner==target}
            text.typeface=Typeface.DEFAULT_BOLD;text.textSize=17*d;text.color=Color.WHITE;c.drawText("BREAK PEACE?",q.centerX(),q.top+40*d,text)
            text.typeface=Typeface.create("sans-serif",Typeface.NORMAL);text.textSize=9*d;text.color=Color.rgb(216,224,228)
            c.drawText("Declare war on ${playerFactionName(target)}.",q.centerX(),q.top+72*d,text)
            if(inside>0){text.color=Color.rgb(247,154,132);c.drawText("$inside unit${if(inside==1)"" else "s"} in their territory will be lost.",q.centerX(),q.top+98*d,text)}
            text.color=Color.rgb(216,224,228);c.drawText("Your remaining units cannot act this turn.",q.centerX(),q.top+124*d,text)
            val by=q.bottom-49*d
            diplomacyCancelRect=RectF(q.centerX()-105*d,by,q.centerX()-8*d,by+35*d);paint.color=Color.rgb(43,68,82);c.drawRoundRect(diplomacyCancelRect!!,9*d,9*d,paint)
            diplomacyConfirmRect=RectF(q.centerX()+8*d,by,q.centerX()+105*d,by+35*d);paint.color=Color.rgb(137,53,45);c.drawRoundRect(diplomacyConfirmRect!!,9*d,9*d,paint)
            text.typeface=Typeface.DEFAULT_BOLD;text.textSize=8*d;text.color=Color.WHITE;c.drawText("CANCEL",diplomacyCancelRect!!.centerX(),diplomacyCancelRect!!.centerY()+3*d,text);c.drawText("BREAK PEACE",diplomacyConfirmRect!!.centerX(),diplomacyConfirmRect!!.centerY()+3*d,text)
        }
    }

'''
anchor='    private fun drawMenu(c:Canvas){'
assert anchor in s,'drawMenu insertion anchor changed'
s=s.replace(anchor,diplomacy+anchor,1)

pat=re.compile(r'    private fun drawMenu\(c:Canvas\)\{.*?\n    \}\n    private fun drawStats',re.S)
m=pat.search(s)
assert m,'drawMenu block changed'
menu=r'''    private fun drawMenu(c:Canvas){
        drawOverlayTopBar(c,"")
        text.typeface=Typeface.DEFAULT_BOLD;text.textSize=28*d;text.color=Color.WHITE;c.drawText("MENU",width/2f,height*.25f,text)
        val labels=listOf("Resume Game","Diplomacy","How to Play","New Game")
        val rects=mutableListOf<RectF>();val w=min(width*.68f,300*d);val h=48*d;val start=height*.32f
        for(i in labels.indices){val r=RectF(width/2f-w/2,start+i*61*d,width/2f+w/2,start+i*61*d+h);rects+=r;paint.color=if(i==3)Color.rgb(127,49,44) else Color.rgb(21,35,44);c.drawRoundRect(r,13*d,13*d,paint);paint.style=Paint.Style.STROKE;paint.strokeWidth=1f*d;paint.color=Color.rgb(79,106,122);c.drawRoundRect(r,13*d,13*d,paint);paint.style=Paint.Style.FILL;text.typeface=Typeface.create("sans-serif",Typeface.NORMAL);text.textSize=13*d;text.color=Color.WHITE;c.drawText(labels[i],r.centerX(),r.centerY()+4*d,text)}
        menuRects=rects
    }
    private fun drawStats'''
s=s[:m.start()]+menu+s[m.end():]

anchor='''            ScreenMode.STATS->tapStats(x,y)
            ScreenMode.HELP->tapHelp(x,y)'''
replacement='''            ScreenMode.STATS->tapStats(x,y)
            ScreenMode.DIPLOMACY->tapDiplomacy(x,y)
            ScreenMode.HELP->tapHelp(x,y)'''
assert anchor in s,'tap dispatch anchor changed'
s=s.replace(anchor,replacement,1)

pat=re.compile(r'    private fun tapMenu\(x:Float,y:Float\)\{.*?\n    \}\n    private fun tapHelp',re.S)
m=pat.search(s)
assert m,'tapMenu block changed'
taps=r'''    private fun tapDiplomacy(x:Float,y:Float){
        diplomacyConfirmBreak?.let{pending->
            if(diplomacyCancelRect?.contains(x,y)==true){diplomacyConfirmBreak=null;return}
            if(diplomacyConfirmRect?.contains(x,y)==true){presenter.execute(pending);diplomacyConfirmBreak=null;return}
            return
        }
        if(hypot(x-34*d,y-42*d)<30*d){presenter.setScreen(ScreenMode.MENU);return}
        diplomacyActionRects.firstOrNull{it.first.contains(x,y)}?.second?.let{cmd->if(cmd is BreakPeace)diplomacyConfirmBreak=cmd else presenter.execute(cmd)}
    }
    private fun tapMenu(x:Float,y:Float){
        if(hypot(x-34*d,y-42*d)<30*d){presenter.setScreen(ScreenMode.WORLD);return}
        val i=menuRects.indexOfFirst{it.contains(x,y)}
        when(i){
            0->presenter.setScreen(ScreenMode.WORLD)
            1->{diplomacyConfirmBreak=null;presenter.setScreen(ScreenMode.DIPLOMACY)}
            2->{helpReturn=ScreenMode.MENU;presenter.setScreen(ScreenMode.HELP)}
            3->{saveStore.clear();savedGame=null;activeGame=false;presenter=GamePresenter();selectedMode=0;selectedFaction=0;presenter.setScreen(ScreenMode.TITLE)}
        }
    }
    private fun tapHelp'''
s=s[:m.start()]+taps+s[m.end():]

anchor='''            ScreenMode.TECH,ScreenMode.ACTIONS,ScreenMode.UNIT_INFO,ScreenMode.CITY_INFO,ScreenMode.STATS->{presenter.setScreen(ScreenMode.WORLD)}
            ScreenMode.TECH_DETAIL->{presenter.setScreen(ScreenMode.TECH)}
            ScreenMode.MENU->{presenter.setScreen(ScreenMode.WORLD)}'''
replacement='''            ScreenMode.TECH,ScreenMode.ACTIONS,ScreenMode.UNIT_INFO,ScreenMode.CITY_INFO,ScreenMode.STATS->{presenter.setScreen(ScreenMode.WORLD)}
            ScreenMode.TECH_DETAIL->{presenter.setScreen(ScreenMode.TECH)}
            ScreenMode.DIPLOMACY->{if(diplomacyConfirmBreak!=null)diplomacyConfirmBreak=null else presenter.setScreen(ScreenMode.MENU)}
            ScreenMode.MENU->{presenter.setScreen(ScreenMode.WORLD)}'''
assert anchor in s,'system back anchor changed'
s=s.replace(anchor,replacement,1)

for required in [
    'ScreenMode.DIPLOMACY->drawDiplomacy(c)',
    'ScreenMode.DIPLOMACY->tapDiplomacy(x,y)',
    'val labels=listOf("Resume Game","Diplomacy","How to Play","New Game")',
    'private fun drawDiplomacy(c:Canvas)',
    'private fun tapDiplomacy(x:Float,y:Float)',
    'diplomacyConfirmBreak:BreakPeace?',
]:
    assert required in s,required

view.write_text(s)
print('V1.4 diplomacy UI: Settings entry, per-rival legal actions, embassy/peace status, break-peace warning and confirmation')
