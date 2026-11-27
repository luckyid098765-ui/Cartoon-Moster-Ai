package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.CartoonLanguage
import com.example.model.CartoonStyle
import com.example.model.VideoQuality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studioDataStore by preferencesDataStore(name = "cartoon_studio_prefs")

data class StudioPreferences(
    val defaultStyle: CartoonStyle = CartoonStyle.THREE_D,
    val defaultLanguage: CartoonLanguage = CartoonLanguage.ENGLISH,
    val defaultQuality: VideoQuality = VideoQuality.HD_720P,
    val autoCleanTempFiles: Boolean = true
)

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val STYLE = stringPreferencesKey("default_style")
        val LANGUAGE = stringPreferencesKey("default_language")
        val QUALITY = stringPreferencesKey("default_quality")
        val AUTO_CLEAN = booleanPreferencesKey("auto_clean_temp")
    }

    val preferencesFlow: Flow<StudioPreferences> = context.studioDataStore.data.map { prefs ->
        StudioPreferences(
            defaultStyle = CartoonStyle.safeValueOf(prefs[Keys.STYLE]),
            defaultLanguage = CartoonLanguage.safeValueOf(prefs[Keys.LANGUAGE]),
            defaultQuality = VideoQuality.safeValueOf(prefs[Keys.QUALITY]),
            autoCleanTempFiles = prefs[Keys.AUTO_CLEAN] ?: true
        )
    }

    suspend fun saveDefaults(
        style: CartoonStyle,
        language: CartoonLanguage,
        quality: VideoQuality
    ) {
        context.studioDataStore.edit { prefs ->
            prefs[Keys.STYLE] = style.name
            prefs[Keys.LANGUAGE] = language.name
            prefs[Keys.QUALITY] = quality.name
        }
    }
}
