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

import org.simbrain.network.NetworkModel
import org.simbrain.network.core.*
import org.simbrain.network.neurongroups.NeuronGroup
import org.simbrain.network.updaterules.BinaryRule
import org.simbrain.util.UserParameter
import org.simbrain.util.format
import org.simbrain.util.propertyeditor.EditableObject
import java.util.*
import java.util.function.Consumer

/**
 * **Hopfield** is a basic implementation of a discrete Hopfield network.
 */
class Hopfield(numNeurons: Int) : Subnetwork() {
    @JvmField
    val neuronGroup: NeuronGroup

    val synapseGroup: SynapseGroup

    /**
     * The update function used by this Hopfield network.
     */
    @UserParameter(label = "Update function")
    private val updateFunc = DEFAULT_UPDATE

    private val infoText: InfoText

    /**
     * Creates a new Hopfield network.
     *
     * @param numNeurons Number of neurons in new network
     */
    init {
        label = "Hopfield network"

        // Create main neuron group
        neuronGroup = NeuronGroup(numNeurons)
        neuronGroup.label = "The Neurons"
        neuronGroup.applyLayout()
        addModel(neuronGroup)

        // Set neuron rule
        val binary = BinaryRule()
        binary.threshold = 0.0
        binary.setCeiling(1.0)
        binary.setFloor(0.0)
        neuronGroup.setUpdateRule(binary)
        neuronGroup.setIncrement(1.0)

        // Connect the neurons together
        synapseGroup = SynapseGroup(neuronGroup, neuronGroup)
        synapseGroup.displaySynapses = false
        addModel(synapseGroup)

        // Symmetric randomization
        // randomize() TODO()

        // Create info text
        infoText = InfoText(stateInfoText)
    }

    context(Network)
    override fun randomize() {
        synapseGroup.randomizeSymmetric()
    }

    context(Network)
    override fun update() {
        updateFunc.update(this)
        updateStateInfoText()
    }

    val network: NetworkModel
        get() = this

    val stateInfoText: String
        get() = "Energy: " + neuronGroup.neuronList.getEnergy().format(4)

    fun updateStateInfoText() {
        infoText.text = stateInfoText
        events.customInfoUpdated.fireAndBlock()
    }

    override val customInfo: NetworkModel
        get() = infoText

    /**
     * Apply the basic Hopfield rule to the current pattern. This is not the
     * main training algorithm, which directly makes use of the input data.
     */
    fun trainOnCurrentPattern() {
        neuronGroup.neuronList.forEach(Consumer { src: Neuron ->
            src.fanIn.forEach(
                Consumer { s: Synapse ->
                    val tar = s.source
                    val deltaW = bipolar(src.activation) * bipolar(tar.activation)
                    s.strength = s.strength + deltaW
                })
        })
        synapseGroup.events.updated.fireAndForget()
        events.updated.fireAndForget()
    }

    /**
     * Main forms of Hopfield update rule.
     */
    enum class HopfieldUpdate {
        RAND {
            /**
             * Update neurons in random order
             */
            context(Network)
            override fun update(hop: Hopfield) {
                val copy = hop.neuronGroup.neuronList.shuffled()
                copy.forEach {
                    it.updateInputs()
                    it.update()
                }
            }

            override fun toString(): String {
                return "Random"
            }
        },
        SEQ {
            /**
             * Sequential update of neurons (same sequence every time)
             */
            context(Network)
            override fun update(hop: Hopfield) {
                // TODO: Cache the sorted list
                hop.neuronGroup.neuronList
                    .sortedBy { it.updatePriority }
                    .forEach {
                        it.updateInputs()
                        it.update()
                    }
            }


            override fun toString(): String {
                return "Sequential"
            }
        },
        SYNC {
            context(Network)
            override fun update(hop: Hopfield) {
                hop.neuronGroup.neuronList.forEach { it.updateInputs() }
                hop.neuronGroup.neuronList.forEach { it.update() }
            }

            override fun toString(): String {
                return "Synchronous"
            }
        };

        context(Network)
        abstract fun update(hop: Hopfield)
    }

    /**
     * Helper class for creating new Hopfield nets using [org.simbrain.util.propertyeditor.AnnotatedPropertyEditor].
     */
    class HopfieldCreator : EditableObject {
        @UserParameter(
            label = "Number of neurons",
            description = "How many neurons this Hofield net should have",
            order = -1
        )
        var numNeurons: Int = DEFAULT_NUM_UNITS

        /**
         * Create the hopfield net
         */
        fun create(): Hopfield {
            return Hopfield(numNeurons)
        }
    }

    companion object {
        /**
         * Custom update rule for Hopfield.
         */
        val DEFAULT_UPDATE: HopfieldUpdate = HopfieldUpdate.SYNC

        /**
         * Default number of neurons.
         */
        const val DEFAULT_NUM_UNITS: Int = 36

        /**
         * Convenience method to convert binary values (1,0) to bipolar
         * values(1,-1).
         *
         * @param in number to convert
         * @return converted number
         */
        fun bipolar(`in`: Double): Double {
            return if (`in` == 0.0) -1.0 else `in`
        }
    }
}