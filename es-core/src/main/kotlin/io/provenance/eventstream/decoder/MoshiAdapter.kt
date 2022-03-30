package io.provenance.eventstream.decoder

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.tinder.scarlet.messageadapter.moshi.MoshiMessageAdapter
import io.provenance.eventstream.adapter.json.JSONObjectAdapter
import io.provenance.eventstream.adapter.json.decoder.MoshiDecoderEngine
import io.provenance.eventstream.stream.decoder.DecoderAdapter
import io.provenance.eventstream.stream.decoder.decoderAdapter

/**
 * Create the default [Moshi] JSON serializer/deserializer.
 *
 * @return The [Moshi] instance to use for the event stream.
 */
fun defaultMoshi(): Moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .add(JSONObjectAdapter())
    .build()

/**
 * Create the [Moshi] flavor of the required [DecoderAdapter] fields.
 *
 * @param moshi The [Moshi] instance to use under the hood for json conversion.
 */
fun moshiDecoderAdapter(moshi: Moshi = defaultMoshi()): DecoderAdapter =
    decoderAdapter(
        MoshiDecoderEngine(moshi),
        MoshiMessageAdapter.Factory(moshi)::create,
    )
