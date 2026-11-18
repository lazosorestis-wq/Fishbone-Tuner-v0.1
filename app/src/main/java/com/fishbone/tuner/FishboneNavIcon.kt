package com.fishbone.tuner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Small hand-drawn monochrome icons: no emoji, matching the guitar-tool aesthetic. */
@Composable
fun FishboneNavIcon(destination: Int, color: Color) {
    Canvas(Modifier.size(28.dp)) {
        val s=size.minDimension
        val cx=size.width/2f
        val cy=size.height/2f
        val stroke=2.1.dp.toPx()
        when(destination) {
            0 -> { // tuning fork
                drawLine(color,Offset(cx-s*.24f,cy-s*.39f),
                    Offset(cx-s*.24f,cy+s*.03f),stroke,cap=StrokeCap.Round)
                drawLine(color,Offset(cx+s*.24f,cy-s*.39f),
                    Offset(cx+s*.24f,cy+s*.03f),stroke,cap=StrokeCap.Round)
                drawArc(color,0f,180f,false,
                    Offset(cx-s*.24f,cy-s*.24f),
                    androidx.compose.ui.geometry.Size(s*.48f,s*.48f),
                    style=Stroke(width=stroke,cap=StrokeCap.Round))
                drawLine(color,Offset(cx,cy+s*.03f),
                    Offset(cx,cy+s*.44f),stroke,cap=StrokeCap.Round)
            }
            1 -> { // metronome / rhythm
                val p=androidx.compose.ui.graphics.Path().apply {
                    moveTo(cx-s*.32f,cy+s*.34f)
                    lineTo(cx-s*.08f,cy-s*.31f)
                    lineTo(cx+s*.08f,cy-s*.31f)
                    lineTo(cx+s*.32f,cy+s*.34f)
                    close()
                }
                drawPath(p,color,style=Stroke(width=stroke))
                drawLine(color,Offset(cx,cy+s*.17f),
                    Offset(cx+s*.18f,cy-s*.30f),stroke,cap=StrokeCap.Round)
                drawCircle(color,radius=2.dp.toPx(),
                    center=Offset(cx+s*.18f,cy-s*.30f))
            }
            else -> { // tuning / settings controls
                for(i in 0..2) {
                    val y=cy+(i-1)*s*.23f
                    drawLine(color,Offset(cx-s*.34f,y),
                        Offset(cx+s*.34f,y),stroke,cap=StrokeCap.Round)
                    val knobX=cx+((i%2)*2-1)*s*.13f
                    drawCircle(Color(0xFF111014),radius=4.dp.toPx(),
                        center=Offset(knobX,y))
                    drawCircle(color,radius=4.dp.toPx(),center=Offset(knobX,y),
                        style=Stroke(width=1.7.dp.toPx()))
                }
            }
        }
    }
}
