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
        title = "Method Call Diagram - Async Loading"
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
        
        // Add info panel with queue status
        val infoPanel = createInfoPanel()
        dialogPanel.add(infoPanel, BorderLayout.SOUTH)
        
        return dialogPanel
    }
    
    /**
     * Create info panel with async loading instructions
     */
    private fun createInfoPanel(): JPanel {
        val infoPanel = JPanel(BorderLayout())
        infoPanel.border = BorderFactory.createTitledBorder("Async Method Call Diagram")
        
        val infoText = """
            <html>
            <b>Progressive Loading:</b><br>
            • Method blocks load callers progressively as you expand them<br>
            • Click ▶ button to expand and find callers for a method<br>
            • Loading spinner shows when searching for callers<br>
            • Progress bar indicates search progress<br>
            • Error indicator (⚠) appears if search fails - click to retry<br>
            <br>
            <b>Visual Indicators:</b><br>
            • Blue blocks: Currently loading callers<br>
            • Red blocks: Error occurred during loading<br>
            • Gray blocks: Ready to expand<br>
            • Cyan block: Root method (your selected method)<br>
            <br>
            <b>Navigation:</b><br>
            • Click ▶ to expand callers (async loading)<br>
            • Click ▼ to collapse expanded callers<br>
            • Double-click any block to navigate to method definition<br>
            • Mouse wheel to zoom, drag to pan<br>
            • Hover for detailed tooltips
            </html>
        """.trimIndent()
        
        val infoLabel = JLabel(infoText)
        infoPanel.add(infoLabel, BorderLayout.CENTER)
        
        return infoPanel
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