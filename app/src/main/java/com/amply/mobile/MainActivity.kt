package com.amply.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amply.mobile.pro.AccentColor
import com.amply.mobile.ui.theme.AmplyTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Only on a fresh start: after a rotation the intent was already handled.
        if (savedInstanceState == null) viewModel.handleIntent(intent)
        setContent {
            val settings by viewModel.effectiveSettings.collectAsStateWithLifecycle()
            val accent = AccentColor.of(settings.accent)
            AmplyTheme(accentLight = Color(accent.light), accentDark = Color(accent.dark), trueBlack = settings.trueBlack) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AmplyPermissionGate(viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.handleIntent(intent)
    }

    companion object {
        /** Used by the notification, lock screen and widget to open straight into Now Playing. */
        const val ACTION_OPEN_NOW_PLAYING = "com.amply.mobile.OPEN_NOW_PLAYING"
    }
}
