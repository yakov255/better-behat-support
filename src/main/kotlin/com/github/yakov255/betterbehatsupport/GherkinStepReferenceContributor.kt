package com.github.yakov255.betterbehatsupport

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import java.util.regex.Pattern

class GherkinStepReferenceContributor : PsiReferenceContributor() {

    companion object {
        private val STEP_FILE_PATTERN = Pattern.compile("(\\S+\\.\\S{2,})")
    }

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(GherkinStep::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                    val step = element as GherkinStep
                    val text = step.text
                    val matcher = STEP_FILE_PATTERN.matcher(text)

                    val featureFile = step.containingFile.virtualFile ?: return emptyArray()
                    val virtualDirectory = featureFile.parent ?: return emptyArray()

                    val references = mutableListOf<PsiReference>()

                    while (matcher.find()) {
                        val start = matcher.start(1)
                        val end = matcher.end(1)
                        val textRange = TextRange(start, end)
                        val fileName = text.substring(textRange.startOffset, textRange.endOffset)
                            .trim('"', '\'')

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

    private fun findFiles(virtualDirectory: VirtualFile, fileName: String): List<VirtualFile> {
        ProgressManager.checkCanceled()

        val directFile = VfsUtil.findRelativeFile(virtualDirectory, *fileName.split('/').toTypedArray())
        if (directFile != null && !directFile.isDirectory) {
            return listOf(directFile)
        }

        ProgressManager.checkCanceled()

        val results = mutableListOf<VirtualFile>()
        val pathSegments = fileName.split('/').toTypedArray()

        // Try resolving the full path relative to each immediate subdirectory
        virtualDirectory.children.forEach { child ->
            if (child.isDirectory) {
                ProgressManager.checkCanceled()
                val file = VfsUtil.findRelativeFile(child, *pathSegments)
                if (file != null && !file.isDirectory) {
                    results.add(file)
                }
            }
        }

        return results
    }
}
