package io.provenance.eventstream.stream

import io.provenance.blockchain.stream.api.BlockSink
import io.provenance.eventstream.flow.kafka.toByteArray
import io.provenance.eventstream.stream.models.StreamBlockImpl
import io.provenance.eventstream.stream.models.StreamBlock
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.Serdes
import java.time.Duration
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

fun kafkaBlockSink(producerProps: Map<String, Any>, topicName: String): KafkaBlockSink =
    KafkaBlockSink(producerProps, topicName)

suspend fun <T> Future<T>.asDeferred(timeout: Duration? = null, coroutineContext: CoroutineContext = Dispatchers.IO): Deferred<T> {
    return withContext(coroutineContext) {
        async {
            if (timeout == null) get()
            else get(timeout.toMillis(), TimeUnit.MILLISECONDS)
        }
    }
}

@OptIn(ExperimentalStdlibApi::class)
class KafkaBlockSink(
    producerProps: Map<String, Any>,
    val topicName: String,
) : BlockSink {
    private val serializer = Serdes.ByteArray().serializer()
    private val byteArrayProps = mapOf<String, Any>(
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to serializer.javaClass,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to serializer.javaClass,
    )

    val kafkaProducer = KafkaProducer<ByteArray, ByteArray>(producerProps + byteArrayProps)

    fun send(block: StreamBlockImpl, key: String, producer: Producer<ByteArray, ByteArray>): Future<RecordMetadata> {
        return producer.send(ProducerRecord(topicName, key.toByteArray(), block.toByteArray()))
    }

    override suspend fun invoke(block: StreamBlock) {
        val key = "${block.block.header!!.chainId}.${block.height}"
        send(block as StreamBlockImpl, key, kafkaProducer).asDeferred().await()
    }
}
