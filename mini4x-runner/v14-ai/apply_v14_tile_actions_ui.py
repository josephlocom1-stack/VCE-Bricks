#!/usr/bin/env python3
from pathlib import Path
import os,re

PROJECT=Path(os.environ.get('MINI4X_PROJECT','project'))
view=PROJECT/'app/src/main/java/com/example/mini4x/ui/Mini4xView.kt'
s=view.read_text()
assert 'private fun tileContextLabel(' not in s,'tile actions UI already applied'

# Plain owned/resource tiles already have real legal commands in LegalCommandGenerator.forTile,
# but the player had no Actions control unless a unit/city occupied the selected tile.
# Expose those existing commands without changing simulation rules.
pat=re.compile(r'    private fun drawSelectionContext\(c:Canvas\)\{.*?\n    \}\n\n    private fun drawBitmapCover',re.S)
m=pat.search(s)
assert m,'drawSelectionContext block changed'
context=r'''    private fun tileContextLabel(t:Tile):String=when{
        t.improvement!=null->t.improvement!!.name.lowercase().replace('_',' ').replaceFirstChar{it.uppercase()}
        t.village->"Village"
        t.ruin->"Ruin"
        t.starfish->"Starfish"
        t.resource!=null->t.resource!!.name.lowercase().replaceFirstChar{it.uppercase()}
        else->t.terrain.name.lowercase().replaceFirstChar{it.uppercase()}
    }
    private fun drawSelectionContext(c:Canvas){
        contextInfoRect=null;contextActionRect=null
        val unit=presenter.selection.unitId?.let(presenter.state::unit)
        val city=presenter.selection.cityId?.let(presenter.state::city)
        val tile=presenter.selection.tile?.let(presenter.state::tile)
        val tileContextActions=if(unit==null&&city==null&&tile!=null)presenter.legalActions().filter{it !is MoveUnit}else emptyList()
        if(unit==null&&city==null&&tileContextActions.isEmpty())return
        val portrait=height>width
        val panelW=if(portrait)width*.88f else width*.52f
        val h=if(portrait)70*d else 62*d
        val bottom=height-(if(portrait)101*d else 83*d)
        val top=bottom-h;val left=width/2f-panelW/2;val right=width/2f+panelW/2
        paint.color=Color.argb(225,5,10,14);c.drawRoundRect(left,top,right,bottom,18*d,18*d,paint)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=1f*d;paint.color=Color.argb(120,118,137,148);c.drawRoundRect(left,top,right,bottom,18*d,18*d,paint);paint.style=Paint.Style.FILL
        val art=RectF(left+7*d,top+6*d,left+63*d,bottom-6*d)
        if(unit!=null){
            val fid=playerFactionId(unit.owner);val k=bodyKind(unit.kind);uiBitmap(c,"units/${fid}_${k}_body.png",art)
            text.textAlign=Paint.Align.LEFT;text.typeface=Typeface.create("sans-serif",Typeface.NORMAL);text.textSize=12*d;text.color=Color.WHITE
            c.drawText("${playerFactionName(unit.owner)}  ${unitLabel(unit.kind)}",left+70*d,top+25*d,text)
            text.textSize=8.5f*d;text.color=Color.rgb(198,210,218);c.drawText("HP ${unit.hp}/${UnitCatalog.maxHP(unit)} · choose a highlighted tile or Actions",left+70*d,top+47*d,text)
        } else if(city!=null){
            val fid=playerFactionId(city.owner);uiBitmap(c,"cities/${fid}_city_l${city.level.coerceAtMost(5)}.png",art)
            text.textAlign=Paint.Align.LEFT;text.typeface=Typeface.create("sans-serif",Typeface.NORMAL);text.textSize=12*d;text.color=Color.WHITE
            c.drawText("City lvl ${city.level}  ${playerFactionName(city.owner)}",left+70*d,top+25*d,text)
            text.textSize=8.5f*d;text.color=Color.rgb(198,210,218);c.drawText("${city.population}/${city.populationNeeded} population · ${city.income(0)} stars/turn · ${city.supportedUnitIds.size}/${city.capacity} units",left+70*d,top+47*d,text)
        } else if(tile!=null){
            uiBitmap(c,tileAsset(tile),art)
            text.textAlign=Paint.Align.LEFT;text.typeface=Typeface.create("sans-serif",Typeface.NORMAL);text.textSize=12*d;text.color=Color.WHITE
            c.drawText(tileContextLabel(tile),left+70*d,top+25*d,text)
            text.textSize=8.5f*d;text.color=Color.rgb(198,210,218)
            val count=tileContextActions.size;c.drawText("$count available action${if(count==1)"" else "s"} · tap Actions",left+70*d,top+47*d,text)
        }
        text.textAlign=Paint.Align.CENTER
        if(unit!=null||city!=null){
            val info=RectF(right-82*d,top+16*d,right-48*d,top+50*d);contextInfoRect=info
            paint.color=Color.rgb(39,153,222);c.drawCircle(info.centerX(),info.centerY(),17*d,paint);paint.style=Paint.Style.STROKE;paint.strokeWidth=1.3f*d;paint.color=Color.WHITE;c.drawCircle(info.centerX(),info.centerY(),17*d,paint);paint.style=Paint.Style.FILL
            text.typeface=Typeface.DEFAULT_BOLD;text.textSize=13*d;text.color=Color.WHITE;c.drawText("i",info.centerX(),info.centerY()+4.5f*d,text)
        }
        val act=RectF(right-43*d,top+12*d,right-7*d,top+54*d);contextActionRect=act
        paint.color=Color.WHITE;c.drawCircle(act.centerX(),act.centerY(),19*d,paint)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=4*d;paint.strokeCap=Paint.Cap.SQUARE;paint.color=Color.rgb(10,16,20);c.drawLine(act.centerX()-8*d,act.centerY()-2*d,act.centerX(),act.centerY()+7*d,paint);c.drawLine(act.centerX(),act.centerY()+7*d,act.centerX()+10*d,act.centerY()-8*d,paint);paint.style=Paint.Style.FILL
        text.typeface=Typeface.create("sans-serif",Typeface.NORMAL)
    }

    private fun drawBitmapCover'''
s=s[:m.start()]+context+s[m.end():]

# The action sheet already renders tile command icons. Give a tile selection a proper header
# instead of the old “Select a unit or city first” fallback.
old='''        val city=presenter.selection.cityId?.let(presenter.state::city);val unit=presenter.selection.unitId?.let(presenter.state::unit)
        val pairs=mutableListOf<Pair<RectF,Command>>()'''
new='''        val city=presenter.selection.cityId?.let(presenter.state::city);val unit=presenter.selection.unitId?.let(presenter.state::unit);val tile=presenter.selection.tile?.let(presenter.state::tile)
        val pairs=mutableListOf<Pair<RectF,Command>>()'''
assert old in s,'drawActions selection anchor changed'
s=s.replace(old,new,1)

old='''        }else{text.textSize=12*d;text.color=Color.WHITE;c.drawText("Select a unit or city first.",width/2f,top+44*d,text)}
        paint.style=Paint.Style.STROKE;'''
new='''        }else if(tile!=null){
            uiBitmap(c,tileAsset(tile),RectF(12*d,top+7*d,76*d,infoBottom-4*d))
            text.textAlign=Paint.Align.LEFT;text.typeface=Typeface.create("sans-serif",Typeface.NORMAL);text.textSize=15.5f*d;text.color=Color.WHITE;c.drawText(tileContextLabel(tile),83*d,top+30*d,text)
            text.textSize=8.7f*d;text.color=Color.rgb(205,214,220);c.drawText("Choose an available tile action.",83*d,top+52*d,text);text.textAlign=Paint.Align.CENTER
        }else{text.textSize=12*d;text.color=Color.WHITE;c.drawText("Select a unit, city or actionable tile first.",width/2f,top+44*d,text)}
        paint.style=Paint.Style.STROKE;'''
assert old in s,'drawActions fallback anchor changed'
s=s.replace(old,new,1)

# The compact V1.3 action sheet was only 165dp tall in landscape. With the supported 12-action
# maximum (10 columns -> 2 rows), the second-row labels and hit rectangles extended below the
# viewport. Use one 220dp panel depth in both orientations; this preserves the existing internal
# layout while keeping the full 12-action hit geometry on-screen at the contracted 420dpi target.
old_geometry='val portrait=height>width;val top=if(portrait)height-205*d else height-165*d'
new_geometry='val portrait=height>width;val top=height-220*d'
assert s.count(old_geometry)==2,'draw/tap Actions geometry anchors changed'
s=s.replace(old_geometry,new_geometry)

for required in [
    'private fun tileContextLabel(t:Tile)',
    'val tileContextActions=if(unit==null&&city==null&&tile!=null)presenter.legalActions()',
    'if(unit==null&&city==null&&tileContextActions.isEmpty())return',
    'else if(tile!=null){',
    'Choose an available tile action.',
    'contextActionRect=act',
    'val portrait=height>width;val top=height-220*d',
]:
    assert required in s,required

view.write_text(s)
print('V1.4 tile actions UI: plain actionable tiles expose context + Actions sheet using existing legal commands; full action sheet stays on-screen in portrait/landscape')
