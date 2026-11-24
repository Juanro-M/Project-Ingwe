package io.ingwe.dash.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ingwe.dash.R
import io.ingwe.dash.ui.components.gauges.LocalIgnitionSweep
import io.ingwe.dash.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Start-up sequence, played once per launch:
 *   1. The window background (splash_background.xml) shows the logo while the app loads.
 *   2. This overlay draws the same logo in the same place, then fades and lifts away
 *      while the wordmark fades in and out.
 *   3. Meanwhile every gauge underneath does an ignition sweep to full scale and back.
 *
 * Tap anywhere to skip. All animation values are read in the draw phase
 * (graphicsLayer / gauge draw code), so nothing recomposes per frame.
 */
@Composable
fun IgnitionIntro(content: @Composable () -> Unit) {
    var played by rememberSaveable { mutableStateOf(false) }
    val sweep = remember { Animatable(0f) }
    val overlay = remember { Animatable(if (played) 0f else 1f) }
    val wordmark = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (played) return@LaunchedEffect
        launch {
            wordmark.animateTo(1f, tween(350, delayMillis = 150))
            wordmark.animateTo(0f, tween(300, delayMillis = 350))
        }
        launch { overlay.animateTo(0f, tween(550, delayMillis = 800, easing = FastOutSlowInEasing)) }
        delay(900)
        sweep.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        delay(80)
        sweep.animateTo(0f, tween(800, easing = LinearOutSlowInEasing))
        played = true
    }

    CompositionLocalProvider(LocalIgnitionSweep provides sweep.asState()) {
        // Opaque root: the window background is the splash logo, which must not
        // show through while screens crossfade during navigation.
        Box(Modifier.fillMaxSize().background(IngweBackground)) {
            content()

            val showOverlay by remember { derivedStateOf { overlay.value > 0f } }
            if (showOverlay) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = overlay.value }
                        .background(IngweBackground)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            scope.launch { overlay.snapTo(0f); wordmark.snapTo(0f) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_logo),
                        contentDescription = null,
                        modifier = Modifier
                            .size(168.dp) // matches splash_background.xml for a seamless hand-off
                            .graphicsLayer {
                                val t = 1f - overlay.value
                                scaleX = 1f + t * 0.25f
                                scaleY = 1f + t * 0.25f
                            },
                    )
                    Text(
                        text = "INGWE",
                        style = LabelStyle.copy(fontSize = 15.sp, letterSpacing = 6.sp),
                        color = IngweTextSecondary,
                        modifier = Modifier
                            .offset(y = 76.dp)
                            .graphicsLayer {
                                alpha = wordmark.value
                                translationY = (1f - wordmark.value) * 8.dp.toPx()
                            },
                    )
                }
            }
        }
    }
}
