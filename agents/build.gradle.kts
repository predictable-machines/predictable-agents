plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.conventions)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.mavenPublish)
    // alias(libs.plugins.openApi)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(libs.arrow.core)
                api(libs.arrow.core.serialization)
                api(libs.arrow.fx.coroutines)
                implementation(libs.openai.client)
                implementation(libs.kotlin.serialization.json)
                implementation(libs.xemantic.ai.tool.schema)
                implementation(libs.kotlin.envvar)
                implementation(libs.kotlin.logging)
                runtimeOnly(libs.ktor.client.cio)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        jvmMain { dependencies { implementation(libs.kotlin.reflect) } }
        // Configure WASM JS source sets
        wasmJsMain { dependencies { implementation(libs.ktor.client.js) } }
    }
}

// Disable iOS tests due to Flow serializer issues
tasks
    .matching {
        it.name.startsWith("iosX64Test") ||
            it.name.startsWith("iosArm64Test") ||
            it.name.startsWith("iosSimulatorArm64Test")
    }
    .configureEach { enabled = false }

mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

    coordinates(group.toString(), "agents", version.toString())

    pom {
        name = "Predictable Agents"
        description = "Kotlin Multiplatform library for building AI agents with tool use and OpenAI support."
        inceptionYear = "2024"
        url = "https://github.com/predictable-machines/predictable-agents"
        licenses {
            license {
                name = "Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0"
            }
        }
        developers {
            developer {
                id = "predictable-machines"
                name = "Predictable Machines"
                url = "https://github.com/predictable-machines"
            }
        }
        scm {
            url = "https://github.com/predictable-machines/predictable-agents"
            connection = "scm:git:https://github.com/predictable-machines/predictable-agents.git"
            developerConnection = "scm:git:ssh://git@github.com/predictable-machines/predictable-agents.git"
        }
    }
}
