package com.meoify.music.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders the currently playing lyric line into a shareable image card and
 * launches the system share sheet (with a direct path to Instagram Stories).
 */
object LyricShare {

    private const val CARD_WIDTH = 1080
    private const val CARD_HEIGHT = 1920

    suspend fun share(
        context: Context,
        title: String,
        artist: String,
        lyric: String,
        artworkUrl: String?,
    ) = withContext(Dispatchers.IO) {
        val bitmap = renderCard(context, title, artist, lyric, artworkUrl)
        val uri = saveBitmap(context, bitmap) ?: run {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Couldn't prepare the image", Toast.LENGTH_SHORT).show()
            }
            return@withContext
        }

        withContext(Dispatchers.Main) {
            // Prefer Instagram Stories when it is installed.
            val instagram = Intent("com.instagram.share.ADD_TO_STORY").apply {
                setDataAndType(uri, "image/*")
                putExtra("source_application", context.packageName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (instagram.resolveActivity(context.packageManager) != null) {
                try {
                    context.startActivity(instagram)
                    return@withContext
                } catch (_: ActivityNotFoundException) {
                }
            }

            val share = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(
                    Intent.EXTRA_TEXT,
                    "\u201c$lyric\u201d\n— $title${if (artist.isNotBlank()) " · $artist" else ""}\n\nShared from Meoify",
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(share, "Share lyric"))
        }
    }

    private suspend fun renderCard(
        context: Context,
        title: String,
        artist: String,
        lyric: String,
        artworkUrl: String?,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background: artwork (if available) with a dark scrim, else gradient.
        val art = loadArtwork(context, artworkUrl)
        if (art != null) {
            val src = Rect(0, 0, art.width, art.height)
            val dst = Rect(0, 0, CARD_WIDTH, CARD_HEIGHT)
            canvas.drawBitmap(art, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.drawRect(
                0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(),
                Paint().apply { color = Color.argb(150, 0, 0, 0) },
            )
        } else {
            val paint = Paint()
            paint.shader = android.graphics.LinearGradient(
                0f, 0f, 0f, CARD_HEIGHT.toFloat(),
                Color.parseColor("#1a1a2e"),
                Color.parseColor("#16213e"),
                android.graphics.Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), paint)
        }

        // Lyric text, centered, wrapped.
        val lyricPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 72f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            setShadowLayer(8f, 0f, 4f, Color.argb(120, 0, 0, 0))
        }
        val maxTextWidth = CARD_WIDTH - 120
        val lines = wrapText(lyric, lyricPaint, maxTextWidth)
        val lineHeight = lyricPaint.textSize * 1.35f
        val blockHeight = lines.size * lineHeight
        var y = (CARD_HEIGHT / 2f) - (blockHeight / 2f) + lyricPaint.textSize
        for (line in lines) {
            canvas.drawText(line, CARD_WIDTH / 2f, y, lyricPaint)
            y += lineHeight
        }

        // Track title / artist near the bottom.
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 255, 255, 255)
            textSize = 36f
            textAlign = Paint.Align.CENTER
        }
        val bottomY = CARD_HEIGHT - 180f
        canvas.drawText(title, CARD_WIDTH / 2f, bottomY, titlePaint)
        if (artist.isNotBlank()) {
            canvas.drawText(artist, CARD_WIDTH / 2f, bottomY + 52f, artistPaint)
        }

        // Watermark.
        val wmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(140, 255, 255, 255)
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Meoify", CARD_WIDTH / 2f, CARD_HEIGHT - 60f, wmPaint)

        return bitmap
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Int): List<String> {
        val words = text.split(Regex("\\s+"))
        val result = mutableListOf<String>()
        val current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                if (current.isNotEmpty()) current.append(' ')
                current.append(word)
            } else {
                if (current.isNotEmpty()) result.add(current.toString())
                current.clear()
                current.append(word)
            }
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result.ifEmpty { listOf(text) }
    }

    private suspend fun loadArtwork(context: Context, url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return try {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context).data(url).build()
            val result = loader.execute(request)
            if (result is SuccessResult) {
                val image = result.image
                if (image is coil3.BitmapImage) {
                    image.bitmap
                } else {
                    val bmp = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
                    val c = Canvas(bmp)
                    image.draw(c)
                    bmp
                }
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun saveBitmap(context: Context, bitmap: Bitmap): Uri? {
        return try {
            val dir = File(context.cacheDir, "images").apply { mkdirs() }
            val file = File(dir, "lyric_share.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: Exception) {
            null
        }
    }
}
