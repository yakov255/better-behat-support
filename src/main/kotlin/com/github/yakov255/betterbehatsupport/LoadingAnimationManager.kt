package com.github.yakov255.betterbehatsupport

import com.intellij.ui.JBColor
import java.awt.*
import java.awt.geom.Arc2D
import javax.swing.Timer

/**
 * Manages loading animations for method call diagrams
 */
class LoadingAnimationManager {
    
    companion object {
        private const val ANIMATION_DELAY = 50 // milliseconds
        private const val SPINNER_SIZE = 16
        private const val PROGRESS_BAR_HEIGHT = 4
    }
    
    private var animationFrame = 0
    private var isAnimating = false
    private val animationListeners = mutableListOf<() -> Unit>()
    
    private val animationTimer = Timer(ANIMATION_DELAY) {
        animationFrame = (animationFrame + 1) % 360
        notifyAnimationListeners()
    }
    
    /**
     * Start the animation timer
     */
    fun startAnimation() {
        if (!isAnimating) {
            isAnimating = true
            animationTimer.start()
        }
    }
    
    /**
     * Stop the animation timer
     */
    fun stopAnimation() {
        if (isAnimating) {
            isAnimating = false
            animationTimer.stop()
        }
    }
    
    /**
     * Get current spinner rotation angle in degrees
     */
    fun getSpinnerRotation(): Double {
        return animationFrame * 4.0 // 4 degrees per frame for smooth rotation
    }
    
    /**
     * Draw a loading spinner at the specified location
     */
    fun drawSpinner(g2d: Graphics2D, x: Int, y: Int, size: Int = SPINNER_SIZE) {
        val originalTransform = g2d.transform
        val originalStroke = g2d.stroke
        
        try {
            // Center the spinner
            val centerX = x + size / 2
            val centerY = y + size / 2
            
            // Rotate around center
            g2d.translate(centerX, centerY)
            g2d.rotate(Math.toRadians(getSpinnerRotation()))
            g2d.translate(-size / 2, -size / 2)
            
            // Draw spinner arcs with varying opacity
            g2d.stroke = BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            
            val arcCount = 8
            val arcAngle = 360.0 / arcCount
            
            for (i in 0 until arcCount) {
                val alpha = (255 * (i + 1) / arcCount).coerceIn(50, 255)
                g2d.color = Color(JBColor.BLUE.red, JBColor.BLUE.green, JBColor.BLUE.blue, alpha)
                
                val startAngle = i * arcAngle
                val arc = Arc2D.Double(
                    2.0, 2.0, 
                    (size - 4).toDouble(), (size - 4).toDouble(),
                    startAngle, arcAngle * 0.6, 
                    Arc2D.OPEN
                )
                g2d.draw(arc)
            }
            
        } finally {
            g2d.transform = originalTransform
            g2d.stroke = originalStroke
        }
    }
    
    /**
     * Draw a progress bar at the specified location
     */
    fun drawProgressBar(
        g2d: Graphics2D, 
        x: Int, 
        y: Int, 
        width: Int, 
        progress: Float,
        height: Int = PROGRESS_BAR_HEIGHT
    ) {
        val originalColor = g2d.color
        
        try {
            // Background
            g2d.color = JBColor.LIGHT_GRAY
            g2d.fillRoundRect(x, y, width, height, height / 2, height / 2)
            
            // Progress fill
            if (progress > 0) {
                val progressWidth = (width * progress.coerceIn(0f, 1f)).toInt()
                g2d.color = JBColor.BLUE
                g2d.fillRoundRect(x, y, progressWidth, height, height / 2, height / 2)
            }
            
            // Border
            g2d.color = JBColor.GRAY
            g2d.drawRoundRect(x, y, width, height, height / 2, height / 2)
            
        } finally {
            g2d.color = originalColor
        }
    }
    
    /**
     * Draw an expand/collapse button
     */
    fun drawExpandButton(
        g2d: Graphics2D,
        x: Int,
        y: Int,
        size: Int,
        isExpanded: Boolean,
        isHovered: Boolean = false
    ) {
        val originalColor = g2d.color
        val originalStroke = g2d.stroke
        
        try {
            // Button background
            g2d.color = if (isHovered) JBColor.LIGHT_GRAY.brighter() else JBColor.LIGHT_GRAY
            g2d.fillOval(x, y, size, size)
            
            // Button border
            g2d.color = JBColor.GRAY
            g2d.stroke = BasicStroke(1f)
            g2d.drawOval(x, y, size, size)
            
            // Arrow icon
            g2d.color = JBColor.BLACK
            g2d.stroke = BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            
            val centerX = x + size / 2
            val centerY = y + size / 2
            val arrowSize = size / 3
            
            if (isExpanded) {
                // Down arrow (collapse)
                val points = arrayOf(
                    Point(centerX - arrowSize / 2, centerY - arrowSize / 4),
                    Point(centerX, centerY + arrowSize / 4),
                    Point(centerX + arrowSize / 2, centerY - arrowSize / 4)
                )
                g2d.drawPolyline(
                    points.map { it.x }.toIntArray(),
                    points.map { it.y }.toIntArray(),
                    points.size
                )
            } else {
                // Right arrow (expand)
                val points = arrayOf(
                    Point(centerX - arrowSize / 4, centerY - arrowSize / 2),
                    Point(centerX + arrowSize / 4, centerY),
                    Point(centerX - arrowSize / 4, centerY + arrowSize / 2)
                )
                g2d.drawPolyline(
                    points.map { it.x }.toIntArray(),
                    points.map { it.y }.toIntArray(),
                    points.size
                )
            }
            
        } finally {
            g2d.color = originalColor
            g2d.stroke = originalStroke
        }
    }
    
    /**
     * Draw an error indicator
     */
    fun drawErrorIndicator(g2d: Graphics2D, x: Int, y: Int, size: Int = SPINNER_SIZE) {
        val originalColor = g2d.color
        val originalStroke = g2d.stroke
        
        try {
            // Error background
            g2d.color = JBColor.RED.brighter()
            g2d.fillOval(x, y, size, size)
            
            // Error border
            g2d.color = JBColor.RED
            g2d.stroke = BasicStroke(1f)
            g2d.drawOval(x, y, size, size)
            
            // Exclamation mark
            g2d.color = JBColor.WHITE
            g2d.stroke = BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            
            val centerX = x + size / 2
            val centerY = y + size / 2
            
            // Exclamation line
            g2d.drawLine(centerX, centerY - size / 3, centerX, centerY)
            
            // Exclamation dot
            g2d.fillOval(centerX - 1, centerY + size / 6, 2, 2)
            
        } finally {
            g2d.color = originalColor
            g2d.stroke = originalStroke
        }
    }
    
    /**
     * Draw a pending indicator (clock icon)
     */
    fun drawPendingIndicator(g2d: Graphics2D, x: Int, y: Int, size: Int = SPINNER_SIZE) {
        val originalColor = g2d.color
        val originalStroke = g2d.stroke
        
        try {
            // Clock background
            g2d.color = JBColor.YELLOW.darker()
            g2d.fillOval(x, y, size, size)
            
            // Clock border
            g2d.color = JBColor.ORANGE
            g2d.stroke = BasicStroke(1f)
            g2d.drawOval(x, y, size, size)
            
            // Clock hands
            g2d.color = JBColor.BLACK
            g2d.stroke = BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            
            val centerX = x + size / 2
            val centerY = y + size / 2
            val radius = size / 3
            
            // Hour hand (pointing to 3)
            g2d.drawLine(centerX, centerY, centerX + radius / 2, centerY)
            
            // Minute hand (pointing to 12)
            g2d.drawLine(centerX, centerY, centerX, centerY - radius)
            
        } finally {
            g2d.color = originalColor
            g2d.stroke = originalStroke
        }
    }
    
    /**
     * Add a listener for animation updates
     */
    fun addAnimationListener(listener: () -> Unit) {
        animationListeners.add(listener)
    }
    
    /**
     * Remove an animation listener
     */
    fun removeAnimationListener(listener: () -> Unit) {
        animationListeners.remove(listener)
    }
    
    /**
     * Notify all animation listeners
     */
    private fun notifyAnimationListeners() {
        animationListeners.forEach { listener ->
            try {
                listener()
            } catch (e: Exception) {
                // Ignore listener errors
            }
        }
    }
    
    /**
     * Check if animation is currently running
     */
    fun isAnimating(): Boolean = isAnimating
    
    /**
     * Dispose and cleanup resources
     */
    fun dispose() {
        stopAnimation()
        animationListeners.clear()
    }
}