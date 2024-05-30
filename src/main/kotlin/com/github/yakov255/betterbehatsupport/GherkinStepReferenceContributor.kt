package com.github.yakov255.betterbehatsupport

import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import org.jetbrains.plugins.cucumber.psi.GherkinScenario
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import java.util.regex.Pattern

class GherkinStepReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(GherkinStep::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                    val text = (element as GherkinStep).text
                    val matcher = Pattern.compile(Enum.pattern).matcher(text)

                    return if (matcher.find()) {
                        val start = matcher.start(1)
                        val end = matcher.end(1)
                        val textRange = TextRange(start, end)

                        arrayOf(GherkinStepFileReference(element, textRange))
                    } else {
                        PsiReference.EMPTY_ARRAY
                    }
                }
            }
        )
    }
}