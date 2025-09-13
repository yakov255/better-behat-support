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
import java.awt.geom.QuadCurve2D
import javax.swing.JPanel
import kotlin.math.*

class MethodCallMindMapPanel(private val rootNode: MethodCallTreeNode?) : JPanel() {
    
    private val methodBlocks = mutableListOf<MethodBlock>()
    private val connections = mutableListOf<Connection>()
    private var selectedBlock: MethodBlock? = null
    
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
        preferredSize = JBUI.size(800, 600)
        setupMouseListener()
        setupMouseWheelListener()
        setupMouseMotionListener()
    }
    
    /**
     * Layout nodes in a mindmap style with collision avoidance
     */
    private fun layoutNodes() {
        if (rootNode == null) return
        
        methodBlocks.clear()
        connections.clear()
        
        val centerX = width / 2
        val centerY = height / 2
        
        // Create root block at center
        val rootBlock = MethodBlock(
            node = rootNode,
            x = centerX - 100,
            y = centerY - 50,
            width = 200,
            height = 100
        )
        methodBlocks.add(rootBlock)
        
        // Layout callers around the root with improved algorithm
        layoutCallersImproved(rootNode, rootBlock, centerX, centerY)
        
        // Apply force-based layout to resolve overlaps
        applyForceBasedLayout()
    }
    
    /**
     * Improved layout for caller nodes with better spacing
     */
    private fun layoutCallersImproved(node: MethodCallTreeNode, parentBlock: MethodBlock, centerX: Int, centerY: Int) {
        val callers = node.callers
        if (callers.isEmpty()) return
        
        // Calculate minimum radius based on block sizes and count
        val minRadius = calculateMinimumRadius(callers.size, 200, 100)
        val radius = maxOf(minRadius, 280) // Ensure minimum distance
        
        // Use golden angle for better distribution
        val goldenAngle = PI * (3.0 - sqrt(5.0)) // ~137.5 degrees
        
        callers.forEachIndexed { index, caller ->
            val angle = index * goldenAngle
            var x = centerX + (radius * cos(angle)).toInt() - 100
            var y = centerY + (radius * sin(angle)).toInt() - 50
            
            // Ensure blocks stay within bounds
            x = x.coerceIn(10, width - 210)
            y = y.coerceIn(10, height - 110)
            
            val callerBlock = MethodBlock(
                node = caller,
                x = x,
                y = y,
                width = 200,
                height = 100
            )
            
            // Check for collisions and adjust position
            val adjustedBlock = resolveCollision(callerBlock)
            methodBlocks.add(adjustedBlock)
            
            // Create connection from caller to parent
            connections.add(Connection(adjustedBlock, parentBlock))
            
            // Recursively layout callers of this caller
            if (caller.callers.isNotEmpty()) {
                layoutCallersRecursiveImproved(caller, adjustedBlock, adjustedBlock.x + 100, adjustedBlock.y + 50, radius * 0.7, 1)
            }
        }
    }
    
    /**
     * Calculate minimum radius to avoid overlaps
     */
    private fun calculateMinimumRadius(nodeCount: Int, blockWidth: Int, blockHeight: Int): Int {
        if (nodeCount <= 1) return 150
        
        // Calculate circumference needed for all blocks with padding
        val blockDiagonal = sqrt((blockWidth * blockWidth + blockHeight * blockHeight).toDouble())
        val padding = 30 // Extra space between blocks
        val totalCircumference = nodeCount * (blockDiagonal + padding)
        
        // Calculate radius from circumference
        return (totalCircumference / (2 * PI)).toInt()
    }
    
    /**
     * Resolve collision by finding a non-overlapping position
     */
    private fun resolveCollision(newBlock: MethodBlock): MethodBlock {
        var attempts = 0
        var currentBlock = newBlock
        
        while (attempts < 20 && hasCollision(currentBlock)) {
            // Try moving in a spiral pattern
            val spiralRadius = 20 + attempts * 10
            val spiralAngle = attempts * 0.5
            
            val newX = (currentBlock.x + spiralRadius * cos(spiralAngle)).toInt()
            val newY = (currentBlock.y + spiralRadius * sin(spiralAngle)).toInt()
            
            currentBlock = MethodBlock(
                node = currentBlock.node,
                x = newX.coerceIn(10, width - currentBlock.width - 10),
                y = newY.coerceIn(10, height - currentBlock.height - 10),
                width = currentBlock.width,
                height = currentBlock.height
            )
            
            attempts++
        }
        
        return currentBlock
    }
    
    /**
     * Check if a block collides with existing blocks
     */
    private fun hasCollision(block: MethodBlock): Boolean {
        return methodBlocks.any { existingBlock ->
            blocksOverlap(block, existingBlock)
        }
    }
    
    /**
     * Check if two blocks overlap
     */
    private fun blocksOverlap(block1: MethodBlock, block2: MethodBlock): Boolean {
        val padding = 15 // Minimum space between blocks
        
        return !(block1.x + block1.width + padding <= block2.x ||
                block2.x + block2.width + padding <= block1.x ||
                block1.y + block1.height + padding <= block2.y ||
                block2.y + block2.height + padding <= block1.y)
    }
    
    /**
     * Recursively layout callers with improved spacing
     */
    private fun layoutCallersRecursiveImproved(
        node: MethodCallTreeNode,
        parentBlock: MethodBlock,
        centerX: Int,
        centerY: Int,
        radius: Double,
        depth: Int
    ) {
        if (depth > 2 || node.callers.isEmpty()) return // Reduced depth to prevent overcrowding
        
        val callers = node.callers
        val minRadius = calculateMinimumRadius(callers.size, 150, 80)
        val actualRadius = maxOf(minRadius, radius.toInt())
        
        val goldenAngle = PI * (3.0 - sqrt(5.0))
        
        callers.forEachIndexed { index, caller ->
            val angle = index * goldenAngle + depth * 0.5 // Offset by depth
            var x = centerX + (actualRadius * cos(angle)).toInt() - 75
            var y = centerY + (actualRadius * sin(angle)).toInt() - 40
            
            // Ensure blocks stay within bounds
            x = x.coerceIn(10, width - 160)
            y = y.coerceIn(10, height - 90)
            
            val callerBlock = MethodBlock(
                node = caller,
                x = x,
                y = y,
                width = 150,
                height = 80
            )
            
            val adjustedBlock = resolveCollision(callerBlock)
            methodBlocks.add(adjustedBlock)
            connections.add(Connection(adjustedBlock, parentBlock))
            
            // Continue recursively with smaller radius
            layoutCallersRecursiveImproved(caller, adjustedBlock, adjustedBlock.x + 75, adjustedBlock.y + 40, actualRadius * 0.6, depth + 1)
        }
    }
    
    /**
     * Apply force-based layout to improve spacing
     */
    private fun applyForceBasedLayout() {
        val iterations = 50
        val repulsionStrength = 1000.0
        val dampening = 0.9
        
        repeat(iterations) {
            val forces = mutableMapOf<MethodBlock, Pair<Double, Double>>()
            
            // Calculate repulsion forces between all blocks
            for (i in methodBlocks.indices) {
                for (j in i + 1 until methodBlocks.size) {
                    val block1 = methodBlocks[i]
                    val block2 = methodBlocks[j]
                    
                    val dx = (block2.x + block2.width / 2) - (block1.x + block1.width / 2)
                    val dy = (block2.y + block2.height / 2) - (block1.y + block1.height / 2)
                    val distance = sqrt((dx * dx + dy * dy).toDouble()).coerceAtLeast(1.0)
                    
                    val force = repulsionStrength / (distance * distance)
                    val forceX = force * dx / distance
                    val forceY = force * dy / distance
                    
                    // Apply opposite forces
                    val force1 = forces.getOrDefault(block1, Pair(0.0, 0.0))
                    val force2 = forces.getOrDefault(block2, Pair(0.0, 0.0))
                    
                    forces[block1] = Pair(force1.first - forceX, force1.second - forceY)
                    forces[block2] = Pair(force2.first + forceX, force2.second + forceY)
                }
            }
            
            // Apply forces to move blocks (except root)
            forces.forEach { (block, force) ->
                if (block.node != rootNode) {
                    val newX = (block.x + force.first * dampening).toInt()
                    val newY = (block.y + force.second * dampening).toInt()
                    
                    // Update block position within bounds
                    val index = methodBlocks.indexOf(block)
                    if (index >= 0) {
                        methodBlocks[index] = MethodBlock(
                            node = block.node,
                            x = newX.coerceIn(10, width - block.width - 10),
                            y = newY.coerceIn(10, height - block.height - 10),
                            width = block.width,
                            height = block.height
                        )
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
            val maxX = methodBlocks.maxOfOrNull { it.x + it.width } ?: 800
            val maxY = methodBlocks.maxOfOrNull { it.y + it.height } ?: 600
            val scaledWidth = ((maxX + 100) * zoomFactor).toInt()
            val scaledHeight = ((maxY + 100) * zoomFactor).toInt()
            preferredSize = JBUI.size(maxOf(scaledWidth, 800), maxOf(scaledHeight, 600))
            revalidate()
        }
    }
    
    /**
     * Find method block at coordinates
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
        if (methodBlocks.isEmpty() || (width != preferredSize.width || height != preferredSize.height)) {
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
            
            // Calculate connection points (center of blocks)
            val fromX = fromBlock.x + fromBlock.width / 2
            val fromY = fromBlock.y + fromBlock.height / 2
            val toX = toBlock.x + toBlock.width / 2
            val toY = toBlock.y + toBlock.height / 2
            
            // Draw curved arrow
            drawArrow(g2d, fromX, fromY, toX, toY)
        }
    }
    
    /**
     * Draw curved arrow between two points
     */
    private fun drawArrow(g2d: Graphics2D, fromX: Int, fromY: Int, toX: Int, toY: Int) {
        // Control point for curve (offset perpendicular to line)
        val midX = (fromX + toX) / 2
        val midY = (fromY + toY) / 2
        val dx = toX - fromX
        val dy = toY - fromY
        val length = sqrt((dx * dx + dy * dy).toDouble())
        val offset = 30.0
        val ctrlX = midX - (dy * offset / length).toInt()
        val ctrlY = midY + (dx * offset / length).toInt()
        
        // Draw curve
        val curve = QuadCurve2D.Float(
            fromX.toFloat(), fromY.toFloat(),
            ctrlX.toFloat(), ctrlY.toFloat(),
            toX.toFloat(), toY.toFloat()
        )
        g2d.draw(curve)
        
        // Draw arrowhead
        val arrowLength = 10
        val arrowAngle = PI / 6
        val angle = atan2((toY - ctrlY).toDouble(), (toX - ctrlX).toDouble())
        
        val arrowX1 = toX - arrowLength * cos(angle - arrowAngle)
        val arrowY1 = toY - arrowLength * sin(angle - arrowAngle)
        val arrowX2 = toX - arrowLength * cos(angle + arrowAngle)
        val arrowY2 = toY - arrowLength * sin(angle + arrowAngle)
        
        g2d.drawLine(toX, toY, arrowX1.toInt(), arrowY1.toInt())
        g2d.drawLine(toX, toY, arrowX2.toInt(), arrowY2.toInt())
    }
    
    /**
     * Draw method blocks with code context
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
        g2d.fillRoundRect(block.x, block.y, block.width, block.height, 10, 10)
        
        // Block border
        g2d.color = if (isSelected) JBColor.BLUE else JBColor.DARK_GRAY
        g2d.stroke = BasicStroke(if (isSelected) 3f else 1f)
        g2d.drawRoundRect(block.x, block.y, block.width, block.height, 10, 10)
        
        // Method signature
        g2d.color = JBColor.BLACK
        g2d.font = Font(Font.SANS_SERIF, Font.BOLD, 11)
        val methodName = block.node.getSimpleDisplayText()
        val truncatedName = if (methodName.length > 25) methodName.substring(0, 22) + "..." else methodName
        g2d.drawString(truncatedName, block.x + 5, block.y + 15)
        
        // Code context (mini editor)
        g2d.font = Font(Font.MONOSPACED, Font.PLAIN, 9)
        val codeLines = block.node.getFormattedCodeContext().split("\n")
        var yOffset = 30
        
        codeLines.take(5).forEach { line -> // Show max 5 lines
            val truncatedLine = if (line.length > 30) line.substring(0, 27) + "..." else line
            g2d.color = if (line.startsWith("→")) JBColor.RED else JBColor.DARK_GRAY
            g2d.drawString(truncatedLine, block.x + 5, block.y + yOffset)
            yOffset += 12
        }
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