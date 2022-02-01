package kafka

import com.squareup.moshi.*
import io.provenance.eventstream.stream.infrastructure.Serializer.moshi
import io.provenance.eventstream.stream.models.StreamBlockImpl
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Deserializer

open class KafkaDeserializer
    : Deserializer<StreamBlockImpl> {

    override fun deserialize(ignored: String, bytes: ByteArray): StreamBlockImpl? {
        return if (bytes == null || bytes.size == 0) {
            null
        } else try {
            val jsonAdapter: JsonAdapter<StreamBlockImpl> = moshi.adapter(StreamBlockImpl::class.java)

            jsonAdapter.fromJson(bytes.decodeToString())
        } catch (e: Exception) {
            throw SerializationException(e)
        }
    }

    override fun close() {}
}