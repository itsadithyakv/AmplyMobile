package com.amply.mobile.ui.theme

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * The desktop's neumorphic recipes (`--neu-lift`, `--neu-pressed-shadow` in src/index.css), at the
 * softened 1.8.1 depth. Apply these BEFORE `clip()` in a modifier chain: clipping first would cut
 * the outer shadows off.
 */
enum class NeuDepth(val offset: Dp, val blur: Dp) {
    /** --neu-lift: cards, panels, the player bar. */
    Raised(3.dp, 8.dp),

    /** --neu-lift-sm: buttons, chips, rows. */
    Small(1.5.dp, 4.dp),
}

/** A surface that sits gently above the page: light from the top left, shade to the bottom right. */
@Composable
fun Modifier.neuRaised(shape: Shape, depth: NeuDepth = NeuDepth.Small, color: Color? = null): Modifier {
    val p = Palette
    return this
        .dropShadow(shape, Shadow(radius = depth.blur, color = p.shadowLight, offset = DpOffset(-depth.offset, -depth.offset)))
        .dropShadow(shape, Shadow(radius = depth.blur, color = p.shadowDark, offset = DpOffset(depth.offset, depth.offset)))
        .background(color ?: p.bg, shape)
}

/** A surface pressed into the page: selected chips and tabs, wells, inputs. */
@Composable
fun Modifier.neuPressed(shape: Shape, small: Boolean = true): Modifier {
    val p = Palette
    val offset = if (small) 1.dp else 2.dp
    val blur = if (small) 3.dp else 5.dp
    return this
        .background(p.bg, shape)
        .innerShadow(shape, Shadow(radius = blur, color = p.shadowDark, offset = DpOffset(offset, offset)))
        .innerShadow(shape, Shadow(radius = blur, color = p.shadowLight, offset = DpOffset(-offset, -offset)))
}

/** The accent button (desktop `.neu-accent`): amber fill with the small lift. */
@Composable
fun Modifier.neuAccent(shape: Shape): Modifier = neuRaised(shape, NeuDepth.Small, color = Palette.accent)
