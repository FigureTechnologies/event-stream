package io.provenance.eventstream.adapter.json.decoder

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.reflect.KClass

open class DecoderException(ex: Exception) : Exception(ex)

interface Adapter<T> {
    fun toJson(item: T): String
    fun fromJson(json: String): T?
}

interface DecoderEngine {
    fun <T : Any> adapter(clazz: KClass<T>): Adapter<T>
    fun <T : Any> adapter(type: Type): Adapter<T>

    fun <T : Any> parameterizedType(rawType: Type, vararg typeArgs: Type): ParameterizedType
}

inline fun <reified T : Any> DecoderEngine.adapter(): Adapter<T> = adapter(T::class)