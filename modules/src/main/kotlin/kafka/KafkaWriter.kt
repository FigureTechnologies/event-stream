package kafka

import io.provenance.blockchain.stream.api.BlockSink
import io.provenance.eventstream.stream.models.StreamBlock
import mu.KotlinLogging
import org.apache.kafka.clients.producer.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

fun kafkaWriter(producer: Producer<String, StreamBlock>, topicName: String, key: String) = KafkaWriter(producer, topicName, key)

suspend inline fun <reified K : Any, reified V : Any> Producer<K, V>.dispatch(record: ProducerRecord<K, V>) =
    suspendCoroutine<RecordMetadata> { continuation ->
        val callback = Callback { m: RecordMetadata, e: Exception? ->
            val log = KotlinLogging.logger { }
            when (e) {
                    null -> log.info("Produced record to topic ${m.topic()} partition [${m.partition()}] @ offset ${m.offset()}")
                    else -> e.printStackTrace()
                }
            }
        this.send(record, callback)
    }

@OptIn(ExperimentalStdlibApi::class)
class KafkaWriter(
    val producer: Producer<String, StreamBlock>,
    val topicName: String,
    val key: String
) : BlockSink {
    override suspend fun invoke(block: StreamBlock) {
        producer.use { producer ->
            producer.dispatch(ProducerRecord(topicName, key, block))
            producer.flush()
        }
    }
}
