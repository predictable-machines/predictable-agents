plugins { `kotlin-dsl` }

repositories {
    google {
        mavenContent {
            includeGroupAndSubgroups("androidx")
            includeGroupAndSubgroups("com.android")
            includeGroupAndSubgroups("com.google")
        }
    }
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // Workaround to get Version Catalogs instances inside `build-logic`
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))

    implementation(libs.plugins.android.application.artifact)
    implementation(libs.plugins.dokka.artifact)
    implementation(libs.plugins.android.library.artifact)
    implementation(libs.plugins.kotlin.jvm.artifact)
    implementation(libs.plugins.mavenPublish.artifact)
    implementation(libs.plugins.openApi.artifact)
}

gradlePlugin.plugins.register("conventions") {
    id = "conventions"
    implementationClass = "com.predictable.machines.build.logic.ConventionsPlugin"
}

val Provider<PluginDependency>.artifact: Provider<ExternalModuleDependency>
    get() = map {
        dependencies.create(
            group = it.pluginId,
            name = "${it.pluginId}.gradle.plugin",
            version = it.version.displayName,
        )
    }
