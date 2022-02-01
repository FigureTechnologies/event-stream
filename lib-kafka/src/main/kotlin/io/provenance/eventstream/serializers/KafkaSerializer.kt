package kafka

import org.apache.kafka.common.serialization.Serializer
import com.squareup.moshi.*
import io.provenance.eventstream.stream.infrastructure.Serializer.moshi
import io.provenance.eventstream.stream.models.StreamBlockImpl
import org.apache.kafka.common.errors.SerializationException

class KafkaSerializer
    : Serializer<StreamBlockImpl> {

    override fun close() {}
    override fun serialize(topic: String?, data: StreamBlockImpl?): ByteArray {
        return if (data == null) {
            return ByteArray(0)
        } else try {
            val jsonAdapter: JsonAdapter<StreamBlockImpl> = moshi.adapter(StreamBlockImpl::class.java)

            jsonAdapter.toJson(data).toByteArray()
        } catch (e: Exception) {
            throw SerializationException(e)
        }
    }
}