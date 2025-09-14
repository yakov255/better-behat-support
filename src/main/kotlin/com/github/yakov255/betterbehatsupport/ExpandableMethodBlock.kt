package com.github.yakov255.betterbehatsupport

import java.awt.Point
import java.awt.Rectangle

/**
 * Enhanced method block with loading states and user interaction support
 */
data class ExpandableMethodBlock(
    val node: MethodCallTreeNode,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    var isHovered: Boolean = false,
    var isExpandButtonHovered: Boolean = false,
    var showExpandButton: Boolean = true,
    var showLoadingIndicator: Boolean = false
) {
    
    companion object {
        private const val EXPAND_BUTTON_SIZE = 16
        private const val EXPAND_BUTTON_MARGIN = 4
        private const val LOADING_INDICATOR_SIZE = 16
        private const val LOADING_INDICATOR_MARGIN = 4
    }
    
    /**
     * Get the bounds of the entire block
     */
    fun getBounds(): Rectangle {
        return Rectangle(x, y, width, height)
    }
    
    /**
     * Get the bounds of the expand/collapse button
     */
    fun getExpandButtonBounds(): Rectangle {
        return Rectangle(
            x + width - EXPAND_BUTTON_SIZE - EXPAND_BUTTON_MARGIN,
            y + EXPAND_BUTTON_MARGIN,
            EXPAND_BUTTON_SIZE,
            EXPAND_BUTTON_SIZE
        )
    }
    
    /**
     * Get the bounds of the loading indicator
     */
    fun getLoadingIndicatorBounds(): Rectangle {
        return Rectangle(
            x + width - LOADING_INDICATOR_SIZE - LOADING_INDICATOR_MARGIN,
            y + height - LOADING_INDICATOR_SIZE - LOADING_INDICATOR_MARGIN,
            LOADING_INDICATOR_SIZE,
            LOADING_INDICATOR_SIZE
        )
    }
    
    /**
     * Get the bounds for the progress bar
     */
    fun getProgressBarBounds(): Rectangle {
        val progressBarHeight = 4
        val margin = 2
        return Rectangle(
            x + margin,
            y + height - progressBarHeight - margin,
            width - margin * 2,
            progressBarHeight
        )
    }
    
    /**
     * Check if a point is within the block bounds
     */
    fun contains(point: Point): Boolean {
        return getBounds().contains(point)
    }
    
    /**
     * Check if a point is within the expand button bounds
     */
    fun containsExpandButton(point: Point): Boolean {
        return showExpandButton && getExpandButtonBounds().contains(point)
    }
    
    /**
     * Check if a point is within the loading indicator bounds
     */
    fun containsLoadingIndicator(point: Point): Boolean {
        return showLoadingIndicator && getLoadingIndicatorBounds().contains(point)
    }
    
    /**
     * Determine if the spinner should be shown
     */
    fun shouldShowSpinner(): Boolean {
        return node.isLoading()
    }
    
    /**
     * Determine if the progress bar should be shown
     */
    fun shouldShowProgressBar(): Boolean {
        return node.isLoading() && node.loadingProgress > 0
    }
    
    /**
     * Determine if the expand button should be shown
     */
    fun shouldShowExpandButton(): Boolean {
        return showExpandButton && node.canExpand() && !node.isLoading()
    }
    
    /**
     * Determine if the error indicator should be shown
     */
    fun shouldShowErrorIndicator(): Boolean {
        return node.hasError()
    }
    
    /**
     * Determine if the pending indicator should be shown
     */
    fun shouldShowPendingIndicator(): Boolean {
        return node.loadingState == LoadingState.EXPANDABLE && !node.isLoading()
    }
    
    /**
     * Get the tooltip text for this block
     */
    fun getTooltipText(): String {
        return buildString {
            append(node.getDisplayText())
            append("\n")
            append("State: ${node.getLoadingStateText()}")
            
            if (node.isLoading()) {
                append("\nProgress: ${(node.loadingProgress * 100).toInt()}%")
            }
            
            if (node.hasError()) {
                append("\nError: ${node.errorMessage}")
                append("\nClick to retry")
            }
            
            if (node.canExpand()) {
                append("\nClick ▶ to expand callers")
            }
            
            append("\nDouble-click to navigate to method")
        }
    }
    
    /**
     * Get the main content area bounds (excluding buttons and indicators)
     */
    fun getContentBounds(): Rectangle {
        val buttonWidth = if (shouldShowExpandButton()) EXPAND_BUTTON_SIZE + EXPAND_BUTTON_MARGIN else 0
        return Rectangle(
            x,
            y,
            width - buttonWidth,
            height
        )
    }
    
    /**
     * Check if this block represents the root node
     */
    fun isRootNode(rootNode: MethodCallTreeNode?): Boolean {
        return node == rootNode
    }
    
    /**
     * Check if this block is currently selected
     */
    fun isSelected(selectedBlock: ExpandableMethodBlock?): Boolean {
        return this == selectedBlock
    }
    
    /**
     * Get the display priority for rendering order
     * Higher priority blocks are rendered on top
     */
    fun getDisplayPriority(): Int {
        return when {
            node.hasError() -> 100
            node.isLoading() -> 90
            isHovered -> 80
            node.loadingState == LoadingState.EXPANDABLE -> 70
            else -> 50
        }
    }
    
    /**
     * Create a copy of this block with updated position
     */
    fun withPosition(newX: Int, newY: Int): ExpandableMethodBlock {
        return copy(x = newX, y = newY)
    }
    
    /**
     * Create a copy of this block with updated hover state
     */
    fun withHoverState(hovered: Boolean, expandButtonHovered: Boolean = false): ExpandableMethodBlock {
        return copy(isHovered = hovered, isExpandButtonHovered = expandButtonHovered)
    }
    
    /**
     * Create a copy of this block with updated visibility settings
     */
    fun withVisibility(
        showExpand: Boolean = showExpandButton,
        showLoading: Boolean = showLoadingIndicator
    ): ExpandableMethodBlock {
        return copy(showExpandButton = showExpand, showLoadingIndicator = showLoading)
    }
}