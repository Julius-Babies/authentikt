package es.jvbabi.authentikt.core

abstract class AuthentiktUser<USER>(val user: USER) {

    abstract suspend fun getEmail(): String?
    abstract suspend fun getUsername(): String?
    abstract suspend fun getDisplayName(): String?
}