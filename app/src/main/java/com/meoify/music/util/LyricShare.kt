package com.meoify.music.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** The two cards the now-playing screen can produce. */
enum class ShareCardKind { SONG, LYRICS }

data class ShareCardOptions(
    val kind: ShareCardKind = ShareCardKind.LYRICS,
    val lines: List<String> = emptyList(),
    val title: String = "",
    val artist: String = "",
    val artworkUrl: String? = null,
)

/**
 * Renders a 9:16 card with a bounded square artwork (matching the now-playing
 * player), readable text below it, and a Meoify footer. Preview and export use
 * the same renderer so what the user sees is exactly what gets shared.
 */
object LyricShare {

    suspend fun renderCard(
        context: Context,
        options: ShareCardOptions,
        width: Int = 1080,
        height: Int = 1920,
    ): Bitmap = withContext(Dispatchers.Default) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val art = loadArtwork(context, options.artworkUrl)

        // Clean dark backdrop; the artwork is the focal point of the card.
        canvas.drawRect(
            0f, 0f, width.toFloat(), height.toFloat(),
            Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    Color.parseColor("#312e81"),
                    Color.parseColor("#0b1120"),
                    Shader.TileMode.CLAMP,
                )
            },
        )

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(235, 255, 255, 255)
            textSize = (width * 0.030f).coerceAtLeast(16f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) brandPaint.letterSpacing = 4f
        canvas.drawText("MEOIFY", width / 2f, height * 0.072f, brandPaint)

        // Square artwork filling the card width, like the now-playing player:
        // a bounded square rather than a full-screen background image.
        val pad = (width * 0.066f).toInt()
        val artSize = width
        val artLeft = 0f
        val artTop = height * 0.125f
        val radius = width * 0.08f
        val artRect = RectF(artLeft, artTop, artLeft + artSize, artTop + artSize)

        canvas.drawRoundRect(
            RectF(artRect.left, artRect.top + width * 0.012f, artRect.right, artRect.bottom + width * 0.012f),
            radius, radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(120, 0, 0, 0)
                maskFilter = BlurMaskFilter(width * 0.018f, BlurMaskFilter.Blur.NORMAL)
            },
        )

        val artPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (art != null) {
            val shader = BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val scale = maxOf(artSize / art.width.toFloat(), artSize / art.height.toFloat())
            val offsetX = artLeft + (artSize - art.width * scale) / 2f
            val offsetY = artTop + (artSize - art.height * scale) / 2f
            shader.setLocalMatrix(Matrix().apply {
                setScale(scale, scale)
                postTranslate(offsetX, offsetY)
            })
            artPaint.shader = shader
        } else {
            artPaint.shader = LinearGradient(
                artRect.left, artRect.top, artRect.right, artRect.bottom,
                Color.parseColor("#4c1d95"),
                Color.parseColor("#1e3a8a"),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(artRect, radius, radius, artPaint)

        val textTop = artRect.bottom + height * 0.035f
        val footerY = height * 0.945f
        val maxTextWidth = width - pad * 2

        if (options.kind == ShareCardKind.SONG) {
            val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(190, 255, 255, 255)
                textSize = (width * 0.024f).coerceAtLeast(14f)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("NOW PLAYING", width / 2f, textTop + width * 0.018f, caption)

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = (width * 0.058f).coerceAtLeast(24f)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(
                fitText(options.title.ifBlank { "Meoify" }, titlePaint, maxTextWidth.toFloat()),
                width / 2f,
                textTop + width * 0.105f,
                titlePaint,
            )
            if (options.artist.isNotBlank()) {
                val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(220, 255, 255, 255)
                    textSize = (width * 0.038f).coerceAtLeast(18f)
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(
                    fitText(options.artist, artistPaint, maxTextWidth.toFloat()),
                    width / 2f,
                    textTop + width * 0.165f,
                    artistPaint,
                )
            }
        } else {
            val selected = options.lines.map { it.trim() }.filter { it.isNotBlank() }
            val blocks = selected.ifEmpty { listOf(options.title.ifBlank { "Meoify" }) }
            val available = (footerY - height * 0.115f) - textTop
            var textSize = (width * 0.050f).coerceAtLeast(18f)
            val minSize = (width * 0.026f).coerceAtLeast(12f)
            val measure = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            var visualLines: List<String>
            while (true) {
                measure.textSize = textSize
                val acc = mutableListOf<String>()
                blocks.forEachIndexed { index, block ->
                    acc.addAll(wrapText(block, measure, maxTextWidth))
                    if (index < blocks.lastIndex) acc.add("")
                }
                visualLines = acc
                if (visualLines.size * textSize * 1.32f <= available || textSize <= minSize) break
                textSize = (textSize - 2f).coerceAtLeast(minSize)
            }
            val lyricPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                this.textSize = textSize
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val lineHeight = textSize * 1.32f
            var y = textTop + (available - visualLines.size * lineHeight) / 2f + textSize
            visualLines.forEach { line ->
                if (line.isNotBlank()) canvas.drawText(line, width / 2f, y, lyricPaint)
                y += lineHeight
            }

            val metaY = footerY - height * 0.048f
            val metaTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = (width * 0.030f).coerceAtLeast(15f)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(
                fitText(options.title.ifBlank { "Meoify" }, metaTitle, maxTextWidth.toFloat()),
                width / 2f, metaY, metaTitle,
            )
            if (options.artist.isNotBlank()) {
                val metaArtist = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(200, 255, 255, 255)
                    textSize = (width * 0.024f).coerceAtLeast(13f)
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(
                    fitText(options.artist, metaArtist, maxTextWidth.toFloat()),
                    width / 2f, metaY + width * 0.045f, metaArtist,
                )
            }
        }

        val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 255, 255, 255)
            textSize = (width * 0.024f).coerceAtLeast(14f)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            if (options.kind == ShareCardKind.SONG) "Now playing on Meoify" else "Lyrics · Meoify",
            width / 2f, footerY, captionPaint,
        )

        bitmap
    }

    suspend fun shareToInstagramStory(context: Context, options: ShareCardOptions) {
        val uri = prepareImage(context, options) ?: return
        withContext(Dispatchers.Main) {
            try {
                val story = Intent("com.instagram.share.ADD_TO_STORY").apply {
                    setDataAndType(uri, "image/*")
                    putExtra("source_application", context.packageName)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                if (story.resolveActivity(context.packageManager) != null) {
                    context.startActivity(story)
                } else {
                    Toast.makeText(context, "Instagram isn't installed", Toast.LENGTH_SHORT).show()
                    launchChooser(context, uri, options)
                }
            } catch (t: Throwable) {
                Toast.makeText(context, "Couldn't share: ${t.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    suspend fun shareToChat(context: Context, options: ShareCardOptions) {
        val uri = prepareImage(context, options) ?: return
        withContext(Dispatchers.Main) {
            try {
                launchChooser(context, uri, options)
            } catch (t: Throwable) {
                Toast.makeText(context, "Couldn't share: ${t.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** One-tap variant used when no preview sheet is shown. */
    suspend fun share(context: Context, options: ShareCardOptions) {
        val uri = prepareImage(context, options) ?: return
        withContext(Dispatchers.Main) {
            try {
                val story = Intent("com.instagram.share.ADD_TO_STORY").apply {
                    setDataAndType(uri, "image/*")
                    putExtra("source_application", context.packageName)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                if (story.resolveActivity(context.packageManager) != null) {
                    context.startActivity(story)
                } else {
                    launchChooser(context, uri, options)
                }
            } catch (_: Throwable) {
                launchChooser(context, uri, options)
            }
        }
    }

    private suspend fun prepareImage(context: Context, options: ShareCardOptions): Uri? =
        withContext(Dispatchers.IO) {
            try {
                val bitmap = renderCard(context, options)
                val dir = File(context.cacheDir, "images").apply { mkdirs() }
                val file = File(dir, "meoify_share_${System.currentTimeMillis()}.png")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Couldn't prepare the image: ${t.message}", Toast.LENGTH_LONG).show()
                }
                null
            }
        }

    private fun launchChooser(context: Context, uri: Uri, options: ShareCardOptions) {
        val caption = when (options.kind) {
            ShareCardKind.SONG ->
                "Now playing: ${options.title} — ${options.artist}\nShared from Meoify"
            ShareCardKind.LYRICS ->
                "\u201c${options.lines.joinToString(" ").trim()}\u201d\n— ${options.title}" +
                    "${if (options.artist.isNotBlank()) " · ${options.artist}" else ""}\n\nShared from Meoify"
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share card"))
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Int): List<String> {
        val result = mutableListOf<String>()
        text.split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { word ->
            if (paint.measureText(word) > maxWidth) {
                val chunk = StringBuilder()
                word.forEach { char ->
                    if (paint.measureText(chunk.toString() + char) > maxWidth && chunk.isNotEmpty()) {
                        result.add(chunk.toString())
                        chunk.clear()
                    }
                    chunk.append(char)
                }
                if (chunk.isNotEmpty()) result.add(chunk.toString())
                return@forEach
            }
            val last = result.lastOrNull()
            if (last != null && paint.measureText("$last $word") <= maxWidth) {
                result[result.lastIndex] = "$last $word"
            } else {
                result.add(word)
            }
        }
        return result.ifEmpty { listOf(text) }
    }

    private fun fitText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val end = text.length
        var cut = end
        while (cut > 1 && paint.measureText(text.take(cut) + "…") > maxWidth) cut--
        return text.take(cut).trimEnd() + "…"
    }

    private suspend fun loadArtwork(context: Context, url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return try {
            val result = ImageLoader(context).execute(ImageRequest.Builder(context).data(url).build())
            if (result !is SuccessResult) return null
            val image = result.image
            val bmp = if (image is BitmapImage) {
                image.bitmap
            } else if (image.width > 0 && image.height > 0) {
                val b = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
                image.draw(Canvas(b))
                b
            } else null
            bmp?.takeIf { it.config != Bitmap.Config.HARDWARE }
                ?: bmp?.copy(Bitmap.Config.ARGB_8888, false)
        } catch (_: Exception) {
            null
        }
    }
}
