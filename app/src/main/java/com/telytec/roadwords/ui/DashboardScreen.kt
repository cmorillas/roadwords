package com.telytec.roadwords.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DashboardScreen(state: AppState, onStart: () -> Unit, onStats: () -> Unit, onToggleLevel: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                "ROADWORDS",
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 4.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text("Vocabulario al Volante", fontSize = 14.sp, color = Color.Gray)

            Spacer(modifier = Modifier.height(40.dp))

            // ── Level Selector ──
            Text("Nivel de vocabulario", fontSize = 14.sp, color = Color.LightGray)
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Fila 1
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("A1", "A2", "B1").forEach { level ->
                        LevelChip(level, state, onToggleLevel)
                    }
                }
                // Fila 2
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("B2", "C1", "C2").forEach { level ->
                        LevelChip(level, state, onToggleLevel)
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // ── Start Button ──
            Button(
                onClick = onStart,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Text(
                    "▶  Empezar",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeepSpace
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Stats Button ──
            OutlinedButton(
                onClick = onStats,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    "📊  Estadísticas",
                    fontSize = 16.sp,
                    color = Color.LightGray
                )
            }
        }
    }
}

@Composable
fun LevelChip(level: String, state: AppState, onToggleLevel: (String) -> Unit) {
    val selected = state.selectedLevels.contains(level)
    val color = when (level) {
        "A1" -> Color(0xFF03A9F4)
        "A2" -> Color(0xFF00BCD4)
        "B1" -> Color(0xFF4CAF50)
        "B2" -> Color(0xFFFF9800)
        "C1" -> Color(0xFFE91E63)
        else -> Color(0xFF9C27B0)
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) color.copy(alpha = 0.2f) else Color.Transparent,
        modifier = Modifier
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) color else Color.Gray.copy(alpha = 0.3f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onToggleLevel(level) }
    ) {
        Text(
            level,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) color else Color.Gray
        )
    }
}
