package space.ring0.airheadwaves.models

import kotlinx.serialization.Serializable

/**
 * Transmit Profile - Configuration for sending audio to receivers
 */
@Serializable
data class TransmitProfile(
    val id: String,
    val name: String,
    val destinations: List<Destination>,
    val bitrate: Int,
    val sampleRate: Int,
    val channelConfig: String,
    val bass: Float = 0f,
    val treble: Float = 0f
)

@Serializable
data class Destination(
    val ipAddress: String,
    val port: Int
)

/**
 * Receive Profile - Configuration for receiving audio streams
 */
@Serializable
data class ReceiveProfile(
    val id: String,
    val name: String,
    val listenPort: Int = 8888,
    val outputDevice: OutputDevice = OutputDevice.AUTO,
    val bufferSize: BufferSize = BufferSize.BALANCED,
    val bass: Float = 0f,
    val treble: Float = 0f,
    val volume: Float = 1.0f,
    // Access control
    val allowUnknownTransmitters: Boolean = true,
    val allowedTransmitterIPs: List<String> = emptyList(),
    // Stream configuration
    val autoDetectParameters: Boolean = true,
    val expectedSampleRate: Int? = null,
    val expectedChannels: Int? = null
)

@Serializable
enum class OutputDevice {
    AUTO,
    SPEAKER,
    HEADPHONES
}

@Serializable
enum class BufferSize(val milliseconds: Int) {
    ULTRA_LOW(0),
    LOW_LATENCY(75),
    BALANCED(150),
    SMOOTH(350)
}

/**
 * Streaming mode
 */
enum class StreamMode {
    TRANSMIT,
    RECEIVE
}
