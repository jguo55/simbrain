package org.simbrain.custom_sims.simulations

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.simbrain.custom_sims.addNetworkComponent
import org.simbrain.custom_sims.addOdorWorldComponent
import org.simbrain.custom_sims.couplingManager
import org.simbrain.custom_sims.newSim
import org.simbrain.network.core.Synapse
import org.simbrain.network.neuron_update_rules.DecayRule
import org.simbrain.network.neuron_update_rules.LinearRule
import org.simbrain.network.neuron_update_rules.SigmoidalRule
import org.simbrain.network.neuron_update_rules.interfaces.BiasedUpdateRule
import org.simbrain.network.util.labels
import org.simbrain.util.format
import org.simbrain.util.geneticalgorithms.*
import org.simbrain.util.point
import org.simbrain.util.widgets.ProgressWindow
import org.simbrain.workspace.Workspace
import org.simbrain.world.odorworld.entities.EntityType
import org.simbrain.world.odorworld.entities.OdorWorldEntity

val evolveAvoider = newSim {

    val scope = MainScope()

    fun createEvolution(): Evaluator {
        val environmentBuilder = evolutionarySimulation(1) {

            // Set up the chromosomes.

            val inputs = chromosome(3) {
                nodeGene()
            }

            val hiddens = chromosome(8) {
                nodeGene {
                    updateRule = DecayRule().apply {
                        decayFraction = .01
                    }
                }
            }

            val outputs = chromosome(3) {
                nodeGene {
                    updateRule.let {
                        if (it is LinearRule) {
                            it.lowerBound = 0.0
                        }
                    }
                }
            }

            val evolutionWorkspace = Workspace()

            val connections = chromosome<Synapse, ConnectionGene>()

            val networkComponent = evolutionWorkspace { addNetworkComponent("Avoider") }

            val network = networkComponent.network

            val odorworldComponent = evolutionWorkspace { addOdorWorldComponent("World") }

            val odorworld = odorworldComponent.world.apply {
                isObjectsBlockMovement = false
            }

            val sensors = chromosome(3) {
                objectSensorGene {
                    setObjectType(EntityType.POISON)
                    theta = it * 2 * Math.PI / 3
                    radius = 32.0
                    decayFunction.dispersion = 200.0
                }
            }

            val straightMovement = chromosome(
                straightMovementGene()
            )

            val turning = chromosome(
                turningGene { direction = -1.0 },
                turningGene { direction = 1.0 }
            )

            val mouse = odorworld.addEntity(EntityType.MOUSE).apply {
                setCenterLocation(200.0, 200.0)
            }

            fun OdorWorldEntity.reset() {
                setCenterLocation(random.nextDouble()*300,random.nextDouble()*300)
            }

            fun addPoison() = odorworld.addEntity(EntityType.POISON).apply {
                setCenterLocation(random.nextDouble()*300,random.nextDouble()*300)
                velocityX = random.nextDouble(-5.0,5.0)
                velocityY = random.nextDouble(-5.0,5.0)
                onCollide {
                    if (it === mouse) reset()
                }
            }

            val (poison1, poison2, poison3) = List(3) { addPoison() }

            // Take the current chromosomes,and express them via an agent in a world.
            // Everything needed to build one generation
            // Called once for each genome at each generation
            onBuild { visible ->
                network {
                    if (visible) {
                        val inputGroup = +inputs.asGroup {
                            label = "Input"
                            location = point(0, 200)
                        }
                        inputGroup.neuronList.labels = listOf("center", "left", "right")
                        +hiddens.asGroup {
                            label = "Hidden"
                            location = point(0, 100)
                        }
                        val outputGroup = +outputs.asGroup {
                            label = "Output"
                            location = point(0, 0)
                        }
                        outputGroup.neuronList.labels = listOf("right", "left", "right")

                    } else {
                        // This is update when graphics are off
                        +inputs
                        +hiddens
                        +outputs
                    }
                    +connections
                }
                mouse {
                    +sensors
                    +straightMovement
                    +turning
                }

                evolutionWorkspace {
                    couplingManager.apply {
                        val (straightNeuron, leftNeuron, rightNeuron) = outputs.products
                        val (straightConsumer) = straightMovement.products
                        val (left, right) = turning.products

                        sensors.products couple inputs.products
                        straightNeuron couple straightConsumer
                        leftNeuron couple left
                        rightNeuron couple right
                    }
                }
            }

            //
            // Mutate the chromosomes. Specify what things are mutated at each generation.
            //
            onMutate {
                hiddens.genes.forEach {
                    it.mutate {
                        updateRule.let {
                            if (it is BiasedUpdateRule) it.bias += random.nextDouble(-0.2, 0.2)
                        }
                    }
                }
                outputs.genes.forEach {
                    it.mutate {
                        when (random.nextInt(3)) {
                            0 -> updateRule = SigmoidalRule()
                            1 -> updateRule = DecayRule()
                            2 -> {
                            } // Leave the same
                        }
                    }
                }
                connections.genes.forEach {
                    it.mutate {
                        strength += random.nextDouble(-0.5, 0.5)
                    }
                }

                // Random source neuron
                val source = (inputs.genes + hiddens.genes).let {
                    val index = random.nextInt(0, it.size)
                    it[index]
                }
                // Random target neuron
                val target = (outputs.genes + hiddens.genes).let {
                    val index = random.nextInt(0, it.size)
                    it[index]
                }
                // Add the connection
                connections.genes.add(connectionGene(source, target) {
                    strength = random.nextDouble(-0.2, 0.2)
                })
            }

            //
            // Evaluate the current generation.
            //
            onEval {
                var score = 0.0

                fun OdorWorldEntity.handleCollision() {
                    onCollide { other ->
                        if (other === mouse) {
                            score -= 1
                        }
                    }
                }

                poison1.handleCollision();
                poison2.handleCollision();
                poison3.handleCollision();

                evolutionWorkspace.apply {
                    score += (0..5000).map {
                        simpleIterate()
                        minOf(
                            poison1.getRadiusTo(mouse),
                            poison2.getRadiusTo(mouse),
                            poison3.getRadiusTo(mouse)
                        ) / 100
                    }.minOf { it }
                }

                score
            }

            // Called when evolution finishes. evolutionWorkspace is the "winning" sim.
            onPeek {
                workspace.openFromZipData(evolutionWorkspace.zipData)
            }
        }

        return evaluator(environmentBuilder) {
            populationSize = 100
            eliminationRatio = 0.5
            optimizationMethod = Evaluator.OptimizationMethod.MAXIMIZE_FITNESS
            runUntil { generation == 50 || fitness > 50 }
        }
    }

    scope.launch {
        workspace.clearWorkspace()

        val progressWindow = ProgressWindow(200)

        launch(Dispatchers.Default) {

            val generations = createEvolution().start().onEachGenerationBest { agent, gen ->
                progressWindow.progressBar.value = gen
                progressWindow.fitnessScore.text = "Error: ${agent.fitness.format(2)}"
            }

            val (best, _) = generations.best

            println(best)

            val build = best.visibleBuild()

            build.peek()

            progressWindow.close()

        }
    }



}