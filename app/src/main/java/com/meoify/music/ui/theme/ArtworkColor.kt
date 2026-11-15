package com.meoify.music.ui.theme

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Tiny zero-dependency dominant accent extractor for artwork URLs.
 * Used to tint detail headers and the Now Playing background.
 */
object ArtworkColorCache {

    private val cache = ConcurrentHashMap<String, Color>()

    suspend fun extract(url: String?, fallbackSeed: String): Color {
        if (url.isNullOrBlank()) return fallbackColor(fallbackSeed)
        cache[url]?.let { return it }

        val computed = withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(url).openConnection().apply {
                    connectTimeout = 6_000
                    readTimeout = 6_000
                }
                connection.getInputStream().use { stream ->
                    val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                    val bitmap = BitmapFactory.decodeStream(stream, null, options) ?: return@runCatching null
                    val color = dominantFromBitmap(bitmap)
                    bitmap.recycle()
                    color
                }
            }.getOrNull()
        }

        val finalColor = computed ?: fallbackColor(fallbackSeed)
        if (cache.size > 80) cache.clear()
        cache[url] = finalColor
        return finalColor
    }

    fun fallbackColor(seed: String): Color {
        val pair = GenrePalette[abs(seed.hashCode()) % GenrePalette.size]
        return pair.first
    }

    fun gradientPair(seed: String): Pair<Color, Color> {
        return GenrePalette[abs(seed.hashCode()) % GenrePalette.size]
    }

    private fun dominantFromBitmap(bitmap: Bitmap): Color? {
        val scaled = Bitmap.createScaledBitmap(bitmap, 24, 24, true)
        var rSum = 0f
        var gSum = 0f
        var bSum = 0f
        var totalWeight = 0f

        for (y in 0 until scaled.height) {
            for (x in 0 until scaled.width) {
                val pixel = scaled.getPixel(x, y)
                val r = ((pixel shr 16) and 0xFF) / 255f
                val g = ((pixel shr 8) and 0xFF) / 255f
                val b = (pixel and 0xFF) / 255f

                val maxC = max(r, max(g, b))
                val minC = min(r, min(g, b))
                val sat = if (maxC == 0f) 0f else (maxC - minC) / maxC
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                if (lum < 0.08f || lum > 0.92f) continue

                val weight = 0.25f + sat * 2.5f
                rSum += r * weight
                gSum += g * weight
                bSum += b * weight
                totalWeight += weight
            }
        }
        if (scaled != bitmap) scaled.recycle()
        if (totalWeight <= 0f) return null

        val r = (rSum / totalWeight).coerceIn(0.08f, 0.78f)
        val g = (gSum / totalWeight).coerceIn(0.08f, 0.78f)
        val b = (bSum / totalWeight).coerceIn(0.08f, 0.78f)
        return Color(r, g, b, 1f)
    }

    fun darken(color: Color, factor: Float = 0.48f): Color {
        val argb = color.toArgb()
        val r = (((argb shr 16) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val g = (((argb shr 8) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val b = ((argb and 0xFF) * factor).toInt().coerceIn(0, 255)
        return Color(r, g, b)
    }
}
