package io.provenance.eventstream.flow.kafka

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

suspend fun <T> Future<T>.asDeferred(timeout: Duration? = null, coroutineContext: CoroutineContext = Dispatchers.IO): Deferred<T> {
    return withContext(coroutineContext) {
        async {
            if (timeout == null) get()
            else get(timeout.toMillis(), TimeUnit.MILLISECONDS)
        }
    }
}

@OptIn(ExperimentalStdlibApi::class)
class KafkaSink<K, V>(
    producerProps: Map<String, Any>,
    val topicName: String,
    val kafkaProducer: Producer<K, V> = KafkaProducer(producerProps)
) {

    suspend fun send(block: V, key: K) {
        sendHelper(block, key).asDeferred().await()
    }

    private fun sendHelper(block: V, key: K): Future<RecordMetadata> {
        return kafkaProducer.send(ProducerRecord(topicName, key, block))
    }


//    override suspend fun invoke(block: StreamBlock) {
//        val key = "${block.block.header!!.chainId}.${block.height}"
//        send(block as StreamBlockImpl, key, kafkaProducer).asDeferred().await()
//    }
}
