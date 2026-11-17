package com.fishbone.tuner

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Faithful cropped artwork from the supplied Fishbone Abyss logo. */
@Composable
fun FishboneHeader(section: String) {
    val context = LocalContext.current
    val logo = remember(context) {
        runCatching {
            val text = context.assets.open("fishbone_abyss_logo.webp.b64")
                .bufferedReader().use { it.readText() }
            val bytes = Base64.decode(text.trim(), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
        if (logo != null) {
            Image(
                bitmap = logo,
                contentDescription = "Fishbone Abyss metallic logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.width(170.dp).height(64.dp)
            )
        } else {
            Text("FISHBONE", fontWeight = FontWeight.Black, color = Color.White,
                fontSize = 19.sp, letterSpacing = 1.sp)
        }
        Spacer(Modifier.weight(1f))
        Text(section, fontSize = 12.sp, color = Color(0xFFADB0B8),
            letterSpacing = 1.sp, fontWeight = FontWeight.Medium)
    }
}
