package tech.figure.eventstream.adapter.json.decoder

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.io.EOFException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.reflect.KClass

class MoshiDecoderEngine(private val moshi: Moshi) : DecoderEngine {
    private fun <T> moshiAdapter(adapter: JsonAdapter<T>): Adapter<T> {
        return object : Adapter<T> {
            override fun toJson(item: T): String = adapter.toJson(item)

            override fun fromJson(json: String): T? =
                try {
                    adapter.fromJson(json)
                } catch (e: JsonDataException) {
                    throw DecoderDataException(e)
                } catch (e: JsonEncodingException) {
                    throw DecoderEncodingException(e)
                } catch (e: EOFException) {
                    throw DecoderDataException(e)
                }
        }
    }

    override fun <T : Any> adapter(clazz: KClass<T>): Adapter<T> = moshiAdapter(moshi.adapter(clazz.java))

    override fun <T : Any> adapter(type: Type): Adapter<T> = moshiAdapter(moshi.adapter(type))

    override fun parameterizedType(rawType: Type, vararg typeArgs: Type): ParameterizedType {
        return Types.newParameterizedType(rawType, *typeArgs)
    }
}
