package org.simbrain.custom_sims.simulations
import org.simbrain.custom_sims.addNetworkComponent
import org.simbrain.custom_sims.createControlPanel
import org.simbrain.custom_sims.newSim
import org.simbrain.network.core.WeightMatrix
import org.simbrain.network.core.addNeuronCollection
import org.simbrain.network.subnetworks.RestrictedBoltzmannMachine
import org.simbrain.util.place

/**
 * Demo for studying Room Schema From PDP Chapter 14.
 */

val roomSchemaSim = newSim {

    // Basic setup
    workspace.clearWorkspace()
    val networkComponent = addNetworkComponent("Network")
    val network = networkComponent.network

    // Competitive network
    val rbm = RestrictedBoltzmannMachine(40, 25)
    network.addNetworkModel(rbm)

    // Neuron Collection and Its Configurations
    val nc = network.addNeuronCollection(40).apply {
        setUpperBound(1.0)
        setLowerBound(0.0)
        setClamped(true)
        applyLayout(5, 8)
        locationX = -550.0
        locationY = 0.0
    }

    //Connecting Neuron Collection to Competitive Input Layer
    val wm = WeightMatrix(nc, rbm.visibleLayer)
    network.addNetworkModels(wm)

    // Labeling the neuron collection
    nc.neuronList[0].label = "ceiling"
    nc.neuronList[1].label = "large"
    nc.neuronList[2].label = "telephone"
    nc.neuronList[3].label = "books"
    nc.neuronList[4].label = "sofa"
    nc.neuronList[5].label = "drapes"
    nc.neuronList[6].label = "cupboard"
    nc.neuronList[7].label = "toilet"
    nc.neuronList[8].label = "walls"
    nc.neuronList[9].label = "medium"
    nc.neuronList[10].label = "bed"
    nc.neuronList[11].label = "desk-chair"
    nc.neuronList[12].label = "easy-chair"
    nc.neuronList[13].label = "stove"
    nc.neuronList[14].label = "sink"
    nc.neuronList[15].label = "scale"
    nc.neuronList[16].label = "door"
    nc.neuronList[17].label = "small"
    nc.neuronList[18].label = "typewriter"
    nc.neuronList[19].label = "clock"
    nc.neuronList[21].label = "coffee-cup"
    nc.neuronList[22].label = "coffeepot"
    nc.neuronList[20].label = "dresser"
    nc.neuronList[23].label = "oven"
    nc.neuronList[24].label = "windows"
    nc.neuronList[25].label = "very-small"
    nc.neuronList[26].label = "bookshelf"
    nc.neuronList[27].label = "picture"
    nc.neuronList[28].label = "ashtray"
    nc.neuronList[29].label = "refrigerator"
    nc.neuronList[30].label = "television"
    nc.neuronList[31].label = "computer"
    nc.neuronList[32].label = "very-large"
    nc.neuronList[33].label = "desk"
    nc.neuronList[34].label = "carpet"
    nc.neuronList[35].label = "floor-lamp"
    nc.neuronList[36].label = "fireplace"
    nc.neuronList[37].label = "toaster"
    nc.neuronList[38].label = "bathtub"
    nc.neuronList[39].label = "clothes-hanger"

    withGui {
        place(networkComponent, 139, 10, 1600, 900)
        createControlPanel("Control Panel", 5, 10) {
            addButton("Kitchen") {
                nc.clear()
                rbm.visibleLayer.clear()
                nc.getNeuronByLabel("oven")?.activation = 1.0
                nc.getNeuronByLabel("coffee-pot")?.activation = 1.0
                nc.getNeuronByLabel("cupboard")?.activation = 1.0
                nc.getNeuronByLabel("toaster")?.activation = 1.0
                nc.getNeuronByLabel("refrigerator")?.activation = 1.0
                nc.getNeuronByLabel("sink")?.activation = 1.0
                nc.getNeuronByLabel("stove")?.activation = 1.0
                nc.getNeuronByLabel("drapes")?.activation = 1.0
                nc.getNeuronByLabel("coffee-cup")?.activation = 1.0
                nc.getNeuronByLabel("clock")?.activation = 1.0
                nc.getNeuronByLabel("telephone")?.activation = 1.0
                nc.getNeuronByLabel("small")?.activation = 1.0
                nc.getNeuronByLabel("window")?.activation = 1.0
                nc.getNeuronByLabel("walls")?.activation = 1.0
                nc.getNeuronByLabel("ceiling")?.activation = 1.0
            }
            addButton("Office") {
                nc.clear()
                rbm.visibleLayer.clear()
                nc.getNeuronByLabel("computer")?.activation = 1.0
                nc.getNeuronByLabel("ash-tray")?.activation = 1.0
                nc.getNeuronByLabel("coffee-cup")?.activation = 1.0
                nc.getNeuronByLabel("picture")?.activation = 1.0
                nc.getNeuronByLabel("desk-chair")?.activation = 1.0
                nc.getNeuronByLabel("books")?.activation = 1.0
                nc.getNeuronByLabel("carpet")?.activation = 1.0
                nc.getNeuronByLabel("bookshelf")?.activation = 1.0
                nc.getNeuronByLabel("typewriter")?.activation = 1.0
                nc.getNeuronByLabel("telephone")?.activation = 1.0
                nc.getNeuronByLabel("desk")?.activation = 1.0
                nc.getNeuronByLabel("large")?.activation = 1.0
                nc.getNeuronByLabel("door")?.activation = 1.0
                nc.getNeuronByLabel("walls")?.activation = 1.0
                nc.getNeuronByLabel("ceiling")?.activation = 1.0
            }
            addButton("Bathroom") {
                nc.clear()
                rbm.visibleLayer.clear()
                nc.getNeuronByLabel("scale")?.activation = 1.0
                nc.getNeuronByLabel("toilet")?.activation = 1.0
                nc.getNeuronByLabel("bathtub")?.activation = 1.0
                nc.getNeuronByLabel("cupboard")?.activation = 1.0
                nc.getNeuronByLabel("sink")?.activation = 1.0
                nc.getNeuronByLabel("very-small")?.activation = 1.0
                nc.getNeuronByLabel("door")?.activation = 1.0
                nc.getNeuronByLabel("walls")?.activation = 1.0
                nc.getNeuronByLabel("ceiling")?.activation = 1.0
            }
            addButton("Living Room") {
                nc.clear()
                rbm.visibleLayer.clear()
                nc.getNeuronByLabel("television")?.activation = 1.0
                nc.getNeuronByLabel("drapes")?.activation = 1.0
                nc.getNeuronByLabel("fire-place")?.activation = 1.0
                nc.getNeuronByLabel("easy-chair")?.activation = 1.0
                nc.getNeuronByLabel("sofa")?.activation = 1.0
                nc.getNeuronByLabel("floor-lamp")?.activation = 1.0
                nc.getNeuronByLabel("picture")?.activation = 1.0
                nc.getNeuronByLabel("clock")?.activation = 1.0
                nc.getNeuronByLabel("books")?.activation = 1.0
                nc.getNeuronByLabel("carpet")?.activation = 1.0
                nc.getNeuronByLabel("bookshelf")?.activation = 1.0
                nc.getNeuronByLabel("telephone")?.activation = 1.0
                nc.getNeuronByLabel("very-large")?.activation = 1.0
                nc.getNeuronByLabel("window")?.activation = 1.0
                nc.getNeuronByLabel("door")?.activation = 1.0
                nc.getNeuronByLabel("walls")?.activation = 1.0
                nc.getNeuronByLabel("ceiling")?.activation = 1.0
            }
            addButton("Bedroom") {
                nc.clear()
                rbm.visibleLayer.clear()
                nc.getNeuronByLabel("coat-hanger")?.activation = 1.0
                nc.getNeuronByLabel("television")?.activation = 1.0
                nc.getNeuronByLabel("dresser")?.activation = 1.0
                nc.getNeuronByLabel("drapes")?.activation = 1.0
                nc.getNeuronByLabel("picture")?.activation = 1.0
                nc.getNeuronByLabel("clock")?.activation = 1.0
                nc.getNeuronByLabel("books")?.activation = 1.0
                nc.getNeuronByLabel("carpet")?.activation = 1.0
                nc.getNeuronByLabel("bookshelf")?.activation = 1.0
                nc.getNeuronByLabel("bed")?.activation = 1.0
                nc.getNeuronByLabel("medium")?.activation = 1.0
                nc.getNeuronByLabel("window")?.activation = 1.0
                nc.getNeuronByLabel("door")?.activation = 1.0
                nc.getNeuronByLabel("walls")?.activation = 1.0
                nc.getNeuronByLabel("ceiling")?.activation = 1.0
            }
        }
    }

}