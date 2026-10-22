package com.example.meowmba.ui.companion

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.cos
import kotlin.math.sin

/** Geometry is trusted native code; only bounded poses come from the local model. */
internal class InventedScenePainter {
    private val path = Path()
    fun draw(scope: DrawScope, spec: CustomAnimationSpec, timeMs: Float, center: Offset, unit: Float) = with(scope) {
        // Privacy indicators are drawn afterward and outside this stage. Keeping
        // props within the character's existing footprint preserves drive fitting.
        clipRect(8f, 8f, size.width - 8f, (size.height - 46f).coerceAtLeast(9f)) {
            for (prop in spec.props) {
                val p = prop.sample(timeMs)
                val edge = (timeMs / 240f).coerceIn(0f,1f) * ((spec.durationMs-timeMs)/280f).coerceIn(0f,1f)
                val alpha = p.opacity * edge
                if (alpha <= .001f) continue
                val c = center + Offset(p.x,p.y) * unit
                val u = unit * .11f * p.scale
                if (prop.kind == InventedPropKind.ROD) {
                    val radians = (p.rotation - 65f) * Math.PI.toFloat() / 180f
                    val tip = c + Offset(cos(radians),sin(radians)) * u * 2.15f
                    val target = center + Offset(p.targetX,p.targetY) * unit
                    path.reset(); path.moveTo(c.x,c.y)
                    path.quadraticTo(tip.x + u*.13f, c.y - u*.65f,tip.x,tip.y)
                    drawPath(path,Color(0xFFB7783F).copy(alpha=alpha),style=Stroke(unit*.006f,cap=StrokeCap.Round))
                    drawLine(Color(0xFFE4D4AD).copy(alpha=alpha*.8f),tip,target,unit*.0018f)
                    drawOval(Color(0xFFEADCC1).copy(alpha=alpha),c-Offset(u*.19f,u*.08f),Size(u*.32f,u*.23f))
                    drawCircle(Color(0xFF715036).copy(alpha=alpha),u*.15f,c+Offset(u*.08f,0f))
                    drawCircle(Color(0xFFE9D39C).copy(alpha=alpha),u*.08f,c+Offset(u*.08f,0f))
                    continue
                }
                translate(c.x,c.y) { rotate(p.rotation,Offset.Zero) { scale(u,u,Offset.Zero) {
                    when(prop.kind) {
                        InventedPropKind.FISH -> {
                            path.reset();path.moveTo(-.8f,0f);path.lineTo(-1.15f,-.35f);path.lineTo(-1.15f,.35f);path.close()
                            drawPath(path,Color(0xFFFFB84D).copy(alpha=alpha))
                            drawOval(Color(0xFFF79F45).copy(alpha=alpha),Offset(-.8f,-.38f),Size(1.5f,.76f))
                            drawOval(Color(0xFFFFDD9B).copy(alpha=alpha*.7f),Offset(-.62f,-.28f),Size(1.05f,.27f))
                            drawCircle(Color.White.copy(alpha=alpha),.14f,Offset(.38f,-.08f))
                            drawCircle(Color(0xFF172D35).copy(alpha=alpha),.075f,Offset(.4f,-.08f))
                            drawArc(Color(0xFFB06439).copy(alpha=alpha),-75f,140f,false,Offset(-.08f,-.26f),Size(.25f,.52f),style=Stroke(.035f))
                        }
                        InventedPropKind.BOBBER -> {
                            drawLine(Color.White.copy(alpha=alpha),Offset(0f,-.5f),Offset(0f,.35f),.05f)
                            drawCircle(Color(0xFFFAF5E7).copy(alpha=alpha),.26f)
                            drawArc(Color(0xFFEE645B).copy(alpha=alpha),0f,180f,true,Offset(-.26f,-.26f),Size(.52f,.52f))
                        }
                        InventedPropKind.WATER -> repeat(3) { row ->
                            path.reset()
                            for (i in 0..24) {
                                val x=-1.8f+i*.15f;val y=row*.19f+sin(i*.65f+timeMs*.002f)*.035f
                                if(i==0)path.moveTo(x,y) else path.lineTo(x,y)
                            }
                            drawPath(path,Color(0xFF68C9D5).copy(alpha=alpha*(.55f-row*.1f)),style=Stroke(.035f,cap=StrokeCap.Round))
                        }
                        InventedPropKind.BALL -> {
                            drawCircle(Color(0xFF69BED4).copy(alpha=alpha),.65f)
                            drawCircle(Color.White.copy(alpha=alpha*.55f),.16f,Offset(-.22f,-.25f))
                        }
                        InventedPropKind.STAR -> {
                            path.reset();repeat(10) { i ->val a=i*Math.PI.toFloat()/5f-Math.PI.toFloat()/2f;val r=if(i%2==0).7f else .32f
                                if(i==0)path.moveTo(cos(a)*r,sin(a)*r) else path.lineTo(cos(a)*r,sin(a)*r) };path.close()
                            drawPath(path,Color(0xFFFFD76A).copy(alpha=alpha))
                        }
                        InventedPropKind.HEART -> {
                            path.reset();path.moveTo(0f,.64f);path.cubicTo(-1.1f,-.05f,-.5f,-.9f,0f,-.35f)
                            path.cubicTo(.5f,-.9f,1.1f,-.05f,0f,.64f);path.close()
                            drawPath(path,Color(0xFFEE8B9E).copy(alpha=alpha))
                        }
                        InventedPropKind.ROD -> Unit
                    }
                } } }
            }
        }
    }
}
