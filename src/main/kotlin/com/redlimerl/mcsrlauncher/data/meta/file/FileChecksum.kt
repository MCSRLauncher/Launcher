package com.redlimerl.mcsrlauncher.data.meta.file

import kotlinx.serialization.Serializable

@Serializable
data class FileChecksum(
    val type: String,
    val hash: String
)