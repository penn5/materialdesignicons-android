package com.github.penn5

import com.android.build.gradle.BaseExtension
import groovy.json.JsonSlurper
import org.apache.commons.io.output.WriterOutputStream
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.register
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.*

private const val META_URL = "https://raw.githubusercontent.com/Templarian/MaterialDesign/master/meta.json"
private const val BASE_ICON_URL = "https://materialdesignicons.com/api/download/icon/vectordrawable/"

private const val SIGNATURE = "<!-- File auto-synced, do not edit! MaterialDesignIcons ID: "

val Project.android: BaseExtension? get() = findProperty("android") as BaseExtension?

@Suppress("unused")
class MaterialDesignIconsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.run {
            tasks {
                register("updateDrawables", UpdateDrawablesTask::class)
            }
        }
    }
}

private fun getMeta(): List<*> {
    val conn = (URL(META_URL).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
    }
    conn.connect()
    val json = JsonSlurper().parse(conn.inputStream.readBytes())
    return json as List<*>
}

private fun getIcon(iconID: String): InputStream {
    val conn = (URL(BASE_ICON_URL + iconID).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
    }
    conn.connect()
    return conn.inputStream!!
}

open class UpdateDrawablesTask : DefaultTask() {
    @TaskAction
    fun doAction() {
        try {
            val meta = getMeta().associateBy { (it as Map<*, *>)["id"] as String }
            for (file in files) {
                val iconID = file.bufferedReader().use { reader ->
                    val signature = CharArray(SIGNATURE.length).also {
                        reader.read(it)
                    }.concatToString()
                    if (signature != SIGNATURE) {
                        null
                    } else {
                        reader.readLine().trim().dropLast(3).trimEnd() // drop "-->"
                    }
                }
                iconID ?: continue
                val iconMeta = meta[iconID] as Map<*, *>
                val icon = getIcon(iconID)
                file.bufferedWriter().use { writer ->
                    writer.write("$SIGNATURE${sanitizeComment(iconID)}-->\n")
                    if (iconMeta["deprecated"] as Boolean) {
                        logger.warn("Deprecated icon ${file.path}")
                    } else {
                        // All icons are Apache 2.0 except brand icons, but they're deprecated.
                        writer.write("<!-- SPDX-License-Identifier: Apache-2.0 -->\n")
                        writer.write("<!-- Copyright (C) ${sanitizeComment(iconMeta["author"] as String)} version ${sanitizeComment(iconMeta["version"] as String)}\n")
                        writer.write("Licensed under the Apache License, Version 2.0 (the \"License\");\n")
                        writer.write("you may not use this file except in compliance with the License.\n")
                        writer.write("You may obtain a copy of the License at\n")
                        writer.write("\n")
                        writer.write("    http://www.apache.org/licenses/LICENSE-2.0\n")
                        writer.write("\n")
                        writer.write("Unless required by applicable law or agreed to in writing, software\n")
                        writer.write("distributed under the License is distributed on an \"AS IS\" BASIS,\n")
                        writer.write("WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n")
                        writer.write("See the License for the specific language governing permissions and\n")
                        writer.write("limitations under the License. -->\n")
                    }
                    WriterOutputStream(writer, StandardCharsets.UTF_8).use { outputStream ->
                        icon.transferTo(outputStream)
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun sanitizeComment(text: String): String {
        require(!text.contains("--")) { "$text must not contain \"--\"" }
        return text
    }

    @Internal
    fun getDrawableDirs(): List<File> {
        val srcSet = project.android?.sourceSets?.associate { Pair(it.name, it.res.srcDirs) }?.getOrDefault("main", null)
        val resDir = (srcSet ?: throw RuntimeException("Unable to detect srcSet for res directory")).elementAtOrNull(0)
        resDir ?: throw RuntimeException("Unable to detect res directory for srcSet")

        return resDir.list { _, name -> name.startsWith("drawable") }!!.mapNotNull { name ->
            val dir = resDir.resolve(name)
            if (!dir.isDirectory)
                return@mapNotNull null
            dir
        }
    }

    @Internal
    val files = getDrawableDirs().flatMap { it.listFiles()!!.asIterable() }.filter { file ->
        file.bufferedReader().use { reader ->
            val signature = CharArray(SIGNATURE.length).also {
                reader.read(it)
            }.concatToString()
            signature == SIGNATURE
        }
    }

    @get:InputFiles
    val inputFiles get() = files

    @get:OutputFiles
    val outputFiles get() = files
}