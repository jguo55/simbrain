package org.simbrain.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import org.simbrain.util.propertyeditor.AnnotatedPropertyEditor
import org.simbrain.util.propertyeditor.EditableObject
import org.simbrain.util.widgets.DropDownTriangle
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.event.*
import java.io.File
import javax.swing.*

inline fun StandardDialog.onClosed(crossinline block: (WindowEvent?) -> Unit) = apply {
    addWindowListener(object : WindowAdapter() {
        override fun windowClosed(e: WindowEvent?) {
            block(e)
        }
    })
}

fun showSaveDialog(
    initialDirectory: String = "",
    initialFileName: String? = null,
    block: File.() -> Unit
) {
    val chooser = SFileChooser(initialDirectory, "")
    val theFile = if (initialFileName != null) {
        chooser.showSaveDialog(initialFileName)
    } else {
        chooser.showSaveDialog()
    }
    if (theFile != null) {
        theFile.block()
    }
}

fun showOpenDialog(
    initialDirectory: String = "",
    extension: String? = null,
    block: File.() -> Unit
) {
    val chooser = SFileChooser(initialDirectory, "")
    if (extension != null) {
        chooser.addExtension(extension)
    }
    val theFile = chooser.showOpenDialog()
    if (theFile != null) {
        theFile.block()
    }
}

fun main() {
    // showOpenDialog(extension = "txt") {
    //     println(this.readText())
    // }
    // showSaveDialog("", "test.txt") {
    //     writeText("testing...")
    // }

    // print(showDirectorySelectionDialog())
    // val userInput = showNumericInputDialog("Enter a number:", 1)
    val userInput = showNumericInputDialog("Enter a number:", 1.0)
    println("User entered: $userInput")
}

/**
 * Shows a dialog that lets you select a directory, then returns that directory path as a string.
 */
fun showDirectorySelectionDialog(): String? {
    val chooser = JFileChooser()
    chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    return if (chooser.showDialog(null, "Select Folder") == JFileChooser.APPROVE_OPTION) {
        return if (chooser.selectedFile.isDirectory) {
            chooser.selectedFile.path
        } else {
            chooser.currentDirectory.path
        }
    } else {
        null
    }
}

/**
 * Place the panel in a [StandardDialog] and show the dialog.
 */
@JvmOverloads
fun <T : JComponent> T.displayInDialog(block: T.() -> Unit = {}): StandardDialog {
    val dialog = StandardDialog()
    dialog.contentPane = this
    dialog.makeVisible()
    dialog.addClosingTask { block() }
    return dialog
}

fun JDialog.display() {
    pack()
    setLocationRelativeTo(null)
    isVisible = true
}

inline fun Component.onDoubleClick(crossinline block: MouseEvent.() -> Unit) {
    addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent?) {
            if (e?.clickCount == 2 && e.button == MouseEvent.BUTTON1) e.block()
        }
    })
}

/**
 * The base createAction function. Requires a list of key combos. See other versions if only one or no keyboard shortcut was used.
 */
fun <T : JComponent> T.createAction(
    name: String? = null,
    description: String? = null,
    iconPath: String? = null,
    keyboardShortcuts: List<KeyCombination>,
    initBlock: AbstractAction.() -> Unit = {},
    coroutineScope: CoroutineScope? = null,
    block: suspend T.(e: ActionEvent) -> Unit
): AbstractAction {
    return object : AbstractAction() {
        init {
            if (iconPath != null) {
                putValue(SMALL_ICON, ResourceManager.getSmallIcon(iconPath))
            }

            putValue(NAME, name)
            putValue(SHORT_DESCRIPTION, description)
            keyboardShortcuts.forEach { keyCombo ->
                keyCombo.withKeyStroke { putValue(ACCELERATOR_KEY, it) }
                this@createAction.bindTo(keyCombo, this)
            }

            initBlock()
        }

        override fun actionPerformed(e: ActionEvent) {
            if (coroutineScope != null) {
                coroutineScope.launch { block(e) }
            } else if (this@createAction is CoroutineScope) {
                this@createAction.launch { block(e) }
            } else {
                runBlocking { block(e) }
            }
        }
    }
}

/**
 * The [createAction] with one or no keyboard shortcut.
 */
fun <T : JComponent> T.createAction(
    name: String? = null,
    description: String? = null,
    iconPath: String? = null,
    keyboardShortcut: KeyCombination? = null,
    initBlock: AbstractAction.() -> Unit = {},
    coroutineScope: CoroutineScope? = null,
    block: suspend T.(e: ActionEvent) -> Unit
) = createAction(name, description, iconPath, keyboardShortcut?.let { listOf(it) } ?: listOf(), initBlock, coroutineScope, block)

/**
 * Create an action when no JComponent available. Keyboard shortcuts are not possible.
 */
fun createAction(
    name: String? = null,
    iconPath: String? = null,
    description: String? = null,
    block: (e: ActionEvent) -> Unit
): AbstractAction {
    return object : AbstractAction() {
        init {
            if (iconPath != null) {
                putValue(SMALL_ICON, ResourceManager.getSmallIcon(iconPath))
            }

            putValue(NAME, name)
            putValue(SHORT_DESCRIPTION, description)
        }

        override fun actionPerformed(e: ActionEvent) {
            block(e)
        }
    }
}

/**
 * [createAction] with a char rather than a keyboard shortcut
 */
fun <T : JComponent> T.createAction(
    iconPath: String = "",
    name: String = "",
    description: String = "",
    keyboardShortcut: Char,
    initBlock: AbstractAction.() -> Unit = {},
    coroutineScope: CoroutineScope? = null,
    block: suspend T.(e: ActionEvent) -> Unit
): AbstractAction {
    return createAction(
        iconPath = iconPath,
        name = name,
        description = description,
        keyboardShortcuts = listOf(KeyCombination(keyboardShortcut)),
        initBlock = initBlock,
        coroutineScope = coroutineScope,
        block = block
    )
}

/**
 * Shows a dialog for setting an editable object in an [AnnotatedPropertyEditor]. The provided block is executed when
 * closing the dialog. Add [display] after this call to create and display the dialog.
 */
@JvmOverloads
fun <E : EditableObject> E.createEditorDialog(block: (E) -> Unit = {}): StandardDialog {
    val editor = AnnotatedPropertyEditor(listOf(this))
    return StandardDialog(editor).apply {
        addClosingTask {
            editor.commitChanges()
            block(this@createEditorDialog)
        }
    }
}

fun showWarningDialog(message: String) {
    val dialog = JDialog()
    dialog.isAlwaysOnTop = true
    JOptionPane.showMessageDialog(dialog, message, "Warning!", JOptionPane.WARNING_MESSAGE)
}

fun showWarningConfirmDialog(message: String): Int {
    val dialog = JDialog()
    dialog.isAlwaysOnTop = true
    return JOptionPane.showConfirmDialog(dialog, message, "Warning!", JOptionPane.WARNING_MESSAGE)
}

/**
 * Create a dialog that takes an input in a text field and returns it as a string.
 */
fun showInputDialog(message: String): String {
    val dialog = JDialog()
    dialog.isAlwaysOnTop = true
    return JOptionPane.showInputDialog(dialog, message)
}

/**
 * Create a dialog that takes an input in a text field and returns it as a number.
 * Returns null if user cancels the input.
 *
 * Init value determines the type. Int and Double supported.
 */
fun <T: Number> showNumericInputDialog(message: String, initValue: T): T? {
    val dialog = JDialog()
    dialog.isAlwaysOnTop = true
    while (true) {
        val result = JOptionPane.showInputDialog(dialog, message, initValue)
        if (result == null) {
            // User cancelled
            return null
        }

        when (initValue) {
            is Int -> {
                try {
                    return Integer.parseInt(result) as T
                } catch (e: NumberFormatException) { /* continue below */ }
            }
            is Double -> {
                try {
                    return java.lang.Double.parseDouble(result) as T
                } catch (e: NumberFormatException) { /* continue below */ }
            }
            else -> {
                JOptionPane.showMessageDialog(dialog, "Unsupported number type!", "Error", JOptionPane.ERROR_MESSAGE)
                return null
            }
        }
        JOptionPane.showMessageDialog(dialog, "Please enter a valid number!", "Warning", JOptionPane.WARNING_MESSAGE)
    }
}

/**
 * Collapsable panel that uses a [DetailTriangle]
 */
class DetailTrianglePanel @JvmOverloads constructor(
    val contentPanel: JPanel,
    defaultOpen: Boolean = true,
    upLabel: String = "Settings",
    downLabel: String = upLabel,
    val topPanelComponent: JComponent? = null,
): JPanel() {

    val topPanel = JPanel().apply {
        val padding = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        alignmentX = CENTER_ALIGNMENT
        border = padding

        if (topPanelComponent != null) {
            add(topPanelComponent)
        }

        add(Box.createHorizontalStrut(30))
        add(Box.createHorizontalGlue())
    }.also {
        add(it)
    }

    val detailTriangle = DropDownTriangle(
        DropDownTriangle.UpDirection.LEFT,
        defaultOpen,
        upLabel,
        downLabel
    ).also {
        it.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(arg0: MouseEvent) {
                contentPanel.isVisible = it.isDown
                repaint()
                SwingUtilities.getWindowAncestor(this@DetailTrianglePanel)?.pack()
            }
        })
        topPanel.add(it)
    }

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        contentPanel.isVisible = detailTriangle.isDown
        add(contentPanel)
    }

}

fun <C: JComponent> C.createApplyPanel(commitAction: C.() -> Unit): JPanel {
    val component = this
    return JPanel().apply {
        layout = BorderLayout()
        add(component)
        JButton("Apply").apply {
            addActionListener {
                commitAction()
            }
        }.also { applyButton ->
            add(JPanel(FlowLayout(FlowLayout.RIGHT)).also { it.add(applyButton) }, BorderLayout.SOUTH)
        }
    }
}

val swingDispatcher get() = Dispatchers.Swing