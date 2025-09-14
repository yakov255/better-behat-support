package com.github.yakov255.betterbehatsupport

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.searches.ReferencesSearch
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Queue manager for async caller discovery with priority handling and concurrency control
 */
class CallDiscoveryQueue(private val project: Project) {
    
    companion object {
        private val LOG = Logger.getInstance(CallDiscoveryQueue::class.java)
        private const val MAX_CONCURRENT_TASKS = 3
        private const val TASK_TIMEOUT_MS = 30000L // 30 seconds
    }
    
    private val taskQueue = PriorityBlockingQueue<CallDiscoveryTask>()
    private val activeTasks = ConcurrentHashMap<String, CallDiscoveryTask>()
    private val completedTasks = AtomicInteger(0)
    private val failedTasks = AtomicInteger(0)
    private val totalTasks = AtomicInteger(0)
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isProcessing = false
    
    // Listeners for queue status changes
    private val statusListeners = mutableListOf<(QueueStatus) -> Unit>()
    
    /**
     * Add a task to the discovery queue
     */
    fun enqueueTask(task: CallDiscoveryTask) {
        if (!task.isValid()) {
            LOG.warn("Attempted to enqueue invalid task for node: ${task.node.methodId}")
            return
        }
        
        // Check if task already exists for this node
        val existingTask = activeTasks.values.find { it.node.methodId == task.node.methodId }
        if (existingTask != null) {
            LOG.debug("Task already exists for node: ${task.node.methodId}")
            return
        }
        
        // Set the task ID on the node
        task.node.discoveryTaskId = task.taskId
        task.node.setLoadingState(LoadingState.EXPANDABLE)
        
        taskQueue.offer(task)
        totalTasks.incrementAndGet()
        
        LOG.debug("Enqueued task ${task.taskId} for node: ${task.node.methodId} with priority: ${task.priority}")
        
        notifyStatusListeners()
        startProcessingIfNeeded()
    }
    
    /**
     * Cancel a specific task by ID
     */
    fun cancelTask(taskId: String): Boolean {
        // Try to remove from queue first
        val queueTask = taskQueue.find { it.taskId == taskId }
        if (queueTask != null) {
            taskQueue.remove(queueTask)
            queueTask.node.resetForRetry()
            LOG.debug("Cancelled queued task: $taskId")
            notifyStatusListeners()
            return true
        }
        
        // Try to cancel active task
        val activeTask = activeTasks[taskId]
        if (activeTask != null) {
            activeTask.cancel()
            activeTasks.remove(taskId)
            activeTask.node.resetForRetry()
            LOG.debug("Cancelled active task: $taskId")
            notifyStatusListeners()
            return true
        }
        
        return false
    }
    
    /**
     * Cancel all tasks for a specific node
     */
    fun cancelTasksForNode(nodeId: String) {
        // Cancel queued tasks
        val queuedTasks = taskQueue.filter { it.node.methodId == nodeId }
        queuedTasks.forEach { task ->
            taskQueue.remove(task)
            task.node.resetForRetry()
        }
        
        // Cancel active tasks
        val activeTasks = activeTasks.values.filter { it.node.methodId == nodeId }
        activeTasks.forEach { task ->
            cancelTask(task.taskId)
        }
        
        if (queuedTasks.isNotEmpty() || activeTasks.isNotEmpty()) {
            LOG.debug("Cancelled ${queuedTasks.size + activeTasks.size} tasks for node: $nodeId")
            notifyStatusListeners()
        }
    }
    
    /**
     * Cancel all pending and active tasks
     */
    fun cancelAllTasks() {
        // Cancel all queued tasks
        val queuedTasks = taskQueue.toList()
        taskQueue.clear()
        queuedTasks.forEach { it.node.resetForRetry() }
        
        // Cancel all active tasks
        val activeTasks = activeTasks.values.toList()
        activeTasks.forEach { task ->
            task.cancel()
            task.node.resetForRetry()
        }
        this.activeTasks.clear()
        
        // Reset counters
        completedTasks.set(0)
        failedTasks.set(0)
        totalTasks.set(0)
        
        LOG.debug("Cancelled all tasks (${queuedTasks.size} queued, ${activeTasks.size} active)")
        notifyStatusListeners()
    }
    
    /**
     * Get current queue status
     */
    fun getQueueStatus(): QueueStatus {
        return QueueStatus(
            pendingTasks = taskQueue.size,
            activeTasks = activeTasks.size,
            completedTasks = completedTasks.get(),
            failedTasks = failedTasks.get(),
            totalTasks = totalTasks.get()
        )
    }
    
    /**
     * Add a listener for queue status changes
     */
    fun addStatusListener(listener: (QueueStatus) -> Unit) {
        statusListeners.add(listener)
    }
    
    /**
     * Remove a status listener
     */
    fun removeStatusListener(listener: (QueueStatus) -> Unit) {
        statusListeners.remove(listener)
    }
    
    /**
     * Start processing tasks if not already running
     */
    private fun startProcessingIfNeeded() {
        if (!isProcessing && taskQueue.isNotEmpty()) {
            isProcessing = true
            scope.launch {
                processQueue()
            }
        }
    }
    
    /**
     * Main queue processing loop
     */
    private suspend fun processQueue() {
        while (taskQueue.isNotEmpty() || activeTasks.isNotEmpty()) {
            // Start new tasks up to the concurrency limit
            while (activeTasks.size < MAX_CONCURRENT_TASKS && taskQueue.isNotEmpty()) {
                val task = taskQueue.poll()
                if (task != null && task.isValid()) {
                    startTask(task)
                }
            }
            
            // Wait a bit before checking again
            delay(100)
            
            // Clean up completed/failed tasks
            cleanupCompletedTasks()
        }
        
        isProcessing = false
        notifyStatusListeners()
    }
    
    /**
     * Start executing a single task
     */
    private fun startTask(task: CallDiscoveryTask) {
        LOG.info("Starting task ${task.taskId} for method: ${task.node.methodSignature}")
        activeTasks[task.taskId] = task
        task.node.setLoadingState(LoadingState.LOADING)
        
        val job = scope.launch {
            try {
                LOG.debug("Task ${task.taskId} starting execution with timeout ${TASK_TIMEOUT_MS}ms")
                withTimeout(TASK_TIMEOUT_MS) {
                    executeTask(task)
                }
                LOG.info("Task ${task.taskId} completed successfully")
            } catch (e: TimeoutCancellationException) {
                LOG.error("Task ${task.taskId} timed out after ${TASK_TIMEOUT_MS}ms")
                handleTaskError(task, Exception("Task timed out after ${TASK_TIMEOUT_MS}ms"))
            } catch (e: CancellationException) {
                LOG.info("Task ${task.taskId} was cancelled")
                // Don't count as failed if explicitly cancelled
            } catch (e: Exception) {
                LOG.error("Task ${task.taskId} failed with exception", e)
                handleTaskError(task, e)
            } finally {
                activeTasks.remove(task.taskId)
                LOG.debug("Task ${task.taskId} removed from active tasks")
                notifyStatusListeners()
            }
        }
        
        task.job = job
        LOG.info("Task ${task.taskId} job created and started")
        notifyStatusListeners()
    }
    
    /**
     * Execute the actual caller discovery task
     */
    private suspend fun executeTask(task: CallDiscoveryTask) {
        LOG.info("Starting task ${task.taskId} for method: ${task.node.methodSignature}")
        task.onProgress(0.1f)
        
        try {
            // Direct implementation to avoid circular dependency
            val callers = findCallersForMethod(task.node.method) { progress ->
                LOG.debug("Task ${task.taskId} progress: ${(progress * 100).toInt()}%")
                task.onProgress(0.1f + (progress * 0.9f))
            }
            
            LOG.info("Task ${task.taskId} found ${callers.size} callers")
            
            // Update progress and complete
            task.onProgress(1.0f)
            task.node.callers.clear()
            task.node.callers.addAll(callers)
            task.node.setLoadingState(LoadingState.LOADED)
            task.onComplete(callers)
            
            completedTasks.incrementAndGet()
            LOG.info("Completed task ${task.taskId} successfully")
            
        } catch (e: Exception) {
            LOG.error("Task ${task.taskId} failed with exception", e)
            throw e
        }
    }
    
    /**
     * Find callers for a method directly (avoiding circular dependency)
     */
    private suspend fun findCallersForMethod(
        method: PsiNamedElement,
        progressCallback: (Float) -> Unit = {}
    ): List<MethodCallTreeNode> = withContext(Dispatchers.IO) {
        
        LOG.info("Starting findCallersForMethod for: ${method.name} in file: ${method.containingFile?.name}")
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
            LOG.info("Completed findCallersForMethod for: ${method.name}, found ${callers.size} callers")
            
        } catch (e: Exception) {
            LOG.error("Error finding callers for method: ${method.name}", e)
            throw e
        }
        
        callers
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
     * Handle task execution error
     */
    private fun handleTaskError(task: CallDiscoveryTask, error: Exception) {
        task.node.setLoadingState(LoadingState.ERROR, error = error.message)
        task.onError(error)
        failedTasks.incrementAndGet()
    }
    
    /**
     * Clean up completed tasks from active list
     */
    private fun cleanupCompletedTasks() {
        val completedTaskIds = activeTasks.values
            .filter { it.job?.isCompleted == true }
            .map { it.taskId }
        
        completedTaskIds.forEach { taskId ->
            activeTasks.remove(taskId)
        }
    }
    
    /**
     * Notify all status listeners of queue changes
     */
    private fun notifyStatusListeners() {
        val status = getQueueStatus()
        ApplicationManager.getApplication().invokeLater {
            statusListeners.forEach { listener ->
                try {
                    listener(status)
                } catch (e: Exception) {
                    LOG.warn("Error in status listener", e)
                }
            }
        }
    }
    
    /**
     * Placeholder for actual caller finding logic
     * This will be replaced with the real implementation
     */
    private fun findCallersForNode(
        node: MethodCallTreeNode, 
        depth: Int, 
        indicator: ProgressIndicator
    ): List<MethodCallTreeNode> {
        // This is a placeholder - will be implemented in AsyncMethodCallFinder
        indicator.fraction = 0.5
        Thread.sleep(1000) // Simulate work
        indicator.fraction = 1.0
        return emptyList()
    }
    
    /**
     * Dispose the queue and cancel all tasks
     */
    fun dispose() {
        cancelAllTasks()
        scope.cancel()
        statusListeners.clear()
    }
}