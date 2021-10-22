package io.provenance.eventstream.test.utils

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import org.apache.commons.text.StringSubstitutor
import org.apache.commons.text.io.StringSubstitutorReader
import java.io.File
import java.io.FileReader
import java.net.URL

class MissingTemplate(name: String) : Exception("Template $name not found")
class MissingTemplateDirectory(name: String) : Exception("Template directory $name not found")

data class Template(private val moshi: Moshi) {

    fun read(filename: String, vars: Map<String, Any> = emptyMap()): String {
        val file = try {
            this.javaClass.classLoader.getResource("templates/" + filename).file
        } catch (e: Exception) {
            throw MissingTemplate(filename)
        }
        return StringSubstitutorReader(FileReader(file), StringSubstitutor(vars)).readText()
    }

    fun readAll(directory: String, vars: Map<String, Any> = emptyMap()): Sequence<String> {
        val url: URL = try {
            object {}.javaClass.classLoader.getResource("templates/$directory")
        } catch (e: Exception) {
            throw MissingTemplateDirectory(directory)
        }
        val base = File(url.toURI())
        return sequence {
            base.walk().forEach {
                val filename = it.toRelativeString(base)
                if (!filename.isNullOrEmpty()) {
                    yield(read("$directory/$filename", vars))
                }
            }
        }
    }

    fun <T> readAs(clazz: Class<T>, filename: String, vars: Map<String, Any> = emptyMap()): T? {
        val contents: String = read(filename, vars)
        val adapter: JsonAdapter<T> = moshi.adapter(clazz)
        return adapter.fromJson(contents)
    }

    fun <T> unsafeReadAs(clazz: Class<T>, filename: String, vars: Map<String, Any> = emptyMap()): T =
        readAs(clazz, filename, vars)!!
}
