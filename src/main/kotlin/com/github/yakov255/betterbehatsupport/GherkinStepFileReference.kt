package com.github.yakov255.betterbehatsupport

import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.cucumber.psi.GherkinStep

class GherkinStepFileReference(step: GherkinStep, range: TextRange)
    : PsiReferenceBase<GherkinStep>(step, range), PsiPolyVariantReference {

    override fun resolve(): PsiElement? {
        val fileName = myElement.text.substring(rangeInElement.startOffset, rangeInElement.endOffset)
        val featureFile = myElement.containingFile.virtualFile
        val filesDir = featureFile.parent.findChild(Enum.directory) ?: return null
        val targetFile = filesDir.findChild(fileName) ?: return null

        return PsiManager.getInstance(myElement.project).findFile(targetFile)
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
    override fun handleElementRename(newElementName: String): PsiElement {
        return myElement
    }
}
