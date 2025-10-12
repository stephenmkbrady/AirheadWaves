
package com.example.cosmiccast

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.cosmiccast.ui.theme.CosmicCastTheme

class MainActivity : ComponentActivity() {

    private var isServiceRunning by mutableStateOf(false)

    private val mediaProjectionManager by lazy { getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager }

    private val startMediaProjection = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            val serviceIntent = Intent(this, AudioCaptureService::class.java).apply {
                putExtra(AudioCaptureService.EXTRA_RESULT_CODE, it.resultCode)
                putExtra(AudioCaptureService.EXTRA_DATA, it.data)
            }
            startForegroundService(serviceIntent)
            isServiceRunning = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CosmicCastTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen(isServiceRunning) { startStopService() }
                }
            }
        }
    }

    private fun startStopService() {
        if (isServiceRunning) {
            stopService(Intent(this, AudioCaptureService::class.java))
            isServiceRunning = false
        } else {
            startMediaProjection.launch(mediaProjectionManager.createScreenCaptureIntent())
        }
    }
}

@Composable
fun MainScreen(isServiceRunning: Boolean, onStartStopClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = onStartStopClick) {
            Text(text = if (isServiceRunning) "Stop Streaming" else "Start Streaming")
        }
    }
}
