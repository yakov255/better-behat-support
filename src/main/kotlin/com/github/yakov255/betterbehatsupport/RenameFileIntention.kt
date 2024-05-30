package com.github.yakov255.betterbehatsupport

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
 import com.intellij.refactoring.rename.RenameHandlerRegistry
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.cucumber.psi.GherkinStep

class RenameFileIntention : IntentionAction {

    override fun getText(): String = "Rename file"

    override fun getFamilyName(): String = "Gherkin"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        if (file == null || editor == null) return false

        val caretOffset = editor.caretModel.offset
        val element = file.findElementAt(caretOffset) ?: return false

        val step = element.parent as? GherkinStep ?: return false
        val references = step.references
        return references.any { it is GherkinStepFileReference }
    }

    @Throws(IncorrectOperationException::class)
    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (file == null || editor == null) return

        val caretOffset = editor.caretModel.offset
        val element = file.findElementAt(caretOffset) ?: return

        val step = element.parent as? GherkinStep ?: return
        val reference = step.references.find { it is GherkinStepFileReference } as? GherkinStepFileReference ?: return

        val targetFile = reference.resolve()?.containingFile ?: return

        val dataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PSI_ELEMENT, targetFile)
            .build()

        RenameHandlerRegistry.getInstance().getRenameHandler(dataContext)?.invoke(project, editor, targetFile, dataContext)
    }

    override fun startInWriteAction(): Boolean = false
}
