package org.evomaster.core.problem.httpws.service

import org.evomaster.core.Lazy
import org.evomaster.core.database.DbAction
import org.evomaster.core.database.DbActionUtils
import org.evomaster.core.search.Action
import org.evomaster.core.search.Individual
import org.evomaster.core.search.gene.GeneUtils
import org.evomaster.core.search.service.Randomness
import org.evomaster.core.search.tracer.TrackOperator
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class HttpWsIndividual (
    val dbInitialization: MutableList<DbAction> = mutableListOf(),
    trackOperator: TrackOperator? = null,
    index : Int = -1
): Individual(trackOperator, index){

    companion object{
        private val log : Logger = LoggerFactory.getLogger(HttpWsIndividual::class.java)
    }

    override fun seeInitializingActions(): List<DbAction> {
        return dbInitialization
    }

    override fun repairInitializationActions(randomness: Randomness) {

        /**
         * First repair SQL Genes (i.e. SQL Timestamps)
         */
        if (log.isTraceEnabled)
            log.trace("invoke GeneUtils.repairGenes")

        GeneUtils.repairGenes(this.seeGenes(GeneFilter.ONLY_SQL).flatMap { it.flatView() })

        /**
         * Now repair database constraints (primary keys, foreign keys, unique fields, etc.)
         */
        if (!verifyInitializationActions()) {
            if (log.isTraceEnabled)
                log.trace("invoke GeneUtils.repairBrokenDbActionsList")
            DbActionUtils.repairBrokenDbActionsList(dbInitialization, randomness)
            Lazy.assert{verifyInitializationActions()}
        }
    }

    override fun hasAnyAction(): Boolean {
        return super.hasAnyAction() || dbInitialization.isNotEmpty()
    }
}