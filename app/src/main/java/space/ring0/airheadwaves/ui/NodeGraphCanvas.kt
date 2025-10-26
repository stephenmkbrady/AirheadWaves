package space.ring0.airheadwaves.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import space.ring0.airheadwaves.models.*
import kotlin.math.max
import kotlin.math.min

/**
 * Node Graph Canvas - Main composable for the node graph editor
 */
@Composable
fun NodeGraphCanvas(
    viewModel: NodeGraphViewModel,
    modifier: Modifier = Modifier
) {
    val nodes by remember { derivedStateOf { viewModel.nodes.toList() } }
    val connections by remember { derivedStateOf { viewModel.connections.toList() } }
    val canvasOffset by viewModel.canvasOffset
    val canvasScale by viewModel.canvasScale
    val selectedNodeId by viewModel.selectedNodeId
    val isDrawingConnection by viewModel.isDrawingConnection
    val connectionSourceNodeId by viewModel.connectionSourceNodeId
    val connectionTargetPosition by viewModel.connectionTargetPosition

    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Handle pan and zoom
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (!viewModel.isDragging.value) {
                            // Pan the canvas
                            viewModel.canvasOffset.value += pan

                            // Zoom the canvas
                            val newScale = (canvasScale * zoom).coerceIn(0.5f, 3.0f)
                            viewModel.canvasScale.value = newScale
                        }
                    }
                }
                .pointerInput(Unit) {
                    // Handle node dragging and connection drawing
                    detectDragGestures(
                        onDragStart = { offset ->
                            val canvasPos = screenToCanvas(offset, canvasOffset, canvasScale)
                            val hitNode = findNodeAt(nodes, canvasPos)

                            if (hitNode != null) {
                                viewModel.isDragging.value = true
                                viewModel.draggedNodeId.value = hitNode.id
                                dragOffset = Offset(
                                    canvasPos.x - hitNode.position.x,
                                    canvasPos.y - hitNode.position.y
                                )
                            }
                        },
                        onDrag = { change, _ ->
                            val canvasPos = screenToCanvas(change.position, canvasOffset, canvasScale)
                            val draggedId = viewModel.draggedNodeId.value

                            if (draggedId != null) {
                                // Drag node
                                viewModel.updateNodePosition(
                                    draggedId,
                                    Offset(canvasPos.x - dragOffset.x, canvasPos.y - dragOffset.y)
                                )
                            } else if (isDrawingConnection) {
                                // Update connection drawing
                                viewModel.updateConnectionTarget(canvasPos)
                            }
                        },
                        onDragEnd = {
                            viewModel.isDragging.value = false
                            viewModel.draggedNodeId.value = null
                            dragOffset = Offset.Zero
                        }
                    )
                }
                .pointerInput(Unit) {
                    // Handle taps (selection)
                    detectTapGestures(
                        onTap = { offset ->
                            val canvasPos = screenToCanvas(offset, canvasOffset, canvasScale)
                            val hitNode = findNodeAt(nodes, canvasPos)

                            if (hitNode != null) {
                                viewModel.selectedNodeId.value = hitNode.id
                            } else {
                                viewModel.selectedNodeId.value = null
                            }
                        },
                        onDoubleTap = { offset ->
                            val canvasPos = screenToCanvas(offset, canvasOffset, canvasScale)
                            val hitNode = findNodeAt(nodes, canvasPos)

                            if (hitNode != null && hitNode.type == NodeType.TRANSMITTER) {
                                // Start drawing connection from transmitter
                                viewModel.startConnection(hitNode.id)
                            } else if (isDrawingConnection && hitNode != null && hitNode.type == NodeType.RECEIVER) {
                                // Complete connection to receiver
                                viewModel.completeConnection(hitNode.id)
                            } else {
                                viewModel.cancelConnection()
                            }
                        }
                    )
                }
        ) {
            // Draw grid
            drawGrid(canvasOffset, canvasScale)

            // Draw connections
            connections.forEach { connection ->
                val sourceNode = nodes.find { it.id == connection.sourceNodeId }
                val targetNode = nodes.find { it.id == connection.targetNodeId }

                if (sourceNode != null && targetNode != null) {
                    drawConnection(
                        source = Offset(sourceNode.position.x, sourceNode.position.y),
                        target = Offset(targetNode.position.x, targetNode.position.y),
                        status = connection.status,
                        canvasOffset = canvasOffset,
                        canvasScale = canvasScale
                    )
                }
            }

            // Draw connection being created
            if (isDrawingConnection && connectionSourceNodeId != null && connectionTargetPosition != null) {
                val sourceNode = nodes.find { it.id == connectionSourceNodeId }
                if (sourceNode != null) {
                    drawConnection(
                        source = Offset(sourceNode.position.x, sourceNode.position.y),
                        target = connectionTargetPosition!!,
                        status = ConnectionStatus.CONNECTING,
                        canvasOffset = canvasOffset,
                        canvasScale = canvasScale,
                        dashed = true
                    )
                }
            }

            // Draw nodes
            nodes.forEach { node ->
                drawNode(
                    node = node,
                    isSelected = node.id == selectedNodeId,
                    canvasOffset = canvasOffset,
                    canvasScale = canvasScale
                )
            }
        }
    }
}

/**
 * Draw grid background
 */
private fun DrawScope.drawGrid(offset: Offset, scale: Float) {
    val gridSize = 50f * scale
    val color = Color.LightGray.copy(alpha = 0.3f)

    val startX = ((-offset.x / scale) / gridSize).toInt() * gridSize
    val startY = ((-offset.y / scale) / gridSize).toInt() * gridSize

    val endX = startX + (size.width / scale) + gridSize * 2
    val endY = startY + (size.height / scale) + gridSize * 2

    var x = startX
    while (x < endX) {
        val screenX = (x * scale) + offset.x
        drawLine(
            color = color,
            start = Offset(screenX, 0f),
            end = Offset(screenX, size.height),
            strokeWidth = 1f
        )
        x += gridSize
    }

    var y = startY
    while (y < endY) {
        val screenY = (y * scale) + offset.y
        drawLine(
            color = color,
            start = Offset(0f, screenY),
            end = Offset(size.width, screenY),
            strokeWidth = 1f
        )
        y += gridSize
    }
}

/**
 * Draw a node
 */
private fun DrawScope.drawNode(
    node: GraphNode,
    isSelected: Boolean,
    canvasOffset: Offset,
    canvasScale: Float
) {
    val screenPos = canvasToScreen(
        Offset(node.position.x, node.position.y),
        canvasOffset,
        canvasScale
    )

    val nodeWidth = 200f * canvasScale
    val nodeHeight = 80f * canvasScale
    val cornerRadius = 8f * canvasScale

    // Node color based on type
    val nodeColor = when (node.type) {
        NodeType.TRANSMITTER -> Color(0xFF4CAF50)  // Green
        NodeType.RECEIVER -> Color(0xFF2196F3)     // Blue
    }

    val borderColor = if (isSelected) Color.Yellow else Color.White
    val borderWidth = if (isSelected) 3f else 1f

    // Draw node background
    drawRoundRect(
        color = nodeColor,
        topLeft = screenPos,
        size = Size(nodeWidth, nodeHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
    )

    // Draw border
    drawRoundRect(
        color = borderColor,
        topLeft = screenPos,
        size = Size(nodeWidth, nodeHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius),
        style = Stroke(width = borderWidth)
    )

    // Draw paired indicator
    if (node.deviceId != null) {
        val indicatorSize = 12f * canvasScale
        drawCircle(
            color = Color.Green,
            radius = indicatorSize / 2,
            center = Offset(
                screenPos.x + nodeWidth - indicatorSize,
                screenPos.y + indicatorSize
            )
        )
    }

    // TODO: Draw text labels (requires text measurement)
    // For now, nodes are just colored rectangles
}

/**
 * Draw a connection between nodes
 */
private fun DrawScope.drawConnection(
    source: Offset,
    target: Offset,
    status: ConnectionStatus,
    canvasOffset: Offset,
    canvasScale: Float,
    dashed: Boolean = false
) {
    val screenSource = canvasToScreen(source, canvasOffset, canvasScale)
    val screenTarget = canvasToScreen(target, canvasOffset, canvasScale)

    // Adjust to connect from node centers
    val nodeWidth = 200f * canvasScale
    val nodeHeight = 80f * canvasScale
    val sourceCenter = Offset(
        screenSource.x + nodeWidth / 2,
        screenSource.y + nodeHeight / 2
    )
    val targetCenter = Offset(
        screenTarget.x + nodeWidth / 2,
        screenTarget.y + nodeHeight / 2
    )

    // Connection color based on status
    val connectionColor = when (status) {
        ConnectionStatus.DISCONNECTED -> Color.Red
        ConnectionStatus.CONNECTING -> Color.Yellow
        ConnectionStatus.CONNECTED -> Color.Green
        ConnectionStatus.STREAMING -> Color.Green
    }

    // Draw Bezier curve
    val path = Path().apply {
        moveTo(sourceCenter.x, sourceCenter.y)

        // Control points for Bezier curve
        val distance = (targetCenter.x - sourceCenter.x) / 2
        cubicTo(
            sourceCenter.x + distance, sourceCenter.y,  // Control point 1
            targetCenter.x - distance, targetCenter.y,  // Control point 2
            targetCenter.x, targetCenter.y              // End point
        )
    }

    val pathEffect = if (dashed) {
        PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
    } else null

    drawPath(
        path = path,
        color = connectionColor,
        style = Stroke(
            width = 3f * canvasScale,
            pathEffect = pathEffect
        )
    )

    // Draw arrow at target
    drawArrow(targetCenter, sourceCenter, connectionColor, canvasScale)
}

/**
 * Draw arrow at end of connection
 */
private fun DrawScope.drawArrow(
    tip: Offset,
    from: Offset,
    color: Color,
    scale: Float
) {
    val arrowSize = 12f * scale
    val angle = kotlin.math.atan2(tip.y - from.y, tip.x - from.x)

    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(
            tip.x - arrowSize * kotlin.math.cos(angle - 0.5f),
            tip.y - arrowSize * kotlin.math.sin(angle - 0.5f)
        )
        lineTo(
            tip.x - arrowSize * kotlin.math.cos(angle + 0.5f),
            tip.y - arrowSize * kotlin.math.sin(angle + 0.5f)
        )
        close()
    }

    drawPath(path, color)
}

/**
 * Convert screen coordinates to canvas coordinates
 */
private fun screenToCanvas(screen: Offset, canvasOffset: Offset, scale: Float): Offset {
    return Offset(
        (screen.x - canvasOffset.x) / scale,
        (screen.y - canvasOffset.y) / scale
    )
}

/**
 * Convert canvas coordinates to screen coordinates
 */
private fun canvasToScreen(canvas: Offset, canvasOffset: Offset, scale: Float): Offset {
    return Offset(
        (canvas.x * scale) + canvasOffset.x,
        (canvas.y * scale) + canvasOffset.y
    )
}

/**
 * Find node at canvas position
 */
private fun findNodeAt(nodes: List<GraphNode>, position: Offset): GraphNode? {
    val nodeWidth = 200f
    val nodeHeight = 80f

    return nodes.findLast { node ->
        position.x >= node.position.x &&
        position.x <= node.position.x + nodeWidth &&
        position.y >= node.position.y &&
        position.y <= node.position.y + nodeHeight
    }
}
