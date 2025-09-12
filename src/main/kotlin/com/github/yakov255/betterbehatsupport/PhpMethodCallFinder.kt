package com.github.yakov255.betterbehatsupport

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil

object PhpMethodCallFinder {

    private const val MAX_DEPTH = 10 // Prevent infinite recursion
    
    /**
     * Legacy method for backward compatibility
     */
    fun findMethodCalls(project: Project, event: AnActionEvent): List<PsiElement> {
        val rootNode = buildMethodCallTree(project, event)
        return rootNode?.let { getAllReferences(it) } ?: emptyList()
    }
    
    /**
     * Build a complete method call tree starting from the method under cursor
     */
    fun buildMethodCallTree(project: Project, event: AnActionEvent): MethodCallTreeNode? {
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return null
        val psiFile = event.getData(CommonDataKeys.PSI_FILE) ?: return null

        val offset = editor.caretModel.offset
        val element = psiFile.findElementAt(offset) ?: return null

        // Look for any named element that could be a method
        val namedElement = PsiTreeUtil.getParentOfType(element, PsiNamedElement::class.java) ?: return null
        
        // Check if this looks like a PHP method by checking the element type name
        val elementTypeName = namedElement.javaClass.simpleName
        if (!elementTypeName.contains("Method", ignoreCase = true)) {
            return null
        }
        
        return try {
            val visitedNodes = mutableSetOf<String>()
            buildTreeRecursively(namedElement, visitedNodes, 0)
        } catch (e: Exception) {
            // Handle any search exceptions gracefully
            null
        }
    }
    
    /**
     * Recursively build the call tree for a method
     */
    private fun buildTreeRecursively(
        method: PsiNamedElement,
        visitedNodes: MutableSet<String>,
        depth: Int
    ): MethodCallTreeNode? {
        if (depth > MAX_DEPTH) return null
        
        val methodNode = createMethodNode(method) ?: return null
        
        // Prevent circular references
        if (visitedNodes.contains(methodNode.methodId)) {
            return methodNode
        }
        
        visitedNodes.add(methodNode.methodId)
        
        try {
            // Find who calls this method (callers)
            ReferencesSearch.search(method).forEach { psiReference ->
                val referenceElement = psiReference.element
                val callerMethod = findContainingMethod(referenceElement)
                if (callerMethod != null && callerMethod != method) {
                    val callerNode = buildTreeRecursively(callerMethod, visitedNodes.toMutableSet(), depth + 1)
                    if (callerNode != null) {
                        methodNode.addCaller(callerNode)
                    }
                }
            }
            
            // Find what this method calls (callees)
            findMethodCallsInElement(method).forEach { calledMethod ->
                if (calledMethod != method) {
                    val calleeNode = buildTreeRecursively(calledMethod, visitedNodes.toMutableSet(), depth + 1)
                    if (calleeNode != null) {
                        methodNode.addCallee(calleeNode)
                    }
                }
            }
        } catch (e: Exception) {
            // Continue even if some references fail
        }
        
        visitedNodes.remove(methodNode.methodId)
        return methodNode
    }
    
    /**
     * Create a MethodCallTreeNode from a PsiNamedElement
     */
    private fun createMethodNode(method: PsiNamedElement): MethodCallTreeNode? {
        val containingFile = method.containingFile ?: return null
        val fileName = containingFile.name
        val lineNumber = method.textRange?.let { range ->
            val document = containingFile.viewProvider?.document
            document?.getLineNumber(range.startOffset)?.plus(1)
        } ?: 0
        
        val methodSignature = buildMethodSignature(method)
        
        return MethodCallTreeNode(
            method = method,
            fileName = fileName,
            lineNumber = lineNumber,
            methodSignature = methodSignature
        )
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
     * Find all method calls within a given element
     */
    private fun findMethodCallsInElement(element: PsiElement): List<PsiNamedElement> {
        val methodCalls = mutableListOf<PsiNamedElement>()
        
        element.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                super.visitElement(element)
                
                // Look for method call expressions
                if (isMethodCall(element)) {
                    val resolvedMethod = resolveMethodCall(element)
                    if (resolvedMethod != null) {
                        methodCalls.add(resolvedMethod)
                    }
                }
            }
        })
        
        return methodCalls
    }
    
    /**
     * Check if an element represents a method call
     */
    private fun isMethodCall(element: PsiElement): Boolean {
        val elementType = element.javaClass.simpleName
        return elementType.contains("MethodReference", ignoreCase = true) ||
               elementType.contains("FunctionReference", ignoreCase = true) ||
               elementType.contains("Call", ignoreCase = true)
    }
    
    /**
     * Resolve a method call to its declaration
     */
    private fun resolveMethodCall(element: PsiElement): PsiNamedElement? {
        return try {
            // Try to resolve the reference
            val reference = element.reference
            val resolved = reference?.resolve()
            if (resolved is PsiNamedElement &&
                resolved.javaClass.simpleName.contains("Method", ignoreCase = true)) {
                resolved
            } else null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get all references from a tree node (for backward compatibility)
     */
    private fun getAllReferences(rootNode: MethodCallTreeNode): List<PsiElement> {
        val references = mutableListOf<PsiElement>()
        
        fun collectReferences(node: MethodCallTreeNode, visited: MutableSet<String>) {
            if (visited.contains(node.methodId)) return
            visited.add(node.methodId)
            
            references.add(node.method)
            node.callers.forEach { collectReferences(it, visited) }
            node.callees.forEach { collectReferences(it, visited) }
        }
        
        collectReferences(rootNode, mutableSetOf())
        return references
    }
}