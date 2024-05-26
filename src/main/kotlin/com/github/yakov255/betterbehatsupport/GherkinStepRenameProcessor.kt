package com.github.yakov255.betterbehatsupport

import com.intellij.json.psi.JsonFile
import com.intellij.openapi.editor.Editor
import com.intellij.psi.*
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.usageView.UsageInfo
import org.jetbrains.plugins.cucumber.psi.GherkinFile

class GherkinStepRenameProcessor : RenamePsiElementProcessor() {
    override fun canProcessElement(element: PsiElement): Boolean {
        return element is JsonFile
    }

    override fun substituteElementToRename(element: PsiElement, editor: Editor?): PsiElement? {
        if (element is PsiFile) {
            return element
        }
        return null
    }

    override fun renameElement(
        element: PsiElement,
        newName: String,
        usages: Array<UsageInfo>,
        listener: RefactoringElementListener?
    ) {
        val project = element.project
        val oldName = (element as PsiFile).name

        super.renameElement(element, newName, usages, listener)

        val psiFiles = PsiManager.getInstance(project).findDirectory(element.virtualFile.parent.parent)?.files
        psiFiles?.forEach { psiFile ->
            if (psiFile is GherkinFile) {
                val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                document?.let {
                    val text = it.text
                    val newText = text.replace(oldName, newName)
                    it.setText(newText)
                    PsiDocumentManager.getInstance(project).commitDocument(it)
                }
            }
        }
    }
}