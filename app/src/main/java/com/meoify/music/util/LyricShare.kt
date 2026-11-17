package com.meoify.music.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class LyricShareTemplate { MINIMAL, NEON, GRADIENT, BLUR, SOLID }

enum class LyricShareAspect(val width: Int, val height: Int, val label: String) {
    STORY(1080, 1920, "Story"),
    SQUARE(1080, 1080, "Square"),
    LANDSCAPE(1920, 1080, "Landscape"),
}

data class LyricShareOptions(
    val template: LyricShareTemplate = LyricShareTemplate.GRADIENT,
    val aspect: LyricShareAspect = LyricShareAspect.STORY,
    val lines: List<String> = emptyList(),
    val title: String = "",
    val artist: String = "",
    val artworkUrl: String? = null,
)

object LyricShare {

    suspend fun renderCard(context: Context, options: LyricShareOptions): Bitmap = withContext(Dispatchers.Default) {
        val w = options.aspect.width
        val h = options.aspect.height
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val art = loadArtwork(context, options.artworkUrl)

        when (options.template) {
            LyricShareTemplate.MINIMAL -> canvas.drawColor(Color.parseColor("#0f0f12"))
            LyricShareTemplate.SOLID -> canvas.drawColor(Color.parseColor("#1f2937"))
            LyricShareTemplate.GRADIENT -> {
                val paint = Paint()
                paint.shader = android.graphics.LinearGradient(
                    0f, 0f, 0f, h.toFloat(),
                    Color.parseColor("#4c1d95"),
                    Color.parseColor("#1e3a8a"),
                    android.graphics.Shader.TileMode.CLAMP,
                )
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            }
            LyricShareTemplate.BLUR, LyricShareTemplate.NEON -> {
                if (art != null) {
                    val src = Rect(0, 0, art.width, art.height)
                    val dst = Rect(0, 0, w, h)
                    canvas.drawBitmap(art, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
                } else {
                    canvas.drawColor(Color.parseColor("#111827"))
                }
                val scrim = Paint().apply {
                    color = if (options.template == LyricShareTemplate.NEON)
                        Color.argb(120, 0, 0, 0) else Color.argb(175, 0, 0, 0)
                }
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), scrim)
            }
        }

        val selected = options.lines.map { it.trim() }.filter { it.isNotBlank() }
        val blocks = if (selected.isEmpty()) listOf("♪") else selected

        val textColor = when (options.template) {
            LyricShareTemplate.NEON -> Color.parseColor("#22d3ee")
            else -> Color.WHITE
        }

        val maxTextWidth = w - (w * 0.12f).toInt()
        // Pick a text size that fits reasonably.
        var textSize = (w * 0.066f).coerceAtLeast(36f)
        val minTextSize = (w * 0.04f).coerceAtLeast(24f)
        val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        var visualLines = emptyList<String>()
        while (true) {
            measurePaint.textSize = textSize
            val acc = mutableListOf<String>()
            for ((i, block) in blocks.withIndex()) {
                acc.addAll(wrapText(block, measurePaint, maxTextWidth))
                if (i < blocks.lastIndex) acc.add("")
            }
            visualLines = acc
            val tooMany = visualLines.count { it.isNotBlank() } > 9 && visualLines.size > 10
            if (!tooMany || textSize <= minTextSize) break
            textSize = (textSize - 2f).coerceAtLeast(minTextSize)
        }

        val lyricPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            this.textSize = textSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            setShadowLayer(10f, 0f, 4f, Color.argb(140, 0, 0, 0))
        }
        val lineHeight = lyricPaint.textSize * 1.35f
        val blockHeight = visualLines.size * lineHeight
        var y = (h / 2f) - (blockHeight / 2f) + lyricPaint.textSize
        for (line in visualLines) {
            if (line.isNotBlank()) {
                canvas.drawText(line, w / 2f, y, lyricPaint)
            }
            y += lineHeight
        }

        // Track title / artist near the bottom.
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (w * 0.042f).coerceAtLeast(28f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(210, 255, 255, 255)
            textSize = (w * 0.032f).coerceAtLeast(22f)
            textAlign = Paint.Align.CENTER
        }
        val bottomY = h - (h * 0.10f)
        canvas.drawText(options.title, w / 2f, bottomY, titlePaint)
        if (options.artist.isNotBlank()) {
            canvas.drawText(options.artist, w / 2f, bottomY + titlePaint.textSize * 1.2f, artistPaint)
        }

        val wmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(150, 255, 255, 255)
            textSize = (w * 0.026f).coerceAtLeast(18f)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Meoify", w / 2f, h - (h * 0.035f), wmPaint)

        return@withContext bitmap
    }

    suspend fun shareToInstagramStory(context: Context, options: LyricShareOptions) = withContext(Dispatchers.IO) {
        val uri = prepareImage(context, options) ?: return@withContext
        withContext(Dispatchers.Main) {
            try {
                val instagram = Intent("com.instagram.share.ADD_TO_STORY").apply {
                    setDataAndType(uri, "image/*")
                    putExtra("source_application", context.packageName)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                if (instagram.resolveActivity(context.packageManager) != null) {
                    context.startActivity(instagram)
                } else {
                    Toast.makeText(context, "Instagram isn't installed", Toast.LENGTH_SHORT).show()
                    launchGenericChooser(context, uri, options)
                }
            } catch (t: Throwable) {
                Toast.makeText(context, "Couldn't share: ${t.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    suspend fun shareGeneric(context: Context, options: LyricShareOptions) = withContext(Dispatchers.IO) {
        val uri = prepareImage(context, options) ?: return@withContext
        withContext(Dispatchers.Main) {
            try {
                launchGenericChooser(context, uri, options)
            } catch (t: Throwable) {
                Toast.makeText(context, "Couldn't share: ${t.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    suspend fun share(context: Context, options: LyricShareOptions) = withContext(Dispatchers.IO) {
        val uri = prepareImage(context, options) ?: return@withContext
        withContext(Dispatchers.Main) {
            try {
                val instagram = Intent("com.instagram.share.ADD_TO_STORY").apply {
                    setDataAndType(uri, "image/*")
                    putExtra("source_application", context.packageName)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                if (instagram.resolveActivity(context.packageManager) != null) {
                    context.startActivity(instagram)
                    return@withContext
                }
                launchGenericChooser(context, uri, options)
            } catch (t: Throwable) {
                Toast.makeText(context, "Couldn't share: ${t.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    suspend fun saveToGallery(context: Context, options: LyricShareOptions): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val bitmap = renderCard(context, options)
            val url = MediaStore.Images.Media.insertImage(
                context.contentResolver, bitmap, "Meoify lyric", "Shared from Meoify",
            )
            withContext(Dispatchers.Main) {
                if (url != null) Toast.makeText(context, "Saved to gallery", Toast.LENGTH_SHORT).show()
                else Toast.makeText(context, "Couldn't save", Toast.LENGTH_SHORT).show()
            }
            url != null
        } catch (t: Throwable) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Couldn't save: ${t.message}", Toast.LENGTH_LONG).show()
            }
            false
        }
    }

    private suspend fun prepareImage(context: Context, options: LyricShareOptions): Uri? {
        return try {
            val bitmap = renderCard(context, options)
            saveBitmap(context, bitmap)
        } catch (t: Throwable) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Couldn't prepare the image: ${t.message}", Toast.LENGTH_LONG).show()
            }
            null
        }
    }

    private fun launchGenericChooser(context: Context, uri: Uri, options: LyricShareOptions) {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, buildCaption(options))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(share, "Share lyric"))
    }

    private fun buildCaption(options: LyricShareOptions): String {
        val lyric = options.lines.joinToString(" ").trim()
        return "\u201c$lyric\u201d\n— ${options.title}${if (options.artist.isNotBlank()) " · ${options.artist}" else ""}\n\nShared from Meoify"
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
                val bmp = if (image is coil3.BitmapImage) {
                    image.bitmap
                } else if (image.width > 0 && image.height > 0) {
                    val b = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
                    val c = Canvas(b)
                    image.draw(c)
                    b
                } else null
                bmp?.let {
                    if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it
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
