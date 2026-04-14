package com.telytec.roadwords

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.telytec.roadwords.ui.*

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "RoadWords necesita el micrófono para funcionar", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on while using the app (driving mode)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Request microphone permission properly
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent {
            RoadWordsTheme {
                val state by viewModel.state.collectAsState()
                when (state.screen) {
                    Screen.DASHBOARD -> DashboardScreen(
                        state = state,
                        onStart = { viewModel.startDriving() },
                        onStats = { viewModel.showStats() },
                        onToggleLevel = { viewModel.toggleLevel(it) }
                    )
                    Screen.SESSION -> DriveSessionScreen(
                        state = state,
                        onStop = { viewModel.stopDriving() }
                    )
                    Screen.STATS -> StatsScreen(
                        state = state,
                        onBack = { viewModel.backToDashboard() }
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.onPause()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }
}
