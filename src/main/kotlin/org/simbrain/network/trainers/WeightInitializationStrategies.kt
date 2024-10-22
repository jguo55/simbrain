package org.simbrain.network.trainers

import org.simbrain.network.core.WeightMatrix
import org.simbrain.util.propertyeditor.CopyableObject
import org.simbrain.util.stats.distributions.NormalDistribution
import org.simbrain.util.stats.distributions.UniformRealDistribution
import kotlin.math.sqrt

sealed class WeightInitializationStrategy(val seed: Long? = null): CopyableObject {
    abstract fun initializeWeights(weightMatrix: WeightMatrix)

    override fun getTypeList(): List<Class<out CopyableObject>> = listOf(
        Xavier::class.java,
        He::class.java,
        LeCun::class.java
    )
}

class Xavier(seed: Long? = null): WeightInitializationStrategy(seed) {

    override fun initializeWeights(weightMatrix: WeightMatrix) {
        val numInputs = weightMatrix.src.size
        val numOutputs = weightMatrix.tar.size
        val randomizer = UniformRealDistribution(-sqrt(6.0 / (numInputs + numOutputs)), sqrt(6.0 / (numInputs + numOutputs))).apply { randomSeed = seed }
        weightMatrix.randomize(randomizer)
    }

    override fun copy(): CopyableObject {
        return Xavier(seed)
    }
}

class He(seed: Long? = null): WeightInitializationStrategy(seed) {


    override fun initializeWeights(weightMatrix: WeightMatrix) {
        val numInputs = weightMatrix.src.size
        val randomizer = UniformRealDistribution(-sqrt(6.0 / numInputs), sqrt(6.0 / numInputs)).apply { randomSeed = seed }
        weightMatrix.randomize(randomizer)
    }

    override fun copy(): CopyableObject {
        return He(seed)
    }
}

class LeCun(seed: Long? = null): WeightInitializationStrategy(seed) {

    override fun initializeWeights(weightMatrix: WeightMatrix) {
        val numInputs = weightMatrix.src.size
        val randomizer = NormalDistribution(0.0, 1.0 / numInputs).apply { randomSeed = seed }
        weightMatrix.randomize(randomizer)
    }

    override fun copy(): CopyableObject {
        return LeCun(seed)
    }
}


