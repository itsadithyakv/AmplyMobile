package com.amply.mobile.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/** Sizes are typical Pixel launcher cells in dp (portrait); the point is no layout floats in empty space. */
class WidgetSizesTest {
    @Test
    fun eachGridSizeGetsTheLayoutThatFillsIt() {
        assertEquals(WidgetVariant.Small, chooseWidgetVariant(300f, 80f)) // 4x1
        assertEquals(WidgetVariant.Small, chooseWidgetVariant(300f, 105f)) // 4x1 on a tall-cell launcher: medium got clipped here
        assertEquals(WidgetVariant.Small, chooseWidgetVariant(230f, 80f)) // 3x1
        assertEquals(WidgetVariant.Cover, chooseWidgetVariant(150f, 150f)) // 2x2
        assertEquals(WidgetVariant.Medium, chooseWidgetVariant(230f, 150f)) // 3x2
        assertEquals(WidgetVariant.Cover, chooseWidgetVariant(300f, 150f)) // 4x2
        assertEquals(WidgetVariant.Cover, chooseWidgetVariant(300f, 187f)) // 4x2, Pixel grid
        assertEquals(WidgetVariant.Cover, chooseWidgetVariant(230f, 285f)) // 3x3
        assertEquals(WidgetVariant.CoverWithMixes, chooseWidgetVariant(300f, 285f)) // 4x3, the size that looked empty
        assertEquals(WidgetVariant.CoverWithMixes, chooseWidgetVariant(300f, 420f)) // 4x4
    }

    @Test
    fun tooSmallFallsBackToTheSmallestLayout() {
        assertEquals(WidgetVariant.Small, chooseWidgetVariant(100f, 30f))
    }
}
