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
            crossfadeSeconds = values["crossfadeSeconds"]?.toFloatOrNull()?.coerceIn(0f, 12f) ?: 0f,
            equalizerEnabled = values["equalizerEnabled"]?.toBooleanStrictOrNull() ?: false,
            eqBass = values["eqBass"]?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.50f,
            eqMid = values["eqMid"]?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.50f,
            eqTreble = values["eqTreble"]?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.50f,
            eqPresetName = if ("eqPreset" in values) values["eqPreset"]?.takeIf { it.isNotBlank() } else "Flat",
            accent = values["accent"] ?: "amber",
            trueBlack = values["trueBlack"]?.toBooleanStrictOrNull() ?: false,
            debugPro = values["debugPro"]?.toBooleanStrictOrNull() ?: false,
        )
    }

    suspend fun setMetadataFetchPaused(paused: Boolean) {
        settingsDao.upsert(SettingEntity("metadataFetchPaused", paused.toString()))
    }

    suspend fun metadataFetchPaused(): Boolean =
        settingsDao.valueFor("metadataFetchPaused")?.toBooleanStrictOrNull() ?: false

    suspend fun setDiscoveryIntensity(value: Float) {
        settingsDao.upsert(SettingEntity("discoveryIntensity", value.coerceIn(0f, 1f).toString()))
    }

    suspend fun setRandomnessIntensity(value: Float) {
        settingsDao.upsert(SettingEntity("randomnessIntensity", value.coerceIn(0f, 1f).toString()))
    }

    suspend fun setGaplessPlayback(enabled: Boolean) {
        settingsDao.upsert(SettingEntity("gaplessPlayback", enabled.toString()))
    }

    suspend fun setCrossfadeSeconds(seconds: Float) {
        val clamped = seconds.coerceIn(0f, 12f)
        settingsDao.upsert(SettingEntity("crossfadeSeconds", clamped.toString()))
    }

    suspend fun setEqualizerEnabled(enabled: Boolean) {
        settingsDao.upsert(SettingEntity("equalizerEnabled", enabled.toString()))
    }

    suspend fun setEqBand(key: String, value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        settingsDao.upsert(SettingEntity(key, clamped.toString()))
    }

    suspend fun setEqPreset(name: String?) {
        settingsDao.upsert(SettingEntity("eqPreset", name.orEmpty()))
    }

    suspend fun setAccent(key: String) {
        settingsDao.upsert(SettingEntity("accent", key))
    }

    suspend fun setTrueBlack(enabled: Boolean) {
        settingsDao.upsert(SettingEntity("trueBlack", enabled.toString()))
    }

    suspend fun setDebugPro(enabled: Boolean) {
        settingsDao.upsert(SettingEntity("debugPro", enabled.toString()))
    }

    /**
     * The playback service reads SharedPreferences, so it gets the *effective* values (what the
     * current plan allows) while Room keeps what the user chose.
     */
    fun mirrorEffective(effective: AppSettings) {
        prefs.edit()
            .putFloat("crossfadeSeconds", effective.crossfadeSeconds)
            .putBoolean("equalizerEnabled", effective.equalizerEnabled)
            .putFloat("eqBass", effective.eqBass)
            .putFloat("eqMid", effective.eqMid)
            .putFloat("eqTreble", effective.eqTreble)
            .apply()
    }
}
