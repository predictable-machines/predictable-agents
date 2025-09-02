package com.predictable.machines.build.logic

import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.kotlin.dsl.the
import org.gradle.plugins.signing.SigningExtension

fun Project.mavenPublishingSetup() {
    withMavenPublish {
        if (isCI) {
            pluginManager.apply(libs.plugins.signing.get().pluginId)
            withSigning {
                signInMemory()
                sign(the<PublishingExtension>().publications)
            }
        }

        publishToMavenCentral()
        pom {
            name.set("Predictable Agents")
            description.set(
                "Kotlin Multiplatform library for building AI agents with tool use and OpenAI support."
            )
            inceptionYear.set("2025")
            url.set("https://github.com/predictable-machines/predictable-agents")
            licenses {
                license {
                    name.set("Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0")
                }
            }
            developers {
                developer {
                    id.set("predictable-machines")
                    name.set("Predictable Machines")
                    url.set("https://github.com/predictable-machines")
                }
            }
            scm {
                url.set("https://github.com/predictable-machines/predictable-agents")
                connection.set(
                    "scm:git:https://github.com/predictable-machines/predictable-agents.git"
                )
                developerConnection.set(
                    "scm:git:ssh://git@github.com/predictable-machines/predictable-agents.git"
                )
            }
        }
    }
}

private fun SigningExtension.signInMemory() {
    fun getProperty(name: String): String? =
        project.providers.gradleProperty(name).orNull
            ?: project.providers.environmentVariable(name).orNull

    val gnupgKey: String? = getProperty("ORG_GRADLE_PROJECT_signingInMemoryKey")
    val gnupgPassphrase: String? = getProperty("ORG_GRADLE_PROJECT_signingInMemoryKeyPassword")

    if (gnupgKey != null && gnupgPassphrase != null) {
        useInMemoryPgpKeys(gnupgKey, gnupgPassphrase)
    }
}
