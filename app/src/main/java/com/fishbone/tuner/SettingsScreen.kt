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

private val SettingsRed = Color(0xFFD91425)
private val SettingsGray = Color(0xFFA8A5A9)

@Composable
fun SettingsScreen(preset: Tuning, onTuningChange: (Tuning) -> Unit) {
    var showTunings by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(Color(0xFF08080A))
            .verticalScroll(rememberScrollState())
            .padding(horizontal=17.dp,vertical=16.dp),
        horizontalAlignment=Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("‹",fontSize=29.sp,color=Color.White)
            Spacer(Modifier.weight(1f))
            Text("Settings",fontSize=23.sp,color=Color.White,fontWeight=FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(26.dp))
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(SettingsRed.copy(alpha=0.60f)))
        Spacer(Modifier.height(21.dp))
        SettingsEntry("♩", "Tuning", preset.title) { showTunings = true }
        SettingsEntry("⌁", "Reference Pitch", "A4 = 440 Hz")
        SettingsEntry("♙", "Input", "Microphone · Automatic")
        SettingsEntry("◉", "Theme", "Fishbone Dark")
        SettingsEntry("A", "Notation", "A B C D E F G")
        SettingsEntry("ⓘ", "About", "Fishbone Tuner v0.4.1 · Offline")
        Spacer(Modifier.height(24.dp))
        FishboneHeader("")
        Spacer(Modifier.height(12.dp))
        Text("Designed for guitar and bass",color=SettingsGray,fontSize=12.sp)
    }
    if(showTunings) {
        AlertDialog(
            onDismissRequest={showTunings=false},
            title={Text("Choose tuning")},
            text={
                LazyColumn(Modifier.heightIn(max=410.dp)) {
                    items(Tunings.all) { tuning ->
                        Text(tuning.title,Modifier.fillMaxWidth().clickable {
                            onTuningChange(tuning);showTunings=false
                        }.padding(vertical=12.dp),fontSize=15.sp)
                    }
                }
            },
            confirmButton={TextButton(onClick={showTunings=false}) {Text("CLOSE")}}
        )
    }
}

@Composable
private fun SettingsEntry(icon: String, label: String, description: String, onClick: (() -> Unit)? = null) {
    val shape=RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().padding(bottom=7.dp)
            .background(
                Brush.verticalGradient(listOf(Color(0xFF222124),Color(0xFF121214))),shape
            )
            .border(1.dp,Color(0xFF343034),shape)
            .then(if (onClick != null) Modifier.clickable {onClick()} else Modifier)
            .padding(horizontal=13.dp,vertical=12.dp),
        verticalAlignment=Alignment.CenterVertically
    ) {
        Box(Modifier.width(36.dp),contentAlignment=Alignment.CenterStart) {
            Text(icon,color=Color.White,fontSize=23.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(label,fontWeight=FontWeight.SemiBold,color=Color.White,fontSize=14.sp)
            Text(description,color=SettingsGray,fontSize=12.sp)
        }
        if(onClick != null)Text("›",fontSize=25.sp,color=SettingsGray)
    }
}
