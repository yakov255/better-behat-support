package com.github.yakov255.betterbehatsupport

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project

class PhpMethodCallMapAction : AnAction("Show PHP Method Call Map") {
    override fun actionPerformed(e: AnActionEvent) {
        val project: Project? = e.project
        if (project != null) {
            val methodCalls = PhpMethodCallFinder.findMethodCalls(project, e)
            PhpMethodCallMapDialog(project, methodCalls).show()
        }
    }
}