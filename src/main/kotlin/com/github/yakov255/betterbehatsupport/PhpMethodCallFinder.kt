package com.github.yakov255.betterbehatsupport

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil

object PhpMethodCallFinder {

    fun findMethodCalls(project: Project, event: AnActionEvent): List<PsiElement> {
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return emptyList()
        val psiFile = event.getData(CommonDataKeys.PSI_FILE) ?: return emptyList()

        val offset = editor.caretModel.offset
        val element = psiFile.findElementAt(offset) ?: return emptyList()

        // Look for any named element that could be a method
        val namedElement = PsiTreeUtil.getParentOfType(element, PsiNamedElement::class.java) ?: return emptyList()
        
        // Check if this looks like a PHP method by checking the element type name
        val elementTypeName = namedElement.javaClass.simpleName
        if (!elementTypeName.contains("Method", ignoreCase = true)) {
            return emptyList()
        }
        
        val methodCalls = mutableListOf<PsiElement>()
        try {
            ReferencesSearch.search(namedElement).forEach { psiReference ->
                val referenceElement = psiReference.element
                // Add all references - we'll let the UI show the details
                methodCalls.add(referenceElement)
            }
        } catch (e: Exception) {
            // Handle any search exceptions gracefully
            return emptyList()
        }
        
        return methodCalls
    }
}