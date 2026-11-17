package com.fishbone.tuner

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DarkRed = Color(0xFFD61323)

/** Centered metallic emblem, matching the approved three-phone UI concept. */
@Composable
fun FishboneHeader(section: String) {
    val context = LocalContext.current
    val logo = remember(context) {
        runCatching {
            val encoded = context.assets.open("fishbone_abyss_logo.webp.b64")
                .bufferedReader().use { it.readText().trim() }
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (logo != null) {
            Image(
                bitmap = logo,
                contentDescription = "Fishbone Abyss metallic emblem",
                contentScale = ContentScale.Fit,
                modifier = Modifier.width(224.dp).height(72.dp)
            )
        } else {
            Text("FISHBONE", fontSize = 26.sp, color = Color(0xFFE0E0E0),
                fontWeight = FontWeight.Black, letterSpacing = 3.sp)
        }
        Text("T U N E R", color = DarkRed, fontSize = 12.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(9.dp)) {
            val cy = size.height / 2
            val mid = size.width / 2
            drawLine(DarkRed.copy(alpha=0.75f), Offset(0f,cy), Offset(mid-9.dp.toPx(),cy), strokeWidth=1.dp.toPx())
            drawLine(DarkRed.copy(alpha=0.75f), Offset(mid+9.dp.toPx(),cy), Offset(size.width,cy), strokeWidth=1.dp.toPx())
            val diamond = androidx.compose.ui.graphics.Path().apply {
                moveTo(mid,cy-5.dp.toPx()); lineTo(mid+5.dp.toPx(),cy);
                lineTo(mid,cy+5.dp.toPx());lineTo(mid-5.dp.toPx(),cy); close()
            }
            drawPath(diamond, DarkRed)
        }
        if (section.isNotBlank()) {
            Text(section, color=Color(0xFF73747A), fontSize=9.sp, letterSpacing=1.2.sp)
        }
    }
}
