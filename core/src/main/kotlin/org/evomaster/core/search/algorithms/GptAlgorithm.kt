package org.evomaster.core.search.algorithms

import org.evomaster.core.EMConfig
import org.evomaster.core.problem.rest.RestCallAction
import org.evomaster.core.problem.rest.RestCallResult
import org.evomaster.core.problem.rest.RestIndividual
import org.evomaster.core.search.EvaluatedIndividual
import org.evomaster.core.search.Individual
import org.evomaster.core.search.service.SearchAlgorithm

class GptAlgorithm<T> : SearchAlgorithm<T>() where T : Individual {
    override fun getType(): EMConfig.Algorithm {
        return EMConfig.Algorithm.GPT
    }


    override fun setupBeforeSearch() {
        // Nothing needs to be done before starting the search
    }

    override fun searchOnce() {
        val individual = archive.sampleIndividual()

        val newIndividual = generateNewIndividual(individual)

        archive.addIfNeeded(newIndividual)
    }

    private fun generateNewIndividual(ind: EvaluatedIndividual<T>): EvaluatedIndividual<T> {
        val restCalls =  improveRestCalls(
            ind.individual.seeAllActions().mapTo(mutableListOf()) { it as RestCallAction }
        )
        val oldRestIndividual = ind.individual as RestIndividual


        val newRestIndividual = RestIndividual(restCalls, sampleType = oldRestIndividual.sampleType)

        return ff.calculateCoverage(newRestIndividual as T) ?: throw Exception("Could not cast to T")
    }

    private fun improveRestCalls(calls: MutableList<RestCallAction>): MutableList<RestCallAction> {
        // TODO: Implement GPT
        return calls
    }
}