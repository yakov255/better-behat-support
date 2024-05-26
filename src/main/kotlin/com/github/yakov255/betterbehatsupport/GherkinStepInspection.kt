package com.github.yakov255.betterbehatsupport

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.cucumber.psi.GherkinFile

class GherkinStepInspection : LocalInspectionTool() {

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        if (file !is GherkinFile) return null

        val problems = mutableListOf<ProblemDescriptor>()
        val matcher = Enum().pattern.matcher(file.text)

        while (matcher.find()) {
            val fileName = matcher.group(1)

            val filesDir = file.virtualFile.parent.findChild("expected_responses")
            if(filesDir?.findChild(fileName) != null){
                return problems.toTypedArray()
            }


            val startOffset = matcher.start(1)
            val endOffset = matcher.end(1)

            val problemDescriptor = manager.createProblemDescriptor(
                file,
                TextRange(startOffset, endOffset),
                "File $fileName should exist",
                ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                isOnTheFly,
                object : LocalQuickFix {
                    override fun getName() = "Create file $fileName"

                    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                        filesDir?.createChildData(this, fileName)
                    }

                    override fun getFamilyName() = name
                }
            )

            problems.add(problemDescriptor)
        }

        return problems.toTypedArray()
    }
}