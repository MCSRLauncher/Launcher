package com.redlimerl.mcsrlauncher.data.meta.program

import com.redlimerl.mcsrlauncher.data.asset.rule.AssetRule
import com.redlimerl.mcsrlauncher.util.OSUtils
import kotlinx.serialization.Serializable
import java.net.URI

@Serializable
data class SpeedrunToolMeta(
    val id: String,
    val name: String,
    val description: String,
    val authors: List<String>,
    val downloadPage: String,
    val sources: String? = null,
    val fileFilter: String,
    val rules: List<AssetRule> = listOf()
) {
    fun open() {
        OSUtils.openURI(URI.create(this.downloadPage))
    }
}