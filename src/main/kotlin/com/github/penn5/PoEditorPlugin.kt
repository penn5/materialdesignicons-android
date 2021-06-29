package com.github.penn5

import com.android.build.gradle.BaseExtension
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.register
import org.redundent.kotlin.xml.PrintOptions
import org.redundent.kotlin.xml.xml
import java.io.File
import java.io.IOException
import java.util.*

val Project.android: BaseExtension? get() = findProperty("android") as BaseExtension?
val Project.poeditor: PoEditorPluginExtension get() = findProperty("poeditor") as PoEditorPluginExtension

@Suppress("unused")
class PoEditorPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.run {
            extensions.create("poeditor", PoEditorPluginExtension::class)
            tasks {
                register("importTranslations", ImportPoEditorStringsTask::class)
                register("importTranslationsForFastlane", ImportPoEditorStringsForFastlaneTask::class)
            }
        }
    }
}

open class PoEditorPluginExtension {
    var apiToken: String? = null
    var projectId: Int? = null
}

interface ImportPoEditorStringsBaseTask<T> {
    fun doBaseAction() {
        try {
            // Don't throw an error, probably some other person is building who doesn't have poeditor set up
            val apiToken: String = thisProject.poeditor.apiToken ?: run {
                System.err.println("Please provide a PoEditor API token to import translations.")
                return
            }
            // Do throw an error, the user provided an api key but the project is wrong
            val projectId = thisProject.poeditor.projectId
                    ?: throw RuntimeException("Project ID not set for PoEditor")
            val poProject = PoEditorAPI(apiToken).getProject(projectId)

            val languages = poProject.listLanguages().map { it.code }

            val tmp = init()

            for (language in languages) {
                val terms = poProject.getTerms(language)
                val incompleteSets = mutableSetOf<String>()
                for (term in terms) {
                    if (term.translation?.content != null)
                        continue
                    for (tag in term.tags) {
                        if (!tag.startsWith("require-all-"))
                            continue
                        incompleteSets.add(tag.substringBeforeLast("-keep-"))
                    }
                }
                val filteredTerms = terms.map {
                    var active = default
                    for (tag in it.tags) {
                        if (tag == "ignore-string-$platform" || (!tag.endsWith("-keep-$platform") && tag.substringBeforeLast("-keep-") in incompleteSets)) {
                            active = false
                            break
                        } else if (tag == platform) {
                            active = true
                        }
                    }
                    if (active)
                        it
                    else
                        it.copy(translation = null)
                }
                write(language, filteredTerms, poProject, tmp)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    val platform: String
    val default: Boolean
    val thisProject: Project
    fun init(): T
    fun write(language: String, terms: List<PoEditorTerm>, project: PoEditorProject, data: T)
}

open class ImportPoEditorStringsTask : ImportPoEditorStringsBaseTask<File>, DefaultTask() {
    @Internal
    override val platform = "android"
    @Internal
    override val default = true
    @Internal
    override val thisProject = project

    @TaskAction
    fun doAction() = super.doBaseAction()

    override fun init(): File {
        val srcSet = project.android?.sourceSets?.associate { Pair(it.name, it.res.srcDirs) }?.getOrDefault("main", null)
        val resDir = (srcSet ?: throw RuntimeException("Unable to detect srcSet for res directory")).elementAtOrNull(0)
        resDir ?: throw RuntimeException("Unable to detect res directory for srcSet")

        for (name in resDir.list { _, name -> name.startsWith("values") }!!) {
            val dir = resDir.resolve(name)
            if (!dir.isDirectory)
                continue
            dir.resolve("strings.xml").delete()
        }
        return resDir
    }

    override fun write(language: String, terms: List<PoEditorTerm>, project: PoEditorProject, data: File) {
        val dir = if (Locale.forLanguageTag(project.referenceLanguage) == Locale.forLanguageTag(language))
            File(data, "values")
        else
            File(data, languageTagToAndroid(language))
        if (!dir.isDirectory)
            dir.mkdir() // not mkdirs, because the parent should always exist and if it doesn't we should fail
        val translations = terms.associate { it.key to it.translation?.content }
                .filterValues { it != null }.mapValues { it.value!! }
        val xml = dataToStringsXml(translations).toString(PrintOptions(singleLineTextElements = true))
        File(dir, "strings.xml").writeText(xml)
    }

    private fun languageTagToAndroid(tag: String): String {
        // https://developer.android.com/guide/topics/resources/providing-resources.html#AlternativeResources
        // We ought to use BCP47 (as returned by PoEditor) but it isn't supported before API 24
        when (tag) {
            // special case: Android does not support BCP47 until
            "zh-Hans" -> return "values-zh-rcn"
            "zn-Hant" -> return "values-zh-rtw"
        }
        val locale = Locale.forLanguageTag(tag)
        var ret =  "values-${locale.language}"
        if (locale.country != "")
            ret += "-r${locale.country}"
        return ret
    }

    private fun dataToStringsXml(data: Map<String, String>) = xml("resources") {
        includeXmlProlog = true
        data.forEach { (key, value) ->
            "string"("name" to key) {
                -escapeValue(value)
            }
        }
    }

    // https://developer.android.com/guide/topics/resources/string-resource#escaping_quotes
    private fun escapeValue(value: String) =
            value.replace("\\", "\\\\").replace("@", "\\@").replace("?", "\\?")
                    .replace("'", "\\'").replace("\"", "\\\"").replace("\n", "\\n")
}

open class ImportPoEditorStringsForFastlaneTask : ImportPoEditorStringsBaseTask<File>, DefaultTask() {
    @Internal
    override val platform = "fastlane-android"
    @Internal
    override val default = false
    @Internal
    override val thisProject = project

    @TaskAction
    fun doAction() = super.doBaseAction()

    override fun init(): File {
        val resDir = project.rootProject.rootDir.resolve("fastlane/metadata")
        resDir.deleteRecursively()
        return resDir
    }

    override fun write(language: String, terms: List<PoEditorTerm>, project: PoEditorProject, data: File) {
        val created = mutableSetOf<Pair<String, String>>()
        for (term in terms) {
            term.translation?.content ?: continue
            if (!term.tags.contains("fastlane-android"))
                continue
            val types = term.tags.filter { it.startsWith("fastlane-") }.map { it.substringAfter('-') }
            types.forEach {
                val languageTag = expandLanguageTag(language)
                val langDir = data.resolve(it).resolve(languageTag)
                if (created.add(it to languageTag)) {
                    check(langDir.mkdirs()) { "Couldn't create $langDir" }
                }
                val out = langDir.resolve(term.key + ".txt")
                out.writeText(term.translation.content)
            }
        }
    }

    private fun expandLanguageTag(language: String): String {
        return when (language) {
            // special case: Google (incorrectly) takes zh-CN for simplified Chinese and zh-TN for traditional (should use the BCP47 script data)
            "zh-Hans" -> "zh-cn"
            "zn-Hant" -> "zh-tn"
            else -> {
                // reduce precision
                Locale.lookupTag(listOf(Locale.LanguageRange(language)), AVAILABLE_TAGS)?.let {
                    return it
                }
                // increase precision (only one choice)
                val filter = Locale.filterTags(listOf(Locale.LanguageRange(language)), AVAILABLE_TAGS)
                filter.singleOrNull()?.let {
                    return it
                }
                // increase precision (multiple choices, accept if language == region)
                filter
                        .map { it to Locale.forLanguageTag(it) }
                        .singleOrNull { it.second.language.equals(it.second.country, ignoreCase = true) }
                        ?.let {
                            return it.first
                        }
                // no match
                error("No matching tags for $language (see https://support.google.com/googleplay/android-developer/answer/9844778 for full list)")
            }
        }
    }
}


/**
 * List of available language tags from https://support.google.com/googleplay/android-developer/answer/9844778
 */
private val AVAILABLE_TAGS = listOf(
        "af",
        "am",
        "ar",
        "hy-AM",
        "az-AZ",
        "eu-ES",
        "be",
        "bn-BD",
        "bg",
        "my-MM",
        "ca",
        "zh-HK",
        "zh-CN",
        "zh-TW",
        "hr",
        "cs-CZ",
        "da-DK",
        "nl-NL",
        "en-AU",
        "en-CA",
        "en-IN",
        "en-SG",
        "en-GB",
        "en-US",
        "et",
        "fil",
        "fi-FI",
        "fr-FR",
        "fr-CA",
        "gl-ES",
        "ka-GE",
        "de-DE",
        "el-GR",
        "iw-IL",
        "hi-IN",
        "hu-HU",
        "is-IS",
        "id",
        "it-IT",
        "ja-JP",
        "kn-IN",
        "km-KH",
        "ko-KR",
        "ky-KG",
        "lo-LA",
        "lv",
        "lt",
        "mk-MK",
        "ms",
        "ml-IN",
        "mr-IN",
        "mn-MN",
        "ne-NP",
        "no-NO",
        "fa",
        "pl-PL",
        "pt-BR",
        "pt-PT",
        "ro",
        "rm",
        "ru-RU",
        "sr",
        "si-LK",
        "sk",
        "sl",
        "es-419",
        "es-ES",
        "es-US",
        "sw",
        "sv-SE",
        "ta-IN",
        "te-IN",
        "th",
        "tr-TR",
        "uk",
        "vi",
        "zu"
)
