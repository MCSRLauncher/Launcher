package com.redlimerl.mcsrlauncher.data.launcher

interface LauncherSharedOptions {
    var javaPath: String
    var jvmArguments: String
    var minMemory: Int
    var maxMemory: Int
    var maximumResolution: Boolean
    var resolutionWidth: Int
    var resolutionHeight: Int
    var customGLFWPath: String
    var wrapperCommand: String
    var preLaunchCommand: String
    var postExitCommand: String
    var enableFeralGamemode: Boolean
    var enableMangoHud: Boolean
    var useDiscreteGpu: Boolean
    var useZink: Boolean
}