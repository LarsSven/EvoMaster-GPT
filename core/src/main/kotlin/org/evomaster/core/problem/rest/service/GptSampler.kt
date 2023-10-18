package org.evomaster.core.problem.rest.service

import org.evomaster.core.problem.enterprise.SampleType
import org.evomaster.core.problem.httpws.auth.HttpWsAuthenticationInfo
import org.evomaster.core.problem.httpws.auth.NoAuth
import org.evomaster.core.problem.rest.HttpVerb
import org.evomaster.core.problem.rest.RestCallAction
import org.evomaster.core.problem.rest.RestIndividual
import org.evomaster.core.problem.rest.RestPath

class GptSampler : AbstractRestSampler() {
    override fun customizeAdHocInitialIndividuals() {
        println("Initial")
        adHocInitialIndividuals.clear()

        for (i in 0 until 10) {
            val ind = createIndividual(SampleType.SMART, mutableListOf(generateRestCall()))
            ind.doGlobalInitialize(searchGlobalState)
            adHocInitialIndividuals.add(ind)
        }
    }

    override fun sampleAtRandom(): RestIndividual {
        val actions = mutableListOf<RestCallAction>()
        val n = randomness.nextInt(1, getMaxTestSizeDuringSampler())

        (0 until n).forEach {
            actions.add(sampleRandomAction(0.05) as RestCallAction)
        }

        //GPT: Random sample

        val ind = RestIndividual(actions, SampleType.RANDOM, mutableListOf(), this, time.evaluatedIndividuals)
        ind.doGlobalInitialize(searchGlobalState)
        return ind
    }

    fun generateRestCall(): RestCallAction {
        println("generateRestCall")
        return RestCallAction(
            id = "id",
            verb = HttpVerb.GET,
            path = RestPath("/"),
            parameters = mutableListOf(),
            auth = NoAuth(),
            saveLocation = false,
            locationId = null,
            produces = listOf(),
            responseRefs = mutableMapOf(),
            skipOracleChecks = false
        )
    }
}