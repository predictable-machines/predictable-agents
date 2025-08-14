package com.predictable.machines.build.logic

import org.gradle.api.Project
import org.gradle.api.provider.Provider

val Project.predictableMachinesGroup: Provider<String>
    get() = providers.gradleProperty("predictable.machines.group")

val Project.predictableMachinesVersion: Provider<String>
    get() = providers.gradleProperty("predictable.machines.version")
