package tech.figure.eventstream.adapter.json.decoder

import com.tinder.scarlet.Message
import tech.figure.eventstream.requireType
import tech.figure.eventstream.stream.rpc.response.MessageType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.reflect.KClass

open class DecoderException(ex: Throwable) : Exception(ex)
class DecoderEncodingException(ex: Throwable) : DecoderException(ex)
class DecoderDataException(ex: Throwable) : DecoderException(ex)

typealias MessageDecoder = (Message) -> MessageType

interface Adapter<T> {
    fun toJson(item: T): String

    @Throws(DecoderException::class)
    fun fromJson(json: String): T?
}

interface DecoderEngine {
    fun <T : Any> adapter(clazz: KClass<T>): Adapter<T>
    fun <T : Any> adapter(type: Type): Adapter<T>

    fun parameterizedType(rawType: Type, vararg typeArgs: Type): ParameterizedType

    /**
     * Decode a [Message] into a [MessageType] using this [DecoderEngine] instance.
     */
    fun toMessageDecoder(): MessageDecoder {
        val decoder = MessageType.Decoder(this)
        return { message ->
            requireType<Message.Text>(message) { "invalid type:${message::class.simpleName}" }.let {
                decoder.decode(it.value)
            }
        }
    }
}

inline fun <reified T : Any> DecoderEngine.adapter(): Adapter<T> = adapter(T::class)
