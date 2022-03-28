package io.provenance.eventstream

inline fun <reified T : Any> requireType(item: Any, block: () -> String): T {
    require(item is T) { block() }
    return item
}
