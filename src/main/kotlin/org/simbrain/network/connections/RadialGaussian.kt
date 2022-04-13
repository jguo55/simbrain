/*
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
package org.simbrain.network.connections

import org.simbrain.network.core.Network
import org.simbrain.network.core.Neuron
import org.simbrain.network.core.Synapse
import org.simbrain.network.groups.SynapseGroup
import org.simbrain.util.SimbrainConstants.Polarity
import org.simbrain.util.UserParameter
import org.simbrain.util.propertyeditor.EditableObject
import org.simbrain.util.stats.distributions.UniformRealDistribution
import java.util.concurrent.*
import kotlin.math.floor

const val DEFAULT_DIST_CONST: Double = 0.25

const val DEFAULT_EE_CONST: Double = 0.2

const val DEFAULT_EI_CONST: Double = 0.3

const val DEFAULT_IE_CONST: Double = 0.4

const val DEFAULT_II_CONST: Double = 0.1

val DEFAULT_LAMBDA: Double = 200.0

/**
 * This connection type makes four types of distance-based connection probabilistically.
 *
 *  1. Excitatory to Excitatory (EE)
 *  2. Excitatory to Inhibitory (EI)
 *  3. Inhibitory to Inhibitory (II)
 *  4. Inhibitory to Excitatory (IE)
 *
 * The probability of making a connection drops off according to a gaussian centered
 * on each neuron, which is scaled differently according to the polarity of the source
 * and target neuron. Specifically the probability of forming a connection between
 * a neuron <emp>a</emp> with polarity <emp>x</emp> and another neuron <emp>b</emp>
 * with polarity <emp>y</emp> is given by P(a, b) = min( C_xy * exp( -(D(a, b) / λ)^2 ), 1).
 * Where D(a, b) gives the Euclidean distance in pixels, C_xy is a scalar constant
 * unique to the polarity of x and y (e.g. exc to exc may have a C_ee of 0.2 while
 * inh to inh may have a C_ii of 0.1, meaning as a baseline exc/exc synapses are
 * twice as likely as inh/inh synapses). Lambda <emp>roughly</emp> represents the standard
 * deviation in with respect to distance for the gaussian drop off.
 *
 * Any of the 4 constants for the 4 cases can be set to a value between 0 and 1.
 * Set to 0, if you want no connections of that type to be made. Set to 1 to
 * have it make the most connections possible given the exponential
 * distribution.
 *
 * The larger any of the constants is relative to the others, the more likely a connection
 * of that type will occur.
 *
 * Lambda is roughly the average distance in pixels of connections that will be made.
 *
 * @author Zoë Tosi
 */
class RadialGaussian(

    /**
     * The connection constant for connections between 2 excitatory neurons.
     */
    @UserParameter(
        label = "Exc. \u2192 Exc. Constant", minimumValue = 0.0, maximumValue = 1.0, increment = .1, order
        = 2
    )
    var eeDistConst: Double = DEFAULT_EE_CONST,

    /**
     * The connection constant for connection from an excitatory to an inhibitory neuron.
     */
    @UserParameter(
        label = "Exc. \u2192 Inh. Constant", minimumValue = 0.0, maximumValue = 1.0, increment = .1, order
        = 3
    )
    var eiDistConst: Double = DEFAULT_EI_CONST,

    /**
     * The connection constant for connection from an inhibitory to an excitatory neuron.
     */
    @UserParameter(
        label = "Inh. \u2192 Exc. Constant", minimumValue = 0.0, maximumValue = 1.0, increment = .1, order
        = 4
    )
    var ieDistConst: Double = DEFAULT_IE_CONST,

    /**
     * The connection constant for connections between 2 inhibitory neurons.
     */
    @UserParameter(
        label = "Inh. \u2192 Inh. Constant", minimumValue = 0.0, maximumValue = 1.0, increment = .1, order
        = 5
    )
    var iiDistConst: Double = DEFAULT_II_CONST,

    /**
     * The connection constant for general connections. Used in cases where neurons have no explicit polarity.
     */
    @UserParameter(
        label = "No Polarity Constant", description = "Connection probability for" +
                "non-polar synapses", minimumValue = 0.0, maximumValue = 1.0, increment = .1, order = 6
    )
    var distConst: Double = DEFAULT_DIST_CONST,

    /**
     * A regulating constant governing overall connection density. Higher values create denser connections. Lambda can
     * be thought of as the average connection distance in pixels.
     */
    @UserParameter(label = "Distance Drop-off", increment = 5.0, minimumValue = 0.01, order = 1)
    var lambda: Double = DEFAULT_LAMBDA

) : ConnectionStrategy(), EditableObject {

    // TODO: Add a sparsity constraint, such that connections are still chosen stochastically
    // based on distance, but a specific number of connections are guaranteed to be made.

    override fun connectNeurons(network: Network, source: List<Neuron>, target: List<Neuron>): List<Synapse> {
        val syns: List<Synapse> = connectRadialPolarized(source, target, eeDistConst, eiDistConst, ieDistConst, iiDistConst, distConst, lambda)
        network.addNetworkModels(syns)
        return syns
    }

    /**
     * Specifically: Connects neurons based on a probability function related to their distance from one another, which
     * exponentially decays with distance.
     */
    override fun connectNeurons(synGroup: SynapseGroup) {
        synGroup.connectionManager = this
        var source: List<Neuron> = synGroup.sourceNeurons
        var target: List<Neuron> = synGroup.targetNeurons
        var synapses: List<Synapse>
        if (source.size < 500) {
            synapses = connectRadialPolarized(source, target, eeDistConst, eiDistConst, ieDistConst, iiDistConst, distConst, lambda)
            synapses.forEach { s -> synGroup.addNewSynapse(s) }
        } else {
            val workers: MutableList<Callable<Collection<Synapse>>> = ArrayList()
            val threads: Int = Runtime.getRuntime().availableProcessors()
            val idealShare: Int = floor((source.size / threads).toDouble()).toInt()
            var remaining: Int = source.size
            val srcIter: Iterator<Neuron> = source.iterator()
            var srcChunk: MutableList<Neuron>
            var runningPercentEx: Double = 0.0
            for (i in 0 until threads) {
                srcChunk = ArrayList(Math.ceil((idealShare * 2) / 0.75).toInt())
                var share: Int
                if (remaining < idealShare * 2) {
                    share = remaining
                } else {
                    share = idealShare
                }
                var j: Int = 0
                while (j < share) {
                    val n: Neuron = srcIter.next()
                    srcChunk.add(n)
                    if (n.isPolarized()) {
                        if (Polarity.EXCITATORY === n.getPolarity()) {
                            runningPercentEx++
                        }
                    }
                    j++
                }
                remaining -= j
                workers.add(ConnectorService(srcChunk, target, false))
            }
            runningPercentEx /= source.size.toDouble()
            synGroup.setExcitatoryRatio(runningPercentEx)
            val ex: ExecutorService = Executors.newFixedThreadPool(threads)
            val generatedSyns: List<Future<Collection<Synapse>>>
            try {
                generatedSyns = ex.invokeAll(workers)
                ex.shutdown()
                ex.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                e.printStackTrace()
                return
            }
            var numSyns = 0
            for (future: Future<Collection<Synapse>> in generatedSyns) {
                try {
                    numSyns += future.get().size
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                } catch (e: ExecutionException) {
                    e.printStackTrace()
                }
            }
            synGroup.preAllocateSynapses(numSyns)
            for (future: Future<Collection<Synapse>> in generatedSyns) {
                try {
                    for (s: Synapse? in future.get()) {
                        synGroup.addNewSynapse(s)
                    }
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                } catch (e: ExecutionException) {
                    e.printStackTrace()
                }
            }
        }

        //        if (synGroup.isRecurrent()) {
        //            connectionDensity = (double) synGroup.size() / (synGroup.getSourceNeuronGroup().size() * (synGroup.getSourceNeuronGroup().size() - 1));
        //        } else {
        //            connectionDensity = (double) synGroup.size() / (synGroup.getSourceNeuronGroup().size() * synGroup.getTargetNeuronGroup().size());
        //        }
        // source = null
        // target = null
        // synapses = null
        Runtime.getRuntime().gc()
    }

    public override fun toString(): String {
        return "Radial (Gaussian)"
    }

    private inner class ConnectorService constructor(
        private val srcColl: Collection<Neuron>,
        private val targColl: Collection<Neuron>?,
        private val loose: Boolean,
    ) : Callable<Collection<Synapse>> {
        var rand: UniformRealDistribution = UniformRealDistribution(0.0, 1.0);
        @Throws(Exception::class)
        public override fun call(): Collection<Synapse> {
            // Attempting to pre-allocate... assumes that connection density
            // will be less than #src * #tar * 0.2 or 20% connectivity
            val synapses: MutableList<Synapse> =
                ArrayList(Math.ceil(srcColl.size * targColl!!.size * 0.2 * 0.75).toInt())
            for (src: Neuron in srcColl) {
                for (tar: Neuron in targColl) {
                    val randVal: Double = rand.sampleDouble()
                    var probability: Double
                    if (src.getPolarity() === Polarity.EXCITATORY) {
                        if (tar.getPolarity() === Polarity.EXCITATORY) {
                            probability = calcConnectProb(src, tar, eeDistConst, lambda)
                        } else if (tar.getPolarity() === Polarity.INHIBITORY) {
                            probability = calcConnectProb(src, tar, eiDistConst, lambda)
                        } else {
                            probability = calcConnectProb(src, tar, distConst, lambda)
                        }
                    } else if (src.getPolarity() === Polarity.INHIBITORY) {
                        if (tar.getPolarity() === Polarity.EXCITATORY) {
                            probability = calcConnectProb(src, tar, ieDistConst, lambda)
                        } else if (tar.getPolarity() === Polarity.INHIBITORY) {
                            probability = calcConnectProb(src, tar, iiDistConst, lambda)
                        } else {
                            probability = calcConnectProb(src, tar, distConst, lambda)
                        }
                    } else {
                        probability = calcConnectProb(src, tar, distConst, lambda)
                    }
                    if (randVal < probability) {
                        val s: Synapse = Synapse(src, tar)
                        synapses.add(s)
                        if (loose) {
                            src.getNetwork().addNetworkModel(s)
                        }
                    }
                }
            }
            return synapses
        }
    }

    // inner class DensityEstimator constructor() : Runnable {
    //     var densityEsitmate: Double = 0.0
    //         private set
    //
    //     override fun run() {
    //         var count: Int = 0
    //         for (src: Neuron in synapseGroup!!.getSourceNeurons()) {
    //             for (tar: Neuron in synapseGroup!!.getTargetNeurons()) {
    //                 val randVal: Double = Math.random()
    //                 var probability: Double
    //                 if (src.getPolarity() === Polarity.EXCITATORY) {
    //                     if (tar.getPolarity() === Polarity.EXCITATORY) {
    //                         probability = calcConnectProb(src, tar, eeDistConst, lambda)
    //                     } else if (tar.getPolarity() === Polarity.INHIBITORY) {
    //                         probability = calcConnectProb(src, tar, eiDistConst, lambda)
    //                     } else {
    //                         probability = calcConnectProb(src, tar, distConst, lambda)
    //                     }
    //                 } else if (src.getPolarity() === Polarity.INHIBITORY) {
    //                     if (tar.getPolarity() === Polarity.EXCITATORY) {
    //                         probability = calcConnectProb(src, tar, ieDistConst, lambda)
    //                     } else if (tar.getPolarity() === Polarity.INHIBITORY) {
    //                         probability = calcConnectProb(src, tar, iiDistConst, lambda)
    //                     } else {
    //                         probability = calcConnectProb(src, tar, distConst, lambda)
    //                     }
    //                 } else {
    //                     probability = calcConnectProb(src, tar, distConst, lambda)
    //                 }
    //                 if (randVal < probability) {
    //                     count++
    //                 }
    //             }
    //         }
    //         if (synapseGroup!!.isRecurrent()) {
    //             densityEsitmate = count.toDouble() / (synapseGroup!!.getSourceNeuronGroup()
    //                 .size() * (synapseGroup!!.getSourceNeuronGroup().size() - 1))
    //         } else {
    //             densityEsitmate = count.toDouble() / (synapseGroup!!.getSourceNeuronGroup()
    //                 .size() * synapseGroup!!.getTargetNeuronGroup().size())
    //         }
    //         // TODO
    //         // synchronized(this, { notify() })
    //     }
    // }

    override fun getName(): String {
        return "Radial (Gaussian)"
    }
}

fun connectRadialPolarized(
    source: List<Neuron>,
    target: List<Neuron>,
    eeDistConst: Double = DEFAULT_EI_CONST,
    eiDistConst: Double = DEFAULT_EI_CONST,
    ieDistConst: Double = DEFAULT_IE_CONST,
    iiDistConst: Double = DEFAULT_II_CONST,
    distConst: Double = DEFAULT_DIST_CONST,
    lambda: Double = DEFAULT_LAMBDA
): List<Synapse> {
    // Pre-allocating assuming that if one is using this as a connector
    // then they are probably not going to have greater than 25%
    // connectivity
    val synapses: MutableList<Synapse> = ArrayList(source!!.size * target!!.size / 4)
    for (src: Neuron in source) {
        for (tar: Neuron in target) {
            val randVal: Double = Math.random()
            var probability: Double
            if (src.getPolarity() === Polarity.EXCITATORY) {
                if (tar.getPolarity() === Polarity.EXCITATORY) {
                    probability = calcConnectProb(src, tar, eeDistConst, lambda)
                } else if (tar.getPolarity() === Polarity.INHIBITORY) {
                    probability = calcConnectProb(src, tar, eiDistConst, lambda)
                } else {
                    probability = calcConnectProb(src, tar, distConst, lambda)
                }
            } else if (src.getPolarity() === Polarity.INHIBITORY) {
                if (tar.getPolarity() === Polarity.EXCITATORY) {
                    probability = calcConnectProb(src, tar, ieDistConst, lambda)
                } else if (tar.getPolarity() === Polarity.INHIBITORY) {
                    probability = calcConnectProb(src, tar, iiDistConst, lambda)
                } else {
                    probability = calcConnectProb(src, tar, distConst, lambda)
                }
            } else {
                probability = calcConnectProb(src, tar, distConst, lambda)
            }
            if (randVal < probability) {
                val s = Synapse(src, tar)
                if (src.polarity === Polarity.INHIBITORY) {
                    s.forceSetStrength(-1.0)
                } else {
                    s.forceSetStrength(1.0)
                }
                synapses.add(s)
            }
        }
    }
    return synapses
}

/**
 * @param distConst the connection constant for general connections. Used in cases where neurons have no explicit
 * polarity.
 * @param lambda average connection distance.
 */
fun connectRadialNoPolarity(
    source: List<Neuron>,
    target: List<Neuron>,
    distConst: Double,
    lambda: Double
): List<Synapse> {
    // Pre-allocating assuming that if one is using this as a connector
    // then they are probably not going to have greater than 25%
    // connectivity
    val synapses: MutableList<Synapse> = ArrayList(source.size * target.size / 4)
    for (src: Neuron in source) {
        for (tar: Neuron in target) {
            val randVal: Double = Math.random()
            val probability: Double = calcConnectProb(src, tar, distConst, lambda)
            if (randVal < probability) {
                val s = Synapse(src, tar)
                synapses.add(s)
            }
        }
    }
    return synapses
}

/**
 * @param src       the source neuron.
 * @param tar       the target neuron.
 * @param distConst the connection constant for general connections. Used in cases where neurons have no explicit
 * polarity.
 * @param lambda    average connection distance.
 * @return
 */
private fun calcConnectProb(src: Neuron, tar: Neuron, distConst: Double, lambda: Double): Double {
    val dist: Double = -getRawDist(src, tar)
    var exp: Double = Math.exp(dist / (lambda * lambda))
    if (exp == 1.0) { // Same location == same neuron: cheapest way to
        // prevent self connections
        exp = 0.0
    }
    return distConst * exp
}

/**
 * @param n1 neuron one
 * @param n2 neuron two
 * @return
 */
private fun getRawDist(n1: Neuron, n2: Neuron): Double {
    var x2: Double = (n1.getX() - n2.getX())
    x2 *= x2
    var y2: Double = (n1.getY() - n2.getY())
    y2 *= y2
    var z2: Double = (n1.getZ() - n2.getZ())
    z2 *= z2
    return x2 + y2 + z2
}