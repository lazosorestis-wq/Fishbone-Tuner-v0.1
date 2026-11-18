package com.fishbone.tuner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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

private val HeaderRed = Color(0xFFE01124)

/**
 * Silhouette-mask the genuine metallic emblem from the older banner art.
 * The dark red honeycomb is NEVER drawn: transparent pixels replace the grille.
 * Coordinates correspond to the 374 x 136 banner embedded in the APK.
 */
private fun isolateFishbone(source: Bitmap): Bitmap {
    val w = source.width
    val h = source.height
    val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val maskCanvas = AndroidCanvas(mask)
    maskCanvas.save()
    maskCanvas.scale(w / 374f, h / 136f)

    val ink = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = AndroidPaint.Style.FILL
    }
    fun blade(vararg points: Pair<Float, Float>) {
        val path = AndroidPath()
        path.moveTo(points[0].first, points[0].second)
        for (i in 1 until points.size) path.lineTo(points[i].first, points[i].second)
        path.close()
        maskCanvas.drawPath(path, ink)
    }
    fun rib(l: Float, t: Float, r: Float, b: Float) {
        maskCanvas.drawRect(l, t, r, b, ink)
    }
    blade(14f to 72f, 113f to 12f, 114f to 135f)
    rib(129f, 31f, 147f, 135f)
    rib(164f, 32f, 181f, 135f)
    rib(207f, 31f, 224f, 135f)
    rib(241f, 32f, 260f, 135f)
    blade(135f to 70f, 166f to 70f, 168f to 81f, 210f to 81f,
        210f to 70f, 249f to 70f, 249f to 89f, 210f to 89f,
        210f to 94f, 168f to 94f, 163f to 89f, 134f to 89f)
    blade(260f to 80f, 365f to 31f, 343f to 53f,
        331f to 73f, 328f to 81f, 333f to 97f,
        346f to 112f, 365f to 132f)
    maskCanvas.restore()

    val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val target = AndroidCanvas(output)
    // Lift dark metal highlights without changing the original steel detailing.
    val lift = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
            1.30f,0f,0f,0f,9f,
            0f,1.30f,0f,0f,9f,
            0f,0f,1.30f,0f,9f,
            0f,0f,0f,1f,0f
        )))
    }
    target.drawBitmap(source, 0f, 0f, lift)
    target.drawBitmap(mask, 0f, 0f, AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    })
    mask.recycle()
    return output
}

/** Transparent metallic emblem, restrained red title and divider. */
@Composable
fun FishboneHeader(section: String = "", compact: Boolean = false) {
    val context = LocalContext.current
    val logo = remember(context) {
        runCatching {
            val encoded = context.assets.open("fishbone_abyss_logo.webp.b64")
                .bufferedReader().use { it.readText().trim() }
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            raw?.let(::isolateFishbone)?.asImageBitmap()
        }.getOrNull()
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (logo != null) {
            Image(
                bitmap = logo,
                contentDescription = "Metallic Fishbone emblem without background",
                modifier = Modifier.width(if (compact) 175.dp else 218.dp)
                    .height(if (compact) 59.dp else 77.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Text("FISHBONE", fontSize = 24.sp, color = Color(0xFFDCDCDD),
                fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        }
        Text("T U N E R", color = HeaderRed, fontSize = 12.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 2.5.sp)
        Spacer(Modifier.height(if (compact) 3.dp else 7.dp))
        Canvas(Modifier.fillMaxWidth().height(7.dp)) {
            val cy = size.height / 2
            val center = size.width / 2
            drawLine(HeaderRed.copy(alpha = 0.65f), Offset(0f,cy),
                Offset(center-8.dp.toPx(),cy), strokeWidth = 1.dp.toPx())
            drawLine(HeaderRed.copy(alpha = 0.65f), Offset(center+8.dp.toPx(),cy),
                Offset(size.width,cy), strokeWidth = 1.dp.toPx())
            val diamond = androidx.compose.ui.graphics.Path().apply {
                moveTo(center,cy-4.8.dp.toPx())
                lineTo(center+4.8.dp.toPx(),cy)
                lineTo(center,cy+4.8.dp.toPx())
                lineTo(center-4.8.dp.toPx(),cy)
                close()
            }
            drawPath(diamond, HeaderRed)
        }
        if (section.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(section.uppercase(), color = Color(0xFFB5AFB3),
                fontSize = 10.sp, letterSpacing = 1.5.sp)
        }
    }
}
