package com.github.yakov255.betterbehatsupport

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase

class MyPsiReference(element: PsiElement, private val tableName: String) : PsiReferenceBase<PsiElement>(element) {

    override fun resolve(): PsiElement? {
        // Implement logic to resolve the table in the database
        // This might involve querying the database schema or looking up metadata
        // For the sake of example, return null (indicating unresolved)
        return null
    }

    override fun getVariants(): Array<Any> {
        return arrayOf()
    }

    override fun getRangeInElement(): TextRange {
        val text = element.text
        val start = text.indexOf(tableName)
        return TextRange(start, start + tableName.length)
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        // Implement logic for renaming the element, if necessary
        return element
    }
}