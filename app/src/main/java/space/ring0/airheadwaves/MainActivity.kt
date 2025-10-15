package space.ring0.airheadwaves

import android.content.Intent
import android.content.SharedPreferences
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import space.ring0.airheadwaves.ui.theme.AirheadWavesTheme
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ServerProfile(
    val id: String,
    val name: String,
    val ipAddress: String,
    val port: Int,
    val bitrate: Int,
    val sampleRate: Int,
    val channelConfig: String,
    val bass: Float = 0f,
    val treble: Float = 0f
)

class MainActivity : ComponentActivity() {

    private val mediaProjectionManager by lazy { getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager }
    private lateinit var viewModel: MainViewModel

    private val startMediaProjection = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            viewModel.selectedProfile.value?.let { profile ->
                val serviceIntent = Intent(this, AudioCaptureService::class.java).apply {
                    putExtra(AudioCaptureService.EXTRA_RESULT_CODE, it.resultCode)
                    putExtra(AudioCaptureService.EXTRA_DATA, it.data)
                    putExtra("SERVER_IP", profile.ipAddress)
                    putExtra("SERVER_PORT", profile.port)
                    putExtra("BITRATE", profile.bitrate)
                    putExtra("SAMPLE_RATE", profile.sampleRate)
                    putExtra("CHANNEL_CONFIG", profile.channelConfig)
                    putExtra("BASS", profile.bass)
                    putExtra("TREBLE", profile.treble)
                    putExtra(AudioCaptureService.EXTRA_VOLUME, viewModel.streamVolume.value)
                }
                startForegroundService(serviceIntent)
                // Service will update ViewModel state in onCreate
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = MainViewModel.getInstance(application)

        setContent {
            val sharedPrefs = getSharedPreferences("Settings", MODE_PRIVATE)
            val theme = sharedPrefs.getString("THEME", "System") ?: "System"
            val isDarkTheme = when (theme) {
                "Light" -> false
                "Dark" -> true
                else -> isSystemInDarkTheme()
            }

            // Collect state from ViewModel
            val isServiceRunning by viewModel.isServiceRunning.collectAsState()
            val stats by viewModel.stats.collectAsState()
            val streamVolume by viewModel.streamVolume.collectAsState()
            val audioLevel by viewModel.audioLevel.collectAsState()
            val profiles by viewModel.profiles.collectAsState()
            val selectedProfile by viewModel.selectedProfile.collectAsState()
            val visualizationEnabled by viewModel.visualizationEnabled.collectAsState()

            AirheadWavesTheme(darkTheme = isDarkTheme) {
                val animatedColor by animateColorAsState(
                    targetValue = if (isServiceRunning && visualizationEnabled) {
                        lerp(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.primary, audioLevel)
                    } else {
                        MaterialTheme.colorScheme.background
                    },
                    animationSpec = tween(durationMillis = 100)
                )

                Surface(modifier = Modifier.fillMaxSize(), color = animatedColor) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "main") {
                        composable("main") {
                            MainScreen(
                                navController = navController,
                                isServiceRunning = isServiceRunning,
                                stats = stats,
                                currentVolume = streamVolume,
                                profiles = profiles,
                                selectedProfile = selectedProfile,
                                onVolumeChange = { viewModel.updateStreamVolume(it) },
                                onProfileSelected = { viewModel.selectProfile(it) },
                                onStartStopClick = { startStopService(isServiceRunning) }
                            )
                        }
                        composable("settings") { SettingsScreen(navController) }
                        composable("profile_settings") {
                            ProfileSettingsScreen(
                                profiles = profiles,
                                onSave = { viewModel.updateProfiles(it) }
                            )
                        }
                        composable("app_settings") {
                            AppSettingsScreen(
                                sharedPreferences = sharedPrefs,
                                onVisualizationToggle = { viewModel.updateVisualizationEnabled(it) }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.updateServiceRunning(AudioCaptureService.isRunning)
    }

    private fun startStopService(isServiceRunning: Boolean) {
        if (isServiceRunning) {
            stopService(Intent(this, AudioCaptureService::class.java))
            // Service will update ViewModel state in onDestroy
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
        Text(text = stats, color = MaterialTheme.colorScheme.onBackground)
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
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
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
        Text(text = "Volume", color = MaterialTheme.colorScheme.onBackground)
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
fun AppSettingsScreen(sharedPreferences: SharedPreferences, onVisualizationToggle: (Boolean) -> Unit) {
    val themes = listOf("Light", "Dark", "System")
    var expandedTheme by remember { mutableStateOf(false) }
    var selectedTheme by remember { mutableStateOf(sharedPreferences.getString("THEME", "System") ?: "System") }
    var visualizationEnabled by remember { mutableStateOf(sharedPreferences.getBoolean("VISUALIZATION_ENABLED", true)) }
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
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Enable Visualization", color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.width(16.dp))
            Switch(
                checked = visualizationEnabled,
                onCheckedChange = {
                    visualizationEnabled = it
                    onVisualizationToggle(it)
                }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            with(sharedPreferences.edit()) {
                putString("THEME", selectedTheme)
                putBoolean("VISUALIZATION_ENABLED", visualizationEnabled)
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
    profiles: List<ServerProfile>,
    onSave: (List<ServerProfile>) -> Unit
) {
    var editingProfiles by remember { mutableStateOf(profiles) }

    // Save immediately when editingProfiles changes
    LaunchedEffect(editingProfiles) {
        if (editingProfiles != profiles) {
            onSave(editingProfiles)
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val newProfileName = "New Profile ${editingProfiles.size + 1}"
                editingProfiles = editingProfiles + ServerProfile(id = UUID.randomUUID().toString(), name = newProfileName, ipAddress = "", port = 8888, bitrate = 128000, sampleRate = 44100, channelConfig = "Stereo")
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Profile")
            }
        }
    ) {
        LazyColumn(modifier = Modifier.padding(it)) {
            items(editingProfiles, key = { it.id }) { profile ->
                ProfileEditor(profile, onProfileChange = { updatedProfile ->
                    editingProfiles = editingProfiles.map { p ->
                        if (p.id == profile.id) updatedProfile else p
                    }
                }, onDelete = {
                    editingProfiles = editingProfiles.filter { p -> p.id != profile.id }
                })
            }
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
    var bitrate by remember { mutableIntStateOf(profile.bitrate) }
    var sampleRate by remember { mutableIntStateOf(profile.sampleRate) }
    var channelConfig by remember { mutableStateOf(profile.channelConfig) }
    var bass by remember { mutableFloatStateOf(profile.bass) }
    var treble by remember { mutableFloatStateOf(profile.treble) }
    var isExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(name, ipAddress, port, bitrate, sampleRate, channelConfig, bass, treble) {
        onProfileChange(profile.copy(name = name, ipAddress = ipAddress, port = port.toIntOrNull() ?: 8888, bitrate = bitrate, sampleRate = sampleRate, channelConfig = channelConfig, bass = bass, treble = treble))
    }

    Card(modifier = Modifier.padding(16.dp)) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name") },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (isExpanded) "Collapse" else "Expand")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Profile")
                }
            }
            if (isExpanded) {
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
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
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
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
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
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
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
                Text("Tone Control", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Bass", modifier = Modifier.width(60.dp))
                    Slider(
                        value = bass,
                        onValueChange = { bass = it },
                        valueRange = -15f..15f,
                        modifier = Modifier.weight(1f)
                    )
                    Text("%.1f dB".format(bass), modifier = Modifier.width(60.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Treble", modifier = Modifier.width(60.dp))
                    Slider(
                        value = treble,
                        onValueChange = { treble = it },
                        valueRange = -15f..15f,
                        modifier = Modifier.weight(1f)
                    )
                    Text("%.1f dB".format(treble), modifier = Modifier.width(60.dp))
                }
            }
        }
    }
}
