package io.provenance.eventstream.stream

import com.squareup.moshi.JsonAdapter
import io.provenance.eventstream.stream.infrastructure.Serializer.moshi
import io.provenance.eventstream.stream.models.StreamBlockImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import org.apache.kafka.common.errors.SerializationException

fun Flow<KafkaStreamBlock>.acking(block: (KafkaStreamBlock) -> Unit): Flow<AckedKafkaStreamBlock<ByteArray, ByteArray>> {
    return flow {
        collect {
            block(it)
            emit(AckedKafkaStreamBlock(it.ack()))
        }
    }
}

fun StreamBlockImpl.toByteArray(): ByteArray? {
    return if (this == null) {
        return ByteArray(0)
    } else try {
        val jsonAdapter: JsonAdapter<StreamBlockImpl> = moshi.adapter(StreamBlockImpl::class.java)

        jsonAdapter.toJson(this).toByteArray()
    } catch (e: Exception) {
        throw SerializationException(e)
    }
}

fun ByteArray.toStreamBlock(): StreamBlockImpl? {
    return if (this == null || this.size == 0) {
        null
    } else try {
        val jsonAdapter: JsonAdapter<StreamBlockImpl> = moshi.adapter(StreamBlockImpl::class.java)

        jsonAdapter.fromJson(this.decodeToString())
    } catch (e: Exception) {
        throw SerializationException(e)
    }
}
