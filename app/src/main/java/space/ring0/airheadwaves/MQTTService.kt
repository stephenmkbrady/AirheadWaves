package space.ring0.airheadwaves

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import space.ring0.airheadwaves.models.*
import java.util.UUID

/**
 * MQTT Service for CNC (Command & Control) communication
 *
 * Handles MQTT pub/sub for:
 * - Config updates (transmitter → receiver)
 * - Control commands (transmitter → receiver)
 * - Status/heartbeat (receiver → transmitter)
 */
class MQTTService : Service() {
    companion object {
        private const val TAG = "MQTTService"
        private const val QOS = 1  // At least once delivery
    }

    private val binder = MQTTBinder()
    private var mqttClient: MqttClient? = null
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // Callbacks for received messages
    private val messageListeners = mutableListOf<MessageListener>()

    inner class MQTTBinder : Binder() {
        fun getService(): MQTTService = this@MQTTService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    /**
     * Connect to MQTT broker
     */
    fun connect(brokerIp: String, brokerPort: Int = 8883, clientId: String = UUID.randomUUID().toString()) {
        try {
            val brokerUrl = "tcp://$brokerIp:$brokerPort"
            Log.i(TAG, "Connecting to MQTT broker: $brokerUrl")

            mqttClient = MqttClient(brokerUrl, clientId, MemoryPersistence())

            val options = MqttConnectOptions().apply {
                isCleanSession = true
                connectionTimeout = 10
                keepAliveInterval = 20
                isAutomaticReconnect = true

                // TODO: Add TLS support when ready
                // For prototype: no certificate validation
                // socketFactory = getSSLSocketFactory()
            }

            mqttClient?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    Log.w(TAG, "MQTT connection lost", cause)
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    if (topic != null && message != null) {
                        handleMessage(topic, String(message.payload))
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    Log.v(TAG, "Message delivery complete")
                }
            })

            mqttClient?.connect(options)
            Log.i(TAG, "Connected to MQTT broker")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to MQTT broker", e)
        }
    }

    /**
     * Disconnect from MQTT broker
     */
    fun disconnect() {
        try {
            mqttClient?.disconnect()
            mqttClient?.close()
            mqttClient = null
            Log.i(TAG, "Disconnected from MQTT broker")
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting from MQTT", e)
        }
    }

    /**
     * Subscribe to topics for a specific receiver
     */
    fun subscribeToReceiver(receiverId: String) {
        try {
            val topics = arrayOf(
                "airheadwaves/$receiverId/status"
            )
            mqttClient?.subscribe(topics, intArrayOf(QOS))
            Log.i(TAG, "Subscribed to receiver: $receiverId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to subscribe to receiver topics", e)
        }
    }

    /**
     * Subscribe to topics for a specific transmitter (when this device is a receiver)
     */
    fun subscribeToTransmitter(deviceId: String) {
        try {
            val topics = arrayOf(
                "airheadwaves/$deviceId/config",
                "airheadwaves/$deviceId/command"
            )
            mqttClient?.subscribe(topics, intArrayOf(QOS, QOS))
            Log.i(TAG, "Subscribed to transmitter topics for device: $deviceId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to subscribe to transmitter topics", e)
        }
    }

    /**
     * Publish configuration update to receiver
     */
    fun publishConfig(receiverId: String, config: NodeConfig) {
        try {
            val message = MQTTControlMessage.ConfigUpdate(
                nodeId = receiverId,
                config = config
            )
            val payload = json.encodeToString(message)
            val topic = "airheadwaves/$receiverId/config"

            mqttClient?.publish(topic, MqttMessage(payload.toByteArray()).apply {
                qos = QOS
                isRetained = false
            })

            Log.i(TAG, "Published config to $receiverId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish config", e)
        }
    }

    /**
     * Publish control command to receiver
     */
    fun publishCommand(receiverId: String, command: ControlCommand) {
        try {
            val message = MQTTControlMessage.Command(command = command)
            val payload = json.encodeToString(message)
            val topic = "airheadwaves/$receiverId/command"

            mqttClient?.publish(topic, MqttMessage(payload.toByteArray()).apply {
                qos = QOS
                isRetained = false
            })

            Log.i(TAG, "Published command $command to $receiverId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish command", e)
        }
    }

    /**
     * Publish status/heartbeat (when this device is a receiver)
     */
    fun publishStatus(deviceId: String, nodeId: String, status: ConnectionStatus, activeConnections: List<String>) {
        try {
            val message = MQTTControlMessage.Status(
                nodeId = nodeId,
                deviceId = deviceId,
                connectionStatus = status,
                activeConnections = activeConnections
            )
            val payload = json.encodeToString(message)
            val topic = "airheadwaves/$deviceId/status"

            mqttClient?.publish(topic, MqttMessage(payload.toByteArray()).apply {
                qos = QOS
                isRetained = false
            })

            Log.v(TAG, "Published status for device $deviceId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish status", e)
        }
    }

    /**
     * Handle incoming MQTT messages
     */
    private fun handleMessage(topic: String, payload: String) {
        Log.d(TAG, "Received message on topic: $topic")

        try {
            when {
                topic.endsWith("/config") -> {
                    val message = json.decodeFromString<MQTTControlMessage.ConfigUpdate>(payload)
                    notifyListeners { it.onConfigReceived(message) }
                }
                topic.endsWith("/command") -> {
                    val message = json.decodeFromString<MQTTControlMessage.Command>(payload)
                    notifyListeners { it.onCommandReceived(message) }
                }
                topic.endsWith("/status") -> {
                    val message = json.decodeFromString<MQTTControlMessage.Status>(payload)
                    notifyListeners { it.onStatusReceived(message) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse MQTT message", e)
        }
    }

    /**
     * Add message listener
     */
    fun addMessageListener(listener: MessageListener) {
        messageListeners.add(listener)
    }

    /**
     * Remove message listener
     */
    fun removeMessageListener(listener: MessageListener) {
        messageListeners.remove(listener)
    }

    private fun notifyListeners(action: (MessageListener) -> Unit) {
        messageListeners.forEach { action(it) }
    }

    /**
     * Check if connected to broker
     */
    fun isConnected(): Boolean = mqttClient?.isConnected == true

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    /**
     * Listener interface for MQTT messages
     */
    interface MessageListener {
        fun onConfigReceived(message: MQTTControlMessage.ConfigUpdate) {}
        fun onCommandReceived(message: MQTTControlMessage.Command) {}
        fun onStatusReceived(message: MQTTControlMessage.Status) {}
    }
}
