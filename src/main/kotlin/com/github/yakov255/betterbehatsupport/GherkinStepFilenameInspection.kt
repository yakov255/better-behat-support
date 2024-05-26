package com.github.yakov255.betterbehatsupport

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.cucumber.psi.GherkinElementVisitor
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import java.io.File

class GherkinStepFilenameInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : GherkinElementVisitor() {
            override fun visitStep(step: GherkinStep) {
                val stepText = step.text
                val regex = Regex(Enum.pattern)
                val matchResult = regex.find(stepText)

                if (matchResult != null) {
                    val fileName = matchResult.groupValues[1]
                    val featureFile = step.containingFile.virtualFile
                    val filesDir = File(featureFile.parent.path, Enum.directory)

                    if (!filesDir.exists()) {
                        return
                    }
                    val referencedFile = File(filesDir, fileName)

                    if (!referencedFile.exists()) {

                        val fix = object : LocalQuickFix {
                            override fun getName() = "Create file $fileName"

                            override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                                referencedFile.createNewFile()
                            }

                            override fun getFamilyName() = name
                        }

                        holder.registerProblem(
                            step,
                            "File '$fileName' not found in the '${Enum.directory}' directory.",
                            ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                            fix
                        )
                    }
                }
            }
        }
    }
}