package io.provenance.eventstream.flow.kafka

import com.squareup.moshi.JsonAdapter
import io.provenance.eventstream.stream.AckedKafkaStreamBlock
import io.provenance.eventstream.stream.KafkaStreamBlock
import io.provenance.eventstream.stream.infrastructure.Serializer.moshi
import io.provenance.eventstream.stream.models.StreamBlockImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import org.apache.kafka.common.errors.SerializationException

internal fun <T, L : Iterable<T>> L.ifEmpty(block: () -> L): L = if (count() == 0) block() else this

fun Flow<KafkaStreamBlock<String, StreamBlockImpl>>.acking(block: (KafkaStreamBlock<String, StreamBlockImpl>) -> Unit): Flow<AckedKafkaStreamBlock<String, StreamBlockImpl>> {
    return flow {
        collect {
            val ackedConsumerRecordImpl = it.record.ack()
            emit(AckedKafkaStreamBlock(ackedConsumerRecordImpl))
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