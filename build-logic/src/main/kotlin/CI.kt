package com.predictable.machines.build.logic

val isCI: Boolean
    get() = System.getenv("CI")?.toBoolean() ?: false
