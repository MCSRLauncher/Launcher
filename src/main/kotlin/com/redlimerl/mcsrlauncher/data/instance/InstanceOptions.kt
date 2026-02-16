package com.redlimerl.mcsrlauncher.data.instance

import com.redlimerl.mcsrlauncher.MCSRLauncher
import com.redlimerl.mcsrlauncher.data.launcher.LauncherSharedOptions
import com.redlimerl.mcsrlauncher.util.OSUtils
import kotlinx.serialization.Serializable

@Serializable
data class InstanceOptions(
    var useLauncherJavaOption: Boolean = true,
    var useLauncherResolutionOption: Boolean = true,
    var autoModUpdates: Boolean = false,
    var clearBeforeLaunch: Boolean = false,
    var useLauncherWorkarounds: Boolean = true,

    override var customGLFWPath: String = "",
    override var useSystemGLFW: Boolean = false,
    override var wrapperCommand: String = "",
    override var preLaunchCommand: String = "",
    override var postExitCommand: String = "",
    override var enableFeralGamemode: Boolean = false,
    override var enableMangoHud: Boolean = false,
    override var useDiscreteGpu: Boolean = false,
    override var useZink: Boolean = false,
    override var disableGlThreadedOpt: Boolean = false,
    override var enableEnvironmentVariables: Boolean = false,
    override var environmentVariables: MutableMap<String, String> = mutableMapOf(),

    override var minMemory: Int = 1024,
    override var maxMemory: Int = if (OSUtils.getTotalMemoryGB() > 15) 4096 else 2048,
    override var javaPath: String = "",
    override var jvmArguments: String = "",
    override var maximumResolution: Boolean = false,
    override var resolutionWidth: Int = 854,
    override var resolutionHeight: Int = 480
) : LauncherSharedOptions {

    fun <T> getSharedJavaValue(sharedOptions: (LauncherSharedOptions) -> T): T {
        return sharedOptions(if (useLauncherJavaOption) MCSRLauncher.options else this)
    }

    fun <T> getSharedResolutionValue(sharedOptions: (LauncherSharedOptions) -> T): T {
        return sharedOptions(if (useLauncherResolutionOption) MCSRLauncher.options else this)
    }

    fun <T> getSharedWorkaroundValue(sharedOptions: (LauncherSharedOptions) -> T): T {
        return sharedOptions(
            if (useLauncherWorkarounds) MCSRLauncher.options else this
        )
    }

    fun save(instanceId: String) {
        val json = kotlinx.serialization.json.Json {
            prettyPrint = true
            encodeDefaults = true
        }
        val instancePath = com.redlimerl.mcsrlauncher.launcher.InstanceManager.INSTANCES_PATH
            .resolve(instanceId)
            .resolve("instance.json")
        org.apache.commons.io.FileUtils.writeStringToFile(
            instancePath.toFile(),
            json.encodeToString(this),
            Charsets.UTF_8
        )
    }
}
