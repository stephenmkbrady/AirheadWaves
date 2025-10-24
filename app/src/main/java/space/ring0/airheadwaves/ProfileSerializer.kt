package space.ring0.airheadwaves

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import space.ring0.airheadwaves.models.ReceiveProfile
import space.ring0.airheadwaves.models.TransmitProfile

/**
 * Helper class for profile serialization/deserialization
 *
 * Provides static methods accessible from Java services
 * for converting profiles to/from JSON strings.
 */
object ProfileSerializer {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Serialize ReceiveProfile to JSON string
     */
    @JvmStatic
    fun serializeReceiveProfile(profile: ReceiveProfile): String {
        return json.encodeToString(profile)
    }

    /**
     * Deserialize ReceiveProfile from JSON string
     */
    @JvmStatic
    fun deserializeReceiveProfile(jsonString: String): ReceiveProfile {
        return json.decodeFromString(jsonString)
    }

    /**
     * Serialize TransmitProfile to JSON string
     */
    @JvmStatic
    fun serializeTransmitProfile(profile: TransmitProfile): String {
        return json.encodeToString(profile)
    }

    /**
     * Deserialize TransmitProfile from JSON string
     */
    @JvmStatic
    fun deserializeTransmitProfile(jsonString: String): TransmitProfile {
        return json.decodeFromString(jsonString)
    }
}
