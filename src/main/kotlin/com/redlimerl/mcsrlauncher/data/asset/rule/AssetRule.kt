package com.redlimerl.mcsrlauncher.data.asset.rule

import kotlinx.serialization.Serializable

@Serializable
data class AssetRule(
    val action: AssetRuleAction,
    val os: AssetRuleOS? = null,
    val features: AssetRuleFeatures? = null
) {
    fun applies(): Boolean {
        if (this.os != null && !this.os.apply()) return false
        return true
    }
}

fun List<AssetRule>.evaluate(): Boolean {
    if (isEmpty()) return true
    var allowed = false
    for (rule in this) {
        if (rule.applies()) {
            allowed = rule.action == AssetRuleAction.ALLOW
        }
    }
    return allowed
}