package org.simbrain.util.geneticalgorithm

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.simbrain.custom_sims.createControlPanel
import org.simbrain.util.ControlPanelKt
import org.simbrain.util.format
import org.simbrain.util.propertyeditor.AnnotatedPropertyEditor
import org.simbrain.util.propertyeditor.EditableObject
import org.simbrain.util.propertyeditor.GuiEditable
import org.simbrain.util.sampleWithReplacement
import org.simbrain.util.widgets.ProgressWindow
import org.simbrain.workspace.Workspace
import org.simbrain.workspace.gui.SimbrainDesktop
import java.awt.event.ActionEvent
import kotlin.math.roundToInt
import kotlin.random.Random

interface Genotype {
    val random: Random
}

/**
 * Subclasses should provide a domain specific express function
 */
abstract class Gene<P> {
    abstract val template: P
    abstract fun copy(): Gene<P>

    fun mutate(block: P.() -> Unit) {
        template.apply(block)
    }
}

/**
 * An interface provides a zero arg express function.
 */
abstract class TopLevelGene<P>: Gene<P>() {
    abstract fun express(): P
}

object TopLevelGeneticsContext

interface EvoSim {
    fun mutate()
    suspend fun build()
    fun visualize(workspace: Workspace): EvoSim
    fun copy(): EvoSim
    suspend fun eval(): Double
}

/**
 * A typed list of Genes, with functions to copy and concatenate.
 */
class Chromosome<P, G : Gene<P>>(genes: List<G>) : MutableList<G> by ArrayList(genes) {

    /**
     * Provides a copy of the chromosome.
     */
    fun copy() = Chromosome(map { it.copy() as G })

    /**
     * Provides the ability to concatenate chromsomes. See usages.
     */
    operator fun plus(other: Chromosome<P, G>) = Chromosome(buildList { addAll(this@Chromosome); addAll(other); })
}


data class PopulatingFunctionParams(val seed: Long)

/**
 * The main evolutionary code.
 * Assumes fitness, i.e. bigger numbers are better. For "error", the eval function should return a negative number.
 * Returns all simulations from the last generation of the run.
 *
 * @param populatingFunction initial evolutionary sim
 * @param populationSize stays constant during the run
 * @param eliminationRatio how many sims to eliminate each generation.
 * @param stoppingFunction a function that determines when to stop running the sim. Generally check a generation
 * number and for fitness.
 * @param peek code to run each iteration, for example to update a progress bar
 */
suspend fun evaluator(
    populatingFunction: PopulatingFunctionParams.() -> EvoSim,
    populationSize: Int,
    eliminationRatio: Double,
    stoppingFunction: GenerationFitnessPair.() -> Boolean,
    peek: GenerationFitnessPair.() -> Unit = {},
    sortDescending: Boolean = true,
    seed: Long = Random.nextLong(),
    random: Random = Random(seed)
): List<EvoSim> = coroutineScope {
    var generation = 0
    val populatingFunctionParams = PopulatingFunctionParams(seed)
    var population = List(populationSize) { populatingFunction(populatingFunctionParams) }
    do {
        generation++
        val fitnessScores = population.map { async { it.eval() } }.awaitAll()
        val agentFitnessPair = (population zip fitnessScores).shuffled(random).let {
            if (sortDescending) {
                it.sortedByDescending { it.second }
            } else {
                it.sortedBy { it.second }
            }
        }
        val eliminationCount = (agentFitnessPair.size * eliminationRatio).roundToInt()
        val survivors = agentFitnessPair.take(populationSize - eliminationCount).map { (sim) -> sim }
        population = (survivors.map { it.copy() } + survivors.sampleWithReplacement(random).take(eliminationCount)
            .toList().map {
                it.copy().apply {
                    mutate()
                }
            })
        val generationFitnessPair = GenerationFitnessPair(generation, agentFitnessPair.map { it.second })
        peek(generationFitnessPair)
    } while (!stoppingFunction(generationFitnessPair))
    population
}

suspend fun evaluator(
    evaluatorParams: EvaluatorParams,
    populatingFunction: PopulatingFunctionParams.() -> EvoSim,
    peek: GenerationFitnessPair.() -> Unit = {}
): List<EvoSim> {
    val lastGeneration = evaluator(
        populatingFunction = populatingFunction,
        populationSize = evaluatorParams.populationSize,
        eliminationRatio = evaluatorParams.eliminationRatio,
        stoppingFunction = {
            evaluatorParams.stoppingCondition.shouldStop(nthPercentileFitness(evaluatorParams.evalutationPercentile), evaluatorParams.targetMetric) || generation > evaluatorParams.maxGenerations
        },
        sortDescending = evaluatorParams.stoppingCondition == EvaluatorParams.StoppingCondition.Fitness,
        peek = {
            listOf(0, 10, 25, 50, 75, 90, 100).joinToString(" ") {
                "$it: ${nthPercentileFitness(it).format(3)}"
            }.also {
                println("[$generation] $it")
                evaluatorParams.updateProgressWindow(this)
            }
            peek()
        },
        seed = evaluatorParams.seed.toLong()
    )
    evaluatorParams.closeProgressWindow()
    return lastGeneration
}

class EvaluatorParams(
    populationSize: Int = 100,
    eliminationRatio: Double = 0.5,
    iterationsPerRun: Int = 100,
    maxGenerations: Int = 500,
    evaluationPercentile: Int = 5,
    var stoppingCondition: StoppingCondition = StoppingCondition.Fitness,
    targetMetric: Double,
    seed: Int = Random.nextInt()
): EditableObject {

    var populationSize by GuiEditable(
        initValue = populationSize,
        description = "Number of simulations spawned per generation",
        min = 0,
        order = 0
    )

    var eliminationRatio by GuiEditable(
        initValue = eliminationRatio,
        description = "Percentage of the population eliminated each generation",
        min = 0.0,
        max = 1.0,
        order = 10
    )

    var iterationsPerRun by GuiEditable(
        initValue = iterationsPerRun,
        description = "Each generation, the simulation is iterated this many times",
        min = 0,
        order = 20
    )

    var maxGenerations by GuiEditable(
        initValue = maxGenerations,
        description = "After this many generations stop, regardless of ${stoppingCondition.name.lowercase()}",
        min = 0,
        order = 30
    )

    var targetMetric by GuiEditable(
        label = "Target ${stoppingCondition.name.lowercase()}",
        description = if (stoppingCondition == StoppingCondition.Error) {
            "Once the error is below this amount, the simulation is stopped"
        } else {
            "Once the fitness is above this amount, the simulation is stopped"
        },
        initValue = targetMetric,
        min = 0.0,
        order = 50
    )

    var evalutationPercentile by GuiEditable(
        initValue = evaluationPercentile,
        label = "Evaluation percentile",
        description = "When deciding whether to stop the simulation, consider current ${stoppingCondition.name.lowercase()} in this percentile of the population",
        min = 0,
        max = 100,
        order = 60
    )

    var seed by GuiEditable(
        initValue = seed,
        description = "Random seed that can be used for replicability",
        order = 70
    )

    private var controlPanel: ControlPanelKt? = null

    private var editor: AnnotatedPropertyEditor<EvaluatorParams>? = null

    private var progressWindow: ProgressWindow? = null

    context(SimbrainDesktop)
    fun createControlPanel(name: String, x: Int, y: Int) = if (controlPanel == null) {
        controlPanel = createControlPanel(name, x, y) {
            editor = AnnotatedPropertyEditor(this@EvaluatorParams)
            addAnnotatedPropertyEditor(editor!!)
        }
        controlPanel!!
    } else {
        controlPanel!!
    }

    private fun getProgressText(metricsString: String, generation: Int) =
        """
            <html>
                Generation: $generation<br />
                $evalutationPercentile Percentile ${stoppingCondition.name}: $metricsString
            </html>
        """.trimIndent()

    context(SimbrainDesktop)
    fun addControlPanelButton(text: String, block: suspend (ActionEvent) -> Unit) {
        controlPanel!!.addButton(text) {
            editor!!.commitChanges()
            block(it)
        }
    }

    fun addProgressWindow() {
        progressWindow = ProgressWindow(maxGenerations, getProgressText("", 0)).apply {
            minimumSize = java.awt.Dimension(300, 100)
            setLocationRelativeTo(null)
        }
    }

    fun updateProgressWindow(generationFitnessPair: GenerationFitnessPair) {
        progressWindow?.apply {
            text = getProgressText(generationFitnessPair.nthPercentileFitness(evalutationPercentile).format(3), generationFitnessPair.generation)
            value = generationFitnessPair.generation
        }
    }

    fun closeProgressWindow() {
        progressWindow?.close()
    }

    /**
     * Use error when the evolutionary algorithm is trying to minimize a value, and fitness when it is trying to maximize a value.
     */
    sealed class StoppingCondition {
        abstract fun shouldStop(actual: Double, target: Double): Boolean

        abstract val name: String

        data object Error : StoppingCondition() {
            override fun shouldStop(actual: Double, target: Double) = actual < target
            override val name = "Error"
        }

        data object Fitness : StoppingCondition() {
            override fun shouldStop(actual: Double, target: Double) = actual > target
            override val name = "Fitness"
        }
    }
}
