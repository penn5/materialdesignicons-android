import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `maven-publish`
    kotlin("jvm") version "1.6.21"
    `kotlin-dsl`
}

group = "com.github.penn5"
version = "0.0.1"

gradlePlugin {
    plugins {
        register("materialdesigniconsPlugin") {
            id = "materialdesignicons-android"
            implementationClass = "com.github.penn5.MaterialDesignIconsPlugin"
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
    mavenCentral()
}
dependencies {
    compileOnly(gradleApi())
    implementation("com.android.tools.build:gradle:7.4.0")
    implementation("commons-io:commons-io:2.11.0")
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
