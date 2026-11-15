package com.meoify.music

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NPRequest
import org.schabi.newpipe.extractor.downloader.Response as NPResponse
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo

class StreamCheckTest {

    @Test
    fun resolveAStream() {
        val client = OkHttpClient()
        NewPipe.init(object : Downloader() {
            override fun execute(request: NPRequest): NPResponse {
                val body = request.dataToSend()?.let { data ->
                    val type = request.headers()["Content-Type"]?.firstOrNull()
                        ?.substringBefore(';')?.trim()?.toMediaType()
                        ?: "application/json; charset=utf-8".toMediaType()
                    data.toRequestBody(type)
                }
                val builder = Request.Builder().url(request.url()).method(request.httpMethod(), body)
                request.headers().forEach { (name, values) ->
                    values.forEach { builder.addHeader(name, it) }
                }
                if (request.headers()["User-Agent"].isNullOrEmpty()) {
                    builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
                }
                client.newCall(builder.build()).execute().use { response ->
                    return NPResponse(
                        response.code,
                        response.message,
                        response.headers.toMultimap(),
                        response.body?.string(),
                        response.request.url.toString(),
                    )
                }
            }
        }, Localization("en", "US"), ContentCountry("US"))

        val info = StreamInfo.getInfo(
            ServiceList.YouTube,
            "https://music.youtube.com/watch?v=1N8hOpMqvYs",
        )
        println("TITLE: ${info.name}")
        info.audioStreams.forEach {
            println("AUDIO: ${it.format?.name} ${it.averageBitrate} ${it.url?.take(80)}")
        }
        assertTrue("No audio streams resolved", info.audioStreams.isNotEmpty())
    }
}
