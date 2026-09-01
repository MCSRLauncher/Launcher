package com.redlimerl.mcsrlauncher.data.instance

import kotlinx.serialization.Serializable

@Serializable
data class MMCPackData(
    val components: List<Component>,
    val formatVersion: Int = 1
)

@Serializable
data class Component(
    val cachedName: String,
    val cachedRequires: List<CachedRequirement>? = null,
    val cachedVersion: String,
    val cachedVolatile: Boolean? = null,
    val dependencyOnly: Boolean? = null,
    val important: Boolean? = null,
    val uid: String,
    val version: String
)

@Serializable
data class CachedRequirement(
    val equals: String? = null,
    val suggests: String? = null,
    val uid: String
)
