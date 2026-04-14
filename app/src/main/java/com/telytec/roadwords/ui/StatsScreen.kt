package com.telytec.roadwords.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telytec.roadwords.data.WordWithProgress

@Composable
fun StatsScreen(state: AppState, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Text("←", fontSize = 22.sp, color = Color.White)
            }
            Text("Estadísticas", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Summary cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                label = "Total", 
                value = "${state.totalWords}", 
                color = AccentBlue, 
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Aprendidas", 
                value = "${state.learnedWords}", 
                color = NeonGreen, 
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Pendientes", 
                value = "${state.totalWords - state.learnedWords}", 
                color = ErrorRed, 
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Progress bar
        val progress = if (state.totalWords > 0) state.learnedWords.toFloat() / state.totalWords else 0f
        Column {
            Text("Progreso general: ${(progress * 100).toInt()}%", fontSize = 13.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = NeonGreen,
                trackColor = MaterialTheme.colorScheme.surface,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Word list
        Text("Todas las palabras", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(state.wordList) { word ->
                WordRow(word)
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Black, color = color)
            Text(label, fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
fun WordRow(word: WordWithProgress) {
    val enLevel = word.enToEsLevel ?: 0
    val esLevel = word.esToEnLevel ?: 0
    val statusColor = when {
        enLevel >= 3 && esLevel >= 2 -> NeonGreen
        enLevel > 0 || esLevel > 0 -> Color(0xFFFF9800)
        else -> Color.Gray
    }
    val lvlColor = when (word.cefrLevel) { "A1" -> Color(0xFF03A9F4); "A2" -> Color(0xFF00BCD4); "B1" -> Color(0xFF4CAF50); "B2" -> Color(0xFFFF9800); "C1" -> Color(0xFFE91E63); else -> Color(0xFF9C27B0) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(word.english, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    if (word.isPhrasalVerb) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PV", fontSize = 10.sp, color = NeonGreen, fontWeight = FontWeight.Bold)
                    }
                }
                Text(word.spanish, fontSize = 13.sp, color = Color.Gray)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(word.cefrLevel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = lvlColor)
                Spacer(modifier = Modifier.height(2.dp))
                Row {
                    Text("$enLevel", fontSize = 11.sp, color = statusColor)
                    Text(" / ", fontSize = 11.sp, color = Color.Gray)
                    Text("$esLevel", fontSize = 11.sp, color = statusColor)
                }
            }
        }
    }
}
