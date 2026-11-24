package io.ingwe.dash.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import io.ingwe.dash.ui.navigation.IngweNavHost
import io.ingwe.dash.ui.theme.IngweTheme
import io.ingwe.dash.ui.theme.IngweThemeState

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep screen on — critical for the head unit
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("ingwe", MODE_PRIVATE)
        IngweThemeState.isLight = prefs.getBoolean(KEY_LIGHT, false)

        setContent {
            // Save the theme whenever it changes.
            LaunchedEffect(Unit) {
                snapshotFlow { IngweThemeState.isLight }.collect { prefs.edit().putBoolean(KEY_LIGHT, it).apply() }
            }
            IngweTheme {
                IngweNavHost()
            }
        }
    }

    private companion object {
        const val KEY_LIGHT = "theme_light"
    }
}
