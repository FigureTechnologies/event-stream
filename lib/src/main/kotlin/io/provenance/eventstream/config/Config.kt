package io.provenance.eventstream.config

import com.sksamuel.hoplite.ConfigAlias
import kotlinx.coroutines.flow.DEFAULT_CONCURRENCY

// Data classes in this file are intended to be instantiated by the hoplite configuration library

data class StreamEventsFilterConfig(
    @ConfigAlias("tx_events") val txEvents: Set<String> = emptySet(),
    @ConfigAlias("block_events") val blockEvents: Set<String> = emptySet()
) {
    companion object {
        val empty: StreamEventsFilterConfig get() = StreamEventsFilterConfig()
    }
}

data class BatchConfig(
    val size: Int,
    @ConfigAlias("timeout_ms") val timeoutMillis: Long?,
)

data class EventStreamConfig(
    val batch: BatchConfig,
    val concurrency: Int = DEFAULT_CONCURRENCY,
    val filter: StreamEventsFilterConfig = StreamEventsFilterConfig.empty
)

data class UploadConfig(
    val extractors: List<String> = emptyList()
) {
    companion object {
        val empty: UploadConfig get() = UploadConfig()
    }
}

data class Config(
    val node: String,
    val from: Long?,
    val to: Long?,
    val batch_size: Int = 16,
    val skip_empty_blocks: Boolean = false,
    val ordered: Boolean = false,
    val wsThrottleDurationMs: Long = 0,
    @ConfigAlias("event-stream") val eventStream: EventStreamConfig,
)
