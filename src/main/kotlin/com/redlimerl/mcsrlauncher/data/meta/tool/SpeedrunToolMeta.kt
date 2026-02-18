package com.redlimerl.mcsrlauncher.data.meta.tool

import com.redlimerl.mcsrlauncher.data.asset.rule.AssetRule
import kotlinx.serialization.Serializable

@Serializable
data class SpeedrunToolMeta(
    val id: String,
    val name: String,
    val format: String,
    val homepage: String,
    val rules: List<AssetRule> = listOf(),
    val versions: List<SpeedrunToolVersion>
) {
    fun shouldApply(): Boolean {
        for (rule in this.rules) {
            if (!rule.shouldAllow()) return false
        }
        return true
    }
}