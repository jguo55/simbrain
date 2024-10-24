package org.simbrain.network.events

import org.simbrain.network.updaterules.NeuronUpdateRule
import org.simbrain.util.Events

/**
 * See [Events].
 */
class NeuronEvents: LocationEvents() {

    val activationChanged = ChangedEvent<Double>()
    val spiked = OneArgEvent<Boolean>()
    val colorChanged = NoArgEvent()
    val updateRuleChanged = ChangedEvent<NeuronUpdateRule<*, *>>()
    val priorityChanged = ChangedEvent<Int>()
}