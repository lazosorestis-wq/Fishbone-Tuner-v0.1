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
import com.fishbone.tuner.audio.DrumKit
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
    var kit by remember { mutableStateOf(DrumKit.ROCK) }
    var showGrooves by remember { mutableStateOf(false) }
    var swing by remember { mutableIntStateOf(0) }
    var volume by remember { mutableFloatStateOf(0.7f) }
    var playing by remember { mutableStateOf(false) }
    var visibleBeat by remember { mutableIntStateOf(-1) }
    val tapHistory = remember { ArrayDeque<Long>() }

    val settings = RhythmSettings(mode, bpm, meter, groove, swing, volume, kit)
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
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(
                listOf(Color(0xFF180509),RhythmBackground,RhythmBackground)
            ))
            .verticalScroll(rememberScrollState())
            .padding(horizontal=17.dp,vertical=11.dp),
        horizontalAlignment=Alignment.CenterHorizontally
    ) {
        FishboneHeader("")
        Spacer(Modifier.height(15.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement=Arrangement.spacedBy(8.dp)
        ) {
            RhythmModeButton("DRUMS",mode==RhythmMode.DRUMS,Modifier.weight(1f)) {
                mode=RhythmMode.DRUMS
            }
            RhythmModeButton("METRONOME",mode==RhythmMode.METRONOME,Modifier.weight(1f)) {
                mode=RhythmMode.METRONOME
            }
        }
        Spacer(Modifier.height(13.dp))
        if(mode==RhythmMode.DRUMS) {
            // Compact preset browser like the agreed reference mockup.
            Row(
                Modifier.fillMaxWidth().height(55.dp)
                    .background(RhythmSurface,RoundedCornerShape(11.dp))
                    .border(1.dp,RhythmOutline,RoundedCornerShape(11.dp)),
                verticalAlignment=Alignment.CenterVertically
            ) {
                TextButton(onClick={
                    val i=DrumGrooves.all.indexOf(groove)
                    groove=DrumGrooves.all[(i-1+DrumGrooves.all.size)%DrumGrooves.all.size]
                    bpm=groove.suggestedBpm;swing=groove.swing
                }) {Text("‹",fontSize=24.sp,color=Color.White)}
                Column(
                    Modifier.weight(1f).clickable {showGrooves=true},
                    horizontalAlignment=Alignment.CenterHorizontally
                ) {
                    Text(groove.name,fontWeight=FontWeight.Bold,
                        fontSize=15.sp,color=Color.White,maxLines=1)
                    Text(groove.genre+"  ·  "+groove.description,
                        fontSize=10.sp,color=RhythmMuted,maxLines=1)
                }
                TextButton(onClick={
                    val i=DrumGrooves.all.indexOf(groove)
                    groove=DrumGrooves.all[(i+1)%DrumGrooves.all.size]
                    bpm=groove.suggestedBpm;swing=groove.swing
                }) {Text("›",fontSize=24.sp,color=Color.White)}
            }
            Spacer(Modifier.height(9.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement=Arrangement.spacedBy(5.dp)
            ) {
                DrumKit.entries.forEach { option ->
                    RhythmModeButton(
                        option.label.uppercase(),kit==option,Modifier.weight(1f)
                    ) {kit=option}
                }
            }
            Spacer(Modifier.height(9.dp))
        } else {
            Text("METRONOME",fontSize=12.sp,color=RhythmMuted,
                fontWeight=FontWeight.SemiBold,letterSpacing=1.8.sp)
            Spacer(Modifier.height(7.dp))
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement=Arrangement.Center,
            verticalAlignment=Alignment.CenterVertically
        ) {
            TempoCircle("−",Modifier.size(44.dp)) {bpm=(bpm-1).coerceAtLeast(40)}
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                Text("$bpm",color=Color.White,
                    fontSize=77.sp,fontWeight=FontWeight.Bold,lineHeight=81.sp)
                Text("BPM",color=RhythmMuted,fontSize=12.sp,letterSpacing=1.4.sp)
            }
            Spacer(Modifier.weight(1f))
            TempoCircle("+",Modifier.size(44.dp)) {bpm=(bpm+1).coerceAtMost(240)}
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier=Modifier.size(94.dp)
                .background(Brush.radialGradient(listOf(
                    Color(0xFF70101C),Color(0xFF200208),Color(0xFF0C090B)
                )),CircleShape)
                .border(2.dp,RhythmRed,CircleShape)
                .clickable {playing=!playing},
            contentAlignment=Alignment.Center
        ) {
            Text(if(playing)"■" else "▶",color=Color.White,
                fontSize=35.sp,fontWeight=FontWeight.Bold)
        }
        Spacer(Modifier.height(11.dp))
        Row(
            horizontalArrangement=Arrangement.spacedBy(14.dp),
            verticalAlignment=Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    val time=SystemClock.elapsedRealtime()
                    if(tapHistory.isNotEmpty() && time-tapHistory.last()>2000L) {
                        tapHistory.clear()
                    }
                    tapHistory.addLast(time)
                    while(tapHistory.size>6)tapHistory.removeFirst()
                    if(tapHistory.size>=2) {
                        val intervals=tapHistory.zipWithNext {first,second->second-first}
                            .filter {it in 250L..1500L}
                        if(intervals.isNotEmpty()) {
                            bpm=(60000.0/intervals.average()).roundToInt().coerceIn(40,240)
                        }
                    }
                },
                border=androidx.compose.foundation.BorderStroke(1.dp,RhythmRed),
                modifier=Modifier.height(40.dp),shape=RoundedCornerShape(20.dp)
            ) {
                Text("TAP TEMPO",fontSize=11.sp,color=Color.White,
                    fontWeight=FontWeight.SemiBold,letterSpacing=1.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().height(23.dp),
            horizontalArrangement=Arrangement.Center,
            verticalAlignment=Alignment.CenterVertically
        ) {
            repeat(if(mode==RhythmMode.METRONOME)meter else 4) { index ->
                Box(Modifier.padding(horizontal=6.dp)
                    .size(if(playing && visibleBeat==index)16.dp else 9.dp)
                    .background(
                        if(playing && visibleBeat==index)RhythmRed
                        else Color(0xFF524B51),CircleShape)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        if(mode==RhythmMode.METRONOME) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement=Arrangement.spacedBy(5.dp)
            ) {
                listOf(2 to "2/4",3 to "3/4",4 to "4/4",6 to "6/8").forEach { (value,label)->
                    RhythmModeButton(label,meter==value,Modifier.weight(1f)) {
                        meter=value
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if(meter==6)"6/8 · accents on beats 1 and 4"
                    else "Accent on the first beat",
                fontSize=10.sp,color=RhythmMuted
            )
        } else {
            // The rhythm timeline is deliberately compact on a phone screen.
            Column(
                Modifier.fillMaxWidth()
                    .background(Color(0xFF161518),RoundedCornerShape(11.dp))
                    .padding(12.dp)
            ) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("16-STEP PATTERN",color=Color.White,fontSize=11.sp,
                        letterSpacing=1.sp,fontWeight=FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("SWING $swing%",color=RhythmMuted,fontSize=10.sp)
                }
                Spacer(Modifier.height(10.dp))
                StepLane("KICK",groove.kick,RhythmRed)
                Spacer(Modifier.height(6.dp))
                StepLane("SNARE",groove.snare,Color(0xFFDF9F69))
                Spacer(Modifier.height(6.dp))
                StepLane("HAT",groove.hat+groove.openHat,Color(0xFF86A9C6))
                Spacer(Modifier.height(6.dp))
            }
            Slider(
                value=swing.toFloat(),onValueChange={swing=it.roundToInt()},
                valueRange=0f..45f,
                colors=SliderDefaults.colors(
                    thumbColor=RhythmRed,activeTrackColor=RhythmRed)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically
        ) {
            Text("◖",fontSize=20.sp,color=Color.White)
            Slider(
                modifier=Modifier.weight(1f).padding(horizontal=7.dp),
                value=volume,onValueChange={volume=it},
                valueRange=0f..1f,
                colors=SliderDefaults.colors(
                    thumbColor=Color.White,activeTrackColor=RhythmRed)
            )
            Text("${(volume*100).roundToInt()}%",fontSize=12.sp,color=RhythmMuted)
        }
        Text(
            if(mode==RhythmMode.DRUMS)"HUMANISED ROCK KIT · OFFLINE"
                else "PRECISE AUDIO · OFFLINE",
            fontSize=10.sp,color=RhythmMuted,letterSpacing=1.sp
        )
        Spacer(Modifier.height(18.dp))
    }

    if(showGrooves) {
        AlertDialog(
            onDismissRequest={showGrooves=false},
            title={Text("Choose a groove")},
            text={
                androidx.compose.foundation.lazy.LazyColumn(
                    Modifier.heightIn(max=440.dp)
                ) {
                    items(DrumGrooves.all.size) { index ->
                        val item=DrumGrooves.all[index]
                        Column(
                            Modifier.fillMaxWidth().clickable {
                                groove=item;bpm=item.suggestedBpm
                                swing=item.swing;showGrooves=false
                            }.padding(vertical=9.dp)
                        ) {
                            Text(item.name,fontWeight=FontWeight.SemiBold,fontSize=14.sp)
                            Text(item.genre+"  ·  "+item.suggestedBpm+" BPM",
                                fontSize=11.sp,color=RhythmMuted)
                        }
                    }
                }
            },
            confirmButton={
                TextButton(onClick={showGrooves=false}) {Text("CLOSE")}
            }
        )
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
        Text(label, color = Color.White, fontWeight = FontWeight.Bold,
            fontSize = if (label.length > 10) 9.sp else 12.sp, maxLines = 1)
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
