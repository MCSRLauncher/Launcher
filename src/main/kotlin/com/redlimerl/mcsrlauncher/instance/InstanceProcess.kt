package com.redlimerl.mcsrlauncher.instance

import com.redlimerl.mcsrlauncher.MCSRLauncher
import com.redlimerl.mcsrlauncher.data.instance.BasicInstance

class InstanceProcess(val instance: BasicInstance, val process: Process) {

    init {
        MCSRLauncher.GAME_PROCESSES.add(this)
        this.instance.onLaunch()
        val exitCode = process.waitFor()
        Thread.sleep(1000L)
        onExit(exitCode)
    }

    fun onExit(code: Int) {
        MCSRLauncher.GAME_PROCESSES.remove(this)
        this.instance.onProcessExit(code)
    }

    fun exit() {
        process.destroy()
    }

}