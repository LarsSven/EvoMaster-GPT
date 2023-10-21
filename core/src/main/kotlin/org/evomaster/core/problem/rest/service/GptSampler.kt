package org.evomaster.core.problem.rest.service

import com.theokanning.openai.completion.chat.ChatCompletionRequest
import com.theokanning.openai.completion.chat.ChatMessage
import com.theokanning.openai.completion.chat.ChatMessageRole
import com.theokanning.openai.service.OpenAiService
import org.evomaster.core.Lazy
import org.evomaster.core.problem.enterprise.SampleType
import org.evomaster.core.problem.httpws.auth.NoAuth
import org.evomaster.core.problem.rest.HttpVerb
import org.evomaster.core.problem.rest.RestCallAction
import org.evomaster.core.problem.rest.RestIndividual
import org.evomaster.core.problem.rest.RestPath
import org.evomaster.core.problem.rest.param.PathParam
import org.evomaster.core.search.tracer.Traceable
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class GptSampler : AbstractRestSampler() {

    override fun customizeAdHocInitialIndividuals() {
        println("Initial")
        adHocInitialIndividuals.clear()

        generateRestCalls()

        for (i in 0 until 10) {
            val ind = createIndividual(SampleType.SMART, mutableListOf(generateRestCall()))
            ind.doGlobalInitialize(searchGlobalState)
            adHocInitialIndividuals.add(ind)
        }
    }

    private fun generateRestCalls(): List<RestCallAction> {

//        val rawYAML = requestCallsFromGPT()
        val rawYAML = """actions:
  - verb: GET
    path: /
    parameters: []
  
  - verb: POST
    path: /
    parameters:
      - name: x
        value: 0
      - name: y
        value: 100
  
  - verb: POST
    path: /
    parameters: []
  
  - verb: GET
    path: /api/tcpPort
    parameters: []
  
  - verb: GET
    path: /api/tcpPortFailed
    parameters: []
  
  - verb: GET
    path: /
    parameters:
      - name: invalidParam
        value: abc
  
  - verb: POST
    path: /
    parameters:
      - name: x
        value: 100
      - name: invalidParam
        value: abc
  
  - verb: POST
    path: /
    parameters:
      - name: x
        value: -50
      - name: y
        value: 200
  
  - verb: GET
    path: /
    parameters:
      - name: x
        value: null
      - name: y
        value: 0
  
  - verb: GET
    path: /
    parameters:
      - name: x
        value: 500
      - name: y
        value: 1000
        """
        print(rawYAML)

        // Parse YAML
//        return parseYAMLToCalls(rawYAML)

        return listOf()
    }

    private fun requestCallsFromGPT(): String {
        val token = System.getenv("OPENAI_TOKEN")

        val service = OpenAiService(token)

        val request = ChatCompletionRequest.builder()
            .model("gpt-3.5-turbo")
            .messages(
                listOf(
                    ChatMessage(
                        ChatMessageRole.SYSTEM.value(),
                        getSystemMessage()
                    ),
                    ChatMessage(
                        ChatMessageRole.USER.value(),
                        "Give me 10 potential requests to the API. Try to cover edge cases, and try to be creative with your chosen parameters."
                    )
                )
            )
            .build()

        val response = service.createChatCompletion(request)
        return response.choices[0].message.content
    }

    private fun getSystemMessage(): String {
        // TODO: insert OpenAPI Schema
        return """
VERY IMPORTANT!!! ONLY RESPOND WITH A SINGLE VALID YAML FILE 

You are being used as a Fuzzer on the given API. I want to cover as many edge cases as possible. Analyze the following OpenAPI schema: \"\"\"
openapi: 3.0.1
info:
  title: Micronaut
  description: Micronaut Latest E2E Test API
  version: latest
paths:
  /:
    get:
      summary: Endpoint to trigger a Micronaut crash with HTTP 500
      description: Used to test a crash scenario.
      operationId: index
      parameters: []
      responses:
        "500":
          description: Expected outcome (HTTP 500)
        "200":
          description: Successful response (HTTP 200)
          content:
            application/json:
              schema:
                type: string
    post:
      summary: POST Controller for testing
      description: Returns a HTTP 200 response
      operationId: indexPost
      parameters:
      - name: x
        in: query
        required: false
        schema:
          minimum: 0
          type: integer
          format: int32
          nullable: true
      - name: "y"
        in: query
        required: false
        schema:
          minimum: 0
          type: integer
          format: int32
          nullable: true
      responses:
        "200":
          description: Successful POST request (HTTP 200)
          content:
            application/json:
              schema:
                type: string
  /api/tcpPort:
    get:
      operationId: tcpPort
      parameters: []
      responses:
        "200":
          description: Successful response for TCP Port (HTTP 200)
          content:
            application/json:
              schema:
                type: string
  /api/tcpPortFailed:
    get:
      operationId: tcpPortFailed
      parameters: []
      responses:
        "200":
          description: Successful response for a failed TCP Port (HTTP 200)
          content:
            application/json:
              schema:
                type: string
\"\"\"

I want any response to be of the following format YAML: \"\"\"
actions:
    - verb: <HTTP_VERB>
      path: <PATH_TO_ENDPOINT>
      parameters:
        - name: <NAME_OF_PARAMETER>
          value: <VALUE_OF_PARAMETER>
        ...
    ...
\"\"\"
"""
    }

//    private fun parseYAMLToCalls(rawYAML: String): List<RestCallAction> {
//        val yaml = Yaml()
//        val actionsList: ActionsList = yaml.loadAs(rawYAML, ActionsList::class.java)
//
//        val actions: MutableList<RestCallAction> = mutableListOf()
////        for (yamlAction in yamlActions) {
////            val paramsAny = yamlAction["parameters"]
////            actions.add(RestCallAction(
////                id = "id",
////                verb = HttpVerb.valueOf(yamlAction["verb"]!!),
////                path = RestPath(yamlAction["path"]!!),
////                parameters = mutableListOf(),
////                auth = NoAuth(),
////                saveLocation = false,
////                locationId = null,
////                produces = listOf(),
////                responseRefs = mutableMapOf(),
////                skipOracleChecks = false
////            ))
////        }
//
//        return actions
//    }

    private fun generateRestCall(): RestCallAction {
        // TODO: Implement GPT
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