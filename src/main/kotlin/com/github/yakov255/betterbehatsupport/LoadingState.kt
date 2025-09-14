package com.github.yakov255.betterbehatsupport

/**
 * Represents the loading state of a method call tree node
 */
enum class LoadingState {
    /** Callers not yet searched */
    NOT_LOADED,
    
    /** Currently searching for callers */
    LOADING,
    
    /** Callers found and loaded */
    LOADED,
    
    /** Error occurred during loading */
    ERROR,
    
    /** Has potential callers but not loaded yet */
    EXPANDABLE
}