package space.ring0.airheadwaves.models

import kotlinx.serialization.Serializable

/**
 * Graph Profile - Complete node graph configuration
 * Represents a saved graph setup (e.g., "Home Theater Setup")
 */
@Serializable
data class GraphProfile(
    val id: String,
    val name: String,
    val nodes: List<GraphNode>,
    val connections: List<GraphConnection>,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis()
)

/**
 * Graph Node - Represents a transmitter or receiver in the graph
 */
@Serializable
data class GraphNode(
    val id: String,  // Unique node ID in graph
    val type: NodeType,
    val deviceId: String?,  // Physical device ID (null if not paired)
    val displayName: String,
    val position: NodePosition,
    val config: NodeConfig
)

/**
 * Node Type - Transmitter or Receiver
 */
@Serializable
enum class NodeType {
    TRANSMITTER,
    RECEIVER
}

/**
 * Node Position - X, Y coordinates on canvas
 */
@Serializable
data class NodePosition(
    val x: Float,
    val y: Float
)

/**
 * Node Config - Configuration specific to node type
 */
@Serializable
data class NodeConfig(
    // Transmitter-specific config
    val transmitProfile: TransmitProfile? = null,

    // Receiver-specific config
    val receiveProfile: ReceiveProfile? = null,

    // Remote config settings
    val remoteConfigEnabled: Boolean = false,
    val mqttBrokerIp: String? = null,
    val mqttBrokerPort: Int = 8883,
    val apiKey: String? = null,  // For pairing authentication

    // Per-transmitter volume for weighted mixing
    val mixVolume: Float = 1.0f  // 0.0 to 1.0, used when receiver has multiple inputs
)

/**
 * Graph Connection - Connection between two nodes
 */
@Serializable
data class GraphConnection(
    val id: String,
    val sourceNodeId: String,  // Transmitter node ID
    val targetNodeId: String,  // Receiver node ID
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,

    // Runtime stats (not persisted, updated in real-time)
    @kotlinx.serialization.Transient
    val bitrate: Int? = null,
    @kotlinx.serialization.Transient
    val latency: Int? = null,
    @kotlinx.serialization.Transient
    val packetLoss: Float? = null
)

/**
 * Connection Status - Visual indicator for connection state
 */
@Serializable
enum class ConnectionStatus {
    DISCONNECTED,  // Red
    CONNECTING,    // Yellow
    CONNECTED,     // Green (idle)
    STREAMING      // Green (animated)
}

/**
 * MQTT Control Message - Messages sent over MQTT control channel
 */
@Serializable
sealed class MQTTControlMessage {
    /**
     * Configuration update message
     * Topic: airheadwaves/{receiver-id}/config
     */
    @Serializable
    data class ConfigUpdate(
        val nodeId: String,
        val config: NodeConfig,
        val timestamp: Long = System.currentTimeMillis()
    ) : MQTTControlMessage()

    /**
     * Control command message
     * Topic: airheadwaves/{receiver-id}/command
     */
    @Serializable
    data class Command(
        val command: ControlCommand,
        val timestamp: Long = System.currentTimeMillis()
    ) : MQTTControlMessage()

    /**
     * Status/heartbeat message
     * Topic: airheadwaves/{receiver-id}/status
     */
    @Serializable
    data class Status(
        val nodeId: String,
        val deviceId: String,
        val connectionStatus: ConnectionStatus,
        val activeConnections: List<String>,  // List of transmitter node IDs
        val timestamp: Long = System.currentTimeMillis()
    ) : MQTTControlMessage()
}

/**
 * Control Commands - Commands sent from CNC to receivers
 */
@Serializable
enum class ControlCommand {
    START_STREAMING,
    STOP_STREAMING,
    RECONNECT,
    UNPAIR
}

/**
 * QR Code Pairing Data - Encoded in QR code for pairing
 */
@Serializable
data class PairingData(
    val protocol: String = "mqtt",
    val brokerIp: String,
    val brokerPort: Int = 8883,
    val apiKey: String,
    val nodeId: String,
    val nodeType: NodeType,
    val pairingExpiry: Long  // Unix timestamp
)
