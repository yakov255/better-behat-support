package com.github.yakov255.betterbehatsupport

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.pom.Navigatable
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

class PhpMethodCallMapDialog(project: Project?, private val rootCallTree: MethodCallTreeNode?) : DialogWrapper(project) {

    private lateinit var tree: Tree
    private lateinit var treeModel: DefaultTreeModel

    init {
        init()
        title = "PHP Method Call Tree"
        setSize(800, 600)
    }

    override fun createCenterPanel(): JComponent {
        val dialogPanel = JPanel(BorderLayout())
        
        if (rootCallTree == null) {
            val label = JLabel("No method found at cursor position or method call tree could not be built.")
            label.horizontalAlignment = SwingConstants.CENTER
            dialogPanel.add(label, BorderLayout.CENTER)
            return dialogPanel
        }

        // Create tree structure
        val rootNode = createTreeNode(rootCallTree, mutableSetOf())
        treeModel = DefaultTreeModel(rootNode)
        tree = Tree(treeModel)
        
        // Configure tree appearance
        tree.isRootVisible = true
        tree.showsRootHandles = true
        tree.expandRow(0) // Expand root by default
        
        // Add double-click listener to navigate to method
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val path = tree.getPathForLocation(e.x, e.y)
                    if (path != null) {
                        navigateToMethod(path)
                    }
                }
            }
        })
        
        val scrollPane = JScrollPane(tree)
        scrollPane.preferredSize = JBUI.size(750, 500)
        dialogPanel.add(scrollPane, BorderLayout.CENTER)
        
        // Add info panel
        val infoPanel = createInfoPanel()
        dialogPanel.add(infoPanel, BorderLayout.SOUTH)
        
        return dialogPanel
    }
    
    /**
     * Create tree node recursively with circular reference detection
     * Shows only callers in a stack trace format
     */
    private fun createTreeNode(callTreeNode: MethodCallTreeNode, visited: MutableSet<String>): DefaultMutableTreeNode {
        val displayText = callTreeNode.getSimpleDisplayText()
        val treeNode = DefaultMutableTreeNode(CallTreeNodeData(callTreeNode, displayText))
        
        // Prevent infinite recursion
        if (visited.contains(callTreeNode.methodId)) {
            treeNode.add(DefaultMutableTreeNode("... (circular reference)"))
            return treeNode
        }
        
        visited.add(callTreeNode.methodId)
        
        // Add callers directly without grouping label (stack trace style)
        callTreeNode.callers.forEach { caller ->
            treeNode.add(createTreeNode(caller, visited.toMutableSet()))
        }
        
        visited.remove(callTreeNode.methodId)
        return treeNode
    }
    
    /**
     * Create info panel with instructions
     */
    private fun createInfoPanel(): JPanel {
        val infoPanel = JPanel(BorderLayout())
        infoPanel.border = BorderFactory.createTitledBorder("Call Stack")
        
        val infoText = """
            <html>
            <b>Call Stack Trace:</b><br>
            • Shows who calls the selected method (stack trace format)<br>
            • Root node is the selected method<br>
            • Child nodes show callers in hierarchical order<br>
            <br>
            <b>Navigation:</b><br>
            • Double-click on any method to navigate to its definition<br>
            • Expand/collapse nodes to explore the call hierarchy
            </html>
        """.trimIndent()
        
        val infoLabel = JLabel(infoText)
        infoPanel.add(infoLabel, BorderLayout.CENTER)
        
        return infoPanel
    }
    
    /**
     * Navigate to the selected method in the editor
     */
    private fun navigateToMethod(path: TreePath) {
        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
        val nodeData = node.userObject as? CallTreeNodeData ?: return
        
        try {
            // Navigate to the method in the editor
            val method = nodeData.callTreeNode.method
            if (method is Navigatable && method.canNavigate()) {
                method.navigate(true)
            }
        } catch (e: Exception) {
            // Handle navigation errors gracefully
        }
    }
    
    /**
     * Data class to hold both the tree node and display text
     */
    private data class CallTreeNodeData(
        val callTreeNode: MethodCallTreeNode,
        val displayText: String
    ) {
        override fun toString(): String = displayText
    }
    
    override fun createActions(): Array<Action> {
        return arrayOf(okAction)
    }
}