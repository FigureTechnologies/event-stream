package io.provenance.eventstream.test.utils

import io.provenance.eventstream.stream.EventStream
import kotlinx.coroutines.ExperimentalCoroutinesApi

/***********************************************************************************************************************
 * Streaming
 **********************************************************************************************************************/

/**
 * Minimum historical block height from block templates in `resources/templates/block`
 */
const val MIN_HISTORICAL_BLOCK_HEIGHT: Long = 2270370

/**
 * Maximum historical block height from block templates in `resources/templates/block`
 */
const val MAX_HISTORICAL_BLOCK_HEIGHT: Long = 2270469

/**
 * Minimum live block height from block templates in `resources/templates/live`
 */
const val MIN_LIVE_BLOCK_HEIGHT: Long = 3126935

/**
 * Maximum live block height from block templates in `resources/templates/live`
 */
const val MAX_LIVE_BLOCK_HEIGHT: Long = 3126940

const val EXPECTED_TOTAL_BLOCKS: Long = (MAX_HISTORICAL_BLOCK_HEIGHT - MIN_HISTORICAL_BLOCK_HEIGHT) + 1

const val EXPECTED_NONEMPTY_BLOCKS: Long = 29

const val EXPECTED_EMPTY_BLOCKS: Long = EXPECTED_TOTAL_BLOCKS - EXPECTED_NONEMPTY_BLOCKS

const val TENDERMINT_MAX_QUERY_RANGE = 20

const val BATCH_SIZE: Int = 4

val heightsRange = (MIN_HISTORICAL_BLOCK_HEIGHT..MAX_HISTORICAL_BLOCK_HEIGHT)
val heights: List<Long> = heightsRange.toList()

@OptIn(ExperimentalCoroutinesApi::class)
val heightChunks: List<Pair<Long, Long>> = heights
    .chunked(20)
    .map { Pair(it.minOrNull()!!, it.maxOrNull()!!) }

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun EventStream.streamBlocks() = streamBlocks(heightsRange.first, heightsRange.last)

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun EventStream.streamHistoricalBlocks() = streamHistoricalBlocks(heightsRange.first, heightsRange.last)