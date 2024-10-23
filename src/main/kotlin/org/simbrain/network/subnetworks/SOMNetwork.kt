/*
 * Part of Simbrain--a java-based neural network kit
 * Copyright (C) 2005,2007 The Authors.  See http://www.simbrain.net/credits
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.simbrain.network.subnetworks

import org.simbrain.network.connections.AllToAll
import org.simbrain.network.core.Network
import org.simbrain.network.core.SynapseGroup
import org.simbrain.network.core.XStreamConstructor
import org.simbrain.network.neurongroups.NeuronGroup
import org.simbrain.network.neurongroups.SOMGroup
import org.simbrain.network.trainers.UnsupervisedNetwork
import org.simbrain.network.trainers.UnsupervisedTrainer
import org.simbrain.network.util.Alignment
import org.simbrain.network.util.Direction
import org.simbrain.network.util.alignNetworkModels
import org.simbrain.network.util.offsetNeuronCollections
import org.simbrain.util.UserParameter
import org.simbrain.util.binaryRandomize
import org.simbrain.util.propertyeditor.EditableObject
import smile.math.matrix.Matrix

/**
 * SOMNetwork is a  network encompassing an [SomGroup]. An input
 * layer and input data have been added so that the SOM can be easily trained
 * using existing Simbrain GUI tools
 *
 * @author Jeff Yoshimi
 */
class SOMNetwork : Subnetwork, UnsupervisedNetwork {

    lateinit var som: SOMGroup

    override lateinit var inputLayer: NeuronGroup

    override val trainer = UnsupervisedTrainer()

    override lateinit var inputData: Matrix

    constructor(numInputNeurons: Int, numSOMNeurons: Int): super() {
        som = SOMGroup(numSOMNeurons)
        som.label = "SOM Group"
        this.addModel(som)
        som.applyLayout()

        inputLayer = NeuronGroup(numInputNeurons)
        inputLayer.setLayoutBasedOnSize()
        this.addModel(inputLayer)
        for (neuron in inputLayer.neuronList) {
            neuron.lowerBound = 0.0
        }
        inputLayer.label = "Input layer"
        inputLayer.setClamped(true)

        this.inputData = Matrix(10, numInputNeurons).binaryRandomize()

        // Connect layers
        val sg = SynapseGroup(inputLayer, som, AllToAll())
        addModel(sg)

        alignNetworkModels(inputLayer, som, Alignment.VERTICAL)
        offsetNeuronCollections(inputLayer, som, Direction.NORTH, 300.0)
    }

    @XStreamConstructor
    constructor(): super()

    context(Network)
    override fun accumulateInputs() {
        inputLayer.accumulateInputs()
    }

    context(Network) override fun update() {
        inputLayer.update()
        // SOM does not need to accumulate inputs because it computes weighted inputs directly
        som.update()
    }

    context(Network) override fun trainOnInputData() {
        inputData.toArray().forEach { row ->
            inputLayer.activationArray = row
            trainOnCurrentPattern()
        }
    }

    context(Network) override fun trainOnCurrentPattern() {
        this.update()
    }

    /**
     * Helper class for creating new SOM nets using [org.simbrain.util.propertyeditor.AnnotatedPropertyEditor].
     */
    class SOMCreator : EditableObject {

        @UserParameter(label = "Number of som neurons", order = 10)
        var numSom: Int = 20

        @UserParameter(label = "Number of inputs", order = 20)
        var numIn: Int = 16

        fun create(): SOMNetwork {
            return SOMNetwork(numIn, numSom)
        }
    }
}
