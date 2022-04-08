package io.provenance.eventstream.stream

import io.provenance.eventstream.stream.infrastructure.Serializer.moshi
import io.provenance.eventstream.stream.models.StreamBlockImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import org.apache.kafka.common.errors.SerializationException
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.io.InputStream
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import java.io.ObjectInputFilter
import java.io.IOException

fun Flow<KafkaStreamBlock>.acking(block: (KafkaStreamBlock) -> Unit): Flow<AckedKafkaStreamBlock<ByteArray, ByteArray>> {
    return flow {
        collect {
            block(it)
            emit(AckedKafkaStreamBlock(it.ack()))
        }
    }
}

fun StreamBlockImpl.toByteArray(): ByteArray? {
    return try {
        moshi.adapter(StreamBlockImpl::class.java)
            .toJson(this)
            .toByteArray()
    } catch (e: Exception) {
        throw SerializationException(e)
    }
}

fun ByteArray.toStreamBlock(): StreamBlockImpl? {
    return try {
        moshi.adapter(StreamBlockImpl::class.java)
            .fromJson(this.decodeToString())
    } catch (e: Exception) {
        throw SerializationException(e)
    }
}

// Convert object to byte[]
@Throws(IOException::class)
fun convertObjectToBytes2(obj: Any?): ByteArray? {
    val boas = ByteArrayOutputStream()
    ObjectOutputStream(boas).use { ois ->
        ois.writeObject(obj)
        return boas.toByteArray()
    }
}

// Convert byte[] to object
fun convertBytesToObject(bytes: ByteArray?): Any? {
    val `is`: InputStream = ByteArrayInputStream(bytes)
    try {
        ObjectInputStream(`is`).use { ois -> return ois.readObject() }
    } catch (ioe: IOException) {
        ioe.printStackTrace()
    } catch (ioe: ClassNotFoundException) {
        ioe.printStackTrace()
    }
    throw RuntimeException()
}

// Convert byte[] to object
@Throws(IOException::class, ClassNotFoundException::class)
fun convertBytesToObject2(bytes: ByteArray?): Any? {
    val `is`: InputStream = ByteArrayInputStream(bytes)
    ObjectInputStream(`is`).use { ois -> return ois.readObject() }
}

// Convert byte[] to object with filter
fun convertBytesToObjectWithFilter(bytes: ByteArray?, filter: ObjectInputFilter?): StreamBlockImpl? {
    val `is`: InputStream = ByteArrayInputStream(bytes)
    try {
        ObjectInputStream(`is`).use { ois ->

            // add filter before readObject
            ois.objectInputFilter = filter
            return ois.readObject() as StreamBlockImpl
        }
    } catch (ioe: IOException) {
        ioe.printStackTrace()
    } catch (ioe: ClassNotFoundException) {
        ioe.printStackTrace()
    }
    throw RuntimeException()
}
