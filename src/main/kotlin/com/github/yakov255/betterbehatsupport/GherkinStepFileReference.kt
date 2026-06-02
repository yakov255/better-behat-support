package com.github.yakov255.betterbehatsupport

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.cucumber.psi.GherkinElementFactory
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinScenario
import org.jetbrains.plugins.cucumber.psi.GherkinStep

class GherkinStepFileReference(
    step: GherkinStep,
    range: TextRange,
    private val virtualFile: VirtualFile
) : PsiReferenceBase<GherkinStep>(step, range), PsiPolyVariantReference {

    override fun resolve(): PsiElement? {
        return PsiManager.getInstance(myElement.project).findFile(virtualFile)
    }

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val target = resolve()
        return if (target != null) {
            arrayOf(PsiElementResolveResult(target))
        } else {
            ResolveResult.EMPTY_ARRAY
        }
    }

    override fun getVariants(): Array<Any> = emptyArray()

    @Throws(IncorrectOperationException::class)
    override fun handleElementRename(newFileName: String): PsiElement {
        val newStepText = buildString {
            append(myElement.text.substring(0, rangeInElement.startOffset))
            append(newFileName)
            append(myElement.text.substring(rangeInElement.endOffset))
        }

        val scenario = myElement.parent as GherkinScenario
        val feature = scenario.parent as GherkinFeature
        val file = feature.parent as GherkinFile
        val language = file.localeLanguage ?: "en"
        val project = scenario.project

        val wrapperText = "Scenario:\n$newStepText"
        try {
            val minimalScenario = GherkinElementFactory.createScenarioFromText(
                project, language, wrapperText
            ) as? GherkinScenario ?: return myElement
            val updatedElement = minimalScenario.steps.firstOrNull() ?: return myElement
            myElement.replace(updatedElement)
            return updatedElement
        } catch (e: Exception) {
            return myElement
        }
    }
}
