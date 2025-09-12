package com.github.yakov255.betterbehatsupport

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

import com.intellij.psi.PsiElement
import javax.swing.JTextArea
import javax.swing.JScrollPane

class PhpMethodCallMapDialog(project: Project?, private val methodCalls: List<PsiElement>) : DialogWrapper(project) {

    init {
        init()
        title = "PHP Method Call Map"
    }

    override fun createCenterPanel(): JComponent {
        val dialogPanel = JPanel(BorderLayout())
        val textArea = JTextArea()
        textArea.isEditable = false
        if (methodCalls.isEmpty()) {
            textArea.text = "No method calls found for the current method."
        } else {
            textArea.text = methodCalls.joinToString("\n") { it.text }
        }
        val scrollPane = JScrollPane(textArea)
        scrollPane.preferredSize = JBUI.size(600, 400)
        dialogPanel.add(scrollPane, BorderLayout.CENTER)
        return dialogPanel
    }
}