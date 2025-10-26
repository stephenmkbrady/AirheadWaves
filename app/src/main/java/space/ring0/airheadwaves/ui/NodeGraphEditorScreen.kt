package space.ring0.airheadwaves.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import space.ring0.airheadwaves.models.NodeType

/**
 * Node Graph Editor Screen - Main UI for the graph editor
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeGraphEditorScreen(
    viewModel: NodeGraphViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
    val selectedNodeId by viewModel.selectedNodeId
    val selectedNode = selectedNodeId?.let { viewModel.getNode(it) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Node Graph Editor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Save graph
                    IconButton(onClick = {
                        // TODO: Show save dialog
                    }) {
                        Icon(Icons.Default.Save, "Save")
                    }

                    // Load graph
                    IconButton(onClick = {
                        // TODO: Show load dialog
                    }) {
                        Icon(Icons.Default.FolderOpen, "Load")
                    }

                    // Clear graph
                    IconButton(onClick = {
                        viewModel.clearGraph()
                    }) {
                        Icon(Icons.Default.Delete, "Clear")
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Add Transmitter Node
                FloatingActionButton(
                    onClick = {
                        viewModel.addNode(
                            type = NodeType.TRANSMITTER,
                            position = Offset(100f, 100f)
                        )
                    },
                    containerColor = Color(0xFF4CAF50)
                ) {
                    Icon(Icons.Default.Add, "Add Transmitter")
                }

                // Add Receiver Node
                FloatingActionButton(
                    onClick = {
                        viewModel.addNode(
                            type = NodeType.RECEIVER,
                            position = Offset(400f, 100f)
                        )
                    },
                    containerColor = Color(0xFF2196F3)
                ) {
                    Icon(Icons.Default.Add, "Add Receiver")
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Main canvas
            NodeGraphCanvas(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )

            // Node property panel (when node selected)
            if (selectedNode != null) {
                NodePropertyPanel(
                    node = selectedNode,
                    onClose = { viewModel.selectedNodeId.value = null },
                    onDelete = {
                        viewModel.removeNode(selectedNode.id)
                    },
                    onClone = {
                        viewModel.cloneNode(selectedNode.id)
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(300.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface)
                )
            }

            // Instructions overlay
            InstructionsOverlay(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            )
        }
    }
}

/**
 * Instructions overlay showing how to use the editor
 */
@Composable
fun InstructionsOverlay(modifier: Modifier = Modifier) {
    var showInstructions by remember { mutableStateOf(true) }

    if (showInstructions) {
        Card(
            modifier = modifier.width(250.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Quick Guide",
                        style = MaterialTheme.typography.titleSmall
                    )
                    IconButton(
                        onClick = { showInstructions = false },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, "Close", modifier = Modifier.size(16.dp))
                    }
                }

                Divider()

                InstructionItem("🟢 Green", "Transmitter")
                InstructionItem("🔵 Blue", "Receiver")
                InstructionItem("Drag", "Move nodes")
                InstructionItem("Pinch", "Zoom canvas")
                InstructionItem("2-finger drag", "Pan canvas")
                InstructionItem("Double-tap TX", "Start connection")
                InstructionItem("Double-tap RX", "Complete connection")
                InstructionItem("Tap node", "Select/configure")
            }
        }
    }
}

@Composable
fun InstructionItem(action: String, description: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            action,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(80.dp)
        )
        Text(
            description,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * Node property panel for configuration
 */
@Composable
fun NodePropertyPanel(
    node: space.ring0.airheadwaves.models.GraphNode,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onClone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    node.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, "Close")
                }
            }

            Divider()

            // Node type
            Text(
                "Type: ${node.type}",
                style = MaterialTheme.typography.bodyMedium
            )

            // Device pairing status
            if (node.deviceId != null) {
                Text(
                    "Paired: ${node.deviceId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4CAF50)
                )
            } else {
                Text(
                    "Not paired",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Divider()

            // Configuration preview
            Text(
                "Configuration",
                style = MaterialTheme.typography.titleSmall
            )

            if (node.type == NodeType.TRANSMITTER && node.config.transmitProfile != null) {
                val profile = node.config.transmitProfile
                Text("Bitrate: ${profile.bitrate / 1000}kbps", style = MaterialTheme.typography.bodySmall)
                Text("Sample Rate: ${profile.sampleRate}Hz", style = MaterialTheme.typography.bodySmall)
                Text("Channels: ${profile.channelConfig}", style = MaterialTheme.typography.bodySmall)
            } else if (node.type == NodeType.RECEIVER && node.config.receiveProfile != null) {
                val profile = node.config.receiveProfile
                Text("Port: ${profile.listenPort}", style = MaterialTheme.typography.bodySmall)
                Text("Buffer: ${profile.bufferSize}", style = MaterialTheme.typography.bodySmall)
                Text("Volume: ${profile.volume}", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.weight(1f))

            // Actions
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { /* TODO: Show QR code for pairing */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.QrCode, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Pair Device")
                }

                Button(
                    onClick = onClone,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Clone Node")
                }

                Button(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete Node")
                }
            }
        }
    }
}
