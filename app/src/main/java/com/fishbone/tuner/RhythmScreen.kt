package com.fishbone.tuner

import android.os.SystemClock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fishbone.tuner.audio.DrumGroove
import com.fishbone.tuner.audio.DrumGrooves
import com.fishbone.tuner.audio.RhythmEngine
import com.fishbone.tuner.audio.RhythmMode
import com.fishbone.tuner.audio.RhythmSettings
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val RhythmBackground = Color(0xFF101114)
private val RhythmSurface = Color(0xFF1A1C21)
private val RhythmRed = Color(0xFFED444D)
private val RhythmMuted = Color(0xFF9A9CA5)
private val RhythmOutline = Color(0xFF33363F)

@Composable
fun RhythmScreen() {
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val engine = remember { RhythmEngine(scope) }
    var mode by remember { mutableStateOf(RhythmMode.METRONOME) }
    var bpm by remember { mutableIntStateOf(100) }
    var meter by remember { mutableIntStateOf(4) }
    var groove by remember { mutableStateOf(DrumGrooves.all.first()) }
    var swing by remember { mutableIntStateOf(0) }
    var volume by remember { mutableFloatStateOf(0.7f) }
    var playing by remember { mutableStateOf(false) }
    var visibleBeat by remember { mutableIntStateOf(-1) }
    val tapHistory = remember { ArrayDeque<Long>() }

    val settings = RhythmSettings(mode, bpm, meter, groove, swing, volume)
    LaunchedEffect(settings) { engine.update(settings) }
    DisposableEffect(playing) {
        if (playing) engine.start(settings) else engine.stop()
        onDispose { engine.stop() }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) playing = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            engine.stop()
        }
    }

    // A display-only pulse; audio timing remains driven by AudioTrack.
    LaunchedEffect(playing, bpm, meter, mode) {
        if (!playing) {
            visibleBeat = -1
        } else {
            var index = 0
            val beats = if (mode == RhythmMode.METRONOME) meter else 4
            while (true) {
                visibleBeat = index % beats
                delay((60_000.0 / bpm).toLong())
                index++
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(RhythmBackground)
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FishboneHeader("RHYTHM · v0.4")
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RhythmModeButton("METRONOME", mode == RhythmMode.METRONOME, Modifier.weight(1f)) {
                mode = RhythmMode.METRONOME
            }
            RhythmModeButton("DRUM MACHINE", mode == RhythmMode.DRUMS, Modifier.weight(1f)) {
                mode = RhythmMode.DRUMS
            }
        }
        Spacer(Modifier.height(18.dp))

        Column(
            Modifier.fillMaxWidth().background(RhythmSurface, RoundedCornerShape(20.dp)).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("TEMPO", color = RhythmMuted, letterSpacing = 2.sp, fontSize = 12.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { bpm = (bpm - 5).coerceAtLeast(40) }) {
                    Text("−5", color = RhythmRed, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Spacer(Modifier.weight(1f))
                Text("$bpm", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 52.sp)
                Spacer(Modifier.width(6.dp))
                Text("BPM", color = RhythmMuted, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { bpm = (bpm + 5).coerceAtMost(240) }) {
                    Text("+5", color = RhythmRed, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
            Slider(
                value = bpm.toFloat(), onValueChange = { bpm = it.roundToInt() },
                valueRange = 40f..240f,
                colors = SliderDefaults.colors(thumbColor = RhythmRed, activeTrackColor = RhythmRed)
            )
            OutlinedButton(
                onClick = {
                    val time = SystemClock.elapsedRealtime()
                    if (tapHistory.isNotEmpty() && time - tapHistory.last() > 2000L) {
                        tapHistory.clear()
                    }
                    tapHistory.addLast(time)
                    while (tapHistory.size > 6) tapHistory.removeFirst()
                    if (tapHistory.size >= 2) {
                        val intervals = tapHistory.zipWithNext { a, b -> b - a }
                            .filter { it in 250L..1500L }
                        if (intervals.isNotEmpty()) {
                            bpm = (60_000.0 / intervals.average()).roundToInt().coerceIn(40, 240)
                        }
                    }
                },
                border = androidx.compose.foundation.BorderStroke(1.dp, RhythmRed),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                shape = RoundedCornerShape(11.dp)
            ) { Text("TAP TEMPO", color = RhythmRed, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
            Text("40–240 BPM · Changes take effect at the next bar",
                fontSize = 11.sp, color = RhythmMuted)
        }

        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(28.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(if (mode == RhythmMode.METRONOME) meter else 4) { index ->
                val active = playing && index == visibleBeat
                Box(
                    Modifier.padding(horizontal = 6.dp).size(if (active) 18.dp else 11.dp)
                        .background(
                            if (active) RhythmRed else RhythmOutline,
                            CircleShape
                        )
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        if (mode == RhythmMode.METRONOME) {
            Text("TIME SIGNATURE", Modifier.fillMaxWidth(),
                fontSize = 11.sp, letterSpacing = 1.6.sp, color = RhythmMuted)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3 to "3/4", 4 to "4/4", 6 to "6/8").forEach { (value, text) ->
                    RhythmModeButton(text, meter == value, Modifier.weight(1f)) {
                        meter = value
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (meter == 6) "6/8: BPM counts eighth-note pulses; accents on 1 and 4"
                else "First beat accented",
                modifier = Modifier.fillMaxWidth(),
                color = RhythmMuted, fontSize = 11.sp
            )
        } else {
            Text("CHOOSE A GROOVE", Modifier.fillMaxWidth(),
                fontSize = 11.sp, letterSpacing = 1.6.sp, color = RhythmMuted)
            Spacer(Modifier.height(10.dp))
            DrumGrooves.all.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { item ->
                        GrooveCard(item, groove == item, Modifier.weight(1f)) {
                            groove = item
                            bpm = item.suggestedBpm
                            swing = item.swing
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(6.dp))
            Column(Modifier.fillMaxWidth().background(RhythmSurface, RoundedCornerShape(16.dp)).padding(14.dp)) {
                Text(groove.name.uppercase(), color = Color.White,
                    fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(groove.description, color = RhythmMuted, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                StepLane("KICK", groove.kick, RhythmRed)
                Spacer(Modifier.height(6.dp))
                StepLane("SNARE", groove.snare, Color(0xFFEEAD63))
                Spacer(Modifier.height(6.dp))
                StepLane("HAT", groove.hat + groove.openHat, Color(0xFF76A8D5))
                Spacer(Modifier.height(10.dp))
                Text("16-step pattern · repeated automatically",
                    fontSize = 11.sp, color = RhythmMuted)
            }
            Spacer(Modifier.height(15.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("SWING", color = RhythmMuted, fontSize = 11.sp, letterSpacing = 1.6.sp)
                Spacer(Modifier.weight(1f))
                Text("$swing%", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Slider(value = swing.toFloat(), onValueChange = { swing = it.roundToInt() },
                valueRange = 0f..45f,
                colors = SliderDefaults.colors(thumbColor = RhythmRed, activeTrackColor = RhythmRed))
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("VOLUME", color = RhythmMuted, fontSize = 11.sp, letterSpacing = 1.6.sp)
            Spacer(Modifier.weight(1f))
            Text("${(volume * 100).roundToInt()}%", color = Color.White, fontSize = 14.sp)
        }
        Slider(value = volume, onValueChange = { volume = it }, valueRange = 0f..1f,
            colors = SliderDefaults.colors(thumbColor = RhythmRed, activeTrackColor = RhythmRed))
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { playing = !playing },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (playing) RhythmOutline else RhythmRed)
        ) {
            Text(if (playing) "■   STOP" else "▶   PLAY",
                fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text("Offline drum sounds · Audio stops when leaving this screen",
            color = RhythmMuted, fontSize = 11.sp)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun RhythmModeButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.background(if (selected) RhythmRed else RhythmSurface, RoundedCornerShape(12.dp))
            .border(1.dp, if (selected) RhythmRed else RhythmOutline, RoundedCornerShape(12.dp))
            .height(48.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun GrooveCard(groove: DrumGroove, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.background(if (selected) Color(0xFF4A252A) else RhythmSurface, RoundedCornerShape(12.dp))
            .border(1.dp, if (selected) RhythmRed else RhythmOutline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(12.dp)
    ) {
        Text(groove.genre, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RhythmRed)
        Spacer(Modifier.height(4.dp))
        Text(groove.name, color = Color.White, fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1)
        Spacer(Modifier.height(3.dp))
        Text("${groove.suggestedBpm} BPM", color = RhythmMuted, fontSize = 11.sp)
    }
}

@Composable
private fun StepLane(title: String, hits: Set<Int>, activeColor: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.width(53.dp), color = RhythmMuted, fontSize = 10.sp)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(16) { step ->
                val active = step in hits
                Box(
                    Modifier.weight(1f).height(17.dp)
                        .background(
                            if (active) activeColor else if (step % 4 == 0) RhythmOutline else Color(0xFF262830),
                            RoundedCornerShape(3.dp)
                        )
                )
            }
        }
    }
}
