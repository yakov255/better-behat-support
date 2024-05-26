package com.github.yakov255.betterbehatsupport

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase

class FilePsiReference(element: PsiElement, private val fileName: String) : PsiReferenceBase<PsiElement>(element) {

    override fun resolve(): PsiElement? {
        val project = myElement.project
        val virtualFile = myElement.containingFile.virtualFile

        return virtualFile.parent?.findChild(Enum.directory)?.findChild(fileName)?.let { targetFile ->
            PsiManager.getInstance(project).findFile(targetFile)
        }
    }
}