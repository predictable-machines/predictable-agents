package com.predictable.machines.build.logic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import io.github.nomisrev.openapi.plugin.OpenApiConfig
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.plugins.signing.SigningExtension
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

fun Project.withAndroid(block: CommonExtension<*, *, *, *, *, *>.() -> Unit) {
    withAndroidApplication { configure<ApplicationExtension> { block(this) } }
    withAndroidLibrary { configure<LibraryExtension> { block(this) } }
}

fun Project.withAndroidApplication(block: ApplicationExtension.() -> Unit) {
    pluginManager.withPlugin(libs.plugins.android.application.get().pluginId) {
        configure<ApplicationExtension> { block(this) }
    }
}

fun Project.withAndroidLibrary(block: LibraryExtension.() -> Unit) {
    pluginManager.withPlugin(libs.plugins.android.library.get().pluginId) {
        configure<LibraryExtension> { block(this) }
    }
}

fun Project.withDokka(block: DokkaExtension.() -> Unit) {
    pluginManager.withPlugin(libs.plugins.dokka.get().pluginId) {
        configure<DokkaExtension> { block(this) }
    }
}

fun Project.withKotlin(block: KotlinProjectExtension.() -> Unit) {
    withKotlinJvm(block)
    withKotlinMultiplatform(block)
}

fun Project.withKotlinJvm(block: KotlinJvmProjectExtension.() -> Unit) {
    pluginManager.withPlugin(libs.plugins.kotlin.jvm.get().pluginId) {
        configure<KotlinJvmProjectExtension> { block(this) }
    }
}

fun Project.withKotlinMultiplatform(block: KotlinMultiplatformExtension.() -> Unit) {
    pluginManager.withPlugin(libs.plugins.kotlin.multiplatform.get().pluginId) {
        configure<KotlinMultiplatformExtension> { block(this) }
    }
}

fun Project.withOpenApi(block: OpenApiConfig.() -> Unit) {
    pluginManager.withPlugin(libs.plugins.openApi.get().pluginId) {
        configure<OpenApiConfig> { block(this) }
    }
}

fun Project.withMavenPublish(block: MavenPublishBaseExtension.() -> Unit) {
    pluginManager.withPlugin(libs.plugins.mavenPublish.get().pluginId) {
        configure<MavenPublishBaseExtension> { block(this) }
    }
}

fun Project.withSigning(block: SigningExtension.() -> Unit) {
    pluginManager.withPlugin(libs.plugins.signing.get().pluginId) {
        configure<SigningExtension> { block(this) }
    }
}
