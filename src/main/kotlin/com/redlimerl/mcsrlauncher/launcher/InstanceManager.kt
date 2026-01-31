package com.redlimerl.mcsrlauncher.launcher

import com.redlimerl.mcsrlauncher.MCSRLauncher
import com.redlimerl.mcsrlauncher.MCSRLauncher.JSON
import com.redlimerl.mcsrlauncher.data.instance.BasicInstance
import com.redlimerl.mcsrlauncher.data.instance.FabricVersionData
import com.redlimerl.mcsrlauncher.data.instance.LWJGLVersionData
import com.redlimerl.mcsrlauncher.data.instance.mcsrranked.MCSRRankedPackType
import java.nio.file.Path

object InstanceManager {

    const val DEFAULT_GROUP = ""
    val INSTANCES_PATH: Path = MCSRLauncher.BASE_PATH.resolve("instances")

    private val oldConfigPath: Path = MCSRLauncher.BASE_PATH.resolve("instances.json")
    val instances = linkedMapOf<String, ArrayList<BasicInstance>>()

    fun loadAll() {
        migrateOldConfig()

        INSTANCES_PATH.toFile().mkdirs()
        for (file in INSTANCES_PATH.toFile().listFiles()!!) {
            if (file.exists() && file.isDirectory) {
                val configFile = file.toPath().resolve("instance.json").toFile()
                if (configFile.exists()) addInstance(JSON.decodeFromString(configFile.readText()), true)
            }
        }
        updateInstancesSort()
    }

    fun getNewInstanceName(string: String): String {
        val replacedName = string.replace(" ", "_").replace(Regex("[^\\p{L}\\p{N}_.]"), "")
        val allInstances = instances.values.flatten()
        if (allInstances.find { it.id == replacedName } == null) return replacedName

        var appendNumber = 2
        while (true) {
            val newName = "${replacedName}${appendNumber}"
            if (allInstances.find { it.id == newName } == null) return newName
            appendNumber++
        }
    }

    fun createInstance(
        name: String,
        group: String?,
        minecraftVersion: String,
        lwjglVersion: LWJGLVersionData,
        fabricVersion: FabricVersionData?,
        mcsrRankedPackType: MCSRRankedPackType?
    ): BasicInstance {
        return BasicInstance(
            getNewInstanceName(name),
            name,
            group ?: DEFAULT_GROUP,
            minecraftVersion,
            lwjglVersion,
            fabricVersion,
            mcsrRankedPackType
        ).also { addInstance(it) }
    }

    fun renameInstance(instance: BasicInstance, name: String) {
        instance.updateName(name)
        instance.save()
        refreshInstanceList()
    }

    fun moveInstanceGroup(instance: BasicInstance, group: String?) {
        for (key in instances.keys) {
            instanceLoop@ for (basicInstance in instances[key]!!) {
                if (instance == basicInstance) {
                    instances[key]?.remove(instance)
                    break@instanceLoop
                }
            }
            if (instances[key].isNullOrEmpty()) instances.remove(key)
        }

        instances.getOrPut(group ?: DEFAULT_GROUP) { arrayListOf() }.add(instance)
        instance.group = group ?: DEFAULT_GROUP
        instance.save()
        refreshInstanceList()
    }

    fun getInstanceGroup(instance: BasicInstance): String {
        for (key in instances.keys) {
            instanceLoop@ for (basicInstance in instances[key]!!) {
                if (instance == basicInstance) {
                    return key
                }
            }
        }
        throw IllegalArgumentException("instance does not exist in InstanceManager")
    }

    fun getInstance(name: String): BasicInstance? {
        for (key in instances.keys) {
            instanceLoop@ for (basicInstance in instances[key]!!) {
                if (basicInstance.id == name) {
                    return basicInstance
                }
            }
        }
        return null
    }

    private fun addInstance(instance: BasicInstance, preload: Boolean = false) {
        instances.getOrPut(instance.group) { arrayListOf() }.add(instance)
        instance.onCreate()
        instance.save()
        if (!preload) refreshInstanceList()
    }

    fun deleteInstance(instance: BasicInstance) {
        for (key in instances.keys) {
            instanceLoop@ for (basicInstance in instances[key]!!) {
                if (instance == basicInstance) {
                    instances[key]?.remove(instance)
                    instance.getInstancePath().toFile().also {
                        if (it.exists()) it.deleteRecursively()
                    }
                    break@instanceLoop
                }
            }
            if (instances[key].isNullOrEmpty()) instances.remove(key)
        }
        refreshInstanceList()
    }

    private fun updateInstancesSort() {
        instances.forEach { (_, instanceList) ->
            instanceList.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
        }

        val sorted = instances.toSortedMap(String.CASE_INSENSITIVE_ORDER)
        instances.clear()
        instances.putAll(sorted)
    }

    fun refreshInstanceList() {
        updateInstancesSort()
        MCSRLauncher.refreshInstanceList()
    }

    private fun migrateOldConfig() {
        if (!oldConfigPath.toFile().exists()) return

        val oldConfigs = JSON.decodeFromString<Map<String, ArrayList<BasicInstance>>>(oldConfigPath.toFile().readText())
        for (groupEntry in oldConfigs) {
            for (instance in groupEntry.value) {
                instance.group = groupEntry.key
                instance.save()
            }
        }
        oldConfigPath.toFile().delete()
    }
}
