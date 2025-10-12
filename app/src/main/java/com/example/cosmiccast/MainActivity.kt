package com.example.cosmiccast

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.cosmiccast.ui.theme.CosmicCastTheme

class MainActivity : ComponentActivity() {

    private var isServiceRunning by mutableStateOf(false)
    private var stats by mutableStateOf("Not Connected")
    private var streamVolume by mutableStateOf(1.0f)

    private val statsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            stats = intent?.getStringExtra(AudioCaptureService.EXTRA_STATS) ?: ""
            isServiceRunning = AudioCaptureService.isRunning
        }
    }

    private val mediaProjectionManager by lazy { getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager }

    private val startMediaProjection = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            val serviceIntent = Intent(this, AudioCaptureService::class.java).apply {
                putExtra(AudioCaptureService.EXTRA_RESULT_CODE, it.resultCode)
                putExtra(AudioCaptureService.EXTRA_DATA, it.data)
                val sharedPrefs = getSharedPreferences("Settings", Context.MODE_PRIVATE)
                putExtra("SERVER_IP", sharedPrefs.getString("SERVER_IP", "192.168.1.100"))
                putExtra("SERVER_PORT", sharedPrefs.getInt("SERVER_PORT", 8888))
                putExtra("BITRATE", sharedPrefs.getInt("BITRATE", 128000))
                putExtra("SAMPLE_RATE", sharedPrefs.getInt("SAMPLE_RATE", 44100))
                putExtra("CHANNEL_CONFIG", sharedPrefs.getString("CHANNEL_CONFIG", "Mono"))
                putExtra(AudioCaptureService.EXTRA_VOLUME, streamVolume)
            }
            startForegroundService(serviceIntent)
            isServiceRunning = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedPrefs = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        streamVolume = sharedPrefs.getFloat("STREAM_VOLUME", 1.0f)
        isServiceRunning = AudioCaptureService.isRunning

        setContent {
            val theme = sharedPrefs.getString("THEME", "System") ?: "System"

            CosmicCastTheme(darkTheme = when(theme) {
                "Light" -> false
                "Dark" -> true
                else -> isSystemInDarkTheme()
            }) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    val context = LocalContext.current
                    NavHost(navController = navController, startDestination = "main") {
                        composable("main") { MainScreen(navController, isServiceRunning, stats, streamVolume, {
                            newVolume ->
                            streamVolume = newVolume
                            val intent = Intent(AudioCaptureService.ACTION_SET_VOLUME)
                            intent.putExtra(AudioCaptureService.EXTRA_VOLUME, newVolume)
                            LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
                            with(sharedPrefs.edit()) {
                                putFloat("STREAM_VOLUME", newVolume)
                                apply()
                            }
                        }) { startStopService() } }
                        composable("settings") { SettingsScreen(navController, sharedPrefs) }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isServiceRunning = AudioCaptureService.isRunning
        LocalBroadcastManager.getInstance(this).registerReceiver(statsReceiver, IntentFilter(AudioCaptureService.ACTION_STATS))
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(statsReceiver)
    }

    private fun startStopService() {
        if (isServiceRunning) {
            stopService(Intent(this, AudioCaptureService::class.java))
            isServiceRunning = false
            stats = "Not Connected"
        } else {
            startMediaProjection.launch(mediaProjectionManager.createScreenCaptureIntent())
        }
    }
}

@Composable
fun MainScreen(
    navController: NavController,
    isServiceRunning: Boolean,
    stats: String,
    currentVolume: Float,
    onVolumeChange: (Float) -> Unit,
    onStartStopClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = stats)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onStartStopClick) {
            Text(text = if (isServiceRunning) "Stop Streaming" else "Start Streaming")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { navController.navigate("settings") }) {
            Text(text = "Settings")
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(text = "Volume")
        Slider(
            value = currentVolume,
            onValueChange = { onVolumeChange(it) },
            valueRange = 0f..1f,
            modifier = Modifier.width(280.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, sharedPreferences: SharedPreferences) {
    var ipAddress by remember { mutableStateOf(sharedPreferences.getString("SERVER_IP", "192.168.1.100") ?: "") }
    var port by remember { mutableStateOf(sharedPreferences.getInt("SERVER_PORT", 8888).toString()) }

    val bitrates = listOf(96000, 128000, 192000, 256000, 320000)
    var expandedBitrate by remember { mutableStateOf(false) }
    var selectedBitrate by remember { mutableStateOf(sharedPreferences.getInt("BITRATE", 128000)) }

    val sampleRates = listOf(22050, 44100, 48000)
    var expandedSampleRate by remember { mutableStateOf(false) }
    var selectedSampleRate by remember { mutableStateOf(sharedPreferences.getInt("SAMPLE_RATE", 44100)) }

    val channelConfigs = listOf("Mono", "Stereo")
    var expandedChannelConfig by remember { mutableStateOf(false) }
    var selectedChannelConfig by remember { mutableStateOf(sharedPreferences.getString("CHANNEL_CONFIG", "Mono") ?: "Mono") }

    val themes = listOf("Light", "Dark", "System")
    var expandedTheme by remember { mutableStateOf(false) }
    var selectedTheme by remember { mutableStateOf(sharedPreferences.getString("THEME", "System") ?: "System") }

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(value = ipAddress, onValueChange = { ipAddress = it }, label = { Text("Server IP Address") })
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Server Port") })
        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.width(280.dp)) {
            ExposedDropdownMenuBox(expanded = expandedBitrate, onExpandedChange = { expandedBitrate = !expandedBitrate }) {
                OutlinedTextField(
                    value = "${selectedBitrate / 1000} kbps",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Bitrate") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedBitrate) },
                    modifier = Modifier.menuAnchor()
                )
                ExposedDropdownMenu(expanded = expandedBitrate, onDismissRequest = { expandedBitrate = false }) {
                    bitrates.forEach { bitrate ->
                        DropdownMenuItem(
                            text = { Text("${bitrate / 1000} kbps") },
                            onClick = {
                                selectedBitrate = bitrate
                                expandedBitrate = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.width(280.dp)) {
            ExposedDropdownMenuBox(expanded = expandedSampleRate, onExpandedChange = { expandedSampleRate = !expandedSampleRate }) {
                OutlinedTextField(
                    value = "$selectedSampleRate Hz",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Sample Rate") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSampleRate) },
                    modifier = Modifier.menuAnchor()
                )
                ExposedDropdownMenu(expanded = expandedSampleRate, onDismissRequest = { expandedSampleRate = false }) {
                    sampleRates.forEach { sampleRate ->
                        DropdownMenuItem(
                            text = { Text("$sampleRate Hz") },
                            onClick = {
                                selectedSampleRate = sampleRate
                                expandedSampleRate = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.width(280.dp)) {
            ExposedDropdownMenuBox(expanded = expandedChannelConfig, onExpandedChange = { expandedChannelConfig = !expandedChannelConfig }) {
                OutlinedTextField(
                    value = selectedChannelConfig,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Channels") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedChannelConfig) },
                    modifier = Modifier.menuAnchor()
                )
                ExposedDropdownMenu(expanded = expandedChannelConfig, onDismissRequest = { expandedChannelConfig = false }) {
                    channelConfigs.forEach { channelConfig ->
                        DropdownMenuItem(
                            text = { Text(channelConfig) },
                            onClick = {
                                selectedChannelConfig = channelConfig
                                expandedChannelConfig = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.width(280.dp)) {
            ExposedDropdownMenuBox(expanded = expandedTheme, onExpandedChange = { expandedTheme = !expandedTheme }) {
                OutlinedTextField(
                    value = selectedTheme,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Theme") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTheme) },
                    modifier = Modifier.menuAnchor()
                )
                ExposedDropdownMenu(expanded = expandedTheme, onDismissRequest = { expandedTheme = false }) {
                    themes.forEach { theme ->
                        DropdownMenuItem(
                            text = { Text(theme) },
                            onClick = {
                                selectedTheme = theme
                                expandedTheme = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            with(sharedPreferences.edit()) {
                putString("SERVER_IP", ipAddress)
                putInt("SERVER_PORT", port.toInt())
                putInt("BITRATE", selectedBitrate)
                putInt("SAMPLE_RATE", selectedSampleRate)
                putString("CHANNEL_CONFIG", selectedChannelConfig)
                putString("THEME", selectedTheme)
                apply()
            }
            navController.popBackStack()
        }) {
            Text(text = "Save")
        }

        DisposableEffect(Unit) {
            onDispose {
                (context as? MainActivity)?.recreate()
            }
        }
    }
}
