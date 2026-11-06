package io.github.yuuichi_s.astertune.utils

import android.graphics.Bitmap
import android.util.Log
import coil3.size.Scale
import io.github.yuuichi_s.astertune.constants.IMAGE_DEBUG

internal class LocalArtworkDebug private constructor(
    private val data: LocalArtworkPath,
    private val targetScale: Scale,
) {
    private var start = 0L
    private var extracted = -1L
    private var decoded = -1L
    private var scaleStart = 0L
    private var artBytes = -1
    private var sample = 1
    private var originalWidth = -1
    private var originalHeight = -1

    fun onStart() {
        start = System.nanoTime()
        extracted = -1L
        decoded = -1L
        artBytes = -1
        sample = 1
    }

    fun onExtracted(bytes: Int) {
        extracted = System.nanoTime()
        artBytes = bytes
    }

    fun onBounds(width: Int, height: Int) {
        originalWidth = width
        originalHeight = height
    }

    fun onSampleSize(sampleSize: Int) {
        sample = sampleSize
    }

    fun onDecoded() {
        decoded = System.nanoTime()
    }

    fun onScaleStart() {
        scaleStart = System.nanoTime()
    }

    fun onComplete(image: Bitmap) {
        val end = System.nanoTime()
        Log.d(
            IMAGE_TAG,
            "fetch req=${data.x}x${data.y} scale=$targetScale orig=${originalWidth}x$originalHeight " +
                "bytes=$artBytes sample=$sample out=${image.width}x${image.height} " +
                "extract=${ms(start, extracted)} decode=${ms(extracted, decoded)} " +
                "scale=${ms(scaleStart, end)} total=${ms(start, end)} " +
                "placeholder=${decoded < 0} path=${data.path}"
        )
    }

    private fun ms(from: Long, to: Long) =
        if (from < 0 || to < 0) "-" else "%.1f".format((to - from) / 1_000_000.0)

    companion object {
        fun create(data: LocalArtworkPath, targetScale: Scale): LocalArtworkDebug? =
            if (IMAGE_DEBUG) LocalArtworkDebug(data, targetScale) else null
    }
}
