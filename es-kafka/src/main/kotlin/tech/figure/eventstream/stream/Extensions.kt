package tech.figure.eventstream.stream

import tech.figure.eventstream.stream.infrastructure.Serializer.moshi
import tech.figure.eventstream.stream.models.StreamBlockImpl
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
    return try {
        moshi.adapter(StreamBlockImpl::class.java)
            .toJson(this)
            .toByteArray()
    } catch (e: Exception) {
        throw SerializationException(e)
    }
}

fun ByteArray.toStreamBlock(): StreamBlockImpl? {
    return try {
        moshi.adapter(StreamBlockImpl::class.java)
            .fromJson(this.decodeToString())
    } catch (e: Exception) {
        throw SerializationException(e)
    }
}
