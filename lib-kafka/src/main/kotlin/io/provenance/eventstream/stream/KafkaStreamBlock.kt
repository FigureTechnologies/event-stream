package io.provenance.eventstream.stream

import io.provenance.eventstream.stream.models.Block
import io.provenance.eventstream.stream.models.BlockEvent
import io.provenance.eventstream.stream.models.StreamBlock
import io.provenance.eventstream.stream.models.TxEvent
import org.apache.kafka.clients.consumer.ConsumerRecord

data class KafkaStreamBlock<K, V>(
    val record: ConsumerRecord<K, V>,
) : StreamBlock {
    override val block: Block by lazy { TODO() }
    override val blockEvents: List<BlockEvent> by lazy { TODO() }
    override val txEvents: List<TxEvent> by lazy { TODO() }
    override val historical: Boolean by lazy { TODO() }
}