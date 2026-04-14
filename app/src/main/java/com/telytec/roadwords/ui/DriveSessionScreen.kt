package com.telytec.roadwords.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

@Composable
fun DriveSessionScreen(state: AppState, onStop: () -> Unit) {
    val dirLabel = if (state.direction == "en_to_es") "EN → ES" else "ES → EN"
    val promptWord = if (state.direction == "en_to_es") state.currentWord?.english else state.currentWord?.spanish

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // ── HEADER ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mazo progress (primary)
            Column {
                Text(
                    "Mazo ${state.mazoNumber}",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White
                )
                Text(
                    "${state.mazoGraduated} / ${state.mazoSize}",
                    fontSize = 14.sp, color = NeonGreen
                )
            }

            // Mazo dots (visual progress)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (i in 0 until state.mazoSize) {
                    val dotColor = if (i < state.mazoGraduated) NeonGreen else Color.Gray.copy(alpha = 0.3f)
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
            }

            // Global progress (secondary)
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${state.learnedWords} / ${state.totalWords}",
                    fontSize = 12.sp, color = Color.Gray
                )
                Text("Total", fontSize = 10.sp, color = Color.Gray.copy(alpha = 0.6f))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── MAZO COMPLETED CELEBRATION ──
        if (state.mazoJustCompleted) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFFFD700).copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🏆", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "¡Mazo Completado!",
                        fontSize = 24.sp, fontWeight = FontWeight.Black,
                        color = Color(0xFFFFD700), textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${state.mazoSize} palabras graduadas",
                        fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        } else {
            // ── WORD CARD ── (only show when not celebrating)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Direction badge + review badge
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = RoundedCornerShape(6.dp), color = AccentBlue.copy(alpha = 0.15f)) {
                            Text(dirLabel, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentBlue)
                        }

                        state.currentWord?.frequencyRank?.takeIf { it > 0 }?.let { rank ->
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(shape = RoundedCornerShape(6.dp), color = Color.White.copy(alpha = 0.15f)) {
                                Text("#$rank", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }

                        if (state.currentWord?.isPhrasalVerb == true) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFE91E63).copy(alpha = 0.15f)) {
                                Text("PV", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE91E63))
                            }
                        }

                        if (state.isReviewWord) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFFFD700).copy(alpha = 0.15f)) {
                                Text("REPASO", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // The word — BIG and central
                    Text(
                        promptWord ?: "...",
                        fontSize = 32.sp, fontWeight = FontWeight.Bold,
                        color = Color.White, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Minimal progress for this word
                    state.currentProgress?.let { p ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val enDone = p.enToEsLevel >= 3
                            val esDone = p.esToEnLevel >= 2
                            ProgressDots("EN→ES", p.enToEsLevel, 3, enDone)
                            Spacer(modifier = Modifier.width(20.dp))
                            ProgressDots("ES→EN", p.esToEnLevel, 2, esDone)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── FEEDBACK ──
            if (state.feedbackMsg.isNotEmpty()) {
                val isGraduated = state.feedbackMsg.contains("Graduada") || state.feedbackMsg.contains("completado")
                val bgColor = when {
                    isGraduated -> Color(0xFFFFD700).copy(alpha = 0.15f)
                    state.isCorrect == true -> NeonGreen.copy(alpha = 0.1f)
                    else -> ErrorRed.copy(alpha = 0.1f)
                }
                val textColor = when {
                    isGraduated -> Color(0xFFFFD700)
                    state.isCorrect == true -> NeonGreen
                    else -> ErrorRed
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = bgColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.feedbackMsg, fontSize = if (isGraduated) 22.sp else 18.sp,
                            fontWeight = FontWeight.Bold, color = textColor, textAlign = TextAlign.Center)
                        if (state.isCorrect == false) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Correcto: ${state.correctAnswer}", fontSize = 16.sp,
                                color = Color.White, textAlign = TextAlign.Center)
                        }
                        if (state.exampleSentence.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(state.exampleSentence, fontSize = 16.sp, 
                                color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ── MIC AREA ──
            val micBgColor = when {
                !state.isListening -> Color.Transparent
                state.isSpeechDetected || state.transcript.isNotEmpty() -> AccentBlue.copy(alpha = 0.08f)
                state.volumeLevel > 0.1f -> NeonGreen.copy(alpha = 0.04f)
                else -> Color.Transparent
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = micBgColor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Transcript
                    if (state.transcript.isNotEmpty()) {
                        Text("\"${state.transcript}\"", fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                            color = AccentBlue, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Bars visualizer
                    AudioBarsVisualizer(
                        isListening = state.isListening,
                        volumeLevel = state.volumeLevel,
                        isSpeechDetected = state.isSpeechDetected || state.transcript.isNotEmpty()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Status text with icon
                    val (statusText, statusColor) = when {
                        !state.isListening -> Pair("⏸  Esperando...", Color.Gray)
                        state.isSpeechDetected || state.transcript.isNotEmpty() -> Pair("🗣  Te escucho...", AccentBlue)
                        state.volumeLevel > 0.1f -> Pair("🔊  Ruido detectado", NeonGreen.copy(alpha = 0.6f))
                        else -> Pair("🎙  Escuchando...", NeonGreen.copy(alpha = 0.5f))
                    }
                    Text(statusText, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = statusColor)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onStop,
            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Terminar Viaje", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ErrorRed)
        }
    }
}

@Composable
fun ProgressDots(label: String, current: Int, max: Int, complete: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 10.sp, color = Color.Gray, modifier = Modifier.width(48.dp))
        Spacer(modifier = Modifier.width(4.dp))
        for (i in 0 until max) {
            val color = when {
                complete -> NeonGreen
                i < current -> AccentBlue
                else -> Color.Gray.copy(alpha = 0.3f)
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            if (i < max - 1) Spacer(modifier = Modifier.width(3.dp))
        }
    }
}

@Composable
fun AudioBarsVisualizer(isListening: Boolean, volumeLevel: Float, isSpeechDetected: Boolean) {
    val barCount = 7
    val maxBarHeight = 48f
    val minBarHeight = 4f

    val infiniteTransition = rememberInfiniteTransition(label = "bars")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(maxBarHeight.dp)
    ) {
        for (i in 0 until barCount) {
            val barFraction = if (!isListening) {
                0f
            } else if (volumeLevel < 0.05f) {
                val wave = kotlin.math.sin(phase + i * 0.9f).toFloat()
                0.15f + 0.1f * wave.coerceIn(-1f, 1f)
            } else {
                val variation = kotlin.math.sin(phase * 2f + i * 1.2f).toFloat() * 0.15f
                (volumeLevel + variation).coerceIn(0.1f, 1f)
            }

            val targetHeight = minBarHeight + (maxBarHeight - minBarHeight) * barFraction
            val animatedHeight by animateFloatAsState(
                targetValue = targetHeight,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "barHeight$i"
            )

            val barColor = when {
                !isListening -> Color.Gray.copy(alpha = 0.3f)
                isSpeechDetected -> AccentBlue
                volumeLevel > 0.05f -> NeonGreen.copy(alpha = 0.4f)
                else -> NeonGreen.copy(alpha = 0.2f)
            }

            Box(
                modifier = Modifier
                    .width(7.dp)
                    .height(animatedHeight.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor)
            )
        }
    }
}

