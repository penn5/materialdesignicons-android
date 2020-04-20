package com.github.penn5

import com.android.build.gradle.BaseExtension
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.register
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
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
            }
        }
    }
}

open class PoEditorPluginExtension {
    var apiToken: String? = null
    var projectId: Int? = null
}

open class ImportPoEditorStringsTask : DefaultTask() {
    @TaskAction
    fun doAction() {
        try {
            // Don't throw an error, probably some other person is building who doesn't have poeditor set up
            val apiToken: String = project.poeditor.apiToken ?: return
            // Do throw an error, the user provided an api key but the project is wrong
            val projectId = project.poeditor.projectId ?: throw RuntimeException("Project ID not set for PoEditor")
            val poProject = PoEditorAPI(apiToken).getProject(projectId)

            val languages = poProject.listLanguages().map{ it.code }

            val srcSet = project.android?.sourceSets?.associate{ Pair(it.name, it.res.srcDirs) }?.getOrDefault("main", null)
            val resDir = (srcSet ?: throw RuntimeException("Unable to detect srcSet for res directory")).elementAtOrNull(0)
            resDir ?: throw RuntimeException("Unable to detect res directory for srcSet")

            var dir: File
            for (language in languages) {
                dir = if (poProject.referenceLanguage == language)
                    File(resDir, "values")
                else
                    File(resDir, languageTagToAndroid(language))
                if (!dir.isDirectory)
                    dir.mkdir() // not mkdirs, because the parent should always exist and if it doesn't we should fail
                val data = poProject.exportTranslation(language, ExportFormat.ANDROID_STRINGS).toString(StandardCharsets.UTF_8)
                File(dir, "strings.xml").writeText(data.replace("^.*><.*$\n".toRegex(RegexOption.MULTILINE), ""))
            }
        } catch (e: IOException) {
             System.err.println("IOException updating translations")
        }
    }

    private fun languageTagToAndroid(tag: String): String {
        // https://developer.android.com/guide/topics/resources/providing-resources.html#AlternativeResources
        val locale = Locale.forLanguageTag(tag)
        var ret =  "values-${locale.language}"
        if (locale.country != "")
            ret += "-r${locale.country}"
        return ret
    }
}
