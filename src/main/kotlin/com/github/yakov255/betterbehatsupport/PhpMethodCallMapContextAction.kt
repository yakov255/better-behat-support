package com.github.yakov255.betterbehatsupport

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil

class PhpMethodCallMapContextAction : AnAction("Show Method Call Diagram") {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    /**
     * This method is called by the IDE to determine if the action should be visible and/or enabled.
     * Keep this method fast. Only check for the presence of required context.
     */
    override fun update(e: AnActionEvent) {
        // Find the PHP method under the caret and update the presentation
        val phpMethod = getPhpMethodFromContext(e)
        e.presentation.isEnabledAndVisible = phpMethod != null
    }

    /**
     * This method is called when the user selects the action.
     * It's safe to do more work here. You should re-fetch the data.
     */
    override fun actionPerformed(e: AnActionEvent) {
        // Get the method again (context might have changed)
        val phpMethod = getPhpMethodFromContext(e)
        
        if (phpMethod == null) {
            return
        }

        val project: Project = e.project ?: return
        
        // Use the new async system - create initial tree node
        val asyncFinder = AsyncMethodCallFinder(project)
        val initialTree = asyncFinder.buildInitialTree(phpMethod)
        
        if (initialTree != null) {
            PhpMethodCallMapDialog(project, initialTree).show()
        } else {
            // Fallback to synchronous method if async fails
            val callTree = PhpMethodCallFinder.buildMethodCallTree(project, e)
            PhpMethodCallMapDialog(project, callTree).show()
        }
    }

    /**
     * A helper method to reliably get the PHP method under the caret.
     */
    private fun getPhpMethodFromContext(e: AnActionEvent): PsiNamedElement? {
        // 1. Get the Editor and PsiFile. If they are null, we can't do anything.
        val editor: Editor = e.getData(CommonDataKeys.EDITOR) ?: return null
        val psiFile: PsiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return null
        
        // 2. Check if we're in a PHP file
        if (!psiFile.name.endsWith(".php", ignoreCase = true)) {
            return null
        }

        // 3. Get the Caret's offset
        val offset = editor.caretModel.offset

        // 4. Find the PsiElement at the caret's offset
        val elementAtCaret: PsiElement = psiFile.findElementAt(offset) ?: return null

        // 5. Use PsiTreeUtil to find the containing method.
        //    This is the key step! It walks up the PSI tree from the element at the caret
        //    (which could be a '{' or a whitespace token) and finds the first
        //    enclosing PHP method.
        val namedElement = PsiTreeUtil.getParentOfType(elementAtCaret, PsiNamedElement::class.java)
        
        // 6. Check if this looks like a PHP method by checking the element type name
        if (namedElement != null) {
            val elementTypeName = namedElement.javaClass.simpleName
            if (elementTypeName.contains("Method", ignoreCase = true)) {
                return namedElement
            }
        }
        
        return null
    }
}