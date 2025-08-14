package com.predictable.machines.build.logic

val currentOS: String
    get() = System.getProperty("os.name")

val isMacOS: Boolean
    get() = currentOS.lowercase().startsWith("mac")

val isLinux: Boolean
    get() = currentOS.lowercase().startsWith("linux")

val isWindows: Boolean
    get() = currentOS.lowercase().startsWith("windows")
