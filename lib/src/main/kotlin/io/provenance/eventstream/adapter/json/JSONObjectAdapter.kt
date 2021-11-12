package io.provenance.eventstream.adapter.json

import com.squareup.moshi.*
import okio.Buffer
import org.json.JSONObject

/**
 * An adapter for [Moshi] to deserialize JSON into [JSONObject] as a target
 *
 * Usage:
 *
 * ```
 * val moshi: Moshi = Moshi.Builder()
 *   .add(KotlinJsonAdapterFactory())
 *   .add(JSONObjectAdapter())
*    .build()
 * ```
 */
class JSONObjectAdapter : JsonAdapter<JSONObject>() {

    @FromJson
    override fun fromJson(reader: JsonReader): JSONObject? {
        val data = reader.readJsonValue() as? Map<*, *>
        return data?.run { JSONObject(this) }
    }

    @ToJson
    override fun toJson(writer: JsonWriter, value: JSONObject?) {
        value?.let {
            val b = Buffer().writeUtf8(it.toString())
            writer.jsonValue(b)
        }
    }
}