@file:OptIn(ExperimentalWasmDsl::class)

package com.predictable.machines.build.logic

import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.KotlinMultiplatform
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.internal.config.LanguageFeature

class ConventionsPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.setup()
    }
}

private fun Project.setup() {
    projectSetup()
    androidSetup()
    dokkaSetup()
    javaSetup()
    kotlinSetup()
    mavenPublishSetup()
    testSetup()
    openApiSetup()
}

private fun Project.projectSetup() {
    group = predictableMachinesGroup.get()
    version = predictableMachinesVersion.get()
}

private fun Project.androidSetup() {
    val projectName: String = name
    withAndroid {
        namespace = "${predictableMachinesGroup.get()}.$projectName"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }
        compileOptions {
            sourceCompatibility = getJavaVersion()
            targetCompatibility = getJavaVersion()
        }
        packaging {
            resources {
                excludes.addAll(
                    listOf(
                        "META-INF/{AL2.0,LGPL2.1}",
                        "META-INF/INDEX.LIST",
                        "META-INF/*.kotlin_module",
                        "META-INF/LICENSE*",
                        "META-INF/DEPENDENCIES",
                        "META-INF/NOTICE*",
                        "META-INF/io.netty.versions.properties",
                        "logback.xml",
                    )
                )
            }
        }
    }
}

private fun Project.dokkaSetup() {
    withDokka { dokkaMavenPublishSetup() }
}

private fun Project.dokkaMavenPublishSetup() {
    withMavenPublish {
        withKotlinJvm {
            configure(
                platform =
                    KotlinJvm(javadocJar = JavadocJar.Dokka("dokkaGenerate"), sourcesJar = true)
            )
        }
        withKotlinMultiplatform {
            configure(
                platform =
                    KotlinMultiplatform(
                        javadocJar = JavadocJar.Dokka("dokkaGenerate"),
                        sourcesJar = true,
                        androidVariantsToPublish = listOf("debug", "release"),
                    )
            )
        }
    }
}

private fun Project.javaSetup() {
    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = getJavaVersion().toString()
        targetCompatibility = getJavaVersion().toString()
    }
}

private fun Project.kotlinSetup() {
    withKotlin {
        sourceSets.configureEach {
            for (languageFeature: LanguageFeature in enabledLanguageFeatures) {
                languageSettings.enableLanguageFeature(languageFeature.name)
            }
        }
    }
    kotlinJvmSetup()
    kotlinMultiplatformSetup()
}

private fun Project.testSetup() {
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()

        testLogging {
            // set options for log level LIFECYCLE
            events(
                TestLogEvent.FAILED,
                TestLogEvent.PASSED,
                TestLogEvent.SKIPPED,
                TestLogEvent.STANDARD_OUT,
            )
            exceptionFormat = TestExceptionFormat.FULL
            showExceptions = true
            showCauses = true
            showStackTraces = true

            // set options for log level DEBUG and INFO
            debug {
                events(
                    TestLogEvent.STARTED,
                    TestLogEvent.FAILED,
                    TestLogEvent.PASSED,
                    TestLogEvent.SKIPPED,
                    TestLogEvent.STANDARD_ERROR,
                    TestLogEvent.STANDARD_OUT,
                )
                exceptionFormat = TestExceptionFormat.FULL
            }
            info.events = debug.events
            info.exceptionFormat = debug.exceptionFormat
        }
    }
}

private fun Project.openApiSetup() {
    withOpenApi {
        spec("OpenAI", file("openai.yml")) {
            packageName = "${predictableMachinesGroup.get()}.generated"
        }
    }
}

fun Project.kotlinJvmSetup() {
    withKotlinJvm { compilerOptions.jvmTarget.set(JvmTarget.fromTarget(libs.versions.java.get())) }
}

fun Project.kotlinMultiplatformSetup() {
    withKotlinMultiplatform(KotlinMultiplatformExtension::applyDefaultHierarchyTemplate)
    // setupKotlinMultiplatformAndroid()
    // setupKotlinMultiplatformJvm()
    // setupKotlinMultiplatformAppleTargets()
    // setupKotlinMultiplatformLinuxTargets()
    // setupKotlinMultiplatformWAsmTargets()
}

fun Project.setupKotlinMultiplatformAndroid() {
    withKotlinMultiplatform {
        withAndroid { androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } } }
        withAndroidLibrary { androidTarget { publishLibraryVariants("release") } }
    }
}

fun Project.setupKotlinMultiplatformJvm() {
    withKotlinMultiplatform {
        jvm { compilerOptions.jvmTarget.set(JvmTarget.fromTarget(libs.versions.java.get())) }
    }
}

fun Project.setupKotlinMultiplatformAppleTargets() {
    if (isCI && !isMacOS) return
    withKotlinMultiplatform { setupKotlinMultiplatformiOSTargets() }
}

fun Project.setupKotlinMultiplatformiOSTargets() {
    if (isCI && !isMacOS) return
    val projectName: String = name
    withKotlinMultiplatform {
        iosX64()
        iosArm64()
        iosSimulatorArm64 {
            // Skip tests for iosSimulatorArm64 due to SDK compatibility issues
            binaries.framework { baseName = projectName }
        }
    }
}

fun Project.setupKotlinMultiplatformLinuxTargets() {
    if (isCI && !isLinux) return
    withKotlinMultiplatform { linuxX64() }
}

fun Project.setupKotlinMultiplatformWAsmTargets() {
    if (isCI && !isLinux) return
    withKotlinMultiplatform {
        wasmJs {
            browser {
                testTask {
                    // WAsm tests are failing, remove this when fixed
                    enabled = false
                }
            }
        }
    }
}

private fun Project.mavenPublishSetup() {
    withMavenPublish {
        mavenPublishingSetup()
    }
}

private fun Project.getJavaVersion(): JavaVersion =
    JavaVersion.valueOf("VERSION_${libs.versions.java.get()}")
