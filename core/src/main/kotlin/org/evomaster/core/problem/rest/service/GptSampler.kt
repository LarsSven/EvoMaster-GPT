package org.evomaster.core.problem.rest.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import okhttp3.OkHttpClient
import okhttp3.Request
import org.evomaster.core.Lazy
import org.evomaster.core.problem.enterprise.SampleType
import org.evomaster.core.problem.httpws.auth.NoAuth
import org.evomaster.core.problem.rest.HttpVerb
import org.evomaster.core.problem.rest.RestCallAction
import org.evomaster.core.problem.rest.RestIndividual
import org.evomaster.core.problem.rest.RestPath
import org.evomaster.core.problem.rest.param.BodyParam
import org.evomaster.core.problem.rest.param.PathParam
import org.evomaster.core.search.gene.collection.EnumGene
import org.evomaster.core.search.gene.string.StringGene
import org.evomaster.core.search.tracer.Traceable
import org.evomaster.core.utils.GptHelper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.Exception


data class GPTActions(
    val actions: List<GPTAction>,
)
data class GPTAction(
    val verb: String,
    val path: String,
    val parameters: List<Parameter> = listOf()
)

data class Parameter(
    val name: String,
    val value: Any? // Using Any? to account for potential null values or different types
)

class GptSampler : AbstractRestSampler() {

    lateinit var openApiBody: String

    override fun customizeAdHocInitialIndividuals() {
        val actions = generateRestCalls()

        actions.forEach {
            it.doInitialize()
        }

        adHocInitialIndividuals.clear()
        // NOTE: Here we can either add them separately or as once, not sure which is best.
        val ind = createIndividual(SampleType.SMART, actions.toMutableList())
        ind.doGlobalInitialize(searchGlobalState)
        adHocInitialIndividuals.add(ind)
//        for (action in actions) {
//            val ind = createIndividual(SampleType.SMART, mutableListOf(action))
//            ind.doGlobalInitialize(searchGlobalState)
//            adHocInitialIndividuals.add(ind)
//        }
    }

    private fun generateRestCalls(): List<RestCallAction> {
        try {
            // Get OpenAPI schema
            rc.checkConnection()
            val infoDto = rc.getSutInfo()
            val problem = infoDto!!.restProblem
            val openApiUrl = problem.openApiUrl
            val openApiBody = getOpenApiSchema(openApiUrl)
            this.openApiBody = openApiBody

            // Get raw YAML
            val helper = GptHelper(openApiBody)

            return helper.requestSampleCallsFromGpt()
        } catch (e: Exception) {
            println("> COULD NOT PARSE SAMPLE CALLS, TRY AGAIN")
            return generateRestCalls()
        }
    }

    private fun getOpenApiSchema(openApiUrl: String): String {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(openApiUrl)
            .get()
            .build()
        val response = client.newCall(request).execute()
        return response.body!!.string()
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(RestSampler::class.java)
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
//        ind.computeTransitiveBindingGenes()
        return ind
    }


    /*
        FIXME: following call is likely unnecessary... originally under RestAction will could have different
        action types like SQL, but in the end we used a different approach (ie pre-init steps).
        So, likely can be removed, but need to check the refactoring RestResouce first
     */

    private fun sampleRandomCallAction(noAuthP: Double): RestCallAction {
        val action = randomness.choose(actionCluster.filter { a -> a.value is RestCallAction }).copy() as RestCallAction
        action.doInitialize(randomness)
        action.auth = getRandomAuth(noAuthP)
        return action
    }


    // GPT: Smart sample
    override fun smartSample(): RestIndividual {
        /*
            At the beginning, sample from this set, until it is empty
         */
        if (adHocInitialIndividuals.isNotEmpty()) {
            return adHocInitialIndividuals.removeAt(adHocInitialIndividuals.size - 1)
        }

        if (getMaxTestSizeDuringSampler() <= 1) {
            /*
                Here we would have sequences of endpoint calls that are
                somehow linked to each other, eg a DELETE on a resource
                created with a POST.
                If can have only one call, then just go random
             */
            return sampleAtRandom()
        }


        val test = mutableListOf<RestCallAction>()

        val action = sampleRandomCallAction(0.0)

        /*
            TODO: each of these "smart" tests could end with a GET, to make
            the test easier to read and verify the results (eg side-effects of
            DELETE/PUT/PATCH operations).
            But doing that as part of the tests could be inefficient (ie a lot
            of GET calls).
            Maybe that should be done as part of an "assertion generation" phase
            (which would also be useful for creating checks on returned JSONs)
         */

        val sampleType = when (action.verb) {
            HttpVerb.GET -> handleSmartGet(action, test)
            HttpVerb.POST -> handleSmartPost(action, test)
            HttpVerb.PUT -> handleSmartPut(action, test)
            HttpVerb.DELETE -> handleSmartDelete(action, test)
            HttpVerb.PATCH -> handleSmartPatch(action, test)
            else -> SampleType.RANDOM
        }

        if (!test.isEmpty()) {
            val objInd = RestIndividual(test, sampleType, mutableListOf()//, usedObjects.copy()
                ,trackOperator = if (config.trackingEnabled()) this else null, index = if (config.trackingEnabled()) time.evaluatedIndividuals else Traceable.DEFAULT_INDEX)

            objInd.doGlobalInitialize(searchGlobalState)
//            objInd.computeTransitiveBindingGenes()
            return objInd
        }
        //usedObjects.clear()
        return sampleAtRandom()
    }

    private fun handleSmartPost(post: RestCallAction, test: MutableList<RestCallAction>): SampleType {

        Lazy.assert{post.verb == HttpVerb.POST}

        //as POST is used in all the others, maybe here we do not really need to handle it specially?
        test.add(post)
        return SampleType.SMART
    }

    private fun handleSmartDelete(delete: RestCallAction, test: MutableList<RestCallAction>): SampleType {

        Lazy.assert{delete.verb == HttpVerb.DELETE}

        createWriteOperationAfterAPost(delete, test)

        return SampleType.SMART
    }

    private fun handleSmartPatch(patch: RestCallAction, test: MutableList<RestCallAction>): SampleType {

        Lazy.assert{patch.verb == HttpVerb.PATCH}

        createWriteOperationAfterAPost(patch, test)

        return SampleType.SMART
    }

    private fun handleSmartPut(put: RestCallAction, test: MutableList<RestCallAction>): SampleType {

        Lazy.assert{put.verb == HttpVerb.PUT}

        /*
            A PUT might be used to update an existing resource, or to create a new one
         */
        if (randomness.nextBoolean(0.2)) {
            /*
                with low prob., let's just try the PUT on its own.
                Recall we already add single calls on each endpoint at initialization
             */
            test.add(put)
            return SampleType.SMART
        }

        createWriteOperationAfterAPost(put, test)
        return SampleType.SMART
    }

    /**
     *    Only for PUT, DELETE, PATCH
     */
    private fun createWriteOperationAfterAPost(write: RestCallAction, test: MutableList<RestCallAction>) {

        Lazy.assert{write.verb == HttpVerb.PUT || write.verb == HttpVerb.DELETE || write.verb == HttpVerb.PATCH}

        test.add(write)

        //Need to find a POST on a parent collection resource
        createResourcesFor(write, test)

        if (write.verb == HttpVerb.PATCH &&
            getMaxTestSizeDuringSampler() >= test.size + 1 &&
            randomness.nextBoolean()) {
            /*
                As PATCH is not idempotent (in contrast to PUT), it can make sense to test
                two patches in sequence
             */
            val secondPatch = createActionFor(write, write)
            test.add(secondPatch)
            secondPatch.locationId = write.locationId
        }

        test.forEach { t ->
            preventPathParamMutation(t as RestCallAction)
        }
    }

    private fun handleSmartGet(get: RestCallAction, test: MutableList<RestCallAction>): SampleType {

        Lazy.assert{get.verb == HttpVerb.GET}

        /*
           A typical case is something like

           POST /elements
           GET  /elements/{id}

           Problems is that the {id} might not be known beforehand,
           eg it would be the result of calling POST first, where the
           path would be in the returned Location header.

           However, we might even encounter cases like:

           POST /elements/{id}
           GET  /elements/{id}

           which is possible, although bit weird, as in such case it
           would be better to have a PUT instead of a POST.

           Note: we prefer a POST to create a resource, as that is the
           most common case, and not all PUTs allow creation
         */

        test.add(get)

        val created = createResourcesFor(get, test)

        if (!created) {
            /*
                A GET with no POST in any ancestor.
                This could happen if the API is "read-only".

                TODO: In such case, would really need to handle things like
                direct creation of data in the DB (for example)
             */
        } else {
            //only lock path params if it is not a single GET
            test.forEach { t ->
                preventPathParamMutation(t as RestCallAction)
            }
        }

        if (created && !get.path.isLastElementAParameter()) {

            val lastPost = test[test.size - 2] as RestCallAction
            Lazy.assert{lastPost.verb == HttpVerb.POST}

            val available = getMaxTestSizeDuringSampler() - test.size

            if (lastPost.path.isEquivalent(get.path) && available > 0) {
                /*
                 The endpoint might represent a collection, ie we
                 can be in the case:

                  POST /api/elements
                  GET  /api/elements

                 Therefore, to properly test the GET, we might
                 need to be able to create many elements.
                 */
                log.trace("Creating POSTs on collection before a GET")
                val k = 1 + randomness.nextInt(available)

                (0 until k).forEach {
                    val create = createActionFor(lastPost, get)
                    preventPathParamMutation(create)
                    create.locationId = lastPost.locationId

                    //add just before the last GET
                    test.add(test.size - 1, create)
                }

                return SampleType.REST_SMART_GET_COLLECTION
            }
        }

        return SampleType.SMART
    }


    private fun createResourcesFor(target: RestCallAction, test: MutableList<RestCallAction>)
            : Boolean {

        if (test.size >= getMaxTestSizeDuringSampler()) {
            return false
        }

        val template = chooseClosestAncestor(target, listOf(HttpVerb.POST))
            ?: return false

        val post = createActionFor(template, target)

        test.add(0, post)

        /*
            Check if POST depends itself on the creation of
            some intermediate resource
         */
        if (post.path.hasVariablePathParameters() &&
            (!post.path.isLastElementAParameter()) ||
            post.path.getVariableNames().size >= 2) {

            val dependencyCreated = createResourcesFor(post, test)
            if (!dependencyCreated) {
                return false
            }
        }


        /*
            Once the POST is fully initialized, need to fix
            links with target
         */
        if (!post.path.isEquivalent(target.path)) {
            /*
                eg
                POST /x
                GET  /x/{id}
             */
            post.saveLocation = true
            target.locationId = post.path.lastElement()
        } else {
            /*
                eg
                POST /x
                POST /x/{id}/y
                GET  /x/{id}/y
             */
            //not going to save the position of last POST, as same as target
            post.saveLocation = false

            // the target (eg GET) needs to use the location of first POST, or more correctly
            // the same location used for the last POST (in case there is a deeper chain)
            target.locationId = post.locationId
        }

        return true
    }

    private fun preventPathParamMutation(action: RestCallAction) {
        action.parameters.forEach { p -> if (p is PathParam) p.preventMutation() }
    }

    fun createActionFor(template: RestCallAction, target: RestCallAction): RestCallAction {

        val res = template.copy() as RestCallAction
        if(res.isInitialized()){
            res.seeTopGenes().forEach { it.randomize(randomness, false) }
        } else {
            res.doInitialize(randomness)
        }
        res.auth = target.auth
        res.bindToSamePathResolution(target)

        return res
    }

    /**
     * Make sure that what returned is different from the target.
     * This can be a strict ancestor (shorter path), or same
     * endpoint but with different HTTP verb.
     * Among the different ancestors, return one of the longest
     */
    private fun chooseClosestAncestor(target: RestCallAction, verbs: List<HttpVerb>): RestCallAction? {

        var others = sameOrAncestorEndpoints(target.path)
        others = hasWithVerbs(others, verbs).filter { t -> t.getName() != target.getName() }

        if (others.isEmpty()) {
            return null
        }

        return chooseLongestPath(others)
    }

    /**
     * Get all ancestor (same path prefix) endpoints that do at least one
     * of the specified operations
     */
    private fun sameOrAncestorEndpoints(path: RestPath): List<RestCallAction> {
        return actionCluster.values.asSequence()
            .filter { a -> a is RestCallAction && a.path.isAncestorOf(path) }
            .map { a -> a as RestCallAction }
            .toList()
    }

    private fun chooseLongestPath(actions: List<RestCallAction>): RestCallAction {

        if (actions.isEmpty()) {
            throw IllegalArgumentException("Cannot choose from an empty collection")
        }

        val max = actions.asSequence().map { a -> a.path.levels() }.maxOrNull()!!
        val candidates = actions.filter { a -> a.path.levels() == max }

        return randomness.choose(candidates)
    }

    private fun hasWithVerbs(actions: List<RestCallAction>, verbs: List<HttpVerb>): List<RestCallAction> {
        return actions.filter { a ->
            verbs.contains(a.verb)
        }
    }
}