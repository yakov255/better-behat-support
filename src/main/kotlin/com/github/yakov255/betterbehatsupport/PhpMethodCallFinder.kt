package com.github.yakov255.betterbehatsupport

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.MethodReference

object PhpMethodCallFinder {

    fun findMethodCalls(project: Project, event: AnActionEvent): List<PsiElement> {
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return emptyList()
        val psiFile = event.getData(CommonDataKeys.PSI_FILE) ?: return emptyList()

        val offset = editor.caretModel.offset
        val element = psiFile.findElementAt(offset)

        val method = PsiTreeUtil.getParentOfType(element, Method::class.java)
        if (method != null) {
            val methodCalls = mutableListOf<PsiElement>()
            ReferencesSearch.search(method).forEach { psiReference ->
                val referenceElement = psiReference.element
                if (referenceElement is MethodReference) {
                    methodCalls.add(referenceElement)
                }
            }
            return methodCalls
        }

        return emptyList()
    }
}