package io.provenance.eventstream.stream

import io.provenance.eventstream.stream.models.*
import org.apache.kafka.clients.consumer.ConsumerRecord
import io.provenance.eventstream.flow.kafka.UnAckedConsumerRecord
import io.provenance.eventstream.stream.models.Block
import io.provenance.eventstream.stream.models.BlockEvent
import io.provenance.eventstream.stream.models.StreamBlock
import io.provenance.eventstream.stream.models.TxEvent

data class KafkaStreamBlock<K, V>(
    val record: UnAckedConsumerRecord<K, V>,
) : StreamBlock {
    override val block: Block by lazy { (record.record.value() as StreamBlockImpl).block }
    override val blockEvents: List<BlockEvent> by lazy { (record.record.value() as StreamBlockImpl).blockEvents }
    override val txEvents: List<TxEvent> by lazy { (record.record.value() as StreamBlockImpl).txEvents }
    override val historical: Boolean by lazy { (record.record.value() as StreamBlockImpl).historical }
}