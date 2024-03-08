/*
 * Part of Simbrain--a java-based neural network kit Copyright (C) 2005,2007 The
 * Authors. See http://www.simbrain.net/credits This program is free software;
 * you can redistribute it and/or modify it under the terms of the GNU General
 * Public License as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version. This program is
 * distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details. You
 * should have received a copy of the GNU General Public License along with this
 * program; if not, write to the Free Software Foundation, Inc., 59 Temple Place
 * - Suite 330, Boston, MA 02111-1307, USA.
 */
package org.simbrain.network.trainers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.simbrain.network.core.Network
import org.simbrain.network.events.TrainerEvents
import org.simbrain.network.subnetworks.BackpropNetwork
import org.simbrain.network.subnetworks.LMSNetwork
import org.simbrain.network.subnetworks.SRNNetwork
import org.simbrain.util.UserParameter
import org.simbrain.util.propertyeditor.CopyableObject
import org.simbrain.util.propertyeditor.EditableObject
import org.simbrain.util.propertyeditor.GuiEditable
import org.simbrain.util.rowVectorTransposed
import kotlin.math.sqrt
import kotlin.random.Random


/**
 * Manage iteration based training algorithms and provides an object that can be edited in the GUI.
 */
abstract class IterableTrainer(val net: SupervisedNetwork) : EditableObject {

    @UserParameter(label = "Learning Rate", order = 1)
    var learningRate = .01

    @UserParameter(label = "Update type", order = 2)
    open var updateType: UpdateMethod = UpdateMethod.Stochastic()

    @UserParameter(label = "Loss Function", order = 3, showDetails = false)
    var lossFunction: LossFunction = LossFunction.SumSquaredError()

    var stoppingCondition by GuiEditable(
        initValue = StoppingCondition(),
        order = 4
    )

    var iteration = 0

    var isRunning = false

    private var stoppingConditionReached = false

    @Transient val events = TrainerEvents()

    context(Network)
    suspend fun startTraining() {
        if (stoppingConditionReached) {
            stoppingConditionReached = false
            iteration = 0
            events.iterationReset.fire()
        }
        isRunning = true
        events.beginTraining.fireAndForget()
        withContext(Dispatchers.Default) {
            while (isRunning) {
                trainOnce()
                if (stoppingCondition.validate(iteration, lossFunction.loss)) {
                    stoppingConditionReached = true
                    stopTraining()
                }
            }
        }
    }

    suspend fun stopTraining() {
        isRunning = false
        events.endTraining.fireAndForget()
    }

    context(Network)
    suspend fun train(iterations: Int) {
        repeat(iterations) {
            trainOnce()
        }
    }

    context(Network)
    suspend fun trainOnce() {
        iteration++
        with(updateType) {
            lossFunction.reset()
            when (this) {
                is UpdateMethod.Stochastic -> lossFunction.accumulateError(trainRow(Random.nextInt(net.trainingSet.inputs.nrow())))
                is UpdateMethod.Epoch -> {
                    for (i in 0 until net.trainingSet.size) {
                        val error = trainRow(i)
                        lossFunction.accumulateError(error)
                    }
                }
                is UpdateMethod.Batch -> {
                    var totalError = 0.0
                    val startIndex = Random.nextInt(0, net.trainingSet.size - batchSize)
                    val endIndex = startIndex + batchSize
                    for (i in (startIndex..endIndex)) {
                        val error = trainRow(i)
                        lossFunction.accumulateError(error)
                    }
                }
            }
        }
        events.errorUpdated.fire(lossFunction)
    }

    context(Network)
    abstract fun trainRow(rowNum: Int): Double

    context(Network)
    open fun applyInputs(rowNum: Int) {
        net.inputLayer.activations = net.trainingSet.inputs.rowVectorTransposed(rowNum)
        net.update()
    }

    abstract fun randomize()

    sealed class UpdateMethod: CopyableObject {
        class Stochastic : UpdateMethod() {
            override fun copy() = this
        }

        class Epoch : UpdateMethod() {
            override fun copy() = this
        }

        class Batch(@UserParameter(label = "Batch Size", order = 1) var batchSize: Int = 5) : UpdateMethod() {
            override fun copy() = Batch(batchSize)
        }

        override fun getTypeList(): List<Class<out CopyableObject>>? {
            return listOf(
                Stochastic::class.java,
                Epoch::class.java,
                Batch::class.java
            )
        }

        /**
         * Given the temporal nature of the rule, only Epoch should be used with SRN
         */
        fun srnTypeList() = listOf(Epoch::class.java)
    }

    sealed class LossFunction: CopyableObject {

        protected var runningError = 0.0

        protected var runningCount = 0

        abstract val loss: Double

        abstract fun accumulateError(error: Double)

        fun reset() {
            runningError = 0.0
            runningCount = 0
        }

        class MeanSquaredError : LossFunction() {
            override fun accumulateError(error: Double) {
                runningError += error * error
                runningCount++
            }

            override val loss: Double
                get() = runningError / runningCount

            override fun copy() = MeanSquaredError()

            override val name: String = "Mean Squared Error"
        }

        class SumSquaredError : LossFunction() {
            override fun accumulateError(error: Double) {
                runningError += error * error
                runningCount++
            }

            override val loss: Double
                get() = runningError

            override fun copy() = SumSquaredError()

            override val name: String = "Sum Squared Error"
        }

        class RootMeanSquaredError : LossFunction() {
            override fun accumulateError(error: Double) {
                runningError += error * error
                runningCount++
            }

            override val loss: Double
                get() = sqrt(runningError / runningCount)

            override fun copy() = RootMeanSquaredError()

            override val name: String = "Root Mean Squared Error"
        }

        override fun getTypeList(): List<Class<out CopyableObject>>? {
            return listOf(
                MeanSquaredError::class.java,
                SumSquaredError::class.java,
                RootMeanSquaredError::class.java
            )
        }
    }

    class StoppingCondition: CopyableObject {
        var maxIterations by GuiEditable(
            initValue = 1000,
            order = 1
        )
        var useErrorThreshold by GuiEditable(
            initValue = false,
            order = 2
        )
        var errorThreshold by GuiEditable(
            0.1,
            order = 3,
            conditionallyEnabledBy = StoppingCondition::useErrorThreshold
        )

        override fun copy(): CopyableObject {
            return StoppingCondition().also {
                it.maxIterations = maxIterations
                it.useErrorThreshold = useErrorThreshold
                it.errorThreshold = errorThreshold
            }
        }

        fun validate(iterations: Int, error: Double): Boolean {
            return iterations >= maxIterations || (useErrorThreshold && error < errorThreshold)
        }
    }

}

class LMSTrainer(val lmsNet: LMSNetwork) : IterableTrainer(lmsNet) {

    context(Network)
    override fun trainRow(rowNum: Int): Double {
        if (rowNum !in 0 until lmsNet.trainingSet.inputs.nrow()) {
            throw IllegalArgumentException("Trying to train invalid row number $rowNum")
        }
        val targetVec = lmsNet.trainingSet.targets.rowVectorTransposed(rowNum)
        lmsNet.inputLayer.isClamped = true
        lmsNet.inputLayer.setActivations(lmsNet.trainingSet.inputs.row(rowNum))
        lmsNet.update()
        val outputs = lmsNet.outputLayer.activations
        val rowError = targetVec.sub(outputs)
        lmsNet.weightMatrix.applyLMS(rowError, learningRate)
        return rowError.transpose().mm(rowError).sum()
    }

    override fun randomize() {
        lmsNet.randomize()
    }

}

class BackpropTrainer(val bp: BackpropNetwork) : IterableTrainer(bp) {

    context(Network)
    override fun trainRow(rowNum: Int): Double {
        bp.inputLayer.setActivations(bp.trainingSet.inputs.row(rowNum))
        val targetVec = bp.trainingSet.targets.rowVectorTransposed(rowNum)
        bp.wmList.forwardPass(bp.inputLayer.activations)
        return bp.wmList.backpropError(targetVec)
    }

    override fun randomize() {
        bp.randomize()
    }

}

class SRNTrainer(val srn: SRNNetwork) : IterableTrainer(srn) {

    val weightMatrixTree = WeightMatrixTree(listOf(srn.inputLayer, srn.contextLayer), srn.outputLayer)

    override var updateType: UpdateMethod by GuiEditable(
        initValue = UpdateMethod.Epoch(),
        typeMapProvider = UpdateMethod::srnTypeList
    )

    context(Network)
    override fun trainRow(rowNum: Int): Double {

        val targetVec = srn.trainingSet.targets.rowVectorTransposed(rowNum)
        val inputVec = srn.trainingSet.inputs.rowVectorTransposed(rowNum)

        srn.inputLayer.activations = inputVec
        srn.update()
        return weightMatrixTree.backpropError(targetVec, epsilon = learningRate)
    }

    override fun randomize() {
        srn.randomize()
    }

}