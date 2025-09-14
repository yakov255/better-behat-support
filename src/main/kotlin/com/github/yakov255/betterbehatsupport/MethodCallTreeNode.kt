package com.github.yakov255.betterbehatsupport

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement

/**
 * Represents a node in the method call tree
 */
data class MethodCallTreeNode(
    val method: PsiNamedElement,
    val fileName: String,
    val lineNumber: Int,
    val methodSignature: String,
    val codeContext: String = "",
    val callers: MutableList<MethodCallTreeNode> = mutableListOf(),
    val callees: MutableList<MethodCallTreeNode> = mutableListOf(),
    var isExpanded: Boolean = true,
    var x: Int = 0,
    var y: Int = 0,
    var width: Int = 200,
    var height: Int = 100,
    // Async loading properties
    var loadingState: LoadingState = LoadingState.NOT_LOADED,
    var hasMoreCallers: Boolean = true,
    var loadingProgress: Float = 0.0f,
    var errorMessage: String? = null,
    var discoveryTaskId: String? = null,
    var isUserExpanded: Boolean = false
) {
    
    /**
     * Unique identifier for this method to detect circular references
     */
    val methodId: String = "${method.containingFile?.name}:${method.name}:$lineNumber"
    
    /**
     * Add a caller to this method
     */
    fun addCaller(caller: MethodCallTreeNode) {
        if (!callers.any { it.methodId == caller.methodId }) {
            callers.add(caller)
        }
    }
    
    /**
     * Add a callee to this method
     */
    fun addCallee(callee: MethodCallTreeNode) {
        if (!callees.any { it.methodId == callee.methodId }) {
            callees.add(callee)
        }
    }
    
    /**
     * Get display text for this node
     */
    fun getDisplayText(): String {
        return "$methodSignature ($fileName:$lineNumber)"
    }
    
    /**
     * Get simplified display text without file info
     */
    fun getSimpleDisplayText(): String {
        return methodSignature
    }
    
    /**
     * Get code context with line numbers
     */
    fun getFormattedCodeContext(): String {
        return if (codeContext.isNotEmpty()) {
            val lines = codeContext.split("\n")
            val startLine = maxOf(1, lineNumber - 3)
            lines.mapIndexed { index, line ->
                val currentLine = startLine + index
                val marker = if (currentLine == lineNumber) "→" else " "
                "$marker $currentLine: $line"
            }.joinToString("\n")
        } else {
            "// Code not available"
        }
    }
    
    /**
     * Check if this node has any children (callers or callees)
     */
    fun hasChildren(): Boolean {
        return callers.isNotEmpty() || callees.isNotEmpty()
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MethodCallTreeNode) return false
        return methodId == other.methodId
    }
    
    override fun hashCode(): Int {
        return methodId.hashCode()
    }
    
    /**
     * Check if this node can be expanded (has potential callers)
     */
    fun canExpand(): Boolean {
        return hasMoreCallers && loadingState != LoadingState.LOADING
    }
    
    /**
     * Check if this node is currently loading
     */
    fun isLoading(): Boolean {
        return loadingState == LoadingState.LOADING
    }
    
    /**
     * Check if this node has an error
     */
    fun hasError(): Boolean {
        return loadingState == LoadingState.ERROR
    }
    
    /**
     * Set loading state and update related properties
     */
    fun setLoadingState(state: LoadingState, progress: Float = 0.0f, error: String? = null) {
        loadingState = state
        loadingProgress = progress
        errorMessage = error
        
        when (state) {
            LoadingState.LOADED -> {
                hasMoreCallers = callers.isNotEmpty()
                loadingProgress = 1.0f
            }
            LoadingState.ERROR -> {
                loadingProgress = 0.0f
            }
            LoadingState.LOADING -> {
                // Keep current progress
            }
            else -> {
                loadingProgress = 0.0f
                errorMessage = null
            }
        }
    }
    
    /**
     * Get display text for loading state
     */
    fun getLoadingStateText(): String {
        return when (loadingState) {
            LoadingState.NOT_LOADED -> "Not loaded"
            LoadingState.LOADING -> "Loading... ${(loadingProgress * 100).toInt()}%"
            LoadingState.LOADED -> "Loaded (${callers.size} callers)"
            LoadingState.ERROR -> "Error: ${errorMessage ?: "Unknown error"}"
            LoadingState.EXPANDABLE -> "Click to expand"
        }
    }
    
    /**
     * Reset loading state for retry
     */
    fun resetForRetry() {
        loadingState = LoadingState.NOT_LOADED
        loadingProgress = 0.0f
        errorMessage = null
        discoveryTaskId = null
    }
}