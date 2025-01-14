package id.ade.ktorwebhooksample.routes

import id.ade.ktorwebhooksample.models.Message
import id.ade.ktorwebhooksample.models.receive.GitlabMergeRequestReceiver
import id.ade.ktorwebhooksample.models.request.SlackBodyRequest
import id.ade.ktorwebhooksample.models.request.SlackBodyRequest.Companion.HEADER_TYPE
import id.ade.ktorwebhooksample.models.request.SlackBodyRequest.Companion.IMAGE_TYPE
import id.ade.ktorwebhooksample.models.request.SlackBodyRequest.Companion.MARKDOWN_TYPE
import id.ade.ktorwebhooksample.models.request.SlackBodyRequest.Companion.SECTION_TYPE
import id.ade.ktorwebhooksample.utils.Constant
import id.ade.ktorwebhooksample.utils.toJson
import io.ktor.application.call
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.features.logging.DEFAULT
import io.ktor.client.features.logging.LogLevel
import io.ktor.client.features.logging.Logger
import io.ktor.client.features.logging.Logging
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Location
import io.ktor.locations.post
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.util.InternalAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

private const val API_VERSION = "/v1"
const val GITLAB_WEBHOOK = "$API_VERSION/gitlab"

@KtorExperimentalLocationsAPI
@Location(GITLAB_WEBHOOK)
class GitlabWebhookRoute

@OptIn(InternalAPI::class)
@KtorExperimentalLocationsAPI
fun Route.gitlabWebhook() {
    post<GitlabWebhookRoute> {
        coroutineScope {
            val gitlabMergeRequestReceiver = call.receive<GitlabMergeRequestReceiver>()
            val client = createClient()
            kotlin.runCatching {
                val response = async(Dispatchers.IO) {
                    client.post<String>(System.getenv(Constant.Slack.SLACK_WEBHOOK_URL)) {
                        contentType(ContentType.Application.Json)
                        val requestBody = SlackBodyRequest(
                            listOf(
                                SlackBodyRequest.Block(
                                    type = SECTION_TYPE,
                                    text = SlackBodyRequest.Block.Text(
                                        text = "<${gitlabMergeRequestReceiver.objectAttributes?.url}|${gitlabMergeRequestReceiver.objectAttributes?.title}>",
                                        type = MARKDOWN_TYPE
                                    )
                                ),
                                SlackBodyRequest.Block(
                                    type = SECTION_TYPE, fields = listOf(
                                        SlackBodyRequest.Block.Field(
                                            text = "*Status:*\n${gitlabMergeRequestReceiver.eventType}",
                                            type = MARKDOWN_TYPE
                                        ),
                                        SlackBodyRequest.Block.Field(
                                            text = "*Created by:*\n<https://gitlab.com/${gitlabMergeRequestReceiver.user?.username}|${gitlabMergeRequestReceiver.user?.name}>",
                                            type = MARKDOWN_TYPE
                                        ),
                                    )
                                ),
                                SlackBodyRequest.Block(
                                    type = SECTION_TYPE,
                                    text = SlackBodyRequest.Block.Text(
                                        text = "*Description:*\n${gitlabMergeRequestReceiver.objectAttributes?.description}",
                                        type = MARKDOWN_TYPE
                                    ),
                                    accessory = SlackBodyRequest.Block.Accessory(
                                        type = IMAGE_TYPE,
                                        imageUrl = gitlabMergeRequestReceiver.user?.avatarUrl,
                                        altText = "avatar"
                                    )
                                )
                            )
                        )
                        body = requestBody
                    }
                }
                response.await().let {
                    if (it.contains("ok")) {
                        call.respond(HttpStatusCode.Accepted, Message(it).toJson())
                    } else {
                        call.respond(HttpStatusCode.BadRequest, Message(it).toJson())
                    }
                }
            }.getOrElse {
                call.respond(HttpStatusCode.BadRequest, Message(it.message.orEmpty()).toJson())
            }
            client.close()
        }
    }
}

fun createClient(): HttpClient = HttpClient(CIO) {
    install(Logging) {
        logger = Logger.DEFAULT
        level = LogLevel.ALL
    }
    install(JsonFeature) {
        serializer = KotlinxSerializer()
    }
}