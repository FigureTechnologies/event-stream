package io.provenance.eventstream.net

import io.provenance.eventstream.WsAdapter
import io.provenance.eventstream.stream.clients.BlockFetcher

interface NetAdapter {
    val wsAdapter: WsAdapter
    val rpcAdapter: BlockFetcher
}