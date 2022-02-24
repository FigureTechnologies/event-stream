package io.provenance.eventstream.utils

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Takes the current offset datetime, formatting it as an ISO8601 datetime string.
 */
fun timestamp(): String = OffsetDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME).toString()
