package com.github.yakov255.betterbehatsupport

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import java.util.regex.Pattern

class GoToFileIntention : IntentionAction {

    override fun getText(): String = "Go to file"

    override fun getFamilyName(): String = "Gherkin"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        if (file == null || editor == null) return false

        val caretOffset = editor.caretModel.offset
        val element = file.findElementAt(caretOffset) ?: return false

        val step = element.parent as? GherkinStep ?: return false
        val stepText = step.text
        val matcher = Pattern.compile(Enum.pattern).matcher(stepText)

        if (matcher.find()) {
            val fileName = matcher.group(1)
            val featureFile = step.containingFile.virtualFile
            val filesDir = featureFile.parent?.findChild(Enum.directory) ?: return false
            return filesDir.findChild(fileName) != null
        }
        return false
    }

    @Throws(IncorrectOperationException::class)
    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (file == null || editor == null) return

        val caretOffset = editor.caretModel.offset
        val element = file.findElementAt(caretOffset) ?: return

        val step = element.parent as? GherkinStep ?: return
        val stepText = step.text
        val matcher = Pattern.compile(Enum.pattern).matcher(stepText)

        if (matcher.find()) {
            val fileName = matcher.group(1)
            val featureFile = step.containingFile.virtualFile
            val filesDir = featureFile.parent?.findChild(Enum.directory) ?: return
            val targetFile = filesDir.findChild(fileName) ?: return

            PsiManager.getInstance(project).findFile(targetFile)?.navigate(true)
        }
    }

    override fun startInWriteAction(): Boolean = false
}