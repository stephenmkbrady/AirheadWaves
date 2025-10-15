package space.ring0.airheadwaves

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("Settings", android.content.Context.MODE_PRIVATE)

    // Service state
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _stats = MutableStateFlow("Not Connected")
    val stats: StateFlow<String> = _stats.asStateFlow()

    // Audio state
    private val _streamVolume = MutableStateFlow(1.0f)
    val streamVolume: StateFlow<Float> = _streamVolume.asStateFlow()

    private val _audioLevel = MutableStateFlow(0.0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    // Profile state
    private val _profiles = MutableStateFlow<List<ServerProfile>>(emptyList())
    val profiles: StateFlow<List<ServerProfile>> = _profiles.asStateFlow()

    private val _selectedProfile = MutableStateFlow<ServerProfile?>(null)
    val selectedProfile: StateFlow<ServerProfile?> = _selectedProfile.asStateFlow()

    // Visualization
    private val _visualizationEnabled = MutableStateFlow(true)
    val visualizationEnabled: StateFlow<Boolean> = _visualizationEnabled.asStateFlow()

    init {
        loadSettings()
        loadProfiles()
    }

    private fun loadSettings() {
        _streamVolume.value = sharedPrefs.getFloat("STREAM_VOLUME", 1.0f)
        _visualizationEnabled.value = sharedPrefs.getBoolean("VISUALIZATION_ENABLED", true)
    }

    private fun loadProfiles() {
        val profilesJson = sharedPrefs.getString("PROFILES", null)
        if (profilesJson != null) {
            try {
                _profiles.value = Json.decodeFromString(profilesJson)
                _selectedProfile.value = _profiles.value.firstOrNull()
            } catch (e: Exception) {
                createDefaultProfile()
            }
        } else {
            createDefaultProfile()
        }
    }

    private fun createDefaultProfile() {
        val defaultProfile = ServerProfile(
            id = java.util.UUID.randomUUID().toString(),
            name = "Default",
            ipAddress = "192.168.1.100",
            port = 8888,
            bitrate = 128000,
            sampleRate = 44100,
            channelConfig = "Stereo"
        )
        _profiles.value = listOf(defaultProfile)
        _selectedProfile.value = defaultProfile
        saveProfiles(_profiles.value)
    }

    // Public update methods
    fun updateServiceRunning(isRunning: Boolean) {
        _isServiceRunning.value = isRunning
    }

    fun updateStats(stats: String) {
        _stats.value = stats
    }

    fun updateStreamVolume(volume: Float) {
        _streamVolume.value = volume
        sharedPrefs.edit().putFloat("STREAM_VOLUME", volume).apply()
    }

    fun updateAudioLevel(level: Float) {
        _audioLevel.value = level
    }

    fun updateProfiles(newProfiles: List<ServerProfile>) {
        _profiles.value = newProfiles

        // Validate selected profile still exists
        val currentSelected = _selectedProfile.value
        if (currentSelected != null) {
            val updatedProfile = newProfiles.find { it.id == currentSelected.id }
            if (updatedProfile != null) {
                // Update to the edited version
                _selectedProfile.value = updatedProfile
            } else {
                // Selected profile was deleted, select first available
                _selectedProfile.value = newProfiles.firstOrNull()
            }
        } else {
            _selectedProfile.value = newProfiles.firstOrNull()
        }

        saveProfiles(newProfiles)
    }

    fun selectProfile(profile: ServerProfile) {
        _selectedProfile.value = profile
    }

    fun updateVisualizationEnabled(enabled: Boolean) {
        _visualizationEnabled.value = enabled
    }

    private fun saveProfiles(profiles: List<ServerProfile>) {
        val profilesJson = Json.encodeToString(profiles)
        sharedPrefs.edit().putString("PROFILES", profilesJson).apply()
    }

    companion object {
        @Volatile
        private var INSTANCE: MainViewModel? = null

        fun getInstance(application: Application): MainViewModel {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MainViewModel(application).also { INSTANCE = it }
            }
        }
    }
}
