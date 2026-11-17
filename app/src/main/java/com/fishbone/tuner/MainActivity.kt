package com.fishbone.tuner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fishbone.tuner.audio.TunerEngine
import com.fishbone.tuner.audio.Tunings
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val Background = Color(0xFF101114)
private val Surface = Color(0xFF1A1C21)
private val Red = Color(0xFFED444D)
private val Green = Color(0xFF60D694)
private val Muted = Color(0xFF9A9CA5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Red, background = Background, surface = Surface)) {
                TunerScreen()
            }
        }
    }
}

@Composable
private fun TunerScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var listening by remember { mutableStateOf(false) }
    var pitch by remember { mutableStateOf<Double?>(null) }
    var preset by remember { mutableStateOf(Tunings.all.first()) }
    var showPresets by remember { mutableStateOf(false) }
    var selectedString by remember { mutableIntStateOf(-1) } // -1 = chromatic/automatic
    val engine = remember { TunerEngine(scope) { pitch = it } }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        hasPermission = allowed
        if (allowed) listening = true
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) listening = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(listening, hasPermission) {
        if (listening && hasPermission) engine.start() else engine.stop()
        onDispose { engine.stop() }
    }

    // Manual mode compares against the chosen string; automatic mode chooses nearest chromatic note.
    val targetMidi = if (selectedString >= 0) preset.strings[selectedString].midi else pitch?.let(Tunings::nearestMidi)
    val cents = if (pitch != null && targetMidi != null) Tunings.cents(pitch!!, targetMidi) else null
    val meterCents = cents?.coerceIn(-50.0, 50.0) ?: 0.0
    val inTune = cents != null && abs(cents) <= 3.0

    Column(
        modifier = Modifier.fillMaxSize().background(Background).statusBarsPadding().navigationBarsPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("◢", fontSize = 28.sp, color = Red, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(10.dp))
            Text("FISHBONE", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            Text("TUNER · v0.2", color = Muted, fontSize = 12.sp, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(26.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("TUNING", fontSize = 11.sp, color = Muted, letterSpacing = 2.sp)
                Text(preset.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = { showPresets = true }) { Text("CHANGE  ▾", color = Red) }
        }
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.fillMaxWidth().weight(1f).background(Surface, RoundedCornerShape(22.dp)).padding(18.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(if (selectedString >= 0) "MANUAL STRING" else "CHROMATIC / AUTO", color = Muted, fontSize = 12.sp, letterSpacing = 1.6.sp)
            Spacer(Modifier.height(14.dp))
            Text(
                targetMidi?.let(Tunings::name) ?: "—",
                color = if (inTune) Green else Color.White,
                fontSize = 67.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(pitch?.let { "%.2f Hz".format(java.util.Locale.US, it) } ?: "Play a string", fontSize = 17.sp, color = Muted)
            if (targetMidi != null) Text("TARGET  %.2f Hz".format(java.util.Locale.US, Tunings.freq(targetMidi)), fontSize = 11.sp, color = Muted, letterSpacing = 1.sp)
            Spacer(Modifier.height(22.dp))
            TuningMeter(meterCents, listening && pitch != null, inTune)
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    !listening -> "MICROPHONE OFF"
                    cents == null -> "LISTENING…"
                    inTune -> "● IN TUNE"
                    cents < 0 -> "↑ TUNE UP"
                    else -> "↓ TUNE DOWN"
                },
                color = if (inTune) Green else Muted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Text(cents?.let { "%+.1f cents".format(java.util.Locale.US, it) } ?: "— cents", color = Color.White, fontSize = 18.sp)
        }
        Spacer(Modifier.height(18.dp))
        Text("SELECT STRING", Modifier.fillMaxWidth(), color = Muted, fontSize = 11.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            StringChip("AUTO", selectedString == -1, Modifier.weight(1.25f)) { selectedString = -1 }
            preset.strings.forEachIndexed { index, note ->
                StringChip(note.label, selectedString == index, Modifier.weight(1f)) { selectedString = index }
            }
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                if (!hasPermission) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                else listening = !listening
            },
            colors = ButtonDefaults.buttonColors(containerColor = if (listening) Color(0xFF33363F) else Red),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(if (listening) "■   STOP LISTENING" else "●   START TUNING", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text("A4 = 440 Hz  •  Offline audio processing", color = Muted, fontSize = 11.sp)
    }

    if (showPresets) {
        AlertDialog(
            onDismissRequest = { showPresets = false },
            title = { Text("Choose tuning") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(Tunings.all) { tuning ->
                        Text(tuning.title, Modifier.fillMaxWidth().clickable {
                            preset = tuning
                            selectedString = -1
                            showPresets = false
                        }.padding(vertical = 13.dp), fontSize = 15.sp)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPresets = false }) { Text("CLOSE") } }
        )
    }
}

@Composable
private fun StringChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.height(46.dp)
            .background(if (selected) Red else Surface, RoundedCornerShape(9.dp))
            .border(1.dp, if (selected) Red else Color(0xFF33353A), RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun TuningMeter(cents: Double, active: Boolean, inTune: Boolean) {
    Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
        val cx = size.width / 2f
        val cy = size.height * 0.88f
        val radius = minOf(size.width * 0.43f, size.height * 0.78f)
        drawArc(Color(0xFF42454D), startAngle = 180f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(cx - radius, cy - radius), size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round))
        drawArc(Green, startAngle = 262f, sweepAngle = 16f, useCenter = false,
            topLeft = Offset(cx - radius, cy - radius), size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round))
        for (i in -10..10) {
            val angle = Math.toRadians((270.0 + i * 9.0))
            val outer = radius - 18.dp.toPx()
            val inner = outer - if (i % 5 == 0) 13.dp.toPx() else 7.dp.toPx()
            val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            drawLine(if (i == 0) Green else Muted, Offset(cx + direction.x * inner, cy + direction.y * inner),
                Offset(cx + direction.x * outer, cy + direction.y * outer), strokeWidth = if (i % 5 == 0) 2.dp.toPx() else 1.dp.toPx())
        }
        val angle = (270 + cents / 50 * 90) * PI / 180.0
        drawLine(if (active && inTune) Green else if (active) Red else Muted,
            start = Offset(cx, cy),
            end = Offset(cx + cos(angle).toFloat() * (radius - 30.dp.toPx()), cy + sin(angle).toFloat() * (radius - 30.dp.toPx())),
            strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(if (active) Red else Muted, radius = 6.dp.toPx(), center = Offset(cx, cy))
    }
}
