package com.amply.mobile.data.repository

import android.content.Context
import com.amply.mobile.data.local.SettingEntity
import com.amply.mobile.data.local.SettingsDao
import com.amply.mobile.domain.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(
    context: Context,
    private val settingsDao: SettingsDao,
) {
    private val prefs = context.getSharedPreferences("amply_playback_settings", Context.MODE_PRIVATE)

    val settings: Flow<AppSettings> = settingsDao.observeSettings().map { rows ->
        val values = rows.associate { it.key to it.value }
        AppSettings(
            metadataFetchPaused = values["metadataFetchPaused"]?.toBooleanStrictOrNull() ?: false,
            discoveryIntensity = values["discoveryIntensity"]?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.35f,
            randomnessIntensity = values["randomnessIntensity"]?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.30f,
            preferSyncedLyrics = values["preferSyncedLyrics"]?.toBooleanStrictOrNull() ?: true,
            gaplessPlayback = values["gaplessPlayback"]?.toBooleanStrictOrNull() ?: true,
            equalizerEnabled = values["equalizerEnabled"]?.toBooleanStrictOrNull() ?: false,
            eqBass = values["eqBass"]?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.50f,
            eqMid = values["eqMid"]?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.50f,
            eqTreble = values["eqTreble"]?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.50f,
        )
    }

    suspend fun setMetadataFetchPaused(paused: Boolean) {
        settingsDao.upsert(SettingEntity("metadataFetchPaused", paused.toString()))
    }

    suspend fun setDiscoveryIntensity(value: Float) {
        settingsDao.upsert(SettingEntity("discoveryIntensity", value.coerceIn(0f, 1f).toString()))
    }

    suspend fun setRandomnessIntensity(value: Float) {
        settingsDao.upsert(SettingEntity("randomnessIntensity", value.coerceIn(0f, 1f).toString()))
    }

    suspend fun setGaplessPlayback(enabled: Boolean) {
        prefs.edit().putBoolean("gaplessPlayback", enabled).apply()
        settingsDao.upsert(SettingEntity("gaplessPlayback", enabled.toString()))
    }

    suspend fun setEqualizerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("equalizerEnabled", enabled).apply()
        settingsDao.upsert(SettingEntity("equalizerEnabled", enabled.toString()))
    }

    suspend fun setEqBand(key: String, value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        prefs.edit().putFloat(key, clamped).apply()
        settingsDao.upsert(SettingEntity(key, clamped.toString()))
    }
}
