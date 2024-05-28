package com.github.yakov255.betterbehatsupport

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase

class FilePsiReference(element: PsiElement, private val fileName: String) : PsiReferenceBase<PsiElement>(element) {

    override fun resolve(): PsiElement? {
        val project = myElement.project
        val featureFile = myElement.containingFile.virtualFile
        val filesDir: VirtualFile? = featureFile.parent?.findChild(Enum.directory)

        val targetFile = filesDir?.findChild(fileName)
        return targetFile?.let { PsiManager.getInstance(project).findFile(it) }
    }

    override fun getVariants(): Array<Any> {
        return emptyArray()
    }
}