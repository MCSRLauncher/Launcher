package com.redlimerl.mcsrlauncher.data.launcher

import kotlinx.serialization.Serializable
import java.util.*

@Serializable
enum class LauncherLanguage(val languageName: String, val localLanguageName: String, val languageCode: String) {
    ENGLISH("English", "English", "en_US"),
    KOREAN("Korean", "한국어", "ko_KR"),
    JAPANESE("Japanese", "日本語", "ja_JP");

    override fun toString(): String {
        return "$languageName ($localLanguageName)"
    }

    fun getLocale(): Locale {
        val localeSplit = this.languageCode.split("_")
        return Locale(localeSplit[0], localeSplit[1])
    }
}