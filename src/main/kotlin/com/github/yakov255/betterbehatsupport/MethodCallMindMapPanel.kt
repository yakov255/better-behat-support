package com.github.yakov255.betterbehatsupport

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.pom.Navigatable
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.QuadCurve2D
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.border.LineBorder
import kotlin.math.*

class MethodCallMindMapPanel(private val rootNode: MethodCallTreeNode?) : JPanel() {
    
    private val methodBlocks = mutableListOf<MethodBlock>()
    private val connections = mutableListOf<Connection>()
    private var selectedBlock: MethodBlock? = null
    
    init {
        background = JBColor.WHITE
        preferredSize = JBUI.size(800, 600)
        
        if (rootNode != null) {
            layoutNodes()
            setupMouseListener()
        }
    }
    
    /**
     * Layout nodes in a mindmap style
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
        
        // Layout callers around the root
        layoutCallers(rootNode, rootBlock, centerX, centerY)
    }
    
    /**
     * Layout caller nodes around the root
     */
    private fun layoutCallers(node: MethodCallTreeNode, parentBlock: MethodBlock, centerX: Int, centerY: Int) {
        val callers = node.callers
        if (callers.isEmpty()) return
        
        val radius = 250
        val angleStep = 2 * PI / maxOf(callers.size, 1)
        
        callers.forEachIndexed { index, caller ->
            val angle = index * angleStep - PI / 2 // Start from top
            val x = centerX + (radius * cos(angle)).toInt() - 100
            val y = centerY + (radius * sin(angle)).toInt() - 50
            
            val callerBlock = MethodBlock(
                node = caller,
                x = x,
                y = y,
                width = 200,
                height = 100
            )
            methodBlocks.add(callerBlock)
            
            // Create connection from caller to parent
            connections.add(Connection(callerBlock, parentBlock))
            
            // Recursively layout callers of this caller (smaller radius)
            if (caller.callers.isNotEmpty()) {
                layoutCallersRecursive(caller, callerBlock, x + 100, y + 50, radius / 2, 1)
            }
        }
    }
    
    /**
     * Recursively layout callers with decreasing radius
     */
    private fun layoutCallersRecursive(
        node: MethodCallTreeNode, 
        parentBlock: MethodBlock, 
        centerX: Int, 
        centerY: Int, 
        radius: Int, 
        depth: Int
    ) {
        if (depth > 3 || node.callers.isEmpty()) return // Limit depth
        
        val callers = node.callers
        val angleStep = 2 * PI / maxOf(callers.size, 1)
        
        callers.forEachIndexed { index, caller ->
            val angle = index * angleStep
            val x = centerX + (radius * cos(angle)).toInt() - 75
            val y = centerY + (radius * sin(angle)).toInt() - 40
            
            val callerBlock = MethodBlock(
                node = caller,
                x = x,
                y = y,
                width = 150,
                height = 80
            )
            methodBlocks.add(callerBlock)
            connections.add(Connection(callerBlock, parentBlock))
            
            // Continue recursively with smaller radius
            layoutCallersRecursive(caller, callerBlock, x + 75, y + 40, radius / 2, depth + 1)
        }
    }
    
    /**
     * Setup mouse listener for navigation
     */
    private fun setupMouseListener() {
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val clickedBlock = findBlockAt(e.x, e.y)
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
        })
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
        
        // Relayout if size changed
        if (methodBlocks.isNotEmpty() && (width != preferredSize.width || height != preferredSize.height)) {
            layoutNodes()
        }
        
        // Draw connections first (behind blocks)
        drawConnections(g2d)
        
        // Draw method blocks
        drawMethodBlocks(g2d)
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