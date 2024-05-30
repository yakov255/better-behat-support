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
        val newStart = rangeInElement.endOffset - virtualFile.name.length
        val newStepText = myElement.text.substring(0, newStart) + newFileName + myElement.text.substring(rangeInElement.endOffset, myElement.text.length)

        val scenario = myElement.parent as GherkinScenario

        val newScenarioText = scenario.text.replace(myElement.text, newStepText)
        val stepIndex = scenario.steps.indexOf(myElement)


        val feature = scenario.parent as GherkinFeature
        val file = feature.parent as GherkinFile
        val language = file.localeLanguage

        // There is no GherkinStepFactory or createStepFromText
        val project = scenario.project
        val updatedScenario =
            GherkinElementFactory.createScenarioFromText(project, language, newScenarioText) as GherkinScenario
        val updatedElement = updatedScenario.steps.elementAt(stepIndex)

        // Update element (changes element in the editor)
        myElement.replace(updatedElement)

        return updatedElement
    }
}
