package org.evomaster.core.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.theokanning.openai.client.OpenAiApi
import com.theokanning.openai.completion.chat.ChatCompletionRequest
import com.theokanning.openai.completion.chat.ChatMessage
import com.theokanning.openai.completion.chat.ChatMessageRole
import com.theokanning.openai.service.OpenAiService
import okhttp3.OkHttpClient
import org.evomaster.core.problem.api.param.Param
import org.evomaster.core.problem.httpws.auth.NoAuth
import org.evomaster.core.problem.rest.HttpVerb
import org.evomaster.core.problem.rest.RestCallAction
import org.evomaster.core.problem.rest.RestPath
import org.evomaster.core.problem.rest.param.BodyParam
import org.evomaster.core.problem.rest.param.FormParam
import org.evomaster.core.problem.rest.param.UpdateForBodyParam
import org.evomaster.core.problem.rest.service.GPTActions
import org.evomaster.core.problem.rest.service.Parameter
import org.evomaster.core.search.FitnessValue
import org.evomaster.core.search.gene.collection.EnumGene
import org.evomaster.core.search.gene.numeric.IntegerGene
import org.evomaster.core.search.gene.string.StringGene
import java.time.Duration

class GptHelper(private val openApiBody: String) {

    private val getHardcoded = false

    fun requestSampleCallsFromGpt(): List<RestCallAction> {
        val token = System.getenv("OPENAI_TOKEN")

        val rawYAML = if (token == "" || getHardcoded) {
            Thread.sleep(1000) // Emulate exagerated request time
            getHardcodedYaml()
        } else {
            val mapper = OpenAiService.defaultObjectMapper()
            val client: OkHttpClient = OpenAiService.defaultClient(token, Duration.ofSeconds(60))
                .newBuilder()
                .build()
            val retrofit = OpenAiService.defaultRetrofit(client, mapper)
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
                            "Give me 10 potential requests to the API. Try to cover edge cases, and try to be creative with your chosen parameters."
                        )
                    )
                )
                .build()

            println("> REQUESTING SAMPLE CALLS FROM GPT")
            val response = service.createChatCompletion(request)
            response.choices[0].message.content
        }

        return parseYAMLToCalls(rawYAML)
    }

    fun requestImprovedCallsFromGpt(oldCalls: List<RestCallAction>, fitness: FitnessValue): List<RestCallAction> {
        val token = System.getenv("OPENAI_TOKEN")

        val rawYAML = if (token == "" || getHardcoded) {
            Thread.sleep(1000) // Emulate exagerated request time
            oldCallsToYaml(oldCalls)
        } else {
            val mapper = OpenAiService.defaultObjectMapper()
            val client: OkHttpClient = OpenAiService.defaultClient(token, Duration.ofSeconds(60))
                .newBuilder()
                .build()
            val retrofit = OpenAiService.defaultRetrofit(client, mapper)
            val api: OpenAiApi = retrofit.create(OpenAiApi::class.java)
            val service = OpenAiService(api)

            val oldCallsYaml = oldCallsToYaml(oldCalls)
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
                            """YOU MUST FOLLOW THE SPECIFIED/EXISTING FORMAT, IF YOU YOUR MESSAGE IS USELESS.

I asked you before to make 10 calls to the API. Since you are a fuzzer I want you to evolve your previous requests into new ones.
Be creative by implementing the API. You can change requests to use other paths as well, but they must follow the API specifications.

These were the previous requests: '''
$oldCallsYaml
'''
                            """
                        )
                    )
                )
                .build()

            println("> REQUESTING IMPROVED CALLS FROM GPT")
            val response = service.createChatCompletion(request)
            response.choices[0].message.content
        }

        return parseYAMLToCalls(rawYAML)
    }

    private fun parseYAMLToCalls(rawYAML: String): List<RestCallAction> {
        val mapper = ObjectMapper(YAMLFactory())
        mapper.registerModule(KotlinModule())

        val yamlAfter = "actions:" + rawYAML.substringAfter("actions:")
        val mappedYaml = mapper.readValue(yamlAfter.toByteArray(), GPTActions::class.java)

        val actions: MutableList<RestCallAction> = mutableListOf()
        for (action in mappedYaml.actions) {
            actions.add(
                RestCallAction(
                    id = "ID",
                    verb = HttpVerb.valueOf(action.verb),
                    path = RestPath(action.path),
                    parameters = actionParamsToGenes(action.parameters),
                    auth = NoAuth(),
                    saveLocation = false,
                    locationId = null,
                    produces = listOf(),
                    responseRefs = mutableMapOf(),
                    skipOracleChecks = false
                )
            )
        }

        return actions
    }

    private fun actionParamsToGenes(params: List<Parameter>): MutableList<Param> {
        // TODO: understand the genes better and actually add correct ones.
        // No idea how it works "behind the scene" but I saw the EnumGene in another file
        return params.map {
            UpdateForBodyParam(
                BodyParam(
                    gene = StringGene(
                        name = it.name,
                        value = it.value.toString()
                    ),
                    EnumGene("contentType", listOf("application/json"))
                )
            )
        }.toMutableList()
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

    private fun oldCallsToYaml(oldCalls: List<RestCallAction>): String {
        var yaml = "actions:\n"
        for (call in oldCalls) {
            yaml += """    - verb: ${call.verb}
      path: ${call.path}
      parameters:
        - name: <NAME_OF_PARAMETER>
          value: <VALUE_OF_PARAMETER>
"""
            // TODO: add the existing paramters in the call. Not sure how to extract from the genes.
        }
        return yaml
    }

    private fun getHardcodedYaml(): String {
        return """SOME RANDOM COMMENTS FROM GPT!!@
            
actions:
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