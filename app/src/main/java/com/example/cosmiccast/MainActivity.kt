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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ServerProfile(
    val name: String,
    val ipAddress: String,
    val port: Int,
    val bitrate: Int,
    val sampleRate: Int,
    val channelConfig: String
)

class MainActivity : ComponentActivity() {

    private var isServiceRunning by mutableStateOf(false)
    private var stats by mutableStateOf("Not Connected")
    private var streamVolume by mutableStateOf(1.0f)
    private var profiles by mutableStateOf<List<ServerProfile>>(emptyList())
    private var selectedProfile by mutableStateOf<ServerProfile?>(null)

    private val statsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            stats = intent?.getStringExtra(AudioCaptureService.EXTRA_STATS) ?: ""
            isServiceRunning = AudioCaptureService.isRunning
        }
    }

    private val mediaProjectionManager by lazy { getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager }

    private val startMediaProjection = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            selectedProfile?.let {
                profile ->
                val serviceIntent = Intent(this, AudioCaptureService::class.java).apply {
                    putExtra(AudioCaptureService.EXTRA_RESULT_CODE, it.resultCode)
                    putExtra(AudioCaptureService.EXTRA_DATA, it.data)
                    putExtra("SERVER_IP", profile.ipAddress)
                    putExtra("SERVER_PORT", profile.port)
                    putExtra("BITRATE", profile.bitrate)
                    putExtra("SAMPLE_RATE", profile.sampleRate)
                    putExtra("CHANNEL_CONFIG", profile.channelConfig)
                    putExtra(AudioCaptureService.EXTRA_VOLUME, streamVolume)
                }
                startForegroundService(serviceIntent)
                isServiceRunning = true
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadProfiles()

        setContent {
            val sharedPrefs = getSharedPreferences("Settings", Context.MODE_PRIVATE)
            val theme = sharedPrefs.getString("THEME", "System") ?: "System"

            CosmicCastTheme(darkTheme = when (theme) {
                "Light" -> false
                "Dark" -> true
                else -> isSystemInDarkTheme()
            }) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "main") {
                        composable("main") { MainScreen(navController, isServiceRunning, stats, streamVolume, profiles, selectedProfile, {
                            newVolume ->
                            streamVolume = newVolume
                            val intent = Intent(AudioCaptureService.ACTION_SET_VOLUME)
                            intent.putExtra(AudioCaptureService.EXTRA_VOLUME, newVolume)
                            LocalBroadcastManager.getInstance(this@MainActivity).sendBroadcast(intent)
                            with(sharedPrefs.edit()) {
                                putFloat("STREAM_VOLUME", newVolume)
                                apply()
                            }
                        }, { profile -> selectedProfile = profile }) { startStopService() } }
                        composable("settings") { SettingsScreen(navController) }
                        composable("profile_settings") { ProfileSettingsScreen(navController, profiles) { newProfiles -> saveProfiles(newProfiles) } }
                        composable("app_settings") { AppSettingsScreen(navController, sharedPrefs) }
                    }
                }
            }
        }
    }

    private fun loadProfiles() {
        val sharedPrefs = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        streamVolume = sharedPrefs.getFloat("STREAM_VOLUME", 1.0f)
        val profilesJson = sharedPrefs.getString("PROFILES", null)
        if (profilesJson != null) {
            profiles = Json.decodeFromString(profilesJson)
            selectedProfile = profiles.firstOrNull()
        } else {
            profiles = listOf(ServerProfile("Default", "192.168.1.100", 8888, 128000, 44100, "Mono"))
            selectedProfile = profiles.first()
        }
    }

    private fun saveProfiles(newProfiles: List<ServerProfile>) {
        profiles = newProfiles
        val sharedPrefs = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val profilesJson = Json.encodeToString(newProfiles)
        with(sharedPrefs.edit()) {
            putString("PROFILES", profilesJson)
            apply()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavController,
    isServiceRunning: Boolean,
    stats: String,
    currentVolume: Float,
    profiles: List<ServerProfile>,
    selectedProfile: ServerProfile?,
    onVolumeChange: (Float) -> Unit,
    onProfileSelected: (ServerProfile) -> Unit,
    onStartStopClick: () -> Unit
) {
    var expandedProfile by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = stats)
        Spacer(modifier = Modifier.height(16.dp))

        if (profiles.isNotEmpty()) {
            Box(modifier = Modifier.width(280.dp)) {
                ExposedDropdownMenuBox(expanded = expandedProfile, onExpandedChange = { expandedProfile = !expandedProfile }) {
                    OutlinedTextField(
                        value = selectedProfile?.name ?: "Select a Profile",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Server Profile") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProfile) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expandedProfile, onDismissRequest = { expandedProfile = false }) {
                        profiles.forEach { profile ->
                            DropdownMenuItem(
                                text = { Text(profile.name) },
                                onClick = {
                                    onProfileSelected(profile)
                                    expandedProfile = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onStartStopClick, enabled = selectedProfile != null) {
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

@Composable
fun SettingsScreen(navController: NavController) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = { navController.navigate("profile_settings") }) {
            Text(text = "Manage Server Profiles")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { navController.navigate("app_settings") }) {
            Text(text = "App Settings")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(navController: NavController, sharedPreferences: SharedPreferences) {
    val themes = listOf("Light", "Dark", "System")
    var expandedTheme by remember { mutableStateOf(false) }
    var selectedTheme by remember { mutableStateOf(sharedPreferences.getString("THEME", "System") ?: "System") }
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
                putString("THEME", selectedTheme)
                apply()
            }
            (context as? MainActivity)?.recreate()
        }) {
            Text(text = "Save")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsScreen(
    navController: NavController,
    profiles: List<ServerProfile>,
    onSave: (List<ServerProfile>) -> Unit
) {
    var editingProfiles by remember { mutableStateOf(profiles) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val newProfileName = "New Profile ${editingProfiles.size + 1}"
                editingProfiles = editingProfiles + ServerProfile(newProfileName, "", 8888, 128000, 44100, "Mono")
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Profile")
            }
        }
    ) {
        LazyColumn(modifier = Modifier.padding(it)) {
            items(editingProfiles, key = { it.name }) { profile ->
                ProfileEditor(profile, onProfileChange = { updatedProfile ->
                    editingProfiles = editingProfiles.map { p ->
                        if (p.name == profile.name) updatedProfile else p
                    }
                }, onDelete = {
                    editingProfiles = editingProfiles.filter { p -> p.name != profile.name }
                })
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onSave(editingProfiles)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditor(
    profile: ServerProfile,
    onProfileChange: (ServerProfile) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(profile.name) }
    var ipAddress by remember { mutableStateOf(profile.ipAddress) }
    var port by remember { mutableStateOf(profile.port.toString()) }
    var bitrate by remember { mutableStateOf(profile.bitrate) }
    var sampleRate by remember { mutableStateOf(profile.sampleRate) }
    var channelConfig by remember { mutableStateOf(profile.channelConfig) }

    LaunchedEffect(name, ipAddress, port, bitrate, sampleRate, channelConfig) {
        onProfileChange(ServerProfile(name, ipAddress, port.toIntOrNull() ?: 8888, bitrate, sampleRate, channelConfig))
    }

    Card(modifier = Modifier.padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name") },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Profile")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = ipAddress, onValueChange = { ipAddress = it }, label = { Text("Server IP Address") })
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Server Port") })

            val bitrates = listOf(96000, 128000, 192000, 256000, 320000)
            var expandedBitrate by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth()) {
                ExposedDropdownMenuBox(expanded = expandedBitrate, onExpandedChange = { expandedBitrate = !expandedBitrate }) {
                    OutlinedTextField(
                        value = "${bitrate / 1000} kbps",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Bitrate") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedBitrate) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expandedBitrate, onDismissRequest = { expandedBitrate = false }) {
                        bitrates.forEach { b ->
                            DropdownMenuItem(text = { Text("${b / 1000} kbps") }, onClick = {
                                bitrate = b
                                expandedBitrate = false
                            })
                        }
                    }
                }
            }

            val sampleRates = listOf(22050, 44100, 48000)
            var expandedSampleRate by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth()) {
                ExposedDropdownMenuBox(expanded = expandedSampleRate, onExpandedChange = { expandedSampleRate = !expandedSampleRate }) {
                    OutlinedTextField(
                        value = "$sampleRate Hz",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Sample Rate") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSampleRate) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expandedSampleRate, onDismissRequest = { expandedSampleRate = false }) {
                        sampleRates.forEach { sr ->
                            DropdownMenuItem(text = { Text("$sr Hz") }, onClick = {
                                sampleRate = sr
                                expandedSampleRate = false
                            })
                        }
                    }
                }
            }

            val channelConfigs = listOf("Mono", "Stereo")
            var expandedChannelConfig by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth()) {
                ExposedDropdownMenuBox(expanded = expandedChannelConfig, onExpandedChange = { expandedChannelConfig = !expandedChannelConfig }) {
                    OutlinedTextField(
                        value = channelConfig,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Channels") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedChannelConfig) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expandedChannelConfig, onDismissRequest = { expandedChannelConfig = false }) {
                        channelConfigs.forEach { cc ->
                            DropdownMenuItem(text = { Text(cc) }, onClick = {
                                channelConfig = cc
                                expandedChannelConfig = false
                            })
                        }
                    }
                }
            }
        }
    }
}
