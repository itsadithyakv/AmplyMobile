package com.amply.mobile.metadata

private val unknownGenres = setOf(
    "",
    "unknown",
    "unknown genre",
    "n/a",
    "na",
    "none",
    "unspecified",
    "other",
    "various",
)

private val genreRules = listOf(
    "Pop" to listOf("pop", "k-pop", "kpop"),
    "Hip-Hop" to listOf("hip hop", "hip-hop", "rap", "trap"),
    "Rap" to listOf("drill", "boom bap", "gangsta rap"),
    "Rock" to listOf("rock", "punk", "grunge", "metal"),
    "Alternative" to listOf("alternative", "alt ", "alt-rock", "indie rock"),
    "Electronic" to listOf("electronic", "edm", "dance", "house", "techno", "trance", "dubstep"),
    "R&B" to listOf("r&b", "rnb", "soul", "neo soul"),
    "Indie" to listOf("indie", "lofi", "lo-fi"),
    "Jazz" to listOf("jazz", "swing", "bebop"),
    "Classical" to listOf("classical", "orchestral", "symphony"),
    "Country" to listOf("country", "americana"),
    "Latin" to listOf("latin", "reggaeton", "salsa", "bachata"),
    "World" to listOf("world", "bollywood", "hindi", "indian", "afro", "afrobeat"),
)

fun normalizeGenreBucket(raw: String?): String {
    val value = raw.orEmpty().trim()
    val lower = value.lowercase()
    if (lower in unknownGenres) return "Unknown"

    genreRules.forEach { (bucket, hints) ->
        if (hints.any { lower.contains(it) }) {
            return bucket
        }
    }
    return value.ifBlank { "Unknown" }
}

fun isUnknownGenre(raw: String?): Boolean = normalizeGenreBucket(raw) == "Unknown"
