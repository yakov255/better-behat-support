package com.github.yakov255.betterbehatsupport

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.searches.ReferencesSearch
import kotlinx.coroutines.*

/**
 * Async version of PhpMethodCallFinder with progressive loading capabilities
 */
class AsyncMethodCallFinder(private val project: Project) {
    
    companion object {
        private val LOG = Logger.getInstance(AsyncMethodCallFinder::class.java)
        private const val MAX_DEPTH = 10
    }
    
    private val discoveryQueue = CallDiscoveryQueue(project)
    private val cache = CallDiscoveryCache()
    private val nodeUpdateListeners = mutableListOf<(MethodCallTreeNode) -> Unit>()
    
    /**
     * Build initial method call tree with just the root node
     */
    fun buildInitialTree(method: PsiNamedElement): MethodCallTreeNode? {
        return ReadAction.compute<MethodCallTreeNode?, Exception> {
            try {
                createMethodNode(method)?.also { rootNode ->
                    rootNode.setLoadingState(LoadingState.EXPANDABLE)
                    rootNode.hasMoreCallers = true
                }
            } catch (e: Exception) {
                LOG.warn("Failed to create initial tree node", e)
                null
            }
        }
    }
    
    /**
     * Start progressive discovery of callers for the root node
     */
    fun startProgressiveDiscovery(
        rootNode: MethodCallTreeNode,
        onNodeUpdate: (MethodCallTreeNode) -> Unit = {}
    ) {
        LOG.info("Starting progressive discovery for root method: ${rootNode.methodSignature}")
        addNodeUpdateListener(onNodeUpdate)
        
        // Start with the root node expansion
        LOG.info("Expanding root node with HIGH priority")
        expandNodeAsync(rootNode, TaskPriority.HIGH)
    }
    
    /**
     * Expand a node asynchronously to find its callers
     */
    fun expandNodeAsync(
        node: MethodCallTreeNode,
        priority: TaskPriority = TaskPriority.MEDIUM,
        depth: Int = 0
    ) {
        LOG.info("expandNodeAsync called for method: ${node.methodSignature}, depth: $depth, priority: $priority")
        
        if (!node.canExpand()) {
            LOG.debug("Node ${node.methodId} cannot expand (canExpand=false)")
            return
        }
        
        if (depth > MAX_DEPTH) {
            LOG.debug("Node ${node.methodId} exceeds max depth ($depth > $MAX_DEPTH)")
            return
        }
        
        // Check cache first
        val cachedCallers = cache.getCachedCallers(node.methodId)
        if (cachedCallers != null) {
            LOG.info("Using cached callers for ${node.methodId} (${cachedCallers.size} callers)")
            node.callers.clear()
            node.callers.addAll(cachedCallers)
            node.setLoadingState(LoadingState.LOADED)
            notifyNodeUpdate(node)
            return
        }
        
        LOG.info("Creating discovery task for ${node.methodId}")
        
        // Create discovery task
        val task = CallDiscoveryTask(
            node = node,
            priority = priority,
            depth = depth,
            maxDepth = MAX_DEPTH,
            onProgress = { progress ->
                LOG.debug("Progress update for ${node.methodId}: ${(progress * 100).toInt()}%")
                node.loadingProgress = progress
                notifyNodeUpdate(node)
            },
            onComplete = { callers ->
                LOG.info("Task completed for ${node.methodId}, found ${callers.size} callers")
                handleCallersFound(node, callers, depth)
            },
            onError = { error ->
                LOG.error("Task failed for ${node.methodId}", error)
                node.setLoadingState(LoadingState.ERROR, error = error.message)
                notifyNodeUpdate(node)
            }
        )
        
        LOG.info("Enqueueing task ${task.taskId} for ${node.methodId}")
        discoveryQueue.enqueueTask(task)
    }
    
    /**
     * Find callers for a method synchronously (used by the queue)
     */
    suspend fun findCallersAsync(
        method: PsiNamedElement,
        progressCallback: (Float) -> Unit = {}
    ): List<MethodCallTreeNode> = withContext(Dispatchers.IO) {
        
        LOG.info("Starting findCallersAsync for method: ${method.name} in file: ${method.containingFile?.name}")
        val callers = mutableListOf<MethodCallTreeNode>()
        
        try {
            progressCallback(0.1f)
            LOG.debug("Starting reference search for method: ${method.name}")
            
            // Use ReadAction for PSI operations
            val references = ReadAction.compute<List<com.intellij.psi.PsiReference>, Exception> {
                LOG.debug("Executing ReferencesSearch.search for method: ${method.name}")
                val searchResults = ReferencesSearch.search(method).findAll().toList()
                LOG.debug("Found ${searchResults.size} references for method: ${method.name}")
                searchResults
            }
            
            progressCallback(0.3f)
            LOG.info("Found ${references.size} references for method: ${method.name}")
            
            val totalReferences = references.size
            var processedReferences = 0
            
            for ((index, reference) in references.withIndex()) {
                try {
                    LOG.debug("Processing reference ${index + 1}/${totalReferences} for method: ${method.name}")
                    
                    val callerNode = ReadAction.compute<MethodCallTreeNode?, Exception> {
                        val referenceElement = reference.element
                        LOG.debug("Reference element: ${referenceElement?.javaClass?.simpleName} in file: ${referenceElement?.containingFile?.name}")
                        
                        val callerMethod = findContainingMethod(referenceElement)
                        LOG.debug("Found containing method: ${callerMethod?.name}")
                        
                        if (callerMethod != null && callerMethod != method) {
                            val node = createMethodNode(callerMethod)
                            LOG.debug("Created method node for: ${callerMethod.name}")
                            node
                        } else {
                            LOG.debug("Skipping reference - same method or no containing method")
                            null
                        }
                    }
                    
                    if (callerNode != null) {
                        callers.add(callerNode)
                        LOG.debug("Added caller: ${callerNode.methodSignature}")
                    }
                    
                } catch (e: Exception) {
                    LOG.warn("Failed to process reference ${index + 1} for method: ${method.name}", e)
                }
                
                processedReferences++
                val progress = 0.3f + (processedReferences.toFloat() / totalReferences) * 0.6f
                progressCallback(progress)
                
                // Add a small delay to prevent overwhelming the system
                if (processedReferences % 10 == 0) {
                    delay(10)
                }
            }
            
            progressCallback(1.0f)
            LOG.info("Completed findCallersAsync for method: ${method.name}, found ${callers.size} callers")
            
        } catch (e: Exception) {
            LOG.error("Error finding callers for method: ${method.name}", e)
            throw e
        }
        
        callers
    }
    
    /**
     * Handle when callers are found for a node
     */
    private fun handleCallersFound(
        node: MethodCallTreeNode,
        callers: List<MethodCallTreeNode>,
        depth: Int
    ) {
        // Update the node
        node.callers.clear()
        node.callers.addAll(callers)
        node.setLoadingState(LoadingState.LOADED)
        node.hasMoreCallers = callers.isNotEmpty()
        
        // Cache the results
        cache.cacheCallers(node.methodId, callers)
        
        // Set initial state for new caller nodes
        callers.forEach { caller ->
            caller.setLoadingState(LoadingState.EXPANDABLE)
            caller.hasMoreCallers = true
        }
        
        notifyNodeUpdate(node)
        
        // Optionally start background loading for immediate callers
        if (depth < 2) { // Only auto-expand first 2 levels
            callers.take(3).forEach { caller -> // Limit to first 3 callers
                expandNodeAsync(caller, TaskPriority.LOW, depth + 1)
            }
        }
    }
    
    /**
     * Create a MethodCallTreeNode from a PsiNamedElement
     */
    private fun createMethodNode(method: PsiNamedElement): MethodCallTreeNode? {
        return try {
            val containingFile = method.containingFile ?: return null
            val fileName = containingFile.name
            val lineNumber = method.textRange?.let { range ->
                val document = containingFile.viewProvider?.document
                document?.getLineNumber(range.startOffset)?.plus(1)
            } ?: 0
            
            val methodSignature = buildMethodSignature(method)
            val codeContext = extractCodeContext(method, lineNumber)
            
            MethodCallTreeNode(
                method = method,
                fileName = fileName,
                lineNumber = lineNumber,
                methodSignature = methodSignature,
                codeContext = codeContext,
                loadingState = LoadingState.NOT_LOADED
            )
        } catch (e: Exception) {
            LOG.warn("Failed to create method node", e)
            null
        }
    }
    
    /**
     * Build a readable method signature
     */
    private fun buildMethodSignature(method: PsiNamedElement): String {
        val className = findContainingClass(method)?.name ?: "Unknown"
        val methodName = method.name ?: "unknown"
        return "$className::$methodName()"
    }
    
    /**
     * Find the containing class of a method
     */
    private fun findContainingClass(element: PsiElement): PsiNamedElement? {
        var current = element.parent
        while (current != null) {
            if (current is PsiNamedElement &&
                current.javaClass.simpleName.contains("Class", ignoreCase = true)) {
                return current
            }
            current = current.parent
        }
        return null
    }
    
    /**
     * Find the containing method of an element
     */
    private fun findContainingMethod(element: PsiElement): PsiNamedElement? {
        var current = element.parent
        while (current != null) {
            if (current is PsiNamedElement &&
                current.javaClass.simpleName.contains("Method", ignoreCase = true)) {
                return current
            }
            current = current.parent
        }
        return null
    }
    
    /**
     * Extract code context around the method
     */
    private fun extractCodeContext(method: PsiNamedElement, lineNumber: Int): String {
        return try {
            val containingFile = method.containingFile ?: return ""
            val document = containingFile.viewProvider?.document ?: return ""
            
            val startLine = maxOf(0, lineNumber - 4)
            val endLine = minOf(document.lineCount - 1, lineNumber + 2)
            
            val lines = mutableListOf<String>()
            for (i in startLine..endLine) {
                val lineStartOffset = document.getLineStartOffset(i)
                val lineEndOffset = document.getLineEndOffset(i)
                val lineText = document.getText().substring(lineStartOffset, lineEndOffset)
                lines.add(lineText)
            }
            
            lines.joinToString("\n")
        } catch (e: Exception) {
            "// Code context not available"
        }
    }
    
    /**
     * Cancel expansion for a specific node
     */
    fun cancelNodeExpansion(node: MethodCallTreeNode) {
        node.discoveryTaskId?.let { taskId ->
            discoveryQueue.cancelTask(taskId)
        }
    }
    
    /**
     * Cancel all pending operations
     */
    fun cancelAllOperations() {
        discoveryQueue.cancelAllTasks()
    }
    
    /**
     * Get current queue status
     */
    fun getQueueStatus(): QueueStatus {
        return discoveryQueue.getQueueStatus()
    }
    
    /**
     * Add a listener for node updates
     */
    fun addNodeUpdateListener(listener: (MethodCallTreeNode) -> Unit) {
        nodeUpdateListeners.add(listener)
    }
    
    /**
     * Remove a node update listener
     */
    fun removeNodeUpdateListener(listener: (MethodCallTreeNode) -> Unit) {
        nodeUpdateListeners.remove(listener)
    }
    
    /**
     * Notify all listeners of a node update
     */
    private fun notifyNodeUpdate(node: MethodCallTreeNode) {
        ApplicationManager.getApplication().invokeLater {
            nodeUpdateListeners.forEach { listener ->
                try {
                    listener(node)
                } catch (e: Exception) {
                    LOG.warn("Error in node update listener", e)
                }
            }
        }
    }
    
    /**
     * Add queue status listener
     */
    fun addQueueStatusListener(listener: (QueueStatus) -> Unit) {
        discoveryQueue.addStatusListener(listener)
    }
    
    /**
     * Remove queue status listener
     */
    fun removeQueueStatusListener(listener: (QueueStatus) -> Unit) {
        discoveryQueue.removeStatusListener(listener)
    }
    
    /**
     * Dispose and cleanup resources
     */
    fun dispose() {
        discoveryQueue.dispose()
        nodeUpdateListeners.clear()
        cache.clear()
    }
}