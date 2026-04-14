package es.jvbabi.authentikt.core.utils

inline fun buildGenericMap(block: MutableMap<String, Any?>.() -> Unit): Map<String, Any?> {
    return buildMap(block).toMap()
}