import com.predictable.machines.build.logic.setupKotlinMultiplatformJvm
import com.predictable.machines.build.logic.setupKotlinMultiplatformWAsmTargets
import com.predictable.machines.build.logic.setupKotlinMultiplatformiOSTargets

plugins {
    alias(libs.plugins.conventions)
    alias(libs.plugins.dokka)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.mavenPublish)
}

setupKotlinMultiplatformJvm()
setupKotlinMultiplatformiOSTargets()
setupKotlinMultiplatformWAsmTargets()

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.agents)

                api(libs.kotlin.mcp.sdk)

                implementation(libs.kotlin.serialization.json)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
            }
        }
    }
}

mavenPublishing {
    coordinates(group.toString(), "mcp", version.toString())
}
