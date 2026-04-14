package es.jvbabi.authentikt.core

abstract class AuthentiktUser {
    abstract suspend fun getEmail(): String?
    abstract suspend fun getUsername(): String?
}