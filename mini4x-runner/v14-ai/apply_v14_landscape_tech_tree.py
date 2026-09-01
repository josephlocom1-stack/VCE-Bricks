#!/usr/bin/env python3
from pathlib import Path
import os

PROJECT=Path(os.environ.get('MINI4X_PROJECT','project'))
view=PROJECT/'app/src/main/java/com/example/mini4x/ui/Mini4xView.kt'
s=view.read_text()
assert 'val techYScale=if(portrait)1f else .82f' not in s,'landscape tech-tree patch already applied'

old='''    private fun techLayout():Map<String,PointF>{val all=TechnologyCatalog.all;val branches=listOf("climbing","fishing","hunting","organization","riding");val portrait=height>width;val center=PointF(width/2f+techPanX,height*(if(portrait).53f else .54f)+techPanY);val base=min(width,height)*(if(portrait).135f else .15f)*techScale;val mid=base*1.75f;val outer=base*2.55f;val out=mutableMapOf<String,PointF>();for((bi,root)in branches.withIndex()){val a=(-PI/2+bi*2*PI/5).toFloat();val ux=cos(a);val uy=sin(a);val px=-uy;val py=ux;out[root]=PointF(center.x+ux*base,center.y+uy*base);val t2=all.filter{it.requires==root}.sortedBy{it.id};t2.forEachIndexed{j,t->val sign=if(j==0)1 else -1;out[t.id]=PointF(center.x+ux*mid+px*base*.32f*sign,center.y+uy*mid+py*base*.32f*sign);all.firstOrNull{it.requires==t.id}?.let{t3->out[t3.id]=PointF(center.x+ux*outer+px*base*.38f*sign,center.y+uy*outer+py*base*.38f*sign)}}};return out}'''
new='''    private fun techLayout():Map<String,PointF>{val all=TechnologyCatalog.all;val branches=listOf("climbing","fishing","hunting","organization","riding");val portrait=height>width;val center=PointF(width/2f+techPanX,height*(if(portrait).53f else .54f)+techPanY);val base=min(width,height)*(if(portrait).135f else .15f)*techScale;val mid=base*1.75f;val outer=base*2.55f;val techYScale=if(portrait)1f else .82f;val out=mutableMapOf<String,PointF>();for((bi,root)in branches.withIndex()){val a=(-PI/2+bi*2*PI/5).toFloat();val ux=cos(a);val uy=sin(a);val px=-uy;val py=ux;out[root]=PointF(center.x+ux*base,center.y+uy*base*techYScale);val t2=all.filter{it.requires==root}.sortedBy{it.id};t2.forEachIndexed{j,t->val sign=if(j==0)1 else -1;out[t.id]=PointF(center.x+ux*mid+px*base*.32f*sign,center.y+(uy*mid+py*base*.32f*sign)*techYScale);all.firstOrNull{it.requires==t.id}?.let{t3->out[t3.id]=PointF(center.x+ux*outer+px*base*.38f*sign,center.y+(uy*outer+py*base*.38f*sign)*techYScale)}}};return out}'''
assert s.count(old)==1,'techLayout landscape anchor drifted'
s=s.replace(old,new,1)

old_radius='''            val r=min(27*d,min(width,height)*.055f)*techScale.coerceIn(.80f,1.22f);paint.color=col;c.drawCircle(q.x,q.y,r,paint)'''
new_radius='''            val r=min(if(height>width)27*d else 16*d,min(width,height)*(if(height>width).055f else .038f))*techScale.coerceIn(.80f,1.22f);paint.color=col;c.drawCircle(q.x,q.y,r,paint)'''
assert s.count(old_radius)==1,'tech node radius anchor drifted'
s=s.replace(old_radius,new_radius,1)

old_footer='''        text.typeface=Typeface.create("sans-serif",Typeface.NORMAL);text.textSize=7.8f*d;text.color=Color.rgb(237,239,240);c.drawText("Tech costs increase for each city in your empire.",width/2f,height-25*d,text)'''
new_footer='''        text.typeface=Typeface.create("sans-serif",Typeface.NORMAL);text.textSize=7.8f*d;text.color=Color.rgb(237,239,240);c.drawText("Tech costs increase for each city in your empire.",width/2f,height-(if(portrait)25*d else 8*d),text)'''
assert s.count(old_footer)==1,'tech footer anchor drifted'
s=s.replace(old_footer,new_footer,1)

old_tap='''        val n=techNodes.minByOrNull{(_,p)->hypot(x-p.x,y-p.y)}?:return
        if(hypot(x-n.value.x,y-n.value.y)<45*d*techScale){selectedTechId=n.key;presenter.setScreen(ScreenMode.TECH_DETAIL)}'''
new_tap='''        val n=techNodes.minByOrNull{(_,p)->hypot(x-p.x,y-p.y)}?:return
        val hitRadius=(if(height>width)45*d else 30*d)*techScale
        if(hypot(x-n.value.x,y-n.value.y)<hitRadius){selectedTechId=n.key;presenter.setScreen(ScreenMode.TECH_DETAIL)}'''
assert s.count(old_tap)==1,'tech tap radius anchor drifted'
s=s.replace(old_tap,new_tap,1)

for marker in [
    'val techYScale=if(portrait)1f else .82f',
    'if(height>width)27*d else 16*d',
    'if(height>width).055f else .038f',
    'height-(if(portrait)25*d else 8*d)',
    'val hitRadius=(if(height>width)45*d else 30*d)*techScale',
]:
    assert marker in s,marker
view.write_text(s)
print('V1.4 landscape Tech Tree: vertically compressed node field, separated nodes/labels/footer and accessible nearest-node touch targets')
