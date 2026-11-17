package com.fishbone.tuner

import android.os.SystemClock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
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

private val RhythmBackground = Color(0xFF09090B)
private val RhythmSurface = Color(0xFF1C1B1E)
private val RhythmRed = Color(0xFFD91425)
private val RhythmMuted = Color(0xFFA4A1A4)
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 17.dp, vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FishboneHeader("")
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            RhythmModeButton("METRONOME",mode==RhythmMode.METRONOME,Modifier.weight(1f)) {
                mode=RhythmMode.METRONOME
            }
            RhythmModeButton("DRUM MACHINE",mode==RhythmMode.DRUMS,Modifier.weight(1f)) {
                mode=RhythmMode.DRUMS
            }
        }
        Spacer(Modifier.height(19.dp))
        Text(
            if(mode==RhythmMode.METRONOME) "♩  Metronome" else "♬  Drum Machine",
            color=Color.White,fontSize=19.sp,fontWeight=FontWeight.Medium
        )
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement=Arrangement.Center,
            verticalAlignment=Alignment.CenterVertically
        ) {
            TempoCircle("−",Modifier.size(44.dp)) { bpm=(bpm-1).coerceAtLeast(40) }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                Text("$bpm",color=Color.White,fontSize=84.sp,
                    fontWeight=FontWeight.Bold,lineHeight=88.sp)
                Text("BPM",color=Color.White,fontSize=14.sp,letterSpacing=1.4.sp)
            }
            Spacer(Modifier.weight(1f))
            TempoCircle("+",Modifier.size(44.dp)) { bpm=(bpm+1).coerceAtMost(240) }
        }
        Spacer(Modifier.height(16.dp))
        Box(
            modifier=Modifier.size(94.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF5E0810),Color(0xFF150408),Color.Black)
                    ),CircleShape
                )
                .border(2.dp,RhythmRed,CircleShape)
                .clickable { playing=!playing },
            contentAlignment=Alignment.Center
        ) {
            Text(if(playing)"■" else "▶",
                color=Color.White,fontSize=37.sp,fontWeight=FontWeight.Bold)
        }
        Spacer(Modifier.height(15.dp))
        OutlinedButton(
            onClick = {
                val time = SystemClock.elapsedRealtime()
                if (tapHistory.isNotEmpty() && time - tapHistory.last() > 2000L) {
                    tapHistory.clear()
                }
                tapHistory.addLast(time)
                while (tapHistory.size > 6) tapHistory.removeFirst()
                if (tapHistory.size >= 2) {
                    val intervals = tapHistory.zipWithNext { first, second -> second-first }
                        .filter { it in 250L..1500L }
                    if (intervals.isNotEmpty()) {
                        bpm=(60_000.0/intervals.average()).roundToInt().coerceIn(40,240)
                    }
                }
            },
            modifier=Modifier.height(42.dp),
            shape=RoundedCornerShape(20.dp),
            border=androidx.compose.foundation.BorderStroke(1.dp,RhythmRed)
        ) {
            Text("TAP TEMPO",fontWeight=FontWeight.SemiBold,
                color=Color.White,fontSize=12.sp,letterSpacing=1.sp)
        }
        Spacer(Modifier.height(13.dp))
        Row(
            Modifier.fillMaxWidth().height(25.dp),
            horizontalArrangement=Arrangement.Center,
            verticalAlignment=Alignment.CenterVertically
        ) {
            repeat(if (mode==RhythmMode.METRONOME) meter else 4) { index ->
                Box(
                    Modifier.padding(horizontal=5.dp)
                        .size(if(playing && index==visibleBeat) 16.dp else 10.dp)
                        .background(
                            if(playing && index==visibleBeat) RhythmRed
                                else Color(0xFF464149),CircleShape
                        )
                )
            }
        }
        Spacer(Modifier.height(15.dp))
        if(mode==RhythmMode.METRONOME) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                listOf(2 to "2/4",3 to "3/4",4 to "4/4",6 to "6/8").forEach { (value,label) ->
                    RhythmModeButton(label,meter==value,Modifier.weight(1f)) {
                        meter=value
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if(meter==6)"6/8: Eighth-note pulses, accents on 1 and 4"
                    else "Accent on the first beat",
                fontSize=11.sp,color=RhythmMuted
            )
        } else {
            Spacer(Modifier.height(4.dp))
            Text("CHOOSE A GROOVE",Modifier.fillMaxWidth(),color=RhythmMuted,
                fontSize=11.sp,letterSpacing=1.5.sp)
            Spacer(Modifier.height(9.dp))
            DrumGrooves.all.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    pair.forEach { item ->
                        GrooveCard(item,groove==item,Modifier.weight(1f)) {
                            groove=item;bpm=item.suggestedBpm;swing=item.swing
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier.fillMaxWidth()
                    .background(RhythmSurface,RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(groove.name.uppercase(),color=Color.White,fontSize=14.sp,
                    fontWeight=FontWeight.Bold)
                Text(groove.description,color=RhythmMuted,fontSize=12.sp)
                Spacer(Modifier.height(12.dp))
                StepLane("KICK",groove.kick,RhythmRed)
                Spacer(Modifier.height(7.dp))
                StepLane("SNARE",groove.snare,Color(0xFFE6AF6B))
                Spacer(Modifier.height(7.dp))
                StepLane("HAT",groove.hat+groove.openHat,Color(0xFF7BB5D5))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("SWING",color=RhythmMuted,fontSize=12.sp)
                Spacer(Modifier.weight(1f))
                Text("$swing%",color=Color.White,fontSize=13.sp)
            }
            Slider(value=swing.toFloat(),onValueChange={swing=it.roundToInt()},
                valueRange=0f..45f,
                colors=SliderDefaults.colors(thumbColor=RhythmRed,activeTrackColor=RhythmRed))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("♪",fontSize=19.sp,color=Color.White)
            Slider(
                modifier=Modifier.weight(1f).padding(horizontal=9.dp),
                value=volume,onValueChange={volume=it},valueRange=0f..1f,
                colors=SliderDefaults.colors(thumbColor=Color.White,activeTrackColor=RhythmRed)
            )
            Text("◖",fontSize=19.sp,color=Color.White)
        }
        Text("100% OFFLINE",color=RhythmMuted,fontSize=10.sp,letterSpacing=1.sp)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun TempoCircle(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.background(Color(0xFF29292D),CircleShape)
            .border(1.dp,Color(0xFF666266),CircleShape)
            .clickable(onClick=onClick),
        contentAlignment=Alignment.Center
    ) {
        Text(label,color=Color.White,fontSize=26.sp)
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
