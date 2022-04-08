package io.provenance.eventstream.stream

import java.io.File
import java.util.concurrent.atomic.AtomicLong

abstract class Checkpoint(open val checkEvery: Long = 20) {
    abstract fun checkpoint(at: Long)
    abstract fun lastCheckpoint(): Long?
}

class InMemoryCheckpoint(override val checkEvery: Long = 20) : Checkpoint(checkEvery) {
    private val currentBlock = AtomicLong(0)

    override fun checkpoint(at: Long) = currentBlock.set(at)
    override fun lastCheckpoint(): Long? = currentBlock.get().let {
        if (it == 0L) { null } else { it }
    }
}

class FileCheckpoint(override val checkEvery: Long = 20) : Checkpoint(checkEvery) {
    private val filename = "./checkpoint.txt"

    override fun checkpoint(at: Long) {
        File(filename).writeText(at.toString())
    }

    override fun lastCheckpoint(): Long? = with(File(filename)) {
        if (exists()) {
            readLines().first().toLong()
        } else {
            null
        }
    }
}
