package com.pknu.running.music.provider

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.YoutubeService
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * NewPipe Extractor를 사용하여 YouTube에서 곡을 검색하고
 * 오디오 스트림 URL을 추출하는 Resolver.
 *
 * 사용 흐름:
 *   1. "아티스트 - 곡명"으로 YouTube 검색
 *   2. 첫 번째 결과의 스트림 URL 추출
 *   3. 오디오 전용 스트림 중 최고 품질 선택
 */
class YouTubeStreamResolver {

    companion object {
        private const val TAG = "YouTubeResolver"
        private var initialized = false

        fun initialize() {
            if (!initialized) {
                NewPipe.init(OkHttpDownloader())
                initialized = true
            }
        }
    }

    init {
        initialize()
    }

    /**
     * 검색어로 YouTube를 검색하여 첫 번째 결과의 오디오 스트림 URL을 반환한다.
     *
     * @param query 검색어 (예: "Runner - 달려라")
     * @return 오디오 스트림 URL, 실패 시 null
     */
    suspend fun resolveAudioUrl(query: String): ResolvedStream? = withContext(Dispatchers.IO) {
        try {
            val service = ServiceList.YouTube
            val searchExtractor = service.getSearchExtractor(query)
            searchExtractor.fetchPage()

            val items = searchExtractor.initialPage.items
            val firstVideo = items.filterIsInstance<StreamInfoItem>().firstOrNull()

            if (firstVideo == null) {
                Log.w(TAG, "No video found for query: $query")
                return@withContext null
            }

            Log.d(TAG, "Found: ${firstVideo.name} (${firstVideo.url})")

            // 스트림 정보 추출
            val streamExtractor = service.getStreamExtractor(firstVideo.url)
            streamExtractor.fetchPage()

            // 오디오 전용 스트림 중 최고 비트레이트 선택
            val audioStreams = streamExtractor.audioStreams
            val bestAudio = audioStreams
                .filter { it.content != null && it.content.isNotBlank() }
                .maxByOrNull { it.averageBitrate }

            if (bestAudio != null) {
                Log.d(TAG, "Audio stream: ${bestAudio.averageBitrate}kbps, format=${bestAudio.format}")
                ResolvedStream(
                    audioUrl = bestAudio.content,
                    title = streamExtractor.name,
                    durationSec = streamExtractor.length,
                    videoId = firstVideo.url
                )
            } else {
                // 오디오 전용이 없으면 비디오+오디오 스트림에서 추출
                val videoStreams = streamExtractor.videoOnlyStreams + streamExtractor.videoStreams
                val videoWithAudio = streamExtractor.videoStreams.firstOrNull()
                if (videoWithAudio != null) {
                    Log.d(TAG, "Fallback to video stream with audio")
                    ResolvedStream(
                        audioUrl = videoWithAudio.content,
                        title = streamExtractor.name,
                        durationSec = streamExtractor.length,
                        videoId = firstVideo.url
                    )
                } else {
                    Log.e(TAG, "No playable stream found")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve: $query", e)
            null
        }
    }

    /**
     * YouTube URL에서 직접 오디오 스트림을 추출한다.
     */
    suspend fun resolveFromUrl(videoUrl: String): ResolvedStream? = withContext(Dispatchers.IO) {
        try {
            val service = ServiceList.YouTube
            val streamExtractor = service.getStreamExtractor(videoUrl)
            streamExtractor.fetchPage()

            val audioStreams = streamExtractor.audioStreams
            val bestAudio = audioStreams
                .filter { it.content != null && it.content.isNotBlank() }
                .maxByOrNull { it.averageBitrate }

            if (bestAudio != null) {
                ResolvedStream(
                    audioUrl = bestAudio.content,
                    title = streamExtractor.name,
                    durationSec = streamExtractor.length,
                    videoId = videoUrl
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve URL: $videoUrl", e)
            null
        }
    }
}

/**
 * YouTube에서 추출한 스트림 정보.
 */
data class ResolvedStream(
    val audioUrl: String,
    val title: String,
    val durationSec: Long,
    val videoId: String
)

/**
 * NewPipe Extractor가 HTTP 요청을 수행할 때 사용하는 OkHttp 기반 Downloader.
 */
class OkHttpDownloader : Downloader() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override fun execute(request: Request): Response {
        val urlBuilder = request.url()
        val httpMethod = request.httpMethod()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBuilder = okhttp3.Request.Builder()
            .url(urlBuilder)

        // 헤더 설정
        headers.forEach { (key, values) ->
            values.forEach { value ->
                requestBuilder.addHeader(key, value)
            }
        }

        // User-Agent 설정 (YouTube 차단 방지)
        if (headers["User-Agent"] == null) {
            requestBuilder.addHeader("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0")
        }

        // HTTP 메서드
        when {
            httpMethod == "GET" -> requestBuilder.get()
            httpMethod == "HEAD" -> requestBuilder.head()
            httpMethod == "POST" -> {
                val body = dataToSend?.toRequestBody() ?: "".toRequestBody()
                requestBuilder.post(body)
            }
            else -> {
                if (dataToSend != null) {
                    requestBuilder.method(httpMethod, dataToSend.toRequestBody())
                } else {
                    requestBuilder.method(httpMethod, null)
                }
            }
        }

        val response = client.newCall(requestBuilder.build()).execute()

        if (response.code == 429) {
            response.close()
            throw ReCaptchaException("Rate limited", urlBuilder)
        }

        val responseHeaders: MutableMap<String, List<String>> = mutableMapOf()
        response.headers.forEach { (name, value) ->
            responseHeaders[name] = responseHeaders.getOrDefault(name, emptyList()) + value
        }

        val responseBody = response.body?.string() ?: ""
        val latestUrl = response.request.url.toString()

        return Response(
            response.code,
            response.message,
            responseHeaders,
            responseBody,
            latestUrl
        )
    }
}
