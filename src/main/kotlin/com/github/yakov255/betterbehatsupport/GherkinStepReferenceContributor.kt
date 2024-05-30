package com.github.yakov255.betterbehatsupport

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
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
            PlatformPatterns.psiElement(GherkinStep::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                    val step = element as GherkinStep
                    val text = step.text
                    val matcher = Pattern.compile("(\\S+\\.(json|xml|txt))").matcher(text)
                    val references = mutableListOf<PsiReference>()

                    // There is no virtualFile when autocompletion
                    val featureFile = step.containingFile.virtualFile ?: return references.toTypedArray()

                    while (matcher.find()) {
                        val start = matcher.start(1)
                        val end = matcher.end(1)
                        val textRange = TextRange(start, end)

                        val fileName = text.substring(textRange.startOffset, textRange.endOffset)

                        val virtualDirectory: VirtualFile = featureFile.parent

                        val files = findFiles(virtualDirectory, fileName)

                        files.forEach {
                            references.add(GherkinStepFileReference(element, textRange, it))
                        }

                    }

                    return references.toTypedArray()
                }
            }
        )
    }

    private fun findFiles(virtualDirectory: VirtualFile, fileName: String): MutableList<VirtualFile> {
        val files = mutableListOf<VirtualFile>()

        // Split the fileName into components
        val parts = fileName.split("/")
        val targetFileName = parts.last()
        val targetPath = parts.dropLast(1)

        // Function to recursively search for files
        fun searchDirectory(directory: VirtualFile, path: List<String>): List<VirtualFile> {
            val results = mutableListOf<VirtualFile>()

            // Base case: if the path is empty, we are at the target directory level
            if (path.isEmpty()) {
                directory.children.forEach { file ->
                    if (!file.isDirectory && file.name == targetFileName) {
                        results.add(file)
                    }
                }
                return results
            }

            // Otherwise, continue searching in subdirectories
            val nextDirectoryName = path.first()
            directory.children.forEach { file ->
                if (file.isDirectory && file.name == nextDirectoryName) {
                    results.addAll(searchDirectory(file, path.drop(1)))
                }
            }
            return results
        }

        // Search the current directory
        files.addAll(searchDirectory(virtualDirectory, targetPath))

        // Search first-level subdirectories
        virtualDirectory.children.forEach { file ->
            if (file.isDirectory) {
                files.addAll(searchDirectory(file, targetPath))
            }
        }

        return files
    }
}