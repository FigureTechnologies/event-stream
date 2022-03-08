package io.provenance.eventstream.test.utils

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.provenance.eventstream.adapter.json.JSONObjectAdapter
import io.provenance.eventstream.adapter.json.decoder.DecoderEngine
import io.provenance.eventstream.adapter.json.decoder.MoshiDecoderEngine
import io.provenance.eventstream.stream.models.BlockResponse
import io.provenance.eventstream.stream.models.BlockResultsResponse
import io.provenance.eventstream.stream.models.BlockchainResponse

object Defaults {

    val moshi: Moshi = newMoshi()

    private fun newMoshi(): Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .add(JSONObjectAdapter())
        .build()

    fun decoderEngine() = MoshiDecoderEngine(moshi)

    val templates = newTemplate()

    fun newTemplate(): Template = Template(moshi)

    fun blockResponses(): Array<BlockResponse> =
        heights
            .map { templates.unsafeReadAs(BlockResponse::class.java, "block/$it.json") }
            .toTypedArray()

    fun blockResultsResponses(): Array<BlockResultsResponse> =
        heights
            .map { templates.unsafeReadAs(BlockResultsResponse::class.java, "block_results/$it.json") }
            .toTypedArray()

    fun blockchainResponses(): Array<BlockchainResponse> =
        heightChunks
            .map { (minHeight, maxHeight) ->
                templates.unsafeReadAs(
                    BlockchainResponse::class.java,
                    "blockchain/$minHeight-$maxHeight.json"
                )
            }
            .toTypedArray()
}
