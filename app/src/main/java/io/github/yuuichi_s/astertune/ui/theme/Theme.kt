/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 O﻿ute﻿rTu﻿ne Project
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */
package io.github.yuuichi_s.astertune.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import coil3.toUri
import io.github.yuuichi_s.astertune.playback.PlayerConnection
import io.github.yuuichi_s.astertune.utils.LocalArtworkPath
import io.github.yuuichi_s.astertune.utils.coilCoroutine
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.score.Score
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

val DefaultThemeColor = Color(0xFFED5564)

// Preset accent colors the user can choose from. Each value is a seed color; the full Material3
// color scheme is generated from it via SchemeTonalSpot.
val PresetThemeColors = listOf(
    Color(0xFFED5564), // red
    Color(0xFFEC407A), // pink
    Color(0xFFAB47BC), // purple
    Color(0xFF7E57C2), // deep purple
    Color(0xFF5C6BC0), // indigo
    Color(0xFF42A5F5), // blue
    Color(0xFF26C6DA), // cyan
    Color(0xFF26A69A), // teal
    Color(0xFF66BB6A), // green
    Color(0xFF9CCC65), // lime
    Color(0xFFFFCA28), // yellow
    Color(0xFFFFA726), // orange
    Color(0xFF8D6E63), // brown
)

@Composable
fun AsterTuneTheme(
    context: Context,
    playerConnection: PlayerConnection?,
    enableDynamicTheme: Boolean,
    isSystemInDarkTheme: Boolean,
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    customTheme: Boolean = false,
    customThemeColor: Color = DefaultThemeColor,
    content: @Composable () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val highContrast = rememberSystemHighContrast(context)

    var themeColor by rememberSaveable(stateSaver = ColorSaver) {
        mutableStateOf(DefaultThemeColor)
    }

    LaunchedEffect(playerConnection, enableDynamicTheme, isSystemInDarkTheme, customTheme, customThemeColor) {
        // A user-selected accent color takes priority over artwork-based and system dynamic theming.
        if (customTheme) {
            themeColor = customThemeColor
            return@LaunchedEffect
        }
        val playerConnection = playerConnection
        if (!enableDynamicTheme || playerConnection == null) {
            themeColor = DefaultThemeColor
            return@LaunchedEffect
        }
                playerConnection.service.currentMediaMetadata.collectLatest { song ->
                    coroutineScope.launch(coilCoroutine) {
                        var ret = DefaultThemeColor
                        if (song != null) {
                            val uri = (if (song.isLocal) song.localPath else song.thumbnailUrl)?.toUri()
                            if (uri != null) {
                                val model = if (uri.toString().startsWith("/storage/")) {
                                    LocalArtworkPath(uri.toString(), 100, 100)
                                } else {
                                    uri
                                }

                                val result = context.imageLoader.execute(
                                    ImageRequest.Builder(context)
                                        .data(model)
                                        .allowHardware(false)
                                        .build()
                                )

                                ret = result.image?.toBitmap()?.extractThemeColor() ?: DefaultThemeColor
                            }
                        }
                        themeColor = ret
                    }
                }
    }


    val colorScheme = remember(darkTheme, pureBlack, themeColor, customTheme, highContrast) {
       // When customTheme is on, always use SchemeTonalSpot even if the chosen color happens to
       // equal DefaultThemeColor, so it does not fall through to the system Material You branch.
       if (!customTheme && themeColor == DefaultThemeColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val systemTheme = if (darkTheme) {
                dynamicDarkColorScheme(context).pureBlack(pureBlack)
            } else {
                dynamicLightColorScheme(context)
            }


            // Use a surface background and secondary foreground for secondary containers
            // in the high-contrast system color scheme.
            if (highContrast) {
                systemTheme.copy(
                    secondaryContainer = systemTheme.surfaceContainerHigh,
                    onSecondaryContainer = systemTheme.secondary,
                )
            } else {
                systemTheme
            }
        } else {
            SchemeTonalSpot(Hct.fromInt(themeColor.toArgb()), darkTheme, 0.0)
                .toColorScheme()
                .pureBlack(darkTheme && pureBlack)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}

/**
 * Detects whether Android's system contrast is set to medium or higher and keeps it up to date.
 *
 * The contrast setting and its change listener are only available on API 34+; on older versions
 * (which have no such setting) this always returns false.
 */
@Composable
fun rememberSystemHighContrast(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        return false
    }

    val uiModeManager = remember(context) {
        context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    }
    var highContrast by remember { mutableStateOf(uiModeManager.contrast >= 0.5f) }

    DisposableEffect(uiModeManager) {
        val listener = UiModeManager.ContrastChangeListener { contrast ->
            highContrast = contrast >= 0.5f
        }
        uiModeManager.addContrastChangeListener(ContextCompat.getMainExecutor(context), listener)
        onDispose {
            uiModeManager.removeContrastChangeListener(listener)
        }
    }

    return highContrast
}

fun Bitmap.extractThemeColor(): Color {
    val colorsToPopulation = Palette.from(this)
        .maximumColorCount(8)
        .generate()
        .swatches
        .associate { it.rgb to it.population }
    val rankedColors = Score.score(colorsToPopulation)
    return Color(rankedColors.first())
}

fun Bitmap.extractGradientColors(): List<Color> {
    val extractedColors = Palette.from(this)
        .maximumColorCount(16)
        .generate()
        .swatches
        .associate { it.rgb to it.population }

    val orderedColors = Score.score(extractedColors, 2, 0xff4285f4.toInt(), true)
        .sortedByDescending { Color(it).luminance() }

    return if (orderedColors.size >= 2)
        listOf(Color(orderedColors[0]), Color(orderedColors[1]))
    else
        listOf(Color(0xFF595959), Color(0xFF0D0D0D))
}

fun DynamicScheme.toColorScheme() = ColorScheme(
    primary = Color(primary),
    onPrimary = Color(onPrimary),
    primaryContainer = Color(primaryContainer),
    onPrimaryContainer = Color(onPrimaryContainer),
    inversePrimary = Color(inversePrimary),
    secondary = Color(secondary),
    onSecondary = Color(onSecondary),
    secondaryContainer = Color(secondaryContainer),
    onSecondaryContainer = Color(onSecondaryContainer),
    tertiary = Color(tertiary),
    onTertiary = Color(onTertiary),
    tertiaryContainer = Color(tertiaryContainer),
    onTertiaryContainer = Color(onTertiaryContainer),
    background = Color(background),
    onBackground = Color(onBackground),
    surface = Color(surface),
    onSurface = Color(onSurface),
    surfaceVariant = Color(surfaceVariant),
    onSurfaceVariant = Color(onSurfaceVariant),
    surfaceTint = Color(primary),
    inverseSurface = Color(inverseSurface),
    inverseOnSurface = Color(inverseOnSurface),
    error = Color(error),
    onError = Color(onError),
    errorContainer = Color(errorContainer),
    onErrorContainer = Color(onErrorContainer),
    outline = Color(outline),
    outlineVariant = Color(outlineVariant),
    scrim = Color(scrim),
    surfaceBright = Color(surfaceBright),
    surfaceDim = Color(surfaceDim),
    surfaceContainer = Color(surfaceContainer),
    surfaceContainerHigh = Color(surfaceContainerHigh),
    surfaceContainerHighest = Color(surfaceContainerHighest),
    surfaceContainerLow = Color(surfaceContainerLow),
    surfaceContainerLowest = Color(surfaceContainerLowest),
    primaryFixed = Color(primaryFixed),
    primaryFixedDim = Color(primaryFixedDim),
    onPrimaryFixed = Color(onPrimaryFixed),
    onPrimaryFixedVariant = Color(onPrimaryFixedVariant),
    secondaryFixed = Color(secondaryFixed),
    secondaryFixedDim = Color(secondaryFixedDim),
    onSecondaryFixed = Color(onSecondaryFixed),
    onSecondaryFixedVariant = Color(onSecondaryFixedVariant),
    tertiaryFixed = Color(tertiaryFixed),
    tertiaryFixedDim = Color(tertiaryFixedDim),
    onTertiaryFixed = Color(onTertiaryFixed),
    onTertiaryFixedVariant = Color(onTertiaryFixedVariant),
)

fun ColorScheme.pureBlack(apply: Boolean) =
    if (apply) copy(
        surface = Color.Black,
        background = Color.Black
    ) else this

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}
