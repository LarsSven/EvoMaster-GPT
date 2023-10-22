package org.evomaster.core.problem.rest.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.theokanning.openai.client.OpenAiApi
import com.theokanning.openai.completion.chat.ChatCompletionRequest
import com.theokanning.openai.completion.chat.ChatMessage
import com.theokanning.openai.completion.chat.ChatMessageRole
import com.theokanning.openai.service.OpenAiService
import com.theokanning.openai.service.OpenAiService.*
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
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration


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

    override fun customizeAdHocInitialIndividuals() {
        rc.checkConnection()
        val infoDto = rc.getSutInfo()
        val problem = infoDto!!.restProblem
        val openApiUrl = problem.openApiUrl

        val actions = generateRestCalls(openApiUrl)

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

    private fun generateRestCalls(openApiUrl: String): List<RestCallAction> {

        // NOTE: here you can enable the call to GPT, but for testing use the hardcoded YAML
//        val openApiBody = getOpenApiSchema(openApiUrl)
//        val rawYAML = requestCallsFromGPT(openApiBody)
        val rawYAML = getHardcodedYaml()

        // Parse YAML
        return parseYAMLToCalls(rawYAML)
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

    private fun requestCallsFromGPT(openApiBody: String): String {
        val token = System.getenv("OPENAI_TOKEN")

        val mapper = defaultObjectMapper()
        val client: OkHttpClient = defaultClient(token, Duration.ofSeconds(60))
            .newBuilder()
            .build()
        val retrofit = defaultRetrofit(client, mapper)
        val api: OpenAiApi = retrofit.create(OpenAiApi::class.java)
        val service = OpenAiService(api)

        val request = ChatCompletionRequest.builder()
            .model("gpt-3.5-turbo")
            .messages(
                listOf(
                    ChatMessage(
                        ChatMessageRole.SYSTEM.value(),
                        getSystemMessage(openApiBody)
                    ),
                    ChatMessage(
                        ChatMessageRole.USER.value(),
                        "Give me 50 potential requests to the API. Try to cover edge cases, and try to be creative with your chosen parameters."
                    )
                )
            )
            .build()

        val response = service.createChatCompletion(request)
        return response.choices[0].message.content
    }

    private fun getSystemMessage(openApiBody: String): String {
        return """
VERY IMPORTANT!!! ONLY RESPOND WITH A SINGLE VALID YAML FILE 

You are being used as a Fuzzer on the given API. I want to cover as many edge cases as possible. Analyze the following OpenAPI schema: '''
$openApiBody
'''

I want any response to be of the following format YAML: '''
actions:
    - verb: <HTTP_VERB>
      path: <PATH_TO_ENDPOINT>
      parameters:
        - name: <NAME_OF_PARAMETER>
          value: <VALUE_OF_PARAMETER>
        ...
    ...
'''
"""
    }

    private fun parseYAMLToCalls(rawYAML: String): List<RestCallAction> {
        val mapper = ObjectMapper(YAMLFactory())
        mapper.registerModule(KotlinModule())

        val mappedYaml = mapper.readValue(rawYAML.toByteArray(), GPTActions::class.java)

        val actions: MutableList<RestCallAction> = mutableListOf()
        for (action in mappedYaml.actions) {
            action.parameters

            val params = action.parameters.map {
                BodyParam(
                    gene = StringGene(
                        name = it.name,
                        value = it.value.toString()
                    ),
                    typeGene = EnumGene(
                        name = "${it.name}-gene",
                        data = listOf()
                    )
                )
            }

            actions.add(RestCallAction(
                id = "ID",
                verb = HttpVerb.valueOf(action.verb),
                path = RestPath(action.path),
                parameters = params.toMutableList(),
                auth = NoAuth(),
                saveLocation = false,
                locationId = null,
                produces = listOf(),
                responseRefs = mutableMapOf(),
                skipOracleChecks = false
            ))
        }

        return actions
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

    private fun getHardcodedYaml(): String {
        return """actions:
  # 1. GET request to the index endpoint
  - verb: GET
    path: /

  # 2. GET request to the tcpPort endpoint
  - verb: GET
    path: /api/tcpPort

  # 3. GET request to the tcpPortFailed endpoint
  - verb: GET
    path: /api/tcpPortFailed

  # 4. POST request to the indexPost endpoint with both parameters
  - verb: POST
    path: /
    parameters:
      - name: x
        value: 0
      - name: y
        value: 0

  # 5. POST request to the indexPost endpoint with only x parameter
  - verb: POST
    path: /
    parameters:
      - name: x
        value: 0

  # 6. POST request to the indexPost endpoint with only y parameter
  - verb: POST
    path: /
    parameters:
      - name: y
        value: 0

  # 7. POST request to the indexPost endpoint with invalid x parameter
  - verb: POST
    path: /
    parameters:
      - name: x
        value: -1

  # 8. POST request to the indexPost endpoint with invalid y parameter
  - verb: POST
    path: /
    parameters:
      - name: y
        value: -1

  # 9. POST request to the indexPost endpoint with null x parameter
  - verb: POST
    path: /
    parameters:
      - name: x
        value: null

  # 10. POST request to the indexPost endpoint with null y parameter
  - verb: POST
    path: /
    parameters:
      - name: y
        value: null

  # 11. POST request to the indexPost endpoint with string x parameter
  - verb: POST
    path: /
    parameters:
      - name: x
        value: "string_value"

  # 12. POST request to the indexPost endpoint with string y parameter
  - verb: POST
    path: /
    parameters:
      - name: y
        value: "string_value"

  # 13. POST request to the indexPost endpoint with decimal x parameter
  - verb: POST
    path: /
    parameters:
      - name: x
        value: 0.5

  # 14. POST request to the indexPost endpoint with decimal y parameter
  - verb: POST
    path: /
    parameters:
      - name: y
        value: 0.5

  # 15. POST request to the indexPost endpoint with large integer x parameter
  - verb: POST
    path: /
    parameters:
      - name: x
        value: 1000000

  # 16. POST request to the indexPost endpoint with large integer y parameter
  - verb: POST
    path: /
    parameters:
      - name: y
        value: 1000000

  # 17. POST request to the indexPost endpoint with multiple occurrences of x parameter
  - verb: POST
    path: /
    parameters:
      - name: x
        value: 0
      - name: x
        value: 1

  # 18. POST request to the indexPost endpoint with multiple occurrences of y parameter
  - verb: POST
    path: /
    parameters:
      - name: y
        value: 0
      - name: y
        value: 1

  # 19. POST request to the indexPost endpoint with both parameters as arrays
  - verb: POST
    path: /
    parameters:
      - name: x
        value:
          - 0
          - 1
      - name: y
        value:
          - 0
          - 1

  # 20. GET request to the index endpoint with invalid query parameter
  - verb: GET
    path: /
    parameters:
      - name: invalidParam
        value: invalidValue

  # 21. POST request to the indexPost endpoint with invalid query parameter
  - verb: POST
    path: /
    parameters:
      - name: invalidParam
        value: invalidValue

  # 22. POST request to the indexPost endpoint without any parameters
  - verb: POST
    path: /

  # 23. GET request to a non-existent endpoint
  - verb: GET
    path: /nonexistent

  # 24. POST request to a non-existent endpoint
  - verb: POST
    path: /nonexistent

  # 25. GET request to the index endpoint with empty query parameter
  - verb: GET
    path: /
    parameters:
      - name: x
        value: ""

  # 26. GET request to the index endpoint with empty query parameter
  - verb: GET
    path: /
    parameters:
      - name: y
        value: ""

  # 27. POST request to the indexPost endpoint with empty query parameter
  - verb: POST
    path: /
    parameters:
      - name: x
        value: ""

  # 28. POST request to the indexPost endpoint with empty query parameter
  - verb: POST
    path: /
    parameters:
      - name: y
        value: ""

  # 29. GET request to the index endpoint with missing required query parameter x
  - verb: GET
    path: /
    parameters:
      - name: y
        value: 0

  # 30. GET request to the index endpoint with missing required query parameter y
  - verb: GET
    path: /
    parameters:
      - name: x
        value: 0

  # 31. POST request to the indexPost endpoint with missing required query parameter x
  - verb: POST
    path: /
    parameters:
      - name: y
        value: 0

  # 32. POST request to the indexPost endpoint with missing required query parameter y
  - verb: POST
    path: /
    parameters:
      - name: x
        value: 0

  # 33. POST request to the indexPost endpoint with missing required query parameters
  - verb: POST
    path: /

  # 34. GET request to the index endpoint with negative integer x parameter
  - verb: GET
    path: /
    parameters:
      - name: x
        value: -1

  # 35. GET request to the index endpoint with negative integer y parameter
  - verb: GET
    path: /
    parameters:
      - name: y
        value: -1

  # 36. GET request to the index endpoint with null x parameter
  - verb: GET
    path: /
    parameters:
      - name: x
        value: null

  # 37. GET request to the index endpoint with null y parameter
  - verb: GET
    path: /
    parameters:
      - name: y
        value: null

  # 38. GET request to the index endpoint with string x parameter
  - verb: GET
    path: /
    parameters:
      - name: x
        value: "string_value"

  # 39. GET request to the index endpoint with string y parameter
  - verb: GET
    path: /
    parameters:
      - name: y
        value: "string_value"

  # 40. GET request to the index endpoint with decimal x parameter
  - verb: GET
    path: /
    parameters:
      - name: x
        value: 0.5

  # 41. GET request to the index endpoint with decimal y parameter
  - verb: GET
    path: /
    parameters:
      - name: y
        value: 0.5

  # 42. GET request to the index endpoint with large integer x parameter
  - verb: GET
    path: /
    parameters:
      - name: x
        value: 1000000

  # 43. GET request to the index endpoint with large integer y parameter
  - verb: GET
    path: /
    parameters:
      - name: y
        value: 1000000

  # 44. GET request to the index endpoint with multiple occurrences of x parameter
  - verb: GET
    path: /
    parameters:
      - name: x
        value: 0
      - name: x
        value: 1

  # 45. GET request to the index endpoint with multiple occurrences of y parameter
  - verb: GET
    path: /
    parameters:
      - name: y
        value: 0
      - name: y
        value: 1

  # 46. GET request to the index endpoint with both parameters as arrays
  - verb: GET
    path: /
    parameters:
      - name: x
        value:
          - 0
          - 1
      - name: y
        value:
          - 0
          - 1

  # 47. POST request to the indexPost endpoint with negative integer x parameter
  - verb: POST
    path: /
    parameters:
      - name: x
        value: -1

  # 48. POST request to the indexPost endpoint with negative integer y parameter
  - verb: POST
    path: /
    parameters:
      - name: y
        value: -1

  # 49. GET request to the index endpoint with empty request body
  - verb: GET
    path: /
    parameters: []

  # 50. POST request to the index endpoint with empty request body
  - verb: POST
    path: /
    parameters: []
        """
    }
}