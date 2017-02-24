package org.evomaster.experiments

import com.google.inject.Injector
import com.google.inject.Key
import com.google.inject.Module
import com.google.inject.TypeLiteral
import com.netflix.governator.guice.LifecycleInjector
import org.evomaster.core.BaseModule
import org.evomaster.core.EMConfig
import org.evomaster.core.search.algorithms.MioAlgorithm
import org.evomaster.core.search.algorithms.MosaAlgorithm
import org.evomaster.core.search.algorithms.RandomAlgorithm
import org.evomaster.core.search.algorithms.WtsAlgorithm
import org.evomaster.core.search.service.SearchAlgorithm
import org.evomaster.experiments.maxv.LinearIndividual
import org.evomaster.experiments.maxv.LinearModule
import org.evomaster.experiments.maxv.LinearProblemDefinition
import java.util.*


class Main {

    companion object {

        /**
         * Main entry point of the EvoMaster application
         */
        @JvmStatic
        fun main(args: Array<String>) {

            val seed = 42L
            val budget = 1000
            val nTargets = 1
            val range = 1000
            val disruptiveP = 0.01

            val optima = createOptima(nTargets, range, seed)

            val mio = getMio(seed, budget, nTargets, range, disruptiveP, optima)
            val rand = getRand(seed, budget, nTargets, range, disruptiveP, optima)
            val mosa = getMosa(seed, budget, nTargets, range, disruptiveP, optima)
            val wts = getWts(seed, budget, nTargets, range, disruptiveP, optima)

            val algs: List<SearchAlgorithm<LinearIndividual>> = listOf(rand, wts, mosa, mio)

            println("algorithm,coverage,n,range,budget")
            algs.forEach { a ->
                val sol = a.search()
                val covered = sol.overall.coveredTargets()
                val cov = 100.0 * ( covered.toDouble() / nTargets.toDouble())

                println("${a.getType()},$cov,$nTargets,$range,$budget")
            }
        }

        fun createOptima(n: Int, range: Int, seed: Long) : List<Int>{
            val optima = mutableListOf<Int>()
            val rand = Random(seed)
            (1..n).forEach {
                optima.add(rand.nextInt(range+1))
            }

            return optima
        }

        fun getMio(seed: Long, budget: Int, nTargets: Int, range: Int, disruptiveP: Double, optima: List<Int>)
                : MioAlgorithm<LinearIndividual>{

            val injector = getInjector(seed, budget, nTargets, range, disruptiveP, optima)
            val config = injector.getInstance(EMConfig::class.java)
            config.algorithm = EMConfig.Algorithm.MIO

            return injector.getInstance(Key.get(
                    object : TypeLiteral<MioAlgorithm<LinearIndividual>>() {}))
        }

        fun getRand(seed: Long, budget: Int, nTargets: Int, range: Int, disruptiveP: Double, optima: List<Int>)
                : RandomAlgorithm<LinearIndividual>{

            val injector = getInjector(seed, budget, nTargets, range, disruptiveP, optima)
            val config = injector.getInstance(EMConfig::class.java)
            config.algorithm = EMConfig.Algorithm.RANDOM

            return injector.getInstance(Key.get(
                    object : TypeLiteral<RandomAlgorithm<LinearIndividual>>() {}))
        }

        fun getMosa(seed: Long, budget: Int, nTargets: Int, range: Int, disruptiveP: Double, optima: List<Int>)
                : MosaAlgorithm<LinearIndividual>{

            val injector = getInjector(seed, budget, nTargets, range, disruptiveP, optima)
            val config = injector.getInstance(EMConfig::class.java)
            config.algorithm = EMConfig.Algorithm.MOSA

            return injector.getInstance(Key.get(
                    object : TypeLiteral<MosaAlgorithm<LinearIndividual>>() {}))
        }

        fun getWts(seed: Long, budget: Int, nTargets: Int, range: Int, disruptiveP: Double, optima: List<Int>)
                : WtsAlgorithm<LinearIndividual>{

            val injector = getInjector(seed, budget, nTargets, range, disruptiveP, optima)
            val config = injector.getInstance(EMConfig::class.java)
            config.algorithm = EMConfig.Algorithm.WTS

            return injector.getInstance(Key.get(
                    object : TypeLiteral<WtsAlgorithm<LinearIndividual>>() {}))
        }

        fun getInjector(seed: Long, budget: Int, nTargets: Int, range: Int, disruptiveP: Double, optima: List<Int>) : Injector{

            val injector: Injector = LifecycleInjector.builder()
                    .withModules(* arrayOf<Module>(LinearModule(), BaseModule()))
                    .build().createInjector()

            val config = injector.getInstance(EMConfig::class.java)
            config.seed = seed
            config.maxFitnessEvaluations = budget

            val lpd = injector.getInstance(LinearProblemDefinition::class.java)
            lpd.nTargets = nTargets
            lpd.disruptiveP = disruptiveP
            lpd.range = range
            lpd.optima.clear()
            lpd.optima.addAll(optima)

            return injector
        }
    }
}