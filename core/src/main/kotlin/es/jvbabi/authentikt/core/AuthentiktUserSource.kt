package es.jvbabi.authentikt.core

interface AuthentiktUserSource<USER> {
    suspend fun findUserByEmail(email: String): AuthentiktUser<USER>?
}