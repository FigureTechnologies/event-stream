package io.provenance.eventstream.stream

import io.provenance.blockchain.stream.api.BlockSink
import io.provenance.eventstream.serializers.KafkaSerializer
import io.provenance.eventstream.stream.models.StreamBlockImpl
import io.provenance.eventstream.stream.models.StreamBlock
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.apache.kafka.clients.producer.*
import org.apache.kafka.common.serialization.StringSerializer
import java.time.Duration
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

fun kafkaWriter(producerProps: Map<String, Any>, topicName: String): KafkaWriter =
    KafkaWriter(producerProps, topicName)

suspend fun <T> Future<T>.asDeferred(timeout: Duration? = null, coroutineContext: CoroutineContext = Dispatchers.IO): Deferred<T> {
    return withContext(coroutineContext) {
        async {
            if (timeout == null) get()
            else get(timeout.toMillis(), TimeUnit.MILLISECONDS)
        }
    }
}

@OptIn(ExperimentalStdlibApi::class)
class KafkaWriter(
    producerProps: Map<String, Any>,
    val topicName: String,
) : BlockSink {
    private val serializationProps = mapOf<String, Any>(
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::javaClass,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to KafkaSerializer::javaClass,
    )
    val kafkaProducer = KafkaProducer<String, StreamBlockImpl>(producerProps + serializationProps)

    fun send(block: StreamBlockImpl, key: String): Future<RecordMetadata> {
        return kafkaProducer.send(ProducerRecord(topicName, key, block))
    }

    override suspend fun invoke(block: StreamBlock) {
        val key = "${block.block.header!!.chainId}.${block.height}"
        send(block as StreamBlockImpl, key).asDeferred().await()
    }
}
