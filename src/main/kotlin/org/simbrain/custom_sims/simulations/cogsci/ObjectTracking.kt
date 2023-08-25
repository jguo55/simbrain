package org.simbrain.custom_sims.simulations

import org.simbrain.custom_sims.addNetworkComponent
import org.simbrain.custom_sims.couplingManager
import org.simbrain.custom_sims.newSim
import org.simbrain.custom_sims.updateAction
import org.simbrain.network.connections.Sparse
import org.simbrain.network.core.*
import org.simbrain.network.groups.NeuronCollection
import org.simbrain.network.layouts.GridLayout
import org.simbrain.network.neuron_update_rules.LinearRule
import org.simbrain.network.util.BiasedScalarData
import org.simbrain.network.util.SpikingMatrixData
import org.simbrain.network.util.SpikingScalarData
import org.simbrain.util.*
import org.simbrain.util.decayfunctions.StepDecayFunction
import org.simbrain.util.environment.SmellSource
import org.simbrain.util.stats.distributions.NormalDistribution
import org.simbrain.world.odorworld.OdorWorldComponent
import org.simbrain.world.odorworld.entities.EntityType
import org.simbrain.world.odorworld.sensors.ObjectSensor
import java.lang.Double.max
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.component3
import kotlin.math.cos
import kotlin.math.sin

/**
 * Create a reservoir simulation...
 */
val objectTrackingSim = newSim {

    // Number of reservoir neurons
    val numResNeurons = 200
    // Number of left and right sensory neurons. Total sensory neurons is twice this.
    val sensoryNeurons = 31
    // Radius in pixels of the cheese's revolution around the agent.
    val radiusOfRevolution = 100.0
    // Varaables to make cheese change direction once in a while
    var counter = 0
    var direction = 1 // 1 for counterclockwise -1 for clockwise

    // Basic setup
    workspace.clearWorkspace()
    val networkComponent = addNetworkComponent("Spontaneous Object Tracking")
    val network = networkComponent.network

    // Most connections in network use 10% density
    val sparse = Sparse()
    sparse.connectionDensity = .1

    // Add a self-connected neuron array to the network
    val resNeurons = (0..numResNeurons).map {
        val rule = AllostaticUpdateRule()
        val neuron = Neuron(network, AllostaticUpdateRule())
        neuron
    }
    network.addNetworkModelsAsync(resNeurons)
    val reservoir = NeuronCollection(network, resNeurons)
    network.addNetworkModel(reservoir)
    reservoir.label = "Reservoir"
    reservoir.layout(GridLayout())
    reservoir.location = point(0, 0)
    val reservoirSynapseGroup = SynapseGroup2(reservoir, reservoir, sparse)
    network.addNetworkModelAsync(reservoirSynapseGroup)
    val dist = NormalDistribution(1.0, .1)
    reservoirSynapseGroup.synapses.forEach { s ->
        s.strength = dist.sampleDouble()
    }

    // Left inputs
    val leftInputNeurons = (0 until sensoryNeurons).map {
        val rule = LinearRule()
        val neuron = Neuron(network, rule)
        neuron
    }
    network.addNetworkModelsAsync(leftInputNeurons)
    val leftInputs = NeuronCollection(network, leftInputNeurons)
    network.addNetworkModelAsync(leftInputs)
    leftInputs.label = "Left Inputs"
    leftInputs.layout(GridLayout())
    leftInputs.location = point(-616, -195)

    // Right inputs
    val rightInputNeurons = (0 until sensoryNeurons).map {
        val rule = LinearRule()
        val neuron = Neuron(network, rule)
        neuron
    }
    network.addNetworkModelsAsync(rightInputNeurons)
    val rightInputs = NeuronCollection(network, rightInputNeurons)
    network.addNetworkModelAsync(rightInputs)
    rightInputs.label = "Right Inputs"
    rightInputs.layout(GridLayout())
    rightInputs.location = point(-616, 225)

    // Connect input nodes to reservoir
    val leftInputsToRes = SynapseGroup2(leftInputs, reservoir, sparse)
    network.addNetworkModelAsync(leftInputsToRes)
    leftInputsToRes.synapses.forEach { s ->
        s.strength = 0.75
    }
    val rightInputsToRes = SynapseGroup2(rightInputs, reservoir, sparse)
    network.addNetworkModelAsync(rightInputsToRes)
    rightInputsToRes.synapses.forEach { s ->
        s.strength = 0.75
    }

    // Output neurons
    val leftTurnNeuron = Neuron(network, PercentIncomingNeuronRule())
    val rightTurnNeuron = Neuron(network, PercentIncomingNeuronRule())
    network.addNetworkModelAsync(leftTurnNeuron)
    network.addNetworkModelAsync(rightTurnNeuron)
    leftTurnNeuron.upperBound = 100.0
    rightTurnNeuron.upperBound = 100.0
    val leftTurnCollection = NeuronCollection(network, listOf(leftTurnNeuron))
    leftTurnCollection.label = "Left Turn"
    network.addNetworkModelAsync(leftTurnCollection)
    val rightTurnCollection = NeuronCollection(network, listOf(rightTurnNeuron))
    rightTurnCollection.label = "Right Turn"
    network.addNetworkModelAsync(rightTurnCollection)
    leftTurnNeuron.location = point(546, -203)
    rightTurnNeuron.location = point(573, 323)
    val resToLeftTurn = SynapseGroup2(reservoir, leftTurnCollection, sparse)
    network.addNetworkModelAsync(resToLeftTurn)
    val resToRightTurn = SynapseGroup2(reservoir, rightTurnCollection, sparse)
    network.addNetworkModelAsync(resToRightTurn)

    // Location of the network in the desktop
    withGui {
        place(networkComponent) {
            location = point(0, 0)
            width = 400
            height = 400
        }
    }

    // network.addUpdateAction(updateAction("Allostatic Learning Rule") {
    //     println("Custom update....")
    // })

    // ODOR WORLD STUFF

    val odorWorldComponent = OdorWorldComponent("World")
    val odorWorld = odorWorldComponent.world
    odorWorld.isObjectsBlockMovement = false

    // Agent
    val agent = odorWorld.addEntity(EntityType.CIRCLE).apply {
        location = point(odorWorld.width / 2.0, odorWorld.height / 2.0)
        heading = 90.0
        addDefaultEffectors()
    }

    // Effectors
    val (_, turnLeftEffector, turnRightEffector) = agent.effectors

    val fudge = 36.0 // to get the sensor range right

    val leftSensors = linspace(-31, 89, 31)
    // Left sensors (30 - sensoryNeurons / 2 until 30 + sensoryNeurons / 2).forEachIndexed { counter, position ->//
    leftSensors.forEachIndexed { counter, position ->
        val cheeseSensorLeft = ObjectSensor(EntityType.SWISS)
        cheeseSensorLeft.theta = position.toDouble()
        cheeseSensorLeft.radius = EntityType.CIRCLE.imageHeight / 2.0
        cheeseSensorLeft.decayFunction = StepDecayFunction()
        cheeseSensorLeft.decayFunction.dispersion = radiusOfRevolution - fudge
        with(couplingManager) {
            cheeseSensorLeft couple leftInputNeurons[counter]
        }
        agent.addSensor(cheeseSensorLeft)
    }

    // Right sensors (-30 - sensoryNeurons / 2 until -30 + sensoryNeurons / 2).forEachIndexed { counter, position ->//
    val rightSensors = linspace(-90, 30, 31)
    rightSensors.forEachIndexed { counter, position ->
        val cheeseSensorRight = ObjectSensor(EntityType.SWISS)
        cheeseSensorRight.theta = position.toDouble()
        cheeseSensorRight.radius = EntityType.CIRCLE.imageHeight / 2.0
        cheeseSensorRight.decayFunction = StepDecayFunction()
        cheeseSensorRight.decayFunction.dispersion = radiusOfRevolution - fudge
        with(couplingManager) {
            cheeseSensorRight couple rightInputNeurons[counter]
        }
        agent.addSensor(cheeseSensorRight)
    }

    // Objects
    val cheese = odorWorld.addEntity(EntityType.SWISS).apply {
        val (x, y) = point(200.0, 250.0)
        setLocation(x, y)
        smellSource = SmellSource(doubleArrayOf(1.0, .2, .5, .1, 1.0)).apply {
            this.dispersion = 200.0
        }
    }

    fun updateCheeseLocation() {
        val (agentx,agenty) = agent.location

        // Change direction every 2 rotations
        counter += 1
        if (counter % 720 == 0) {
            direction *= -1
        }

        cheese.location = point(
            agentx + radiusOfRevolution * cos((direction * counter).toRadian()),
            agenty + radiusOfRevolution * sin((direction * counter).toRadian())
        )
    }

    updateCheeseLocation()
    workspace.addUpdateAction(updateAction("Move cheese") {
        // println(reservoir.activations.mean)
        updateCheeseLocation()
    })

    workspace.addWorkspaceComponent(odorWorldComponent)
    withGui {
        place(odorWorldComponent) {
            location = point(403, 14)
        }
    }

    // Couple output neurons to effectors
    with(couplingManager) {
        leftTurnNeuron couple turnLeftEffector
        rightTurnNeuron couple turnRightEffector
    }

}

/**
 * Activation set = to number of positive inputs / total number of inputs.
 */
class PercentIncomingNeuronRule: LinearRule() {
    val maxVal = 10.0
    override fun apply(n: Neuron, data: BiasedScalarData) {
        n.activation = maxVal * n.fanIn.count { it.source.isSpike }
            .toDouble() / n.fanIn.size
    }
}

/**
 * See equation (1) in Falandays et. al. 2021
 */
fun Neuron.getAllostaticInput(): Double {
    // Treat linear inputs as sensors and do normal connectionist updating
    val sensorInputs = fanIn.filter { it.source.updateRule is LinearRule}.sumOf { it.source.activation * it.strength }
    // For spiking inputs sum weight strengths for pre-synaptic nodes that fired
    val weightsOfSpikingNodes = fanIn.filter { it.source.isSpike}.sumOf { it.strength }
    return sensorInputs + weightsOfSpikingNodes
}

class AllostaticDataHolder(
    target: Double = 1.0,

    @UserParameter(label = "threshold", minimumValue = 2.0)
    var threshold: Double = 2.0

) : SpikingScalarData() {

    @UserParameter(label = "target", minimumValue = 1.0)
    var target = target

    override fun copy(): SpikingScalarData {
        return AllostaticDataHolder(target, threshold)
    }
}

/**
 * From Falandays' et. al 2021. Add Homeostasis with adjustible set point
 *
 * Each node is characterized by 4 variables:
 * (1) a current activation level xn, initialized at 0;
 * (2) a fixed leak rate lr of 0.75 (e.g. if the activation level of a node is 1 at time t, the activation level will
 * be 0.75 at time t + 1, + in the absence of further input);
 * (3) a variable target activation level, initialized at Tn = 1;
 * (4) and a variable spiking threshold T’n, which was = always equal to 2Tn
 */
class AllostaticUpdateRule: SpikingNeuronUpdateRule<AllostaticDataHolder, SpikingMatrixData>() {

    @UserParameter(label = "leakRate")
    var leakRate = .75

    @UserParameter(label = "learning rate")
    var learningRate = .01

    override fun createScalarData(): AllostaticDataHolder = AllostaticDataHolder()

    override fun apply(n: Neuron, data: AllostaticDataHolder) {

        data as AllostaticDataHolder

        // Equation 1
        val newActivation = n.activation * leakRate + n.getAllostaticInput()
        n.activation = max(0.0, newActivation ) // Prevent from going below 0

        // Only apply learning if neuron has just spiked
        n.isSpike = false

        // Equation 2
        if (n.activation > data.threshold) {
            n.isSpike = true
            // println("Spike!")
            // Equation 3
            n.activation -= data.threshold
        }

        val error = n.activation - data.target

        // Weights
        val toTrain= n.fanIn
            .filter { it.source.updateRule is SpikingNeuronUpdateRule}
            .filter { it.source.isSpike}

        toTrain.forEach {  s ->
            if (toTrain.isNotEmpty()) {
                s.strength -= error/toTrain.size
            }
        }

        data.target += error * learningRate
        // Minimum target is 1
        data.target = max(data.target, 1.0)
        data.threshold = 2*data.target

        // println("target = ${n.target}, threshold = ${n.threshold}, activation = ${n.activation}")
    }

    override fun deepCopy(): AllostaticUpdateRule {
        val copy = AllostaticUpdateRule()
        copy.leakRate = leakRate
        copy.learningRate = learningRate
        return copy
    }

    override val name = "Allostatic Update Rule"

    // Test getSpikingInput
    fun main() {
        val net = Network()
        val n1 = Neuron(net)
        val n2 = Neuron(net)
        net.addNetworkModelsAsync(n1, n2)
        n1.setClamped(true)
        n2.setClamped(true)
        val n3 = Neuron(net)
        net.addNetworkModelAsync(n3)
        val s1 = Synapse(n1, n3)
        s1.strength = 1.0
        val s2 = Synapse(n2, n3)
        s2.strength = .5
        net.addNetworkModelsAsync(s1, s2)
        n1.isSpike = true
        n2.isSpike = true
        println(n3.getAllostaticInput())
    }

}