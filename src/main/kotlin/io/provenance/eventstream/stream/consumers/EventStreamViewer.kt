package io.provenance.eventstream.stream.consumers

import io.provenance.eventstream.stream.models.StreamBlock

typealias BlockSink = (StreamBlock) -> Unit

fun blockSink(action: BlockSink): BlockSink = action