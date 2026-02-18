package com.redlimerl.mcsrlauncher.data.meta.program

import com.redlimerl.mcsrlauncher.data.asset.rule.AssetRule
import com.redlimerl.mcsrlauncher.util.OSUtils
import kotlinx.serialization.Serializable
import java.net.URI

@Serializable
data class SpeedrunProgramMeta(
    val id: String,
    val name: String,
    val description: String,
    val authors: List<String>,
    val downloadPage: String,
    val sources: String? = null,
    val fileFilter: String?,
    val rules: List<AssetRule> = listOf()
) {
    fun open() {
        OSUtils.openURI(URI.create(this.downloadPage))
    }

    fun shouldApply(): Boolean {
        for (rule in this.rules) {
            if (!rule.shouldAllow()) return false
        }
        return true
    }

}