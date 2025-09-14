package com.github.yakov255.betterbehatsupport

import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.ConcurrentHashMap

/**
 * Cache for method caller discovery results with automatic invalidation
 */
class CallDiscoveryCache {
    
    companion object {
        private val LOG = Logger.getInstance(CallDiscoveryCache::class.java)
        private const val CACHE_EXPIRY_MS = 5 * 60 * 1000L // 5 minutes
        private const val MAX_CACHE_SIZE = 1000
    }
    
    private val callerCache = ConcurrentHashMap<String, CacheEntry>()
    private val cacheTimestamps = ConcurrentHashMap<String, Long>()
    
    /**
     * Cache entry containing caller nodes and metadata
     */
    private data class CacheEntry(
        val callers: List<MethodCallTreeNode>,
        val timestamp: Long = System.currentTimeMillis(),
        val fileHash: String? = null
    )
    
    /**
     * Get cached callers for a method
     */
    fun getCachedCallers(methodId: String): List<MethodCallTreeNode>? {
        val entry = callerCache[methodId] ?: return null
        
        // Check if cache entry is expired
        if (System.currentTimeMillis() - entry.timestamp > CACHE_EXPIRY_MS) {
            LOG.debug("Cache entry expired for $methodId")
            callerCache.remove(methodId)
            cacheTimestamps.remove(methodId)
            return null
        }
        
        LOG.debug("Cache hit for $methodId (${entry.callers.size} callers)")
        return entry.callers.map { it.copy() } // Return copies to avoid modification
    }
    
    /**
     * Cache callers for a method
     */
    fun cacheCallers(methodId: String, callers: List<MethodCallTreeNode>) {
        // Enforce cache size limit
        if (callerCache.size >= MAX_CACHE_SIZE) {
            cleanupOldestEntries()
        }
        
        val entry = CacheEntry(
            callers = callers.map { it.copy() }, // Store copies
            timestamp = System.currentTimeMillis()
        )
        
        callerCache[methodId] = entry
        cacheTimestamps[methodId] = entry.timestamp
        
        LOG.debug("Cached ${callers.size} callers for $methodId")
    }

    
    /**
     * Clean up oldest entries when cache is full
     */
    private fun cleanupOldestEntries() {
        val entriesToRemove = cacheTimestamps.entries
            .sortedBy { it.value }
            .take(MAX_CACHE_SIZE / 4) // Remove 25% of entries
            .map { it.key }
        
        entriesToRemove.forEach { key ->
            callerCache.remove(key)
            cacheTimestamps.remove(key)
        }
        
        LOG.debug("Cleaned up ${entriesToRemove.size} oldest cache entries")
    }

    /**
     * Clear all cache entries
     */
    fun clear() {
        val size = callerCache.size
        callerCache.clear()
        cacheTimestamps.clear()
        LOG.debug("Cleared all $size cache entries")
    }
    
    /**
     * Copy a MethodCallTreeNode for caching (to avoid shared state issues)
     */
    private fun MethodCallTreeNode.copy(): MethodCallTreeNode {
        return MethodCallTreeNode(
            method = this.method,
            fileName = this.fileName,
            lineNumber = this.lineNumber,
            methodSignature = this.methodSignature,
            codeContext = this.codeContext,
            callers = mutableListOf(), // Don't copy nested callers to avoid deep copying
            callees = mutableListOf(),
            isExpanded = false, // Reset expansion state
            x = this.x,
            y = this.y,
            width = this.width,
            height = this.height,
            loadingState = LoadingState.NOT_LOADED, // Reset loading state
            hasMoreCallers = this.hasMoreCallers,
            loadingProgress = 0.0f,
            errorMessage = null,
            discoveryTaskId = null,
            isUserExpanded = false
        )
    }
}
