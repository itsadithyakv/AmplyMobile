package com.amply.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.amply.mobile.pro.ProFeature
import com.amply.mobile.pro.ProState
import com.amply.mobile.ui.theme.NeuDepth
import com.amply.mobile.ui.theme.Palette
import com.amply.mobile.ui.theme.neuPressed
import com.amply.mobile.ui.theme.neuRaised

/** "FREE" / "PRO" pill next to the wordmark, as on the desktop sidebar. */
@Composable
fun PlanBadge(isPro: Boolean, modifier: Modifier = Modifier) {
    val p = Palette
    Text(
        if (isPro) "PRO" else "FREE",
        color = if (isPro) p.accent else p.textSecondary,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.08.em),
        modifier = modifier.neuPressed(CircleShape).padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

/** Small lock shown next to a Pro-only control on the free plan. */
@Composable
fun ProLock(modifier: Modifier = Modifier) {
    Icon(Icons.Rounded.Lock, contentDescription = "Amply Pro", tint = Palette.accent, modifier = modifier.size(14.dp))
}

/**
 * The Pro sheet. One-time purchase through Google Play; "Restore purchase" picks it up on another
 * phone with the same Google account. The feature that opened the sheet is listed first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProPaywallSheet(
    trigger: ProFeature,
    pro: ProState,
    isPro: Boolean,
    onBuy: () -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val p = Palette
    val features = listOf(trigger) + ProFeature.entries.filter { it != trigger }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.bg) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(64.dp).neuRaised(CircleShape, NeuDepth.Raised), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = p.accent, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(if (isPro) "You have Amply Pro" else "Amply Pro", style = MaterialTheme.typography.headlineMedium)
            Text(
                if (isPro) "Thank you for supporting Amply." else "One purchase, yours for good. No subscription, no ads, and Amply stays free without it.",
                color = p.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(18.dp))
            Column(
                Modifier.fillMaxWidth().neuRaised(RadiusLg, NeuDepth.Raised).padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                features.forEach { feature ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = p.accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(feature.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text(feature.description, color = p.textSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            pro.message?.let {
                Text(it, color = p.textSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
            }
            if (!isPro) {
                NeuButton(
                    label = when {
                        pro.busy -> "Opening Google Play..."
                        pro.price != null -> "Get Amply Pro - ${pro.price}"
                        else -> "Get Amply Pro"
                    },
                    icon = null,
                    onClick = onBuy,
                    accent = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!pro.available) {
                    Text(
                        "Purchases work in the version installed from Google Play.",
                        color = p.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                TextButton(onClick = onRestore) { Text("Restore purchase", color = p.textSecondary) }
            }
        }
    }
}
