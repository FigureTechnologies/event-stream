package tech.figure.eventstream.utils

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import org.apache.commons.text.StringSubstitutor
import org.apache.commons.text.io.StringSubstitutorReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URL
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class MissingTemplate(name: String) : Exception("Template $name not found")
class MissingTemplateDirectory(name: String) : Exception("Template directory $name not found")

data class Template(private val moshi: Moshi) {

    /**
     * Read a file using the given [filename], substituting variables using [vars], returning the substituted contents.
     *
     * @param filename The file to load.
     * @param vars The variable substitution map.
     * @return The substituted text.
     */
    fun read(filename: String, vars: Map<String, Any> = emptyMap()): String {
        val stream: InputStream = try {
            this.javaClass.classLoader.getResourceAsStream("templates/$filename")
        } catch (e: Exception) {
            throw MissingTemplate(filename)
        }
        return InputStreamReader(stream).use {
            StringSubstitutorReader(it, StringSubstitutor(vars)).readText()
        }
    }

    /**
     * Read all templates in the given [directory], substituting variables using [vars], yielding a sequence
     * of files with substituted contents.
     *
     * See https://stackoverflow.com/a/42632720
     *
     * @param directory The directory to load files from.
     * @param vars The variable substitution map.
     * @return A sequence of files containing substituted text.
     */
    fun readAll(directory: String, vars: Map<String, Any> = emptyMap()): Sequence<String> {
        // Get the location of this JAR for resolving files against:
        val clazz = object {}.javaClass
        val jarUrl: URL = clazz.protectionDomain.codeSource.location
        val jarPath: Path = Paths.get(jarUrl.toString().substring("file:".length))
        val fs = FileSystems.newFileSystem(jarPath, null)
        val dirStream = Files.newDirectoryStream(fs.getPath("templates/$directory"))

        return sequence {
            dirStream.forEach {
                yield(read("$directory/${it.fileName}", vars))
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
