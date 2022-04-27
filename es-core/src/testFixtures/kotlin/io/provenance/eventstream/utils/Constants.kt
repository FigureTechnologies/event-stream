package io.provenance.eventstream.test.utils

import io.provenance.eventstream.stream.EventStream

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

const val EXPECTED_LIVE_TOTAL_BLOCK_COUNT = (MAX_LIVE_BLOCK_HEIGHT - MIN_LIVE_BLOCK_HEIGHT) + 1

const val EXPECTED_TOTAL_BLOCKS = (MAX_HISTORICAL_BLOCK_HEIGHT - MIN_HISTORICAL_BLOCK_HEIGHT) + 1

const val EXPECTED_NONEMPTY_BLOCKS: Long = 29

const val EXPECTED_EMPTY_BLOCKS = EXPECTED_TOTAL_BLOCKS - EXPECTED_NONEMPTY_BLOCKS

val heights: List<Long> = (MIN_HISTORICAL_BLOCK_HEIGHT..MAX_HISTORICAL_BLOCK_HEIGHT).toList()

const val COMBO_TOTAL_NONEMPTY_BLOCK_COUNT = EXPECTED_LIVE_TOTAL_BLOCK_COUNT + EXPECTED_TOTAL_BLOCKS

val heightChunks: List<Pair<Long, Long>> = heights
    .chunked(EventStream.TENDERMINT_MAX_QUERY_RANGE)
    .map { Pair(it.minOrNull()!!, it.maxOrNull()!!) }
