package es.jvbabi.authentikt.core.utils

import com.google.gson.Gson
import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText

private val gson = Gson()

suspend fun ApplicationCall.respondGson(value: Any) {
    respondText(gson.toJson(value), ContentType.Application.Json)
}
