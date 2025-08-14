plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.conventions)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.mavenPublish)
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

mavenPublishing { coordinates(group.toString(), "agents", version.toString()) }
