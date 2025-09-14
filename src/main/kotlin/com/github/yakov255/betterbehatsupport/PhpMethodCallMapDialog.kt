package com.github.yakov255.betterbehatsupport

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.*

class PhpMethodCallMapDialog(private val project: Project?, private val rootCallTree: MethodCallTreeNode?) : DialogWrapper(project) {

    private var diagramPanel: MethodCallDiagramPanel? = null

    init {
        init()
        title = "Method Call Diagram"
        setSize(1000, 700)
    }

    override fun createCenterPanel(): JComponent {
        val dialogPanel = JPanel(BorderLayout())
        
        if (rootCallTree == null || project == null) {
            val label = JLabel("No method found at cursor position or method call tree could not be built.")
            label.horizontalAlignment = SwingConstants.CENTER
            dialogPanel.add(label, BorderLayout.CENTER)
            return dialogPanel
        }

        // Create async diagram panel
        diagramPanel = MethodCallDiagramPanel(rootCallTree, project)
        val scrollPane = JScrollPane(diagramPanel)
        scrollPane.preferredSize = JBUI.size(950, 600)
        scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        
        dialogPanel.add(scrollPane, BorderLayout.CENTER)

        return dialogPanel
    }

    
    override fun createActions(): Array<Action> {
        return arrayOf(okAction)
    }
    
    override fun dispose() {
        // Clean up async resources
        diagramPanel?.dispose()
        super.dispose()
    }
}