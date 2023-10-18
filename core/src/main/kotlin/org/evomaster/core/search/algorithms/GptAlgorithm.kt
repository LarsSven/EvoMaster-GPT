package org.evomaster.core.search.algorithms

import org.evomaster.core.EMConfig
import org.evomaster.core.search.Individual
import org.evomaster.core.search.service.SearchAlgorithm

class GptAlgorithm<T> : SearchAlgorithm<T>() where T : Individual {
    override fun searchOnce() {
        TODO("Not yet implemented")
    }

    override fun setupBeforeSearch() {
        TODO("Not yet implemented")
    }

    override fun getType(): EMConfig.Algorithm {
        TODO("Not yet implemented")
    }
}