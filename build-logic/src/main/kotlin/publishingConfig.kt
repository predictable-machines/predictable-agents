package com.predictable.machines.build.logic

import org.gradle.api.Project

fun Project.mavenPublishingSetup() {
    withMavenPublish {
        publishToMavenCentral()

        if (isCI) signAllPublications()

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
