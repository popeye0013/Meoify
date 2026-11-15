package com.meoify.music.ui.screens.player

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.meoify.music.data.model.Track
import com.meoify.music.data.remote.TrackLyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

/**
 * Renders a shareable "lyrics card" the way Spotify does, branded for Meoify:
 * artwork background, the track title and artist, a handful of lyric lines
 * with the current one highlighted, and a Meoify wordmark plus credit.
 *
 * Everything is drawn on a background thread into a 1080x1920 PNG that is
 * handed to the system share sheet through a FileProvider.
 */
object LyricsShare {

    private const val CARD_WIDTH = 1080
    private const val CARD_HEIGHT = 1920

    suspend fun share(
        context: Context,
        track: Track,
        lyrics: TrackLyrics?,
        positionMs: Long,
        accent: Int,
    ): Boolean = withContext(Dispatchers.IO) {
        val bitmap = runCatching { render(context, track, lyrics, positionMs, accent) }.getOrNull()
            ?: return@withContext false
        val file = runCatching { write(context, track, bitmap) }.getOrNull()
            ?: return@withContext false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_TEXT,
                "${track.title} — ${track.artist}\nShared from Meoify",
            )
            clipData = ClipData.newRawUri("lyrics", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Share lyrics").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(chooser) }.isSuccess
    }

    private fun write(context: Context, track: Track, bitmap: Bitmap): File {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        dir.listFiles()?.forEach { stale ->
            if (stale.isFile && stale.name.startsWith("meoify-lyrics-")) stale.delete()
        }
        val file = File(dir, "meoify-lyrics-${track.id}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
        return file
    }

    private fun render(
        context: Context,
        track: Track,
        lyrics: TrackLyrics?,
        positionMs: Long,
        accent: Int,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)

        // Artwork fills the card, then a strong gradient keeps the text legible.
        val artwork = runCatching {
            track.thumbnailUrl?.let { url ->
                URL(url).openStream().use { BitmapFactory.decodeStream(it) }
            }
        }.getOrNull()
        artwork?.let { drawCover(canvas, it) }

        val scrim = Paint().apply {
            shader = LinearGradient(
                0f,
                0f,
                0f,
                CARD_HEIGHT.toFloat(),
                intArrayOf(
                    Color.argb(120, 0, 0, 0),
                    Color.argb(220, 10, 10, 14),
                    Color.argb(248, 10, 10, 14),
                ),
                floatArrayOf(0f, 0.46f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), scrim)

        // Soft accent glow low on the card.
        val glow = Paint().apply {
            shader = LinearGradient(
                0f,
                CARD_HEIGHT * 0.55f,
                0f,
                CARD_HEIGHT.toFloat(),
                intArrayOf(Color.TRANSPARENT, Color.argb(90, Color.red(accent), Color.green(accent), Color.blue(accent))),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, CARD_HEIGHT * 0.55f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), glow)

        // Brand row: logo, wordmark and tagline.
        drawLogo(canvas, 72f, 84f, 96f)

        val wordmark = textPaint(60f, Typeface.BOLD, Color.WHITE)
        canvas.drawText("Meoify", 192f, 150f, wordmark)
        val tagline = textPaint(30f, Typeface.NORMAL, Color.argb(190, 255, 255, 255))
        canvas.drawText("Unofficial YouTube Music client", 193f, 192f, tagline)

        // Track details.
        var y = 1080f
        val titlePaint = textPaint(76f, Typeface.BOLD, Color.WHITE)
        y += drawWrapped(canvas, track.title, titlePaint, 72f, y, 936f, 2) + 26f

        val artistPaint = textPaint(44f, Typeface.NORMAL, Color.argb(210, 255, 255, 255))
        val artistLine = track.artist.ifBlank { "Unknown artist" }
        canvas.drawText(ellipsize(artistLine, artistPaint, 936f), 74f, y + 34f, artistPaint)
        y += 92f

        // Lyric lines, the current one in the accent colour.
        val lines = lyricLines(lyrics, positionMs)
        val activePaint = textPaint(54f, Typeface.BOLD, Color.WHITE)
        val idlePaint = textPaint(46f, Typeface.NORMAL, Color.argb(120, 255, 255, 255))
        if (lines.isEmpty()) {
            val quote = textPaint(64f, Typeface.BOLD, Color.argb(200, Color.red(accent), Color.green(accent), Color.blue(accent)))
            canvas.drawText("♪", 74f, y + 90f, quote)
        } else {
            lines.forEachIndexed { index, line ->
                if (y > CARD_HEIGHT - 230f) return@forEachIndexed
                val paint = if (index == 0) activePaint else idlePaint
                if (index == 0) {
                    // Accent underline under the active line.
                    val marker = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.argb(230, Color.red(accent), Color.green(accent), Color.blue(accent))
                    }
                    canvas.drawRoundRect(RectF(74f, y + 8f, 86f, y + 60f), 6f, 6f, marker)
                }
                val x = if (index == 0) 112f else 112f
                val used = drawWrapped(canvas, line, paint, x, y, 900f, 2)
                y += used + if (index == 0) 32f else 22f
            }
        }

        // Footer credit.
        val credit = textPaint(32f, Typeface.BOLD, Color.argb(200, 255, 255, 255))
        canvas.drawText("Made with Meoify", 72f, CARD_HEIGHT - 96f, credit)
        val handle = textPaint(30f, Typeface.NORMAL, Color.argb(150, 255, 255, 255))
        val handleText = "t.me/popeye0013"
        canvas.drawText(handleText, CARD_WIDTH - 72f - handle.measureText(handleText), CARD_HEIGHT - 96f, handle)
        canvas.drawRect(
            72f,
            CARD_HEIGHT - 138f,
            CARD_WIDTH - 72f,
            CARD_HEIGHT - 136f,
            Paint().apply { color = Color.argb(45, 255, 255, 255) },
        )

        return bitmap
    }

    private fun lyricLines(lyrics: TrackLyrics?, positionMs: Long): List<String> {
        if (lyrics == null) return emptyList()
        if (lyrics.synced && lyrics.lines.isNotEmpty()) {
            val index = lyrics.lines.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
            return lyrics.lines
                .drop(index)
                .map { it.text.trim() }
                .filter { it.isNotEmpty() }
                .take(5)
        }
        val plain = lyrics.plain.orEmpty()
        return plain
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(5)
    }

    /** Meoify's mark: a crimson disc with the cat silhouette and play cut-out. */
    private fun drawLogo(canvas: Canvas, left: Float, top: Float, size: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.parseColor("#FF4D67")
        canvas.drawCircle(left + size / 2f, top + size / 2f, size / 2f, paint)

        val scale = size / 108f
        fun x(v: Float) = left + v * scale
        fun y(v: Float) = top + v * scale

        paint.color = Color.parseColor("#14060A")
        val ears = Path().apply {
            moveTo(x(30f), y(50f)); lineTo(x(36f), y(22f)); lineTo(x(58f), y(38f)); close()
            moveTo(x(78f), y(50f)); lineTo(x(72f), y(22f)); lineTo(x(50f), y(38f)); close()
        }
        canvas.drawPath(ears, paint)
        canvas.drawCircle(x(54f), y(58f), 25f * scale, paint)

        paint.color = Color.parseColor("#FF4D67")
        val play = Path().apply {
            moveTo(x(48f), y(48f)); lineTo(x(68f), y(58f)); lineTo(x(48f), y(68f)); close()
        }
        canvas.drawPath(play, paint)
    }

    private fun drawCover(canvas: Canvas, art: Bitmap) {
        val scale = maxOf(
            CARD_WIDTH.toFloat() / art.width,
            CARD_HEIGHT.toFloat() / art.height,
        )
        val w = art.width * scale
        val h = art.height * scale
        val left = (CARD_WIDTH - w) / 2f
        val top = (CARD_HEIGHT - h) / 2f
        canvas.drawBitmap(art, null, RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun textPaint(size: Float, style: Int, color: Int): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), style)
            this.color = color
        }

    /** Draws wrapped text and returns the height used. */
    private fun drawWrapped(
        canvas: Canvas,
        text: String,
        paint: TextPaint,
        x: Float,
        y: Float,
        width: Float,
        maxLines: Int,
    ): Float {
        if (text.isBlank()) return 0f
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width.toInt())
            .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(6f, 1f)
            .setMaxLines(maxLines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return layout.height.toFloat()
    }

    private fun ellipsize(text: String, paint: TextPaint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + ellipsis) > maxWidth) end--
        return text.substring(0, end) + ellipsis
    }
}
