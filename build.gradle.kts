import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `maven-publish`
    `kotlin-dsl`
    kotlin("jvm") version "1.4.31"
}

group = "com.github.penn5"
version = "0.1.3"

gradlePlugin {
    plugins {
        register("poeditorPlugin") {
            id = "poeditor-android"
            implementationClass = "com.github.penn5.PoEditorPlugin"
        }
    }
}

publishing {
    repositories {
        maven(url = "build/repository")
    }
}

repositories {
    google()
    jcenter()
}
dependencies {
    compileOnly(gradleApi())
    implementation("com.android.tools.build:gradle:4.2.1")
    implementation("org.redundent:kotlin-xml-builder:1.7.2")
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
