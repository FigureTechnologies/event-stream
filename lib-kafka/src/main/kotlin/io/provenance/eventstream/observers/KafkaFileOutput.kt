package io.provenance.eventstream.observers

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import io.provenance.blockchain.stream.api.BlockSink
import io.provenance.eventstream.stream.models.StreamBlock
import io.provenance.eventstream.stream.models.StreamBlockImpl
import io.provenance.eventstream.stream.models.extensions.sha256
import java.io.File
import java.math.BigInteger

fun kafkaFileOutput(dir: String, decoder: Moshi): KafkaFileOutput = KafkaFileOutput(dir, decoder)

@OptIn(ExperimentalStdlibApi::class)
class KafkaFileOutput(dir: String, decoder: Moshi) : BlockSink {
    private val adapter: JsonAdapter<StreamBlockImpl> = decoder.adapter()
    private val dirname = { name: String -> "$dir/$name" }

    init {
        File(dir).mkdirs()
    }

    override suspend fun invoke(block: StreamBlock) {
        val checksum = sha256(block.height.toString().toByteArray()).toHex()
        val splay = checksum.take(4)
        val dirname = dirname(splay)

        File(dirname).let { f -> if (!f.exists()) f.mkdirs() }

        val filename = "$dirname/${block.height.toString().padStart(10, '0')}.json"
        val file = File(filename)
        if (!file.exists()) {
            file.writeText(adapter.toJson(block.toStreamBlockImpl()))
        }
    }
}

private fun StreamBlock.toStreamBlockImpl(): StreamBlockImpl? {
    return StreamBlockImpl(this.block, this.blockEvents, this.txEvents, this.historical)
}

private fun ByteArray.toHex(): String {
    val bi = BigInteger(1, this)
    return String.format("%0" + (this.size shl 1) + "X", bi)
}
