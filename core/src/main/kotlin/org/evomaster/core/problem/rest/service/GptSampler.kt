package org.evomaster.core.problem.rest.service

import org.evomaster.core.problem.enterprise.SampleType
import org.evomaster.core.problem.httpws.auth.HttpWsAuthenticationInfo
import org.evomaster.core.problem.httpws.auth.NoAuth
import org.evomaster.core.problem.rest.RestCallAction
import org.evomaster.core.problem.rest.RestIndividual

class GptSampler : AbstractRestSampler() {
    override fun customizeAdHocInitialIndividuals() {
        adHocInitialIndividuals.clear()

        sampleEachEndpoint(NoAuth())

        authentications.forEach { auth ->
            sampleEachEndpoint(auth)
        }
    }

    private fun sampleEachEndpoint(auth: HttpWsAuthenticationInfo) {
        actionCluster.asSequence()
            .filter { a -> a.value is RestCallAction }
            .forEach { a ->
                val copy = a.value.copy() as RestCallAction
                copy.auth = auth
                copy.doInitialize(randomness)
                val ind = createIndividual(SampleType.SMART, mutableListOf(copy))
                ind.doGlobalInitialize(searchGlobalState)
                adHocInitialIndividuals.add(ind)
            }
    }

    override fun sampleAtRandom(): RestIndividual {
        val actions = mutableListOf<RestCallAction>()
        val n = randomness.nextInt(1, getMaxTestSizeDuringSampler())

        (0 until n).forEach {
            actions.add(generateRestCall())
        }

        val ind = RestIndividual(actions, SampleType.RANDOM, mutableListOf(), this, time.evaluatedIndividuals)
        ind.doGlobalInitialize(searchGlobalState)
        return ind
    }

    fun generateRestCall(): RestCallAction {

    }
}