package org.simbrain.custom_sims.simulations

import kotlinx.coroutines.awaitAll
import org.simbrain.custom_sims.*
import org.simbrain.network.connections.OneToOne
import org.simbrain.network.layouts.LineLayout
import org.simbrain.network.neurongroups.NormalizationGroup
import org.simbrain.util.place
import org.simbrain.util.point
import org.simbrain.world.odorworld.entities.EntityType
import java.awt.Dimension

/**
 * Based on Spivey's 2024 paper, "A Linking Hypothesis for Eyetracking and Mousetracking
 * in the Visual World Paradigm"
 */
val spiveyNet = newSim {

    workspace.clearWorkspace()

    // Network
    val networkComponent = addNetworkComponent("Spivey Net")
    val net = networkComponent.network

    // TODO: Add Integration nodes and eye nodes and lay them out / wire them up properly
    // (Right now just did a few to show the idea)
    val lexicalNodes = NormalizationGroup(4).apply {
        layout = LineLayout()
        applyLayout()
        label = "Lexical"
    }
    val visualNodes = NormalizationGroup(4).apply {
        layout = LineLayout()
        applyLayout()
        label = "Vision"
    }
    val mouseNodes = NormalizationGroup(4).apply {
        layout = LineLayout()
        applyLayout()
        label = "Mouse"
    }
    net.addNetworkModels(lexicalNodes, visualNodes, mouseNodes).awaitAll()
    lexicalNodes.location = point(0,0)
    visualNodes.location = point(14,152)
    mouseNodes.location = point(240,152)

    val connector = OneToOne().apply {
        percentExcitatory = 100.0
        useBidirectionalConnections = true
    }
    net.addNetworkModels(connector.connectNeurons(lexicalNodes.neuronList, visualNodes.neuronList))
    net.addNetworkModels(connector.connectNeurons(visualNodes.neuronList, mouseNodes.neuronList))

    // World
    val oc = addOdorWorldComponent()
    val world = oc.world
    world.isUseCameraCentering = false
    desktop?.getDesktopComponent(oc)?.title = "Mouse Trace"
    val mouse = world.addEntity(157, 271, EntityType.MOUSE).apply {
        heading = 90.0
    }
    world.addEntity(38, 49, EntityType.CANDLE)
    world.addEntity(287, 44, EntityType.BELL)
    mouse.isShowTrail = true

    val initialLocation = mouse.location

    workspace.addUpdateAction("Move mouse") {
        if (mouse.y > 15) {
            mouse.y -= 1
        }
        // TODO: Properly implement this to deal with all four nodes and check the paper
        mouse.x += (mouseNodes.neuronList[0].activation)
        //mouse.x += (mouseNodes.neuronList[0].activation - mouseNodes.neuronList[1].activation)
    }


    withGui {
        //place(docViewer, 0, 0, 464, 619)
        place(networkComponent, 222, 15, 400, 400)
        place(oc, 613, 15, 391, 455)
        createControlPanel("Control Panel", 15, 15) {
            addButton("Pattern 1") {
                lexicalNodes.setActivations(doubleArrayOf(1.0,1.0,1.0,1.0))
                visualNodes.setActivations(doubleArrayOf(1.0,1.0,1.0,1.0))
            }.apply {
                // Hack to make the panel wider
                preferredSize = Dimension(170, 30)
            }
            addButton("Pattern 2") {
                lexicalNodes.setActivations(doubleArrayOf(-1.0,1.0,-1.0,1.0))
                visualNodes.setActivations(doubleArrayOf(1.0,-1.0,1.0,-1.0))
            }
            addButton("Reset") {
                mouse.clearTrail()
                mouse.location = initialLocation
            }
        }
    }

    //val docViewer = addDocViewer(
    //    "Information",
    //    """
    //        # Introduction
    //
    //        The Hopfield simulation is a recurrent neural network with a synaptic connection pattern for pattern recognition and memory retrieval.
    //
    //        # What to do
    //
    //        - Select an input pattern and click the train button on the Control panel to train the network on the selected pattern.
    //        - The model learns the pattern and “remembers” it.
    //        - When randomizing the network (by clicking “N” [Neuron], “R” [Randomize], and “Space” [Iterate], or using “I” [Wand Mode] over the nodes), the network adjusts the nodes on each iteration to reconfigure the inputted pattern.
    //        - The Network remembers the pattern and the antipattern, and when iterating (“Space”), it iterates to recreate the pattern with the most similar nodes.
    //
    //        You can get the pattern to memorize all the different patterns and antipatterns by training each one, randomizing and iterating to see if it is remembered, and training that pattern again if it needs to be learned.
    //
    //
    //    """.trimIndent()
    //)
}