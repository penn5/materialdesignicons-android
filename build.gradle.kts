import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `maven-publish`
    id("org.gradle.kotlin.kotlin-dsl") version "1.2.6"
    kotlin("jvm") version "1.3.41"
}

group = "com.github.penn5"
version = "0.0.1"

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
    implementation(kotlin("stdlib-jdk8"))
    compileOnly(gradleApi())
    implementation("com.android.tools.build:gradle:3.5.0")
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.11"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.11"
}