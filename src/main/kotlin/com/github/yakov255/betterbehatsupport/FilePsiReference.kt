package com.github.yakov255.betterbehatsupport

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase

class FilePsiReference(element: PsiElement, private val fileName: String) : PsiReferenceBase<PsiElement>(element) {

    override fun resolve(): PsiElement? {

        val project: Project = myElement.project
        val virtualFile: VirtualFile = myElement.containingFile.virtualFile

        // Get the directory containing the current file
        val currentDir: VirtualFile? = virtualFile.parent
        currentDir ?: return null // Return null if there is no parent directory

        // Find the "files" directory alongside the current file's directory
        val filesDir: VirtualFile? = currentDir.findChild(Enum.directory)
        filesDir ?: return null // Return null if the "files" directory does not exist

        // Find the target file in the "files" directory
        val targetFile: VirtualFile? = filesDir.findChild(fileName)
        targetFile ?: return null // Return null if the target file does not exist

        // Convert the VirtualFile to a PsiFile
        val psiFile: PsiFile? = PsiManager.getInstance(project).findFile(targetFile)
        return psiFile
    }
}