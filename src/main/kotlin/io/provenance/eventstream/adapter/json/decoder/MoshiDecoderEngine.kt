package io.provenance.eventstream.adapter.json.decoder

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.reflect.KClass

class MoshiDecoderEngine(private val moshi: Moshi) : DecoderEngine {
    override fun <T : Any> adapter(clazz: KClass<T>): Adapter<T> {
        return object : Adapter<T> {
            private val adapter = moshi.adapter(clazz.java)
            override fun toJson(item: T): String = adapter.toJson(item)
            override fun fromJson(json: String): T? = adapter.fromJson(json)
        }
    }

    override fun <T : Any> adapter(type: Type): Adapter<T> {
        return object : Adapter<T> {
            private val adapter = moshi.adapter<T>(type)
            override fun toJson(item: T): String = adapter.toJson(item)
            override fun fromJson(json: String): T? = adapter.fromJson(json)
        }
    }

    override fun <T : Any> parameterizedType(rawType: Type, vararg typeArgs: Type): ParameterizedType {
        return Types.newParameterizedType(rawType, *typeArgs)
    }
}