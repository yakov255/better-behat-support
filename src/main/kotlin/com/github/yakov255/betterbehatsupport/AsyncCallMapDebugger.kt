package com.github.yakov255.betterbehatsupport

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

/**
 * Debug utility for async call map system
 */
object AsyncCallMapDebugger {
    
    private val LOG = Logger.getInstance(AsyncCallMapDebugger::class.java)
    
    /**
     * Test the async system with a simple method
     */
    fun testAsyncSystem(project: Project, testMethod: com.intellij.psi.PsiNamedElement) {
        LOG.info("=== ASYNC CALL MAP DEBUG TEST START ===")
        LOG.info("Testing with method: ${testMethod.name} in file: ${testMethod.containingFile?.name}")
        
        try {
            val asyncFinder = AsyncMethodCallFinder(project)
            LOG.info("Created AsyncMethodCallFinder")
            
            val initialTree = asyncFinder.buildInitialTree(testMethod)
            if (initialTree != null) {
                LOG.info("Initial tree created successfully: ${initialTree.methodSignature}")
                LOG.info("Initial loading state: ${initialTree.loadingState}")
                
                // Add debug listener
                asyncFinder.addNodeUpdateListener { node ->
                    LOG.info("Node update: ${node.methodId} -> ${node.loadingState} (${node.callers.size} callers)")
                }
                
                // Add queue status listener
                asyncFinder.addQueueStatusListener { status ->
                    LOG.info("Queue status: pending=${status.pendingTasks}, active=${status.activeTasks}, completed=${status.completedTasks}, failed=${status.failedTasks}")
                }
                
                // Start progressive discovery
                LOG.info("Starting progressive discovery...")
                asyncFinder.startProgressiveDiscovery(initialTree)
                
                LOG.info("Progressive discovery started, check logs for updates")
                
            } else {
                LOG.error("Failed to create initial tree")
            }
            
        } catch (e: Exception) {
            LOG.error("Debug test failed", e)
        }
        
        LOG.info("=== ASYNC CALL MAP DEBUG TEST END ===")
    }
    
    /**
     * Log current system state
     */
    fun logSystemState(asyncFinder: AsyncMethodCallFinder) {
        val status = asyncFinder.getQueueStatus()
        LOG.info("=== SYSTEM STATE ===")
        LOG.info("Queue Status: $status")
        LOG.info("Is Idle: ${status.isIdle}")
        LOG.info("Progress: ${(status.progressPercentage * 100).toInt()}%")
        LOG.info("==================")
    }
}