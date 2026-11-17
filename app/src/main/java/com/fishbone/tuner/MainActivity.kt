package com.fishbone.tuner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
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
import com.fishbone.tuner.audio.Tuning
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val Background = Color(0xFF09090B)
private val Surface = Color(0xFF151416)
private val Red = Color(0xFFD91425)
private val Green = Color(0xFF62F529)
private val Muted = Color(0xFFA3A0A5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Red, background = Background, surface = Surface)) {
                FishboneApp()
            }
        }
    }
}

@Composable
private fun FishboneApp() {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        Modifier.fillMaxSize().background(Background)
            .statusBarsPadding().navigationBarsPadding()
    ) {
        Box(Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> TunerScreen(preset) { preset = it }
                1 -> RhythmScreen()
                else -> SettingsScreen(preset) { preset = it }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF080808))
                .padding(horizontal = 9.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            listOf("TUNER", "METRONOME", "SETTINGS").forEachIndexed { index, name ->
                val active = index == selectedTab
                val shape = RoundedCornerShape(10.dp)
                Column(
                    Modifier.weight(1f).height(66.dp)
                        .background(
                            if (active) Brush.verticalGradient(listOf(Color(0xFF65030C),Color(0xFF220004)))
                            else Brush.verticalGradient(listOf(Color(0xFF232225),Color(0xFF101013))),
                            shape
                        )
                        .border(1.dp, if (active) Red else Color(0xFF333338),shape)
                        .clickable { selectedTab = index },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(listOf("♩","♬","⚙")[index],
                        fontSize = 24.sp, color = if(active) Color.White else Color(0xFFD8D8D8))
                    Text(name, fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp, color = Color.White, letterSpacing = 0.3.sp)
                }
            }
        }
    }
}

@Composable
private fun TunerScreen(preset: Tuning, onTuningChange: (Tuning) -> Unit) {
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
    val animatedCents by animateFloatAsState(
        targetValue = meterCents.toFloat(),
        animationSpec = tween(durationMillis = 160),
        label = "Tuning needle"
    )

    Column(
        modifier = Modifier.fillMaxSize().background(Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FishboneHeader("")
        Spacer(Modifier.height(13.dp))
        // Larger centered note and adjacent semitones, as in the approved design.
        Box(Modifier.fillMaxWidth().height(132.dp)) {
            val name = targetMidi?.let(Tunings::name) ?: "—"
            Column(Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(name,
                    color = if (inTune) Green else Color.White,
                    fontSize = if (name.length > 3) 76.sp else 88.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 92.sp,
                    textAlign = TextAlign.Center)
                Text(
                    pitch?.let { "%.2f Hz".format(java.util.Locale.US, it) } ?: "Play a string",
                    color = Color(0xFFDFDBDF), fontSize = 17.sp)
            }
            if (targetMidi != null) {
                Text(Tunings.name(targetMidi - 1),
                    Modifier.align(Alignment.CenterStart).padding(start = 4.dp),
                    fontSize = 23.sp, color = Muted)
                Text(Tunings.name(targetMidi + 1),
                    Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
                    fontSize = 23.sp, color = Muted)
            }
        }
        TuningMeter(animatedCents.toDouble(), listening && pitch != null, inTune)
        Spacer(Modifier.height(4.dp))
        Canvas(Modifier.fillMaxWidth().height(34.dp)) {
            val y = size.height / 2f
            val mid = size.width / 2f
            drawLine(Color(0xFF3D4044), Offset(0f,y),Offset(size.width,y),1.dp.toPx())
            for (i in 0..75) {
                val x = size.width * i / 75f
                val dist = kotlin.math.abs(x-mid)/mid
                val envelope = (1f-dist).coerceAtLeast(0f)
                val amp = (kotlin.math.abs(kotlin.math.sin(i*1.4)).toFloat() *
                    kotlin.math.abs(kotlin.math.sin(i*0.37)).toFloat()) *
                    envelope * size.height * 0.44f
                drawLine(Color.White.copy(alpha=if(listening)0.75f else 0.3f),
                    Offset(x,y-amp),Offset(x,y+amp),strokeWidth=1.dp.toPx())
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                !listening -> "MICROPHONE OFF"
                cents == null -> "LISTENING…"
                inTune -> "● IN TUNE"
                cents < 0 -> "↑ TUNE UP"
                else -> "↓ TUNE DOWN"
            },
            color = if (inTune) Green else Muted,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.7.sp)
        Text(cents?.let { "%+.1f cents".format(java.util.Locale.US, it) } ?: "— cents",
            color = Color(0xFFD5D5D5), fontSize = 13.sp)

        Spacer(Modifier.height(16.dp))
        val tileShape = RoundedCornerShape(11.dp)
        Row(
            Modifier.fillMaxWidth().height(53.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFF262528),Color(0xFF121113))),tileShape)
                .border(1.dp, Color(0xFF383338),tileShape)
                .clickable { showPresets = true }.padding(horizontal=14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("♩", color = Color.White, fontSize=22.sp)
            Spacer(Modifier.width(12.dp))
            Text(preset.title, color=Color.White, fontSize=14.sp, fontWeight=FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text("⌄",color=Color.White,fontSize=22.sp)
        }

        Spacer(Modifier.height(15.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement=Arrangement.spacedBy(4.dp)
        ) {
            StringChip("AUTO", selectedString == -1, Modifier.weight(1.20f)) { selectedString = -1 }
            preset.strings.forEachIndexed { index, note ->
                StringChip(note.label, selectedString == index, Modifier.weight(1f)) {
                    selectedString = index
                }
            }
        }

        Spacer(Modifier.height(15.dp))
        OutlinedButton(
            onClick = {
                if (!hasPermission) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                else listening = !listening
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Red),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (listening) Color(0xFF2C080C) else Color(0xFF4C0911)
            )
        ) {
            Text(if (listening) "■  STOP LISTENING" else "●  START TUNING",
                color = Color.White, fontWeight = FontWeight.Bold, letterSpacing=1.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text("A4 = 440 Hz  ·  OFFLINE", color = Muted, fontSize = 10.sp)
        Spacer(Modifier.height(12.dp))
    }

    if (showPresets) {
        AlertDialog(
            onDismissRequest = { showPresets = false },
            title = { Text("Choose tuning") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(Tunings.all) { tuning ->
                        Text(tuning.title, Modifier.fillMaxWidth().clickable {
                            onTuningChange(tuning)
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
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = modifier.height(40.dp)
            .background(if (selected) Color(0xFF4A070D) else Surface, shape)
            .border(1.dp, if (selected) Red else Color(0xFF343338), shape)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = if (selected) Color.White else Muted,
            fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun TuningMeter(cents: Double, active: Boolean, inTune: Boolean) {
    Canvas(modifier = Modifier.fillMaxWidth().height(176.dp)) {
        val cx = size.width / 2f
        val cy = size.height * 0.91f
        val radius = minOf(size.width * 0.46f, size.height * 0.88f)
        val radiusSize = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
        val arcOrigin = Offset(cx-radius,cy-radius)
        drawArc(Color(0xFF77787E), 190f, 160f, false,
            arcOrigin, radiusSize, style=Stroke(width=1.5.dp.toPx()))
        drawArc(Red.copy(alpha=0.9f),190f,7f,false,
            arcOrigin,radiusSize,style=Stroke(width=3.dp.toPx()))
        drawArc(Red.copy(alpha=0.9f),343f,7f,false,
            arcOrigin,radiusSize,style=Stroke(width=3.dp.toPx()))
        drawArc(Green.copy(alpha=0.7f),266f,8f,false,
            arcOrigin,radiusSize,style=Stroke(width=4.dp.toPx()))
        for (i in -10..10) {
            val deg = 270 + i*8.0
            val radians = deg*PI/180.0
            val dir = Offset(cos(radians).toFloat(),sin(radians).toFloat())
            val outer = radius-2.dp.toPx()
            val inner = outer-(if (i%5==0) 12.dp.toPx() else 7.dp.toPx())
            val color = if(i==0) Green else Color(0xFFD0CDD0)
            drawLine(color,
                Offset(cx+dir.x*inner,cy+dir.y*inner),
                Offset(cx+dir.x*outer,cy+dir.y*outer),
                strokeWidth= if(i%5==0) 1.7.dp.toPx() else 1.dp.toPx())
        }
        // Needle: dim when stopped, fluorescent green when detecting pitch.
        val radians = (270.0+cents.coerceIn(-50.0,50.0)/50.0*80.0)*PI/180.0
        val end = Offset(
            cx+cos(radians).toFloat()*(radius-24.dp.toPx()),
            cy+sin(radians).toFloat()*(radius-24.dp.toPx()))
        val needle = if(active) Green else Color(0xFF69816A)
        drawLine(needle.copy(alpha=0.22f),Offset(cx,cy),end,
            strokeWidth=11.dp.toPx(),cap=StrokeCap.Round)
        drawLine(needle,Offset(cx,cy),end,
            strokeWidth=3.dp.toPx(),cap=StrokeCap.Round)
        drawCircle(needle, radius=7.dp.toPx(),center=Offset(cx,cy),
            style=Stroke(width=2.dp.toPx()))
        for (n in listOf(-50,-25,0,25,50)) {
            val r = (270.0+n/50.0*80.0)*PI/180.0
            val tx = cx+cos(r).toFloat()*(radius+16.dp.toPx())
            val ty = cy+sin(r).toFloat()*(radius+16.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText(
                if(n>0) "+$n" else n.toString(), tx,ty,
                android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color=android.graphics.Color.LTGRAY
                    textAlign=android.graphics.Paint.Align.CENTER
                    textSize=12.sp.toPx()
                })
        }
    }
}
