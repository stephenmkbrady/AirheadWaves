package space.ring0.airheadwaves

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import space.ring0.airheadwaves.models.*

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

    // Mode state
    private val _streamMode = MutableStateFlow(StreamMode.TRANSMIT)
    val streamMode: StateFlow<StreamMode> = _streamMode.asStateFlow()

    // Transmit profile state
    private val _transmitProfiles = MutableStateFlow<List<TransmitProfile>>(emptyList())
    val transmitProfiles: StateFlow<List<TransmitProfile>> = _transmitProfiles.asStateFlow()

    private val _selectedTransmitProfile = MutableStateFlow<TransmitProfile?>(null)
    val selectedTransmitProfile: StateFlow<TransmitProfile?> = _selectedTransmitProfile.asStateFlow()

    // Receive profile state
    private val _receiveProfiles = MutableStateFlow<List<ReceiveProfile>>(emptyList())
    val receiveProfiles: StateFlow<List<ReceiveProfile>> = _receiveProfiles.asStateFlow()

    private val _selectedReceiveProfile = MutableStateFlow<ReceiveProfile?>(null)
    val selectedReceiveProfile: StateFlow<ReceiveProfile?> = _selectedReceiveProfile.asStateFlow()

    // Legacy ServerProfile state (for backward compatibility during migration)
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
        loadTransmitProfiles()
        loadReceiveProfiles()
    }

    private fun loadSettings() {
        _streamVolume.value = sharedPrefs.getFloat("STREAM_VOLUME", 1.0f)
        _visualizationEnabled.value = sharedPrefs.getBoolean("VISUALIZATION_ENABLED", true)

        // Load stream mode
        val modeStr = sharedPrefs.getString("STREAM_MODE", "TRANSMIT")
        _streamMode.value = try {
            StreamMode.valueOf(modeStr ?: "TRANSMIT")
        } catch (e: Exception) {
            StreamMode.TRANSMIT
        }
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

    // New profile management methods for v2.0

    private fun loadTransmitProfiles() {
        val profilesJson = sharedPrefs.getString("TRANSMIT_PROFILES", null)
        if (profilesJson != null) {
            try {
                _transmitProfiles.value = Json.decodeFromString(profilesJson)
                _selectedTransmitProfile.value = _transmitProfiles.value.firstOrNull()
            } catch (e: Exception) {
                createDefaultTransmitProfile()
            }
        } else {
            // Migrate from old ServerProfile if exists
            migrateToTransmitProfiles()
        }
    }

    private fun loadReceiveProfiles() {
        val profilesJson = sharedPrefs.getString("RECEIVE_PROFILES", null)
        if (profilesJson != null) {
            try {
                _receiveProfiles.value = Json.decodeFromString(profilesJson)
                _selectedReceiveProfile.value = _receiveProfiles.value.firstOrNull()
            } catch (e: Exception) {
                createDefaultReceiveProfile()
            }
        } else {
            createDefaultReceiveProfile()
        }
    }

    private fun migrateToTransmitProfiles() {
        // Convert old ServerProfile to new TransmitProfile
        if (_profiles.value.isNotEmpty()) {
            _transmitProfiles.value = _profiles.value.map { old ->
                TransmitProfile(
                    id = old.id,
                    name = old.name,
                    destinations = listOf(Destination(old.ipAddress, old.port)),
                    bitrate = old.bitrate,
                    sampleRate = old.sampleRate,
                    channelConfig = old.channelConfig,
                    bass = old.bass,
                    treble = old.treble
                )
            }
            _selectedTransmitProfile.value = _transmitProfiles.value.firstOrNull()
            saveTransmitProfiles(_transmitProfiles.value)
        } else {
            createDefaultTransmitProfile()
        }
    }

    private fun createDefaultTransmitProfile() {
        val defaultProfile = TransmitProfile(
            id = java.util.UUID.randomUUID().toString(),
            name = "Default",
            destinations = listOf(Destination("192.168.1.100", 8888)),
            bitrate = 128000,
            sampleRate = 44100,
            channelConfig = "Stereo"
        )
        _transmitProfiles.value = listOf(defaultProfile)
        _selectedTransmitProfile.value = defaultProfile
        saveTransmitProfiles(_transmitProfiles.value)
    }

    private fun createDefaultReceiveProfile() {
        val defaultProfile = ReceiveProfile(
            id = java.util.UUID.randomUUID().toString(),
            name = "Default Receiver",
            listenPort = 8888
        )
        _receiveProfiles.value = listOf(defaultProfile)
        _selectedReceiveProfile.value = defaultProfile
        saveReceiveProfiles(_receiveProfiles.value)
    }

    fun updateTransmitProfiles(newProfiles: List<TransmitProfile>) {
        _transmitProfiles.value = newProfiles
        val currentSelected = _selectedTransmitProfile.value
        if (currentSelected != null) {
            val updatedProfile = newProfiles.find { it.id == currentSelected.id }
            _selectedTransmitProfile.value = updatedProfile ?: newProfiles.firstOrNull()
        } else {
            _selectedTransmitProfile.value = newProfiles.firstOrNull()
        }
        saveTransmitProfiles(newProfiles)
    }

    fun updateReceiveProfiles(newProfiles: List<ReceiveProfile>) {
        _receiveProfiles.value = newProfiles
        val currentSelected = _selectedReceiveProfile.value
        if (currentSelected != null) {
            val updatedProfile = newProfiles.find { it.id == currentSelected.id }
            _selectedReceiveProfile.value = updatedProfile ?: newProfiles.firstOrNull()
        } else {
            _selectedReceiveProfile.value = newProfiles.firstOrNull()
        }
        saveReceiveProfiles(newProfiles)
    }

    fun selectTransmitProfile(profile: TransmitProfile) {
        _selectedTransmitProfile.value = profile
    }

    fun selectReceiveProfile(profile: ReceiveProfile) {
        _selectedReceiveProfile.value = profile
    }

    fun updateStreamMode(mode: StreamMode) {
        if (!_isServiceRunning.value) {
            _streamMode.value = mode
            sharedPrefs.edit().putString("STREAM_MODE", mode.name).apply()
        }
    }

    private fun saveTransmitProfiles(profiles: List<TransmitProfile>) {
        val profilesJson = Json.encodeToString(profiles)
        sharedPrefs.edit().putString("TRANSMIT_PROFILES", profilesJson).apply()
    }

    private fun saveReceiveProfiles(profiles: List<ReceiveProfile>) {
        val profilesJson = Json.encodeToString(profiles)
        sharedPrefs.edit().putString("RECEIVE_PROFILES", profilesJson).apply()
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
