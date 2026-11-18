package com.fishbone.tuner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fishbone.tuner.audio.Tuning
import com.fishbone.tuner.audio.Tunings
import kotlin.math.roundToInt

private val SettingsRed = Color(0xFFDD1325)
private val SettingsGray = Color(0xFFAAA8AE)

@Composable
fun SettingsScreen(
    preset: Tuning,
    onTuningChange: (Tuning) -> Unit,
    referencePitch: Int,
    onReferencePitchChange: (Int) -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit
) {
    var showTunings by remember { mutableStateOf(false) }
    var showReference by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF150508),
                Color(0xFF08080A),Color(0xFF0A090B))))
            .verticalScroll(rememberScrollState())
            .padding(horizontal=17.dp,vertical=16.dp),
        horizontalAlignment=Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("SETTINGS",fontSize=23.sp,color=Color.White,
                fontWeight=FontWeight.Bold,letterSpacing=1.sp)
            Spacer(Modifier.weight(1f))
            Text("v0.5",fontSize=12.sp,color=SettingsGray)
        }
        Spacer(Modifier.height(9.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(SettingsRed.copy(alpha=0.65f)))
        Spacer(Modifier.height(18.dp))

        Text("INSTRUMENT",Modifier.fillMaxWidth(),fontSize=10.sp,
            letterSpacing=1.8.sp,color=SettingsGray)
        Spacer(Modifier.height(9.dp))
        SettingsEntry("♩", "Tuning", preset.title, onClick = { showTunings=true })
        SettingsEntry("⌁", "Reference Pitch", "A4 = $referencePitch Hz",
            onClick = { showReference=true })
        SettingsEntry("♩", "Audio Input", "Built-in microphone · Automatic")

        Spacer(Modifier.height(13.dp))
        Text("PREFERENCES",Modifier.fillMaxWidth(),fontSize=10.sp,
            letterSpacing=1.8.sp,color=SettingsGray)
        Spacer(Modifier.height(9.dp))
        SettingsEntry("◉", "Appearance", "Fishbone Dark · Metallic Red")
        SettingsEntry("A", "Note Names", "Scientific notation · E2, A2, D3…")
        SettingsEntry("☼", "Keep Screen On", "Stay awake while practising",
            switchValue=keepScreenOn, onSwitch={onKeepScreenOnChange(it)})

        Spacer(Modifier.height(13.dp))
        Text("ABOUT",Modifier.fillMaxWidth(),fontSize=10.sp,
            letterSpacing=1.8.sp,color=SettingsGray)
        Spacer(Modifier.height(9.dp))
        SettingsEntry("ⓘ", "Fishbone Tuner", "v0.5.0 · 100% offline · Guitar & bass")
        Spacer(Modifier.height(25.dp))
        FishboneHeader("")
        Spacer(Modifier.height(16.dp))
        Text("Made for heavy riffs & honest tone",color=SettingsGray,fontSize=11.sp)
    }

    if(showTunings) {
        AlertDialog(
            onDismissRequest={showTunings=false},
            title={Text("Choose your tuning")},
            text={
                LazyColumn(Modifier.heightIn(max=400.dp)) {
                    items(Tunings.all) { tuning ->
                        Text(tuning.title,
                            Modifier.fillMaxWidth().clickable {
                                onTuningChange(tuning)
                                showTunings=false
                            }.padding(vertical=12.dp),fontSize=15.sp)
                    }
                }
            },
            confirmButton={TextButton(onClick={showTunings=false}) {
                Text("CLOSE")
            }}
        )
    }
    if(showReference) {
        AlertDialog(
            onDismissRequest={showReference=false},
            title={Text("Reference Pitch")},
            text={
                Column(horizontalAlignment=Alignment.CenterHorizontally) {
                    Text("A4 = $referencePitch Hz",fontSize=23.sp,
                        fontWeight=FontWeight.Bold,color=SettingsRed)
                    Spacer(Modifier.height(11.dp))
                    Slider(
                        value=referencePitch.toFloat(),
                        onValueChange={onReferencePitchChange(it.roundToInt())},
                        valueRange=415f..466f,
                        steps=50,
                        colors=SliderDefaults.colors(
                            thumbColor=SettingsRed,activeTrackColor=SettingsRed)
                    )
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text("415 Hz",fontSize=11.sp)
                        Text("466 Hz",fontSize=11.sp)
                    }
                    TextButton(onClick={onReferencePitchChange(440)}) {
                        Text("RESET TO 440 Hz",color=SettingsRed)
                    }
                }
            },
            confirmButton={TextButton(onClick={showReference=false}) {
                Text("DONE")
            }}
        )
    }
}

@Composable
private fun SettingsEntry(
    icon: String,
    label: String,
    description: String,
    onClick: (() -> Unit)? = null,
    switchValue: Boolean? = null,
    onSwitch: ((Boolean) -> Unit)? = null
) {
    val shape=RoundedCornerShape(11.dp)
    val modifier=Modifier.fillMaxWidth().padding(bottom=7.dp)
        .background(Brush.verticalGradient(listOf(
            Color(0xFF242325),Color(0xFF161518))),shape)
        .border(1.dp,Color(0xFF373136),shape)
        .then(
            when {
                onClick!=null -> Modifier.clickable(onClick=onClick)
                onSwitch!=null && switchValue!=null -> Modifier.clickable {
                    onSwitch(!switchValue)
                }
                else -> Modifier
            }
        )
        .padding(horizontal=12.dp,vertical=11.dp)
    Row(modifier,verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.width(38.dp),contentAlignment=Alignment.CenterStart) {
            Text(icon,color=Color.White,fontSize=21.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(label,fontWeight=FontWeight.SemiBold,
                color=Color.White,fontSize=14.sp)
            Text(description,color=SettingsGray,fontSize=11.sp)
        }
        if (switchValue!=null && onSwitch!=null) {
            Switch(
                checked=switchValue,
                onCheckedChange=onSwitch,
                colors=SwitchDefaults.colors(
                    checkedThumbColor=Color.White,
                    checkedTrackColor=SettingsRed
                )
            )
        } else if(onClick != null) {
            Text("›",fontSize=24.sp,color=SettingsGray)
        }
    }
}
