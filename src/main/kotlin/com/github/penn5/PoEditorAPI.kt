package com.github.penn5

import groovy.json.JsonSlurper
import java.lang.RuntimeException
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*

class PoEditorAPI(private val apiToken: String) {
    fun getProject(id: Int): PoEditorProject {
        return PoEditorProject.fromJson(post("projects/view",
                mutableMapOf("id" to id.toString()))["project"] as Map<*, *>, this)
    }

    private fun utf8(string: String): String = java.net.URLEncoder.encode(string, "UTF-8")


    internal fun post(endpoint: String, args: MutableMap<String, String>): Map<*, *> {
        args["api_token"] = this.apiToken
        val data = args.map {"${utf8(it.key)}=${utf8(it.value)}"}.joinToString("&")
        val conn = (URL(POEDITOR_API_URL + endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doInput = true
            doOutput = true
            setFixedLengthStreamingMode(data.length)
        }
        val json: Any
        with(conn) {
            connect()
            conn.outputStream.write(data.toByteArray(StandardCharsets.UTF_8))
            json = JsonSlurper().parse(conn.inputStream.readBytes())
        }
        return (json as? Map<*, *>).orEmpty().getOrDefault("result", mapOf<Nothing, Nothing>()) as? Map<*, *> ?:
                throw RuntimeException("Typing wrong - data was: $json")
    }

    companion object {
        private const val POEDITOR_API_URL = "https://api.poeditor.com/v2/"
    }
}

private fun setNullOnEmpty(string: String): String? {
    if (string.isEmpty())
        return null
    return string
}

private fun parseDate(string: String): Date? {
    if (string.isEmpty())
        return null
    return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH).parse(string)
}

data class ExportFilterFlags(val translated: Boolean?, val fuzzy: Boolean?,
                             val automatic: Boolean?, val proofread: Boolean?) {
    override fun toString(): String {
        val out = mutableListOf<String>()
        out += when(translated) {
            true -> "translated"
            false -> "not_translated"
            null -> ""
        }
        out += when(fuzzy) {
            true -> "fuzzy"
            false -> "not_fuzzy"
            null -> ""
        }
        out += when(automatic) {
            true -> "automatic"
            false -> "not_automatic"
            null -> ""
        }
        out += when(proofread) {
            true -> "proofread"
            false -> "not_proofread"
            null -> ""
        }
        if (out.size == 1)
            return out[0]
        if (out.size == 0)
            return "[]"
        return "[" + out.joinToString(",") { '"' + it + '"' } + "]"
    }
}

@Suppress("unused")
enum class ExportFormat(internal val raw: String) {
    PO("po"),
    POT("pot"),
    MO("mo"),
    XLS("xls"),
    XLSX("xlsx"),
    CSV("csv"),
    RESW("resw"),
    RESX("resx"),
    ANDROID_STRINGS("android_strings"),
    APPLE_STRINGS("apple_strings"),
    XLIFF("xliff"),
    PROPERTIES("properties"),
    KEY_VALUE_JSON("key_value_json"),
    JSON("json"),
    YML("yml"),
    XMB("xmb"),
    XTB("xtb")
}

data class PoEditorProject internal constructor(private val api: PoEditorAPI, val id: Int, val name: String,
                                                val description: String?, val public: Boolean, val open: Boolean,
                                                val referenceLanguage: String?, val termsCount: Int, val created: Date) {
    /*fun getTerms(language: String): List<PoEditorTerm> {
        val resp = api.post("terms/list", mutableMapOf("id" to id.toString(), "language" to language))!!
        return (resp["terms"] as List<*>).map{ PoEditorTerm.fromJson(it as Map<*, *>) }
    }*/

    fun listLanguages(): List<PoEditorLanguageStatus> {
        val resp = api.post("languages/list", mutableMapOf("id" to id.toString()))
        return (resp["languages"] as List<*>).map{ PoEditorLanguageStatus.fromJson(it as Map<*, *>)}
    }

    fun exportTranslationUrl(language: String, format: ExportFormat,
                             filters: ExportFilterFlags? = null, alphabetical: Boolean = false): String {
        val args = mutableMapOf("id" to id.toString(), "language" to language, "type" to format.raw)
        filters?.let {
            args["filters"] = it.toString()
        }
        if (alphabetical)
            args["order"] = "terms"
        val resp = api.post("projects/export", args)
        return resp["url"] as String
    }

    fun exportTranslation(language: String, format: ExportFormat,
                          filters: ExportFilterFlags? = null, alphabetical: Boolean = false): ByteArray {
        return URL(exportTranslationUrl(language, format, filters, alphabetical)).readBytes()
    }

    companion object {
        fun fromJson(json: Map<*, *>, api: PoEditorAPI): PoEditorProject {
            val id = json["id"] as Int
            val name = json["name"] as String
            val description = setNullOnEmpty(json["description"] as String)
            val public = (json["public"] as Int) > 0
            val open = (json["open"] as Int) > 0
            val referenceLanguage = json["reference_language"] as String
            val termsCount = json["terms"] as Int
            val created = parseDate(json["created"] as String)!!
            return PoEditorProject(api, id, name, description, public, open, referenceLanguage, termsCount, created)
        }
    }
}

data class PoEditorLanguageStatus(val name: String, val code: String,
                                  val translations: Int, val percentage: BigDecimal, val updated: Date?) {
    companion object {
        fun fromJson(json: Map<*, *>): PoEditorLanguageStatus {
            val name = json["name"] as String
            val code = json["code"] as String
            val translations = json["translations"] as Int
            val percentage = (json["percentage"] as? Int)?.toBigDecimal() ?: json["percentage"] as BigDecimal
            val updated = parseDate(json["updated"] as String)
            return PoEditorLanguageStatus(name, code, translations, percentage, updated)
        }
    }
}

/*
private data class PoEditorTerm(val key: String, val context: String?, val plural: String?, val created: Date,
                val updated: Date?, val translation: PoEditorTranslation?, val reference: String?,
                val tags: List<String>, val comment: String?) {
    companion object {
        fun fromJson(json: Map<*, *>): PoEditorTerm {
            val key = json["term"] as String
            val context = setNullOnEmpty(json["context"] as String)
            val plural = setNullOnEmpty(json["plural"] as String)
            val created = parseDate(json["created"] as String)!!
            val updated = parseDate(json["updated"] as String)
            val translation = PoEditorTranslation.fromJson(json["translation"] as Map<*,*>)
            val reference = setNullOnEmpty(json["reference"] as String)
            val tags = (json["tags"] as List<*>).filterIsInstance(String::class.java).filter{ it.isNotEmpty() }
            val comment = setNullOnEmpty(json["comment"] as String)
            return PoEditorTerm(key, context, plural, created, updated, translation, reference, tags, comment)
        }
    }
}

private data class PoEditorTranslation(val content: String, val fuzzy: Boolean, val proofread: Boolean?, val updated: Date?) {
    companion object {
        fun fromJson(json: Map<*, *>): PoEditorTranslation {
            val content = setNullOnEmpty(json["content"] as String)!!
            val fuzzy = (json["fuzzy"] as Int) > 0
            val proofread = when (json.getOrDefault("proofread", -1) as Int) {
                -1 -> null
                0 -> false
                1 -> true
                else -> throw RuntimeException()
            }
            val updated = parseDate(json["updated"] as String)
            return PoEditorTranslation(content, fuzzy, proofread, updated)
        }
    }
}
*/
