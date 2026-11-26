package io.ingwe.dash

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.ingwe.dash.data.transport.MockTransport
import io.ingwe.dash.data.vehicle.VehicleRepository
import io.ingwe.dash.ui.components.FitToDesignSize
import io.ingwe.dash.ui.dashboard.DashboardScreen
import io.ingwe.dash.ui.dtc.FaultStore
import io.ingwe.dash.ui.theme.IngweTheme
import io.ingwe.dash.ui.theme.IngweThemeState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real dashboard pages to PNGs on the JVM (Robolectric native graphics),
 * at head-unit screen sizes, so layout problems can be reviewed without the truck.
 *
 * Output: app/build/screenshots/<size>/<page>.png
 * Run:    .\gradlew.bat testDebugUnitTest --tests "io.ingwe.dash.Screenshot*"
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
abstract class ScreenshotBase(private val sizeName: String) {
    @get:Rule val compose = createComposeRule()

    protected fun shot(page: Int, name: String, light: Boolean = false) {
        IngweThemeState.isLight = light
        compose.mainClock.autoAdvance = false
        val repo = VehicleRepository(loadSignals = { emptyList() }, adapterTransport = { MockTransport() })
        compose.setContent {
            IngweTheme {
                FitToDesignSize { DashboardScreen(rememberNavController(), FaultStore(), repo, initialPage = page) }
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
        compose.onRoot().captureRoboImage("build/screenshots/$sizeName/$name${if (light) "-light" else ""}.png")
    }

    @Test fun cluster() = shot(0, "cluster")
    @Test fun driveline() = shot(2, "driveline")
    @Test fun offroad() = shot(3, "offroad")
    @Test fun economy() = shot(1, "economy")
    @Test fun air() = shot(4, "air")
    @Test fun engine() = shot(5, "engine")
}

/** Design target: 1280×720 dp. Also captures light mode. */
@Config(qualifiers = "w1280dp-h720dp-land-mdpi")
class Screenshot1280x720 : ScreenshotBase("1280x720") {
    @Test fun clusterLight() = shot(0, "cluster", light = true)
    @Test fun economyLight() = shot(1, "economy", light = true)
    @Test fun engineLight() = shot(5, "engine", light = true)
}

/** 1280×720 px panel at 1.5× density = 853×480 dp — a common head-unit setup. */
@Config(qualifiers = "w853dp-h480dp-land-hdpi")
class Screenshot853x480 : ScreenshotBase("853x480")

/** 1024×600 head units at mdpi. */
@Config(qualifiers = "w1024dp-h600dp-land-mdpi")
class Screenshot1024x600 : ScreenshotBase("1024x600")
