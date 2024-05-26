package com.github.yakov255.betterbehatsupport

import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import java.util.regex.Pattern

class GherkinStepReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(PsiElement::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                    return if (element is GherkinStep) {
                        val text = element.text
                        val matcher = Pattern.compile(Enum.pattern).matcher(text)
                        if (matcher.find()) {
                            val fileName = matcher.group(1)
                            arrayOf(FilePsiReference(element, fileName))
                        } else {
                            PsiReference.EMPTY_ARRAY
                        }
                    } else {
                        PsiReference.EMPTY_ARRAY
                    }
                }
            }
        )
    }
}