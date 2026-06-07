package es.jvbabi.authentikt.core.utils

import com.google.gson.Gson
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText

private val gson = Gson()

suspend fun ApplicationCall.respondGson(value: Any, status: HttpStatusCode = HttpStatusCode.OK) {
    respondText(
        text = gson.toJson(value),
        contentType = ContentType.Application.Json,
        status = status,
    )
}
