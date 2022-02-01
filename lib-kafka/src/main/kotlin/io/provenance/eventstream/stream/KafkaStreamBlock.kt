package io.provenance.eventstream.stream

import io.provenance.eventstream.stream.models.*
import org.apache.kafka.clients.consumer.ConsumerRecord

data class KafkaStreamBlock<K, V>(
    val record: ConsumerRecord<K, V>,
) : StreamBlock {
    override val block: Block by lazy { (record.value() as StreamBlockImpl).block }
    override val blockEvents: List<BlockEvent> by lazy { (record.value() as StreamBlockImpl).blockEvents }
    override val txEvents: List<TxEvent> by lazy { (record.value() as StreamBlockImpl).txEvents }
    override val historical: Boolean by lazy { (record.value() as StreamBlockImpl).historical }
}