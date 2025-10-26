package space.ring0.airheadwaves.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import space.ring0.airheadwaves.models.*
import java.util.UUID

/**
 * ViewModel for Node Graph Editor
 */
class NodeGraphViewModel : ViewModel() {

    // Graph state
    val nodes = mutableStateListOf<GraphNode>()
    val connections = mutableStateListOf<GraphConnection>()

    // Canvas transform state
    val canvasOffset = mutableStateOf(Offset.Zero)
    val canvasScale = mutableStateOf(1f)

    // Selection state
    val selectedNodeId = mutableStateOf<String?>(null)
    val selectedConnectionId = mutableStateOf<String?>(null)

    // Drag state
    val isDragging = mutableStateOf(false)
    val draggedNodeId = mutableStateOf<String?>(null)

    // Connection creation state
    val isDrawingConnection = mutableStateOf(false)
    val connectionSourceNodeId = mutableStateOf<String?>(null)
    val connectionTargetPosition = mutableStateOf<Offset?>(null)

    /**
     * Add a new node to the graph
     */
    fun addNode(type: NodeType, position: Offset) {
        val nodeId = UUID.randomUUID().toString()
        val displayName = if (type == NodeType.TRANSMITTER) {
            "Transmitter ${nodes.count { it.type == NodeType.TRANSMITTER } + 1}"
        } else {
            "Receiver ${nodes.count { it.type == NodeType.RECEIVER } + 1}"
        }

        val config = NodeConfig(
            transmitProfile = if (type == NodeType.TRANSMITTER) {
                TransmitProfile(
                    id = UUID.randomUUID().toString(),
                    name = displayName,
                    destinations = emptyList(),
                    bitrate = 128000,
                    sampleRate = 44100,
                    channelConfig = "Stereo"
                )
            } else null,
            receiveProfile = if (type == NodeType.RECEIVER) {
                ReceiveProfile(
                    id = UUID.randomUUID().toString(),
                    name = displayName,
                    listenPort = 8888
                )
            } else null
        )

        val node = GraphNode(
            id = nodeId,
            type = type,
            deviceId = null,  // Not paired yet
            displayName = displayName,
            position = NodePosition(position.x, position.y),
            config = config
        )

        nodes.add(node)
        selectedNodeId.value = nodeId
    }

    /**
     * Remove a node and its connections
     */
    fun removeNode(nodeId: String) {
        nodes.removeAll { it.id == nodeId }
        connections.removeAll { it.sourceNodeId == nodeId || it.targetNodeId == nodeId }
        if (selectedNodeId.value == nodeId) {
            selectedNodeId.value = null
        }
    }

    /**
     * Update node position
     */
    fun updateNodePosition(nodeId: String, newPosition: Offset) {
        val index = nodes.indexOfFirst { it.id == nodeId }
        if (index != -1) {
            val node = nodes[index]
            nodes[index] = node.copy(
                position = NodePosition(newPosition.x, newPosition.y)
            )
        }
    }

    /**
     * Start drawing a connection
     */
    fun startConnection(sourceNodeId: String) {
        isDrawingConnection.value = true
        connectionSourceNodeId.value = sourceNodeId
    }

    /**
     * Update connection target position (while dragging)
     */
    fun updateConnectionTarget(position: Offset) {
        connectionTargetPosition.value = position
    }

    /**
     * Complete connection to target node
     */
    fun completeConnection(targetNodeId: String) {
        val sourceId = connectionSourceNodeId.value
        if (sourceId != null && sourceId != targetNodeId) {
            val sourceNode = nodes.find { it.id == sourceId }
            val targetNode = nodes.find { it.id == targetNodeId }

            // Only allow transmitter → receiver connections
            if (sourceNode?.type == NodeType.TRANSMITTER && targetNode?.type == NodeType.RECEIVER) {
                // Check if connection already exists
                val exists = connections.any {
                    it.sourceNodeId == sourceId && it.targetNodeId == targetNodeId
                }

                if (!exists) {
                    val connection = GraphConnection(
                        id = UUID.randomUUID().toString(),
                        sourceNodeId = sourceId,
                        targetNodeId = targetNodeId,
                        status = ConnectionStatus.DISCONNECTED
                    )
                    connections.add(connection)
                }
            }
        }

        cancelConnection()
    }

    /**
     * Cancel connection creation
     */
    fun cancelConnection() {
        isDrawingConnection.value = false
        connectionSourceNodeId.value = null
        connectionTargetPosition.value = null
    }

    /**
     * Remove a connection
     */
    fun removeConnection(connectionId: String) {
        connections.removeAll { it.id == connectionId }
        if (selectedConnectionId.value == connectionId) {
            selectedConnectionId.value = null
        }
    }

    /**
     * Update connection status
     */
    fun updateConnectionStatus(connectionId: String, status: ConnectionStatus) {
        val index = connections.indexOfFirst { it.id == connectionId }
        if (index != -1) {
            val connection = connections[index]
            connections[index] = connection.copy(status = status)
        }
    }

    /**
     * Clone/duplicate a node
     */
    fun cloneNode(nodeId: String) {
        val node = nodes.find { it.id == nodeId } ?: return

        // Offset the cloned node position
        val newPosition = Offset(
            node.position.x + 100f,
            node.position.y + 100f
        )

        addNode(node.type, newPosition)
    }

    /**
     * Load graph from GraphProfile
     */
    fun loadGraph(profile: GraphProfile) {
        nodes.clear()
        connections.clear()
        nodes.addAll(profile.nodes)
        connections.addAll(profile.connections)
        selectedNodeId.value = null
        selectedConnectionId.value = null
    }

    /**
     * Save graph to GraphProfile
     */
    fun saveGraph(name: String): GraphProfile {
        return GraphProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            nodes = nodes.toList(),
            connections = connections.toList()
        )
    }

    /**
     * Clear the graph
     */
    fun clearGraph() {
        nodes.clear()
        connections.clear()
        selectedNodeId.value = null
        selectedConnectionId.value = null
        canvasOffset.value = Offset.Zero
        canvasScale.value = 1f
    }

    /**
     * Get node by ID
     */
    fun getNode(nodeId: String): GraphNode? {
        return nodes.find { it.id == nodeId }
    }

    /**
     * Update node configuration
     */
    fun updateNodeConfig(nodeId: String, newConfig: NodeConfig) {
        val index = nodes.indexOfFirst { it.id == nodeId }
        if (index != -1) {
            val node = nodes[index]
            nodes[index] = node.copy(config = newConfig)
        }
    }
}
