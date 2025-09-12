package com.github.yakov255.betterbehatsupport

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.*

class PhpMethodCallMapDialog(project: Project?, private val rootCallTree: MethodCallTreeNode?) : DialogWrapper(project) {

    init {
        init()
        title = "PHP Method Call Mind Map"
        setSize(1000, 700)
    }

    override fun createCenterPanel(): JComponent {
        val dialogPanel = JPanel(BorderLayout())
        
        if (rootCallTree == null) {
            val label = JLabel("No method found at cursor position or method call tree could not be built.")
            label.horizontalAlignment = SwingConstants.CENTER
            dialogPanel.add(label, BorderLayout.CENTER)
            return dialogPanel
        }

        // Create mindmap panel
        val mindMapPanel = MethodCallMindMapPanel(rootCallTree)
        val scrollPane = JScrollPane(mindMapPanel)
        scrollPane.preferredSize = JBUI.size(950, 600)
        scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        
        dialogPanel.add(scrollPane, BorderLayout.CENTER)
        
        // Add info panel
        val infoPanel = createInfoPanel()
        dialogPanel.add(infoPanel, BorderLayout.SOUTH)
        
        return dialogPanel
    }
    
    /**
     * Create info panel with instructions
     */
    private fun createInfoPanel(): JPanel {
        val infoPanel = JPanel(BorderLayout())
        infoPanel.border = BorderFactory.createTitledBorder("Method Call Mind Map")
        
        val infoText = """
            <html>
            <b>Visual Call Map:</b><br>
            • Center block shows the selected method with code context<br>
            • Surrounding blocks show methods that call the center method<br>
            • Arrows indicate call direction (caller → callee)<br>
            • Each block shows method signature and ±3 lines of code context<br>
            <br>
            <b>Interaction:</b><br>
            • Click to select a method block<br>
            • Double-click on any method block to navigate to its definition<br>
            • Scroll to explore larger call maps
            </html>
        """.trimIndent()
        
        val infoLabel = JLabel(infoText)
        infoPanel.add(infoLabel, BorderLayout.CENTER)
        
        return infoPanel
    }
    
    override fun createActions(): Array<Action> {
        return arrayOf(okAction)
    }
}