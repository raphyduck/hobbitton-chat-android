package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.scheduler.ChatBudget
import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFailsWith

/** `/plafonds` (12/10/2026), the conversations' ceilings, pinned at the request level. */
class SchedulerChatBudgetTest {

    @Test
    fun `the ceilings are read from the scheduler's plain route`() = runTest {
        var method: HttpMethod? = null
        var path: String? = null
        val api = schedulerApi(MockEngine { request ->
            method = request.method
            path = request.url.encodedPath
            respond(content = """{"jour":15.0,"semaine":45.0,"mois":200.0}""", headers = jsonHeaders())
        })

        assertThat(api.chatBudget()).isEqualTo(ChatBudget(day = 15.0, week = 45.0, month = 200.0))
        assertThat(method).isEqualTo(HttpMethod.Get)
        assertThat(path).isEqualTo("/plafonds")
    }

    @Test
    fun `a scheduler from before the route answers no ceilings rather than an error`() = runTest {
        val api = schedulerApi(MockEngine { respond(content = "Not Found", status = HttpStatusCode.NotFound) })

        assertThat(api.chatBudget()).isNull()
    }

    @Test
    fun `setting them posts the three amounts and answers what the gateway applies`() = runTest {
        var method: HttpMethod? = null
        var sent = ""
        val api = schedulerApi(MockEngine { request ->
            method = request.method
            sent = String(request.body.toByteArray())
            respond(content = """{"jour":20.0,"semaine":50.0,"mois":180.5}""", headers = jsonHeaders())
        })

        val applied = api.setChatBudget(day = 20.0, week = 50.0, month = 180.5)

        assertThat(method).isEqualTo(HttpMethod.Post)
        assertThat(sent).isEqualTo("""{"jour":20.0,"semaine":50.0,"mois":180.5}""")
        assertThat(applied).isEqualTo(ChatBudget(day = 20.0, week = 50.0, month = 180.5))
    }

    @Test
    fun `a refusal carries its status and the scheduler's own words`() = runTest {
        val api = schedulerApi(MockEngine {
            respond(
                content = """{"erreur":"le plafond du jour dépasse celui de la semaine"}""",
                status = HttpStatusCode.BadRequest,
                headers = jsonHeaders(),
            )
        })

        val refused = assertFailsWith<ChatBudgetRefused> { api.setChatBudget(day = 50.0, week = 45.0, month = 200.0) }

        assertThat(refused.status).isEqualTo(400)
        assertThat(refused.reason).isEqualTo("le plafond du jour dépasse celui de la semaine")
    }

    @Test
    fun `an answer that is not ceilings is a refusal without words`() = runTest {
        val api = schedulerApi(MockEngine { respond(content = "<html>portail</html>", status = HttpStatusCode.OK) })

        val refused = assertFailsWith<ChatBudgetRefused> { api.chatBudget() }

        assertThat(refused.reason).isNull()
    }
}
