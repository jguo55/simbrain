package org.simbrain.util.stats.distributions

import org.apache.commons.math3.distribution.AbstractRealDistribution
import org.simbrain.util.UserParameter
import org.simbrain.util.stats.ProbabilityDistribution
import org.simbrain.util.toIntArray

class GammaDistribution(shape: Double = 2.0, scale: Double = 1.0) : ProbabilityDistribution() {

    @UserParameter(label = "Shape (k)", description = "Shape (k).", order = 1)
    private var shape = shape
        set(value) {
            field = value
            dist = org.apache.commons.math3.distribution.GammaDistribution(randomGenerator, value, scale)
        }

    @UserParameter(label = "Scale (\u03B8)", description = "Scale (\u03B8).", order = 2)
    private var scale = scale
        set(value) {
            field = value
            dist = org.apache.commons.math3.distribution.GammaDistribution(randomGenerator, shape, value)
        }


    @Transient
    var dist: AbstractRealDistribution =
        org.apache.commons.math3.distribution.GammaDistribution(randomGenerator, shape, scale)

    override fun sampleDouble(): Double = dist.sample()

    override fun sampleDouble(n: Int): DoubleArray = dist.sample(n)

    override fun sampleInt(): Int = dist.sample().toInt()

    override fun sampleInt(n: Int) = dist.sample(n).toIntArray()

    override fun deepCopy(): GammaDistribution {
        val cpy = GammaDistribution()
        cpy.shape = shape
        cpy.scale = scale
        return cpy
    }

    override fun getName(): String {
        return "Gamma"
    }

    // Kotlin hack to support "static method in superclass"
    companion object {
        @JvmStatic
        fun getTypes(): List<Class<*>> {
            return ProbabilityDistribution.getTypes()
        }
    }
}