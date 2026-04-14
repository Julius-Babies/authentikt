package es.jvbabi.authentikt.core

interface AuthentiktUserSource {
    suspend fun findUserByEmail(email: String): AuthentiktUser?
}