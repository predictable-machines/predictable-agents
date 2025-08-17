import com.predictable.machines.build.logic.setupKotlinMultiplatformAndroid
import com.predictable.machines.build.logic.setupKotlinMultiplatformAppleTargets
import com.predictable.machines.build.logic.setupKotlinMultiplatformJvm
import com.predictable.machines.build.logic.setupKotlinMultiplatformLinuxTargets
import com.predictable.machines.build.logic.setupKotlinMultiplatformWAsmTargets
import org.jetbrains.dokka.gradle.DokkaTask
import java.net.URL

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.conventions)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.mavenPublish)
}

setupKotlinMultiplatformAndroid()
setupKotlinMultiplatformJvm()
setupKotlinMultiplatformAppleTargets()
setupKotlinMultiplatformLinuxTargets()
setupKotlinMultiplatformWAsmTargets()

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

// Configure Dokka
// Samples are now in commonMain but marked with @DocumentationSample annotation
// to exclude them from the public API
tasks.withType<DokkaTask>().configureEach {
    // Add custom CSS to hide the Kotlin Playground run button
    // since the playground doesn't have access to our library
    pluginsMapConfiguration.set(
        mapOf(
            "org.jetbrains.dokka.base.DokkaBase" to """
                {
                    "customAssets": [],
                    "customStyleSheets": ["${project.rootDir}/dokka-custom.css"],
                    "mergeImplicitExpectActualDeclarations": false,
                    "homepageLink": "https://github.com/predictable-machines/predictable-agents"
                }
            """
        )
    )
    
    dokkaSourceSets {
        named("commonMain") {
            // Suppress the samples package from documentation
            perPackageOption {
                matchingRegex.set(".*\\.samples.*")
                suppress.set(true)
            }
            
            // Disable playground for samples
            noJdkLink.set(false)
            noStdlibLink.set(false)
            
            // Configure source links for GitHub
            sourceLink {
                localDirectory.set(file("src/commonMain/kotlin"))
                remoteUrl.set(URL("https://github.com/predictable-machines/predictable-agents/tree/main/agents/src/commonMain/kotlin"))
                remoteLineSuffix.set("#L")
            }
        }
    }
}

mavenPublishing { coordinates(group.toString(), "agents", version.toString()) }
