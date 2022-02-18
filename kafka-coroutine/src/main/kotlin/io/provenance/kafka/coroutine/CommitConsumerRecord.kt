package io.provenance.kafka.coroutine

import java.time.Duration
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition

interface CommitConsumerRecord {
    val duration: Duration
    val topicPartition: TopicPartition
    val offsetAndMetadata: OffsetAndMetadata

    fun asCommitable(): Map<TopicPartition, OffsetAndMetadata> = mapOf(topicPartition to offsetAndMetadata)
}

class CommitConsumerRecordImpl(
    override val duration: Duration,
    override val topicPartition: TopicPartition,
    override val offsetAndMetadata: OffsetAndMetadata
) : CommitConsumerRecord
