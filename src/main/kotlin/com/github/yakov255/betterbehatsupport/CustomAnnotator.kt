package com.github.yakov255.betterbehatsupport

import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import org.jetbrains.plugins.cucumber.psi.GherkinStep

class MyPsiReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(PsiElement::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                    return if (element is GherkinStep) {
                        val text = element.text
                        val regex = Regex("в таблице (\\w+) есть данные: -timestamps")
                        val match = regex.find(text)
                        if (match != null) {
                            val tableName = match.groupValues[1]
                            arrayOf(MyPsiReference(element, tableName))
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