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
        
        jvmMain {
            dependencies {
                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.cio)
                implementation(libs.ktor.server.sse)
                implementation(libs.ktor.server.content.negotiation)
                implementation(libs.kotlin.logging)
                implementation(libs.logback)
            }
        }
        
        jvmTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.server.test.host)
                implementation(libs.ktor.client.cio)
                implementation("junit:junit:4.13.2")
                implementation(libs.kotlin.test.junit)
            }
        }
    }
}

// Configure JVM test task to use JUnit
tasks.named<Test>("jvmTest") {
    useJUnit()
}

mavenPublishing {
    coordinates(group.toString(), "mcp", version.toString())
}
