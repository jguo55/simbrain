package org.simbrain.network.gui.nodes

import org.simbrain.network.NetworkModel
import org.simbrain.network.gui.NetworkPanel
import org.simbrain.network.gui.dialogs.getUnsupervisedTrainingPanel
import org.simbrain.network.subnetworks.RestrictedBoltzmannMachine
import org.simbrain.util.StandardDialog
import org.simbrain.util.createAction
import org.simbrain.util.display
import org.simbrain.util.showNumericInputDialog
import org.simbrain.workspace.gui.CouplingMenu
import javax.swing.JPopupMenu

class RBMNode(networkPanel: NetworkPanel, private val rbm: RestrictedBoltzmannMachine):
    SubnetworkNode(networkPanel, rbm) {

    override val model: NetworkModel
        get() = rbm

    override val toolTipText: String
        get() = rbm.toString()

    override val contextMenu: JPopupMenu
        get() {
            val contextMenu = JPopupMenu()
            contextMenu.add(networkPanel.networkActions.cutAction)
            contextMenu.add(networkPanel.networkActions.copyAction)
            contextMenu.add(networkPanel.networkActions.pasteAction)
            contextMenu.addSeparator()

            // Edit Submenu
            contextMenu.add(networkPanel.createAction(name = "Edit network") {
                propertyDialog?.display()
            })
            contextMenu.add(networkPanel.networkActions.deleteAction)
            contextMenu.addSeparator()

            // Train Submenu
            contextMenu.add(networkPanel.createAction(name = "Training dialog...") {
                getUnsupervisedTrainingPanel(rbm) { rbm.trainOnCurrentPattern() }.display()
            })
            // Train once
            contextMenu.add(networkPanel.createAction(name = "Train once...") {
                val iterations: Int? = showNumericInputDialog("Iterations: ", 10)?.toInt()
                if (iterations != null) {
                    with(networkPanel.network) {
                        repeat(iterations) {
                            rbm.trainOnCurrentPattern()
                        }
                    }
                }
            })

            // Coupling menu
            contextMenu.addSeparator()
            contextMenu.add(CouplingMenu(networkPanel.networkComponent, rbm))

            return contextMenu
        }

    override val propertyDialog: StandardDialog
        get() = with(networkPanel) { getUnsupervisedTrainingPanel(rbm) }

}