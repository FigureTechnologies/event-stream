package kafka

import io.provenance.blockchain.stream.api.BlockSink
import io.provenance.eventstream.stream.models.StreamBlock
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import java.time.Duration
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

fun kafkaWriter(producer: Producer<String, StreamBlock>, topicName: String) =
    KafkaWriter(producer, topicName)

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
    val producer: Producer<String, StreamBlock>,
    val topicName: String,
) : BlockSink {
    override suspend fun invoke(block: StreamBlock) {
        val key = "${block.block.header!!.chainId}.${block.height}"
        send(block, key).asDeferred().await()
    }

    fun send(block: StreamBlock, key: String): Future<RecordMetadata> {
        return producer.send(ProducerRecord(topicName, key, block))
    }
}
