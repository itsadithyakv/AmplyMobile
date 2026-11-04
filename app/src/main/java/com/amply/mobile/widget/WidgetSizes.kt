package com.amply.mobile.widget

import com.amply.mobile.R

/** The widget's layouts. `coverBacked` layouts put the artwork behind everything. */
enum class WidgetVariant(val layout: Int, val coverBacked: Boolean, val mixes: Boolean) {
    /** One row: cover, title, prev/play/next. */
    Small(R.layout.widget_player_small, coverBacked = false, mixes = false),

    /** Two rows, wide: cover beside title, progress and controls. */
    Medium(R.layout.widget_player_medium, coverBacked = false, mixes = false),

    /** Square or tall: full-bleed cover with controls on a scrim. */
    Cover(R.layout.widget_player_tall, coverBacked = true, mixes = false),

    /** Big: the cover layout plus four playlist shortcuts. */
    CoverWithMixes(R.layout.widget_player_tall, coverBacked = true, mixes = true),
}

/**
 * Minimum size (dp, width to height) for each layout, checked on the Pixel launcher: one-row
 * widgets report just over 100 dp, so the medium layout (about 108 dp of content) starts at 120
 * or its play button gets clipped. Wide two-row and taller widgets use the cover layout, which
 * fills any extra height with artwork; medium is for narrower two-row slots.
 */
val WIDGET_BREAKPOINTS: List<Pair<Pair<Float, Float>, WidgetVariant>> = listOf(
    (180f to 40f) to WidgetVariant.Small,
    (110f to 110f) to WidgetVariant.Cover,
    (200f to 120f) to WidgetVariant.Medium,
    (250f to 150f) to WidgetVariant.Cover,
    (160f to 200f) to WidgetVariant.Cover,
    (250f to 270f) to WidgetVariant.CoverWithMixes,
)

/**
 * The same rule Android 12+ applies to sized RemoteViews (RemoteViews.findBestFitLayout): among
 * the layouts that fit, the one closest to the widget's size by squared distance; if none fit,
 * the smallest. Used on Android 8-11, where the launcher only reports a size.
 */
fun chooseWidgetVariant(widthDp: Float, heightDp: Float): WidgetVariant {
    val fitting = WIDGET_BREAKPOINTS.filter { (size, _) -> size.first <= widthDp && size.second <= heightDp }
    if (fitting.isEmpty()) return WIDGET_BREAKPOINTS.minBy { (size, _) -> size.first * size.second }.second
    return fitting.minBy { (size, _) ->
        val dx = widthDp - size.first
        val dy = heightDp - size.second
        dx * dx + dy * dy
    }.second
}
