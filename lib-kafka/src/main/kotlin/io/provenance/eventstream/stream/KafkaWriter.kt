package io.provenance.eventstream.stream

import io.provenance.blockchain.stream.api.BlockSink
import io.provenance.eventstream.flow.kafka.toByteArray
import io.provenance.eventstream.stream.models.StreamBlockImpl
import io.provenance.eventstream.stream.models.StreamBlock
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.*
import org.apache.kafka.common.serialization.Serdes
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
    private val serializer = Serdes.ByteArray().serializer()
    private val byteArrayProps = mapOf<String, Any>(
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to serializer.javaClass,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to serializer.javaClass,
    )

    val kafkaProducer = KafkaProducer<ByteArray, ByteArray>(producerProps + byteArrayProps)

    fun send(block: StreamBlockImpl, key: String): Future<RecordMetadata> {
        return kafkaProducer.send(ProducerRecord(topicName, key.toByteArray(), block.toByteArray()))
    }

    override suspend fun invoke(block: StreamBlock) {
        val key = "${block.block.header!!.chainId}.${block.height}"
        send(block as StreamBlockImpl, key).asDeferred().await()
    }
}
