package io.github.yuuichi_s.astertune.extensions

import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import android.view.Surface
import android.view.WindowManager
import androidx.window.core.layout.WindowSizeClass
import androidx.window.layout.WindowMetricsCalculator
import io.github.yuuichi_s.astertune.constants.InnerTubeCookieKey
import io.github.yuuichi_s.astertune.constants.YtmSyncKey
import io.github.yuuichi_s.astertune.utils.dataStore
import io.github.yuuichi_s.astertune.utils.get
import com.zionhuang.innertube.utils.parseCookieString

fun Context.isAutoSyncEnabled(): Boolean {
    return dataStore.get(YtmSyncKey, true) && isUserLoggedIn()
}

fun Context.isUserLoggedIn(): Boolean {
    val cookie = dataStore.get(InnerTubeCookieKey, "")
    return "SAPISID" in parseCookieString(cookie)
}

fun Context.isInternetConnected(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
    return networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false
}

fun Context.supportsWideScreen() : Boolean {
    val config = resources.configuration
    return config.screenWidthDp >= 600
}

/**
 * Returns whether to use tablet UI mode for the current window.
 * Requires a window width of at least 600 dp, even when [tabletUi] is enabled.
 */
fun isTabMode(configuration: Configuration, tabletUi: Boolean): Boolean {
    val isTablet = configuration.smallestScreenWidthDp >= 600
    return (isTablet || tabletUi) && configuration.screenWidthDp >= 600
}

/**
 * Returns whether the player and queue use the landscape layout.
 */
fun usesLandscapePlayer(configuration: Configuration, tabMode: Boolean): Boolean {
    return configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && !tabMode &&
            configuration.screenWidthDp >= 600
}

/**
 * Returns whether to use the navigation rail instead of the bottom navigation bar.
 */
fun Context.usesNavRail(windowSizeClass: WindowSizeClass, configuration: Configuration, tabMode: Boolean): Boolean {
    if (tabMode) return false
    return windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) ||
            (usesLandscapePlayer(configuration, tabMode) && isSmallNaturalLandscapeDisplay())
}

/**
 * Returns whether the display is naturally landscape and its smallest width is below 600 dp.
 *
 * Uses maximum window bounds to avoid treating a tablet's narrow split-screen or freeform
 * window as a small display.
 */
private fun Context.isSmallNaturalLandscapeDisplay(): Boolean {
    val rotation = runCatching {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            @Suppress("DEPRECATION")
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        }
        display?.rotation
    }.getOrNull() ?: return false

    val bounds = WindowMetricsCalculator.getOrCreate().computeMaximumWindowMetrics(this).bounds
    val isLandscapeNow = bounds.width() > bounds.height()
    val isRotated = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
    val isNaturalLandscape = isLandscapeNow != isRotated

    val smallestWidthDp = minOf(bounds.width(), bounds.height()) / resources.displayMetrics.density
    return isNaturalLandscape && smallestWidthDp < 600
}

fun Context.isPowerSaver(): Boolean {
    val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isPowerSaveMode
}