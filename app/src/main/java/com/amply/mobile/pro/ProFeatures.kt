package com.amply.mobile.pro

import com.amply.mobile.domain.AppSettings

/**
 * What Amply Pro adds on the phone. Same line as the desktop (src/services/proFeatures.ts):
 * everything needed to own and play a library stays free; Pro is fine control, extra discovery and
 * the year-in-music extras. Desktop-only items (mini player, Game Mode, tag editor, visualizer
 * styles, duplicate finder) have no phone counterpart yet.
 */
enum class ProFeature(val title: String, val description: String) {
    Themes("Accent colours and true black", "Eight accents and an OLED-black dark mode."),
    SmartRules("Smart playlists", "Playlists that fill themselves from rules and stay up to date."),
    Encore("Amply Encore", "Your year in music as a deck of cards, with a mix of the songs that made it."),
    Equalizer("Full equalizer", "Your own curve plus the Warm and Club presets."),
    Fade("Fade between songs", "Smooth fades from one song into the next."),
    MoreMixes("Genre and mood mixes", "Mixes for every genre and mood in your library."),
    FullStats("30-day and all-time stats", "See further back than the last week."),
    MixTuning("Mix tuning", "Set how adventurous and how random your mixes are."),
}

const val FREE_DISCOVERY = 0.35f
const val FREE_RANDOMNESS = 0.30f
val PRO_EQ_PRESETS = setOf("Warm", "Club")

/**
 * The settings a free install runs on. Saved settings always keep what the user chose; free
 * installs play with this limited copy, so buying Pro brings every choice back unchanged.
 */
fun limitSettings(settings: AppSettings, isPro: Boolean): AppSettings {
    if (isPro) return settings
    val customEq = settings.equalizerEnabled && settings.eqPresetName.let { it == null || it in PRO_EQ_PRESETS }
    return settings.copy(
        accent = AccentColor.Amber.key,
        trueBlack = false,
        crossfadeSeconds = 0f,
        discoveryIntensity = FREE_DISCOVERY,
        randomnessIntensity = FREE_RANDOMNESS,
        eqBass = if (customEq) 0.5f else settings.eqBass,
        eqMid = if (customEq) 0.5f else settings.eqMid,
        eqTreble = if (customEq) 0.5f else settings.eqTreble,
    )
}

/** Pro features these saved settings were using, for the one-time "this is now Pro" notice. */
fun proFeaturesInUse(settings: AppSettings): List<ProFeature> = buildList {
    if (settings.accent != AccentColor.Amber.key || settings.trueBlack) add(ProFeature.Themes)
    if (settings.crossfadeSeconds > 0.1f) add(ProFeature.Fade)
    if (settings.equalizerEnabled && settings.eqPresetName.let { it == null || it in PRO_EQ_PRESETS }) add(ProFeature.Equalizer)
    if (settings.discoveryIntensity != FREE_DISCOVERY || settings.randomnessIntensity != FREE_RANDOMNESS) add(ProFeature.MixTuning)
}

/** Desktop accent set (index.css `data-accent`), light and dark variants. */
enum class AccentColor(val key: String, val label: String, val light: Long, val dark: Long) {
    Amber("amber", "Amber", 0xFFEB7E23, 0xFFF08A34),
    Rose("rose", "Rose", 0xFFD23662, 0xFFF47092),
    Violet("violet", "Violet", 0xFF7252E2, 0xFFA48CFA),
    Blue("blue", "Blue", 0xFF286ED8, 0xFF66A0FA),
    Teal("teal", "Teal", 0xFF119B90, 0xFF38C4B4),
    Green("green", "Green", 0xFF3F9C50, 0xFF6EC67A),
    Gold("gold", "Gold", 0xFFB28013, 0xFFECBA46),
    Graphite("graphite", "Graphite", 0xFF464C5A, 0xFFD6DAE2),
    ;

    companion object {
        fun of(key: String?): AccentColor = entries.firstOrNull { it.key == key } ?: Amber
    }
}

/** Genre and mood mixes are Pro; the core daily mixes and artist radios stay free (desktop isProMix). */
fun isProMix(playlistId: String): Boolean =
    playlistId.startsWith("smart_genre_") || playlistId.startsWith("smart_mood_")
