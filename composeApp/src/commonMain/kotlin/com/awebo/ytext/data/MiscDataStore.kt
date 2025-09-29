package com.awebo.ytext.data


import YTExt.composeApp.BuildConfig
import java.time.Instant
import java.util.prefs.Preferences


enum class AppLanguage(val code: String, val summarizationLanguageText: String) {
    ENGLISH("en", "English"),
    RUSSIAN("ru", "Russian");


    override fun toString(): String {
        return when (this) {
            ENGLISH -> "English"
            RUSSIAN -> "Русский"
        }
    }

    
    companion object {
        fun fromCode(code: String): AppLanguage {
            return values().find { it.code == code } ?: ENGLISH
        }
    }
}

class MiscDataStore(
    private val produceFilePath: () -> String,
) {

    private val prefs: Preferences = if (BuildConfig.IS_RELEASE_MODE) {
        Preferences.userRoot().node("com.awebo.ytext.release")
    } else {
        Preferences.userRoot().node("com.awebo.ytext.debug")
    }
    private val RELOAD_KEY = "last_reload"
    private val LANGUAGE_KEY = "app_language"
    


    fun updateReloadTime(newReloadTime: Instant) {
        prefs.putLong(RELOAD_KEY, newReloadTime.toEpochMilli())
        prefs.flush()
    }

    fun lastReload(): Instant {
        val epochMillis = prefs.getLong(RELOAD_KEY, -1L)
        return if (epochMillis != -1L) {
            Instant.ofEpochMilli(epochMillis)
        } else {
            Instant.EPOCH
        }
    }

    fun setLanguage(language: AppLanguage) {
        prefs.put(LANGUAGE_KEY, language.code)
        prefs.flush()
    }
    
    fun getLanguage(): AppLanguage {
        return AppLanguage.fromCode(prefs.get(LANGUAGE_KEY, AppLanguage.ENGLISH.code))
    }
}