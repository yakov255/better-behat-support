package com.github.yakov255.betterbehatsupport

import com.intellij.pom.Navigatable
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.event.MouseWheelEvent
import java.awt.event.MouseWheelListener
import java.awt.geom.AffineTransform
import java.awt.geom.NoninvertibleTransformException
import java.awt.geom.Point2D
import javax.swing.JPanel
import kotlin.math.*

class MethodCallBlockDiagramPanel(private val rootNode: MethodCallTreeNode?) : JPanel() {
    
    private val methodBlocks = mutableListOf<MethodBlock>()
    private val connections = mutableListOf<Connection>()
    private var selectedBlock: MethodBlock? = null
    
    // Layout constants
    private val blockWidth = 180
    private val blockHeight = 60
    private val horizontalSpacing = 200
    private val verticalSpacing = 80
    private val leftMargin = 50
    private val topMargin = 50
    
    // Zoom and pan variables
    private var zoomFactor = 1.0
    private var panX = 0.0
    private var panY = 0.0
    private var lastPanPoint: Point? = null
    private var isPanning = false
    
    // Zoom constraints
    private val minZoom = 0.1
    private val maxZoom = 5.0
    private val zoomStep = 0.1
    
    init {
        background = JBColor.WHITE
        preferredSize = JBUI.size(1000, 600)
        setupMouseListener()
        setupMouseWheelListener()
        setupMouseMotionListener()
    }
    
    /**
     * Layout nodes in a left-to-right block diagram with full caller hierarchy
     */
    private fun layoutNodes() {
        if (rootNode == null) return
        
        methodBlocks.clear()
        connections.clear()
        
        // Build hierarchy layers
        val layers = buildHierarchyLayers(rootNode)
        
        // Calculate total width needed
        val totalWidth = layers.size * horizontalSpacing + leftMargin * 2
        val maxNodesInLayer = layers.maxOfOrNull { it.size } ?: 1
        val totalHeight = maxNodesInLayer * verticalSpacing + topMargin * 2
        
        // Update preferred size if needed
        if (totalWidth > width || totalHeight > height) {
            preferredSize = JBUI.size(maxOf(totalWidth, 1000), maxOf(totalHeight, 600))
            revalidate()
        }
        
        // Position blocks in layers
        positionBlocksInLayers(layers)
        
        // Create connections
        createConnections(layers)
    }
    
    /**
     * Build hierarchy layers from root node
     */
    private fun buildHierarchyLayers(root: MethodCallTreeNode): List<List<MethodCallTreeNode>> {
        val layers = mutableListOf<MutableList<MethodCallTreeNode>>()
        val visited = mutableSetOf<String>()
        
        // Add root node as the rightmost layer
        layers.add(mutableListOf(root))
        visited.add(root.methodId)
        
        // Build caller layers recursively
        buildCallerLayers(root, layers, visited, 0)
        
        // Reverse layers so callers are on the left
        return layers.reversed()
    }
    
    /**
     * Recursively build caller layers
     */
    private fun buildCallerLayers(
        node: MethodCallTreeNode,
        layers: MutableList<MutableList<MethodCallTreeNode>>,
        visited: MutableSet<String>,
        currentLayerIndex: Int
    ) {
        if (node.callers.isEmpty()) return
        
        // Ensure we have a layer for callers
        val callerLayerIndex = currentLayerIndex + 1
        while (layers.size <= callerLayerIndex) {
            layers.add(mutableListOf())
        }
        
        // Add callers to the layer
        node.callers.forEach { caller ->
            if (!visited.contains(caller.methodId)) {
                layers[callerLayerIndex].add(caller)
                visited.add(caller.methodId)
                
                // Recursively add callers of this caller
                buildCallerLayers(caller, layers, visited, callerLayerIndex)
            }
        }
    }
    
    /**
     * Position blocks in their respective layers
     */
    private fun positionBlocksInLayers(layers: List<List<MethodCallTreeNode>>) {
        layers.forEachIndexed { layerIndex, layer ->
            val x = leftMargin + layerIndex * horizontalSpacing
            
            // Center nodes vertically in the layer
            val totalLayerHeight = layer.size * verticalSpacing
            val startY = (height - totalLayerHeight) / 2
            
            layer.forEachIndexed { nodeIndex, node ->
                val y = startY + nodeIndex * verticalSpacing
                
                val block = MethodBlock(
                    node = node,
                    x = x,
                    y = y,
                    width = blockWidth,
                    height = blockHeight
                )
                methodBlocks.add(block)
            }
        }
    }
    
    /**
     * Create connections between blocks
     */
    private fun createConnections(layers: List<List<MethodCallTreeNode>>) {
        // Create connections from each layer to the next (left to right)
        for (layerIndex in 0 until layers.size - 1) {
            val currentLayer = layers[layerIndex]
            val nextLayer = layers[layerIndex + 1]
            
            currentLayer.forEach { caller ->
                nextLayer.forEach { callee ->
                    // Check if caller actually calls callee (caller -> callee relationship)
                    if (callee.callers.any { it.methodId == caller.methodId }) {
                        
                        val fromBlock = methodBlocks.find { it.node.methodId == caller.methodId }
                        val toBlock = methodBlocks.find { it.node.methodId == callee.methodId }
                        
                        if (fromBlock != null && toBlock != null) {
                            connections.add(Connection(fromBlock, toBlock))
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Setup mouse listener for navigation and panning
     */
    private fun setupMouseListener() {
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (!isPanning) {
                    val transformedPoint = transformPoint(e.point)
                    val clickedBlock = findBlockAt(transformedPoint.x.toInt(), transformedPoint.y.toInt())
                    if (clickedBlock != null) {
                        if (e.clickCount == 2) {
                            // Double-click to navigate
                            navigateToMethod(clickedBlock.node)
                        } else {
                            // Single click to select
                            selectedBlock = clickedBlock
                            repaint()
                        }
                    }
                }
            }
            
            override fun mousePressed(e: MouseEvent) {
                if (e.button == MouseEvent.BUTTON1) {
                    lastPanPoint = e.point
                    isPanning = false
                }
            }
            
            override fun mouseReleased(e: MouseEvent) {
                lastPanPoint = null
                isPanning = false
                cursor = Cursor.getDefaultCursor()
            }
        })
    }
    
    /**
     * Setup mouse wheel listener for zooming
     */
    private fun setupMouseWheelListener() {
        addMouseWheelListener { e ->
            val oldZoom = zoomFactor
            val zoomChange = if (e.wheelRotation < 0) zoomStep else -zoomStep
            zoomFactor = (zoomFactor + zoomChange).coerceIn(minZoom, maxZoom)
            
            if (zoomFactor != oldZoom) {
                // Zoom towards mouse position
                val mousePoint = e.point
                val zoomRatio = zoomFactor / oldZoom
                
                panX = mousePoint.x - (mousePoint.x - panX) * zoomRatio
                panY = mousePoint.y - (mousePoint.y - panY) * zoomRatio
                
                updatePreferredSize()
                repaint()
            }
        }
    }
    
    /**
     * Setup mouse motion listener for panning
     */
    private fun setupMouseMotionListener() {
        addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseDragged(e: MouseEvent) {
                lastPanPoint?.let { lastPoint ->
                    val dx = e.x - lastPoint.x
                    val dy = e.y - lastPoint.y
                    
                    // Only start panning if we've moved a minimum distance
                    if (!isPanning && (abs(dx) > 3 || abs(dy) > 3)) {
                        isPanning = true
                        cursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
                    }
                    
                    if (isPanning) {
                        panX += dx
                        panY += dy
                        lastPanPoint = e.point
                        repaint()
                    }
                }
            }
        })
    }
    
    /**
     * Transform screen coordinates to world coordinates
     */
    private fun transformPoint(screenPoint: Point): Point2D {
        val worldX = (screenPoint.x - panX) / zoomFactor
        val worldY = (screenPoint.y - panY) / zoomFactor
        return Point2D.Double(worldX, worldY)
    }
    
    /**
     * Update preferred size based on zoom factor
     */
    private fun updatePreferredSize() {
        if (methodBlocks.isNotEmpty()) {
            val maxX = methodBlocks.maxOfOrNull { it.x + it.width } ?: 1000
            val maxY = methodBlocks.maxOfOrNull { it.y + it.height } ?: 600
            val scaledWidth = ((maxX + 100) * zoomFactor).toInt()
            val scaledHeight = ((maxY + 100) * zoomFactor).toInt()
            preferredSize = JBUI.size(maxOf(scaledWidth, 1000), maxOf(scaledHeight, 600))
            revalidate()
        }
    }
    
    /**
     * Find method block at coordinates (in world space)
     */
    private fun findBlockAt(x: Int, y: Int): MethodBlock? {
        return methodBlocks.find { block ->
            x >= block.x && x <= block.x + block.width &&
            y >= block.y && y <= block.y + block.height
        }
    }
    
    /**
     * Navigate to method definition
     */
    private fun navigateToMethod(node: MethodCallTreeNode) {
        try {
            val method = node.method
            if (method is Navigatable && method.canNavigate()) {
                method.navigate(true)
            }
        } catch (e: Exception) {
            // Handle navigation errors gracefully
        }
    }
    
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        
        if (rootNode == null) {
            g.color = Color.GRAY
            g.drawString("No method call tree available", 20, 30)
            return
        }
        
        val g2d = g as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        
        // Layout nodes on first paint or when size changes
        if (methodBlocks.isEmpty()) {
            layoutNodes()
        }
        
        // Save original transform
        val originalTransform = g2d.transform
        
        // Apply zoom and pan transformations
        g2d.translate(panX, panY)
        g2d.scale(zoomFactor, zoomFactor)
        
        // Draw connections first (behind blocks)
        drawConnections(g2d)
        
        // Draw method blocks
        drawMethodBlocks(g2d)
        
        // Restore original transform
        g2d.transform = originalTransform
        
        // Draw zoom indicator
        drawZoomIndicator(g2d)
    }
    
    /**
     * Draw zoom level indicator
     */
    private fun drawZoomIndicator(g2d: Graphics2D) {
        g2d.color = JBColor.DARK_GRAY
        g2d.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
        val zoomText = "Zoom: ${(zoomFactor * 100).toInt()}%"
        val fontMetrics = g2d.fontMetrics
        val textWidth = fontMetrics.stringWidth(zoomText)
        g2d.drawString(zoomText, width - textWidth - 10, 20)
        
        // Draw instructions
        g2d.font = Font(Font.SANS_SERIF, Font.PLAIN, 10)
        g2d.color = JBColor.GRAY
        val instructions = "Mouse wheel: zoom, Drag: pan"
        g2d.drawString(instructions, 10, height - 10)
    }
    
    /**
     * Draw connections between method blocks
     */
    private fun drawConnections(g2d: Graphics2D) {
        g2d.color = JBColor.GRAY
        g2d.stroke = BasicStroke(2f)
        
        connections.forEach { connection ->
            val fromBlock = connection.from
            val toBlock = connection.to
            
            // Calculate connection points (right edge of from block to left edge of to block)
            val fromX = fromBlock.x + fromBlock.width
            val fromY = fromBlock.y + fromBlock.height / 2
            val toX = toBlock.x
            val toY = toBlock.y + toBlock.height / 2
            
            // Draw straight arrow
            drawArrow(g2d, fromX, fromY, toX, toY)
        }
    }
    
    /**
     * Draw straight arrow between two points
     */
    private fun drawArrow(g2d: Graphics2D, fromX: Int, fromY: Int, toX: Int, toY: Int) {
        // Draw line
        g2d.drawLine(fromX, fromY, toX, toY)
        
        // Draw arrowhead
        val arrowLength = 10
        val arrowAngle = PI / 6
        val angle = atan2((toY - fromY).toDouble(), (toX - fromX).toDouble())
        
        val arrowX1 = toX - arrowLength * cos(angle - arrowAngle)
        val arrowY1 = toY - arrowLength * sin(angle - arrowAngle)
        val arrowX2 = toX - arrowLength * cos(angle + arrowAngle)
        val arrowY2 = toY - arrowLength * sin(angle + arrowAngle)
        
        g2d.drawLine(toX, toY, arrowX1.toInt(), arrowY1.toInt())
        g2d.drawLine(toX, toY, arrowX2.toInt(), arrowY2.toInt())
    }
    
    /**
     * Draw method blocks with minimal content
     */
    private fun drawMethodBlocks(g2d: Graphics2D) {
        methodBlocks.forEach { block ->
            drawMethodBlock(g2d, block)
        }
    }
    
    /**
     * Draw individual method block
     */
    private fun drawMethodBlock(g2d: Graphics2D, block: MethodBlock) {
        val isSelected = block == selectedBlock
        val isRoot = block.node == rootNode
        
        // Block background
        g2d.color = when {
            isRoot -> JBColor.CYAN.darker()
            isSelected -> JBColor.YELLOW.darker()
            else -> JBColor.LIGHT_GRAY
        }
        g2d.fillRoundRect(block.x, block.y, block.width, block.height, 8, 8)
        
        // Block border
        g2d.color = if (isSelected) JBColor.BLUE else JBColor.DARK_GRAY
        g2d.stroke = BasicStroke(if (isSelected) 2f else 1f)
        g2d.drawRoundRect(block.x, block.y, block.width, block.height, 8, 8)
        
        // Method signature (class::method format only)
        g2d.color = JBColor.BLACK
        g2d.font = Font(Font.SANS_SERIF, Font.BOLD, 11)
        val methodName = block.node.getSimpleDisplayText()
        
        // Center text in block
        val fontMetrics = g2d.fontMetrics
        val textWidth = fontMetrics.stringWidth(methodName)
        val textHeight = fontMetrics.height
        
        val textX = block.x + (block.width - textWidth) / 2
        val textY = block.y + (block.height + textHeight) / 2 - fontMetrics.descent
        
        // Truncate if too long
        val truncatedName = if (textWidth > block.width - 10) {
            val availableChars = (methodName.length * (block.width - 10)) / textWidth
            if (availableChars > 3) methodName.substring(0, availableChars - 3) + "..." else "..."
        } else {
            methodName
        }
        
        g2d.drawString(truncatedName, textX, textY)
    }
    
    /**
     * Data class for method block positioning
     */
    private data class MethodBlock(
        val node: MethodCallTreeNode,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int
    )
    
    /**
     * Data class for connections between blocks
     */
    private data class Connection(
        val from: MethodBlock,
        val to: MethodBlock
    )
}