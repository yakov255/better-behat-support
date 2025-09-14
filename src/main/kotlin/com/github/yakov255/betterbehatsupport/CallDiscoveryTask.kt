package com.github.yakov255.betterbehatsupport

import kotlinx.coroutines.Job
import java.util.*

/**
 * Priority levels for caller discovery tasks
 */
enum class TaskPriority(val value: Int) {
    HIGH(3),    // User-requested expansions
    MEDIUM(2),  // Visible blocks in viewport
    LOW(1)      // Background pre-loading
}

/**
 * Task for discovering method callers asynchronously
 */
data class CallDiscoveryTask(
    val taskId: String = UUID.randomUUID().toString(),
    val node: MethodCallTreeNode,
    val priority: TaskPriority,
    val depth: Int,
    val maxDepth: Int = 3,
    val onProgress: (Float) -> Unit = {},
    val onComplete: (List<MethodCallTreeNode>) -> Unit = {},
    val onError: (Exception) -> Unit = {},
    var job: Job? = null,
    val createdAt: Long = System.currentTimeMillis()
) : Comparable<CallDiscoveryTask> {
    
    override fun compareTo(other: CallDiscoveryTask): Int {
        // Higher priority first, then by creation time (FIFO for same priority)
        val priorityComparison = other.priority.value.compareTo(this.priority.value)
        return if (priorityComparison != 0) {
            priorityComparison
        } else {
            this.createdAt.compareTo(other.createdAt)
        }
    }
    
    /**
     * Check if this task is still valid (node hasn't been disposed)
     */
    fun isValid(): Boolean {
        return node.method.isValid && !node.isLoading()
    }
    
    /**
     * Cancel the associated job
     */
    fun cancel() {
        job?.cancel()
        job = null
    }
}

/**
 * Status information for the discovery queue
 */
data class QueueStatus(
    val pendingTasks: Int,
    val activeTasks: Int,
    val completedTasks: Int,
    val failedTasks: Int,
    val totalTasks: Int
) {
    val isIdle: Boolean get() = pendingTasks == 0 && activeTasks == 0
    val progressPercentage: Float get() = if (totalTasks > 0) (completedTasks + failedTasks).toFloat() / totalTasks else 0f
}