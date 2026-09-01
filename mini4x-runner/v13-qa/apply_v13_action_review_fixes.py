#!/usr/bin/env python3
from pathlib import Path
import os,re,math

PROJECT=Path(os.environ.get('MINI4X_PROJECT','project'))
p=PROJECT/'app/src/main/java/com/example/mini4x/ui/Mini4xView.kt'
s=p.read_text()

# Split the reference-style top status from bottom navigation so ACTIONS can retain
# Score/Stars/Turn without drawing the normal bottom navigation underneath its panel.
pat=re.compile(r'    private fun drawHud\(c:Canvas\)\{.*?\n    \}\n    private fun drawRoundControl',re.S)
m=pat.search(s)
assert m,'post-review drawHud block not found'
replacement=r'''    private fun drawTopStatus(c:Canvas){
        val s=presenter.state;val portrait=height>width;val income=DerivedState.totalIncome(s,0)
        val vals=listOf("Score" to "%,d".format(s.score[0]),"Stars (+$income)" to s.players[0].stars.toString(),"Turn" to s.roundNumber.toString())
        val hx=if(portrait)floatArrayOf(width*.34f,width*.52f,width*.70f) else floatArrayOf(width*.42f,width*.50f,width*.58f)
        val labelY=if(portrait)62*d else 24*d;val valueY=if(portrait)86*d else 48*d
        for(i in vals.indices){val x=hx[i];text.typeface=Typeface.create("sans-serif",Typeface.NORMAL);text.textSize=9.5f*d;text.color=Color.rgb(242,244,245);c.drawText(vals[i].first,x,labelY,text);text.typeface=Typeface.create("sans-serif-light",Typeface.NORMAL);text.textSize=18*d;text.color=Color.WHITE;if(i==1){drawStar(c,x-12*d,valueY-6*d,6.8f*d,Color.rgb(247,194,45));c.drawText(vals[i].second,x+7*d,valueY,text)}else c.drawText(vals[i].second,x,valueY,text)}
    }
    private fun drawHud(c:Canvas){
        drawTopStatus(c);val s=presenter.state;val portrait=height>width;val by=height-56*d
        val xs=if(portrait)floatArrayOf(width*.18f,width*.40f,width*.61f,width*.82f)else floatArrayOf(width*.31f,width*.44f,width*.57f,width*.70f);val labs=arrayOf("Settings","Game Stats","Tech Tree","End Turn")
        for(i in xs.indices)drawRoundControl(c,xs[i],by,27*d,i,labs[i])
        val rank=1+s.players.indices.count{pid->s.score[pid]>s.score[0]};val rankText=when(rank){1->"1st";2->"2nd";3->"3rd";else->"${rank}th"};paint.color=Color.rgb(27,194,53);c.drawRoundRect(xs[1]-29*d,by-37*d,xs[1]-10*d,by-24*d,1*d,1*d,paint);text.typeface=Typeface.DEFAULT_BOLD;text.textSize=6.5f*d;text.color=Color.WHITE;c.drawText(rankText,xs[1]-19.5f*d,by-27*d,text);text.typeface=Typeface.DEFAULT
    }
    private fun drawRoundControl'''
s=s[:m.start()]+replacement+s[m.end():]

old='ScreenMode.ACTIONS->{drawWorld(c,false);drawActions(c)}'
new='ScreenMode.ACTIONS->{drawWorld(c,false);drawTopStatus(c);drawActions(c)}'
assert old in s,'ACTIONS draw dispatch changed'
s=s.replace(old,new,1)

# Reference-like compact selection strip + circular action choices. All legal actions
# remain reachable; common small sets render in one row, larger sets use two compact rows.
pat=re.compile(r'    private fun drawActions\(c:Canvas\)\{.*?\n    \}\n\n    private fun drawHelp',re.S)
m=pat.search(s)
assert m,'drawActions block not found'
actions=r'''    private fun drawActions(c:Canvas){
        val actions=presenter.legalActions().filter{it !is MoveUnit}.take(12);actionRects=emptyList()
        val portrait=height>width;val top=if(portrait)height-205*d else height-165*d
        paint.color=Color.argb(238,3,7,10);c.drawRect(0f,top,width.toFloat(),height.toFloat(),paint)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=1f*d;paint.color=Color.argb(130,93,111,122);c.drawLine(0f,top,width.toFloat(),top,paint);paint.style=Paint.Style.FILL
        circleButton(c,width-39*d,top+38*d,23*d,"✓","")
        val city=presenter.selection.cityId?.let(presenter.state::city);val unit=presenter.selection.unitId?.let(presenter.state::unit)
        val pairs=mutableListOf<Pair<RectF,Command>>()
        val infoBottom=top+78*d
        if(city!=null){
            val fid=playerFactionId(city.owner);uiBitmap(c,"cities/${fid}_city_l${city.level.coerceAtMost(5)}.png",RectF(10*d,top+7*d,76*d,infoBottom-4*d))
            text.textAlign=Paint.Align.LEFT;text.typeface=Typeface.create("sans-serif",Typeface.NORMAL);text.textSize=15.5f*d;text.color=Color.WHITE;c.drawText("City lvl ${city.level}",83*d,top+30*d,text)
            val name=playerFactionName(city.owner);text.textSize=7.3f*d;val pw=text.measureText(name)+19*d;paint.color=playerFactionColor(city.owner);c.drawRoundRect(83*d,top+37*d,83*d+pw,top+57*d,10*d,10*d,paint);text.color=Color.WHITE;c.drawText(name,92*d,top+51*d,text)
            text.textSize=8.7f*d;text.color=Color.rgb(205,214,220);c.drawText("Choose a unit to train or a city action.",83*d+pw+8*d,top+52*d,text);text.textAlign=Paint.Align.CENTER
        }else if(unit!=null){
            val fid=playerFactionId(unit.owner);uiBitmap(c,"units/${fid}_${bodyKind(unit.kind)}_body.png",RectF(12*d,top+7*d,76*d,infoBottom-4*d))
            text.textAlign=Paint.Align.LEFT;text.typeface=Typeface.create("sans-serif",Typeface.NORMAL);text.textSize=15.5f*d;text.color=Color.WHITE;c.drawText(unitLabel(unit.kind),83*d,top+30*d,text)
            val name=playerFactionName(unit.owner);text.textSize=7.3f*d;val pw=text.measureText(name)+19*d;paint.color=playerFactionColor(unit.owner);c.drawRoundRect(83*d,top+37*d,83*d+pw,top+57*d,10*d,10*d,paint);text.color=Color.WHITE;c.drawText(name,92*d,top+51*d,text)
            text.textSize=8.7f*d;text.color=Color.rgb(205,214,220);c.drawText("Choose an available action.",83*d+pw+8*d,top+52*d,text);text.textAlign=Paint.Align.CENTER
        }else{text.textSize=12*d;text.color=Color.WHITE;c.drawText("Select a unit or city first.",width/2f,top+44*d,text)}
        paint.style=Paint.Style.STROKE;paint.strokeWidth=1f*d;paint.color=Color.argb(110,92,109,120);c.drawLine(0f,infoBottom,width.toFloat(),infoBottom,paint);paint.style=Paint.Style.FILL
        if(actions.isNotEmpty()){
            val cols=if(portrait)min(7,actions.size) else min(10,actions.size);val rows=(actions.size+cols-1)/cols;val cell=width/cols.toFloat();val radius=min(26*d,cell*.31f)
            actions.forEachIndexed{i,a->
                val row=i/cols;val col=i%cols;val countInRow=if(row==rows-1)actions.size-row*cols else cols;val offset=(width-countInRow*cell)/2f
                val cx=offset+(col+.5f)*cell;val cy=if(rows==1)top+137*d else top+(112+row*55)*d
                val fid=when{city!=null->playerFactionId(city.owner);unit!=null->playerFactionId(unit.owner);else->playerFactionId(0)}
                val fill=when(a){is AttackUnit->Color.rgb(94,34,34);is RecoverUnit->Color.rgb(28,78,56);else->Color.rgb(24,127,196)}
                paint.color=fill;c.drawCircle(cx,cy,radius,paint);paint.style=Paint.Style.STROKE;paint.strokeWidth=1.5f*d;paint.color=Color.WHITE;c.drawCircle(cx,cy,radius,paint);paint.style=Paint.Style.FILL
                when(a){
                    is TrainUnit->{val k=bodyKind(a.kind);uiBitmap(c,"units/${fid}_${k}_body.png",RectF(cx-radius*.72f,cy-radius*.78f,cx+radius*.72f,cy+radius*.72f));val cost=UnitCatalog[a.kind].cost?:0;drawStar(c,cx-radius*.62f,cy-radius-6*d,3.7f*d,Color.rgb(247,194,45));text.textSize=6.6f*d;text.color=Color.WHITE;c.drawText(cost.toString(),cx-radius*.25f,cy-radius-3.5f*d,text)}
                    else->commandIconPath(a,fid)?.let{uiBitmap(c,it,RectF(cx-radius*.62f,cy-radius*.62f,cx+radius*.62f,cy+radius*.62f))}
                }
                text.typeface=Typeface.create("sans-serif",Typeface.NORMAL);text.textSize=if(actions.size<=7)7.2f*d else 6.2f*d;text.color=Color.WHITE;val label=if(a is TrainUnit)unitLabel(a.kind)else commandLabel(a);c.drawText(label,cx,cy+radius+13*d,text)
                pairs+=RectF(cx-radius-5*d,cy-radius-7*d,cx+radius+5*d,cy+radius+18*d) to a
            }
        }
        actionRects=pairs
    }

    private fun drawHelp'''
s=s[:m.start()]+actions+s[m.end():]

old='''        val portrait=height>width;val top=if(portrait)height-285*d else height-205*d
        if(hypot(x-(width-38*d),y-(top+36*d))<32*d){presenter.setScreen(ScreenMode.WORLD);return}'''
new='''        val portrait=height>width;val top=if(portrait)height-205*d else height-165*d
        if(hypot(x-(width-39*d),y-(top+38*d))<34*d){presenter.setScreen(ScreenMode.WORLD);return}'''
assert old in s,'tapActions close geometry anchor changed'
s=s.replace(old,new,1)

p.write_text(s)
print('Action review fixes: top status retained, compact reference-like selection strip, circular action/train choices, matching hitboxes')
