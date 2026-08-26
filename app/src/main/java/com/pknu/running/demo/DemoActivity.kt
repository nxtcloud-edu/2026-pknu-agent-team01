package com.pknu.running.demo

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pknu.running.BuildConfig
import com.pknu.running.music.provider.MusicProviderFactory
import com.pknu.running.music.provider.MusicProviderType
import com.pknu.running.music.provider.SpotifyAdapter
import com.pknu.running.music.provider.SpotifyAuthCallbackActivity
import com.pknu.running.music.provider.YouTubePlayerAdapter
import com.pknu.running.music.provider.YouTubeStreamResolver
import com.pknu.running.tracking.location.FusedLocationProvider
import com.pknu.running.tracking.model.RunningState
import com.pknu.running.tracking.model.RunningTarget
import kotlinx.coroutines.launch

/**
 * 기능 1(러닝 기록·상태 인식)을 눈으로 확인하기 위한 최소 데모 화면.
 *
 * - "Fake 러닝 시작": 실기기/GPS 없이 시뮬레이션으로 지표·이벤트를 관찰
 * - "실제 GPS 시작": 위치 권한 승인 후 FusedLocationProvider로 실제 러닝
 *
 * XML 리소스 없이 코드로 UI를 구성한다 (데모 목적, 의존성 최소화).
 */
class DemoActivity : ComponentActivity() {

    private val vm: DemoViewModel by viewModels()

    private lateinit var metricsView: TextView
    private lateinit var eventView: TextView
    private lateinit var summaryView: TextView
    private lateinit var spotifyStatusView: TextView

    private var spotifyAdapter: SpotifyAdapter? = null
    private var youtubeAdapter: YouTubePlayerAdapter? = null

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRealRun() else metricsView.text = "위치 권한이 거부되어 실제 GPS 러닝을 시작할 수 없습니다."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        observe()
        setupSpotifyCallback()
    }

    override fun onResume() {
        super.onResume()
        // Custom Tab에서 돌아왔을 때 인증 상태 확인
        checkSpotifyAuthOnResume()
    }

    private fun buildUi(): View {
        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(title("러닝 트래킹 데모"))

        metricsView = mono(16f).apply { text = "대기 중" }
        root.addView(card(metricsView))

        // 버튼 줄 1: 시작
        root.addView(
            row(
                button("Fake 러닝 시작") { vm.startFakeRun() },
                button("실제 GPS 시작") { onRealClicked() },
            )
        )
        // 버튼 줄 2: 제어
        root.addView(
            row(
                button("일시정지") { vm.pause() },
                button("재개") { vm.resume() },
                button("종료") { vm.finish() },
            )
        )

        root.addView(label("이벤트 로그"))
        eventView = mono(13f).apply { text = "(없음)" }
        val eventScroll = ScrollView(this).apply {
            addView(eventView)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(220)
            )
        }
        root.addView(card(eventScroll))

        summaryView = mono(14f)
        root.addView(card(summaryView))

        // --- Spotify 테스트 섹션 ---
        root.addView(label("Spotify 연동 테스트"))
        root.addView(
            row(
                button("Spotify 로그인") { onSpotifyLoginClicked() }
            )
        )
        spotifyStatusView = mono(13f).apply { text = "대기 중" }
        val spotifyScroll = ScrollView(this).apply {
            addView(spotifyStatusView)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(200)
            )
        }
        root.addView(card(spotifyScroll))

        // --- YouTube 재생 테스트 섹션 ---
        root.addView(label("YouTube 재생 테스트"))
        root.addView(
            row(
                button("YouTube 재생") { onYouTubeTestClicked() },
                button("정지") { onYouTubeStopClicked() }
            )
        )

        val scroll = ScrollView(this)
        scroll.addView(root)
        return scroll
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.metrics.collect { m ->
                        metricsView.text = buildString {
                            appendLine("상태: ${m.state}")
                            appendLine("시간: ${DemoViewModel.formatDuration(m.elapsedTimeSec)}")
                            appendLine("거리: ${"%.0f".format(m.totalDistanceMeter)} m")
                            appendLine("현재 페이스: ${DemoViewModel.formatPace(m.currentPaceSecPerKm)}")
                            appendLine("스무딩 페이스: ${DemoViewModel.formatPace(m.smoothedPaceSecPerKm)}")
                            appendLine("평균 페이스: ${DemoViewModel.formatPace(m.averagePaceSecPerKm)}")
                            append("GPS 정확도: ${"%.0f".format(m.gpsAccuracyMeter)} m")
                        }
                    }
                }
                launch {
                    vm.eventLog.collect { log ->
                        eventView.text = if (log.isEmpty()) "(없음)" else log.joinToString("\n")
                    }
                }
                launch {
                    vm.summaryText.collect { s ->
                        summaryView.visibility = if (s == null) View.GONE else View.VISIBLE
                        summaryView.text = s ?: ""
                    }
                }
            }
        }
    }

    private fun onRealClicked() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) startRealRun() else requestPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun startRealRun() {
        val provider = FusedLocationProvider(this)
        vm.startRealRun(provider, RunningTarget(distanceMeter = 5000.0, paceSecPerKm = 360.0))
    }

    // --- YouTube 재생 테스트 ---

    private fun onYouTubeTestClicked() {
        spotifyStatusView.text = "YouTube 검색 중: 'BTS - Dynamite'..."
        lifecycleScope.launch {
            try {
                val resolver = YouTubeStreamResolver()
                val resolved = resolver.resolveAudioUrl("BTS Dynamite")
                if (resolved != null) {
                    spotifyStatusView.text = "✓ 찾음: ${resolved.title}\n(${resolved.durationSec}초)\n\n재생 시작..."

                    // ExoPlayer로 재생
                    if (youtubeAdapter == null) {
                        val clientId = BuildConfig.SPOTIFY_CLIENT_ID
                        val redirectUri = BuildConfig.SPOTIFY_REDIRECT_URI
                        val spotify = SpotifyAdapter(applicationContext, clientId, redirectUri)
                        youtubeAdapter = YouTubePlayerAdapter(this@DemoActivity, spotify)
                    }

                    // 직접 ExoPlayer로 재생 (Spotify 인증 없이 테스트)
                    val player = androidx.media3.exoplayer.ExoPlayer.Builder(this@DemoActivity).build()
                    val mediaItem = androidx.media3.common.MediaItem.fromUri(resolved.audioUrl)
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.playWhenReady = true
                    // 간단히 참조 보관 (정지용)
                    testPlayer = player

                    spotifyStatusView.text = "♪ 재생 중: ${resolved.title}\n(${resolved.durationSec}초)\n\nURL: ${resolved.audioUrl.take(80)}..."
                } else {
                    spotifyStatusView.text = "❌ YouTube에서 오디오를 찾을 수 없음"
                }
            } catch (e: Exception) {
                spotifyStatusView.text = "❌ 에러: ${e.message}\n\n${e.stackTraceToString().take(400)}"
            }
        }
    }

    private var testPlayer: androidx.media3.exoplayer.ExoPlayer? = null

    private fun onYouTubeStopClicked() {
        testPlayer?.stop()
        testPlayer?.release()
        testPlayer = null
        spotifyStatusView.text = "정지됨"
    }

    // --- Spotify 테스트 ---

    private fun onSpotifyLoginClicked() {
        val clientId = BuildConfig.SPOTIFY_CLIENT_ID
        val redirectUri = BuildConfig.SPOTIFY_REDIRECT_URI

        if (clientId.isBlank()) {
            spotifyStatusView.text = "❌ 에러: SPOTIFY_CLIENT_ID가 비어있음\nlocal.properties에 값을 입력하고 다시 빌드하세요."
            return
        }

        spotifyStatusView.text = "Spotify 로그인 시도 중...\nClient ID: ${clientId.take(8)}...\nRedirect URI: $redirectUri"

        try {
            val provider = MusicProviderFactory.create(
                context = this,
                type = MusicProviderType.SPOTIFY,
                clientId = clientId,
                redirectUri = redirectUri
            )
            spotifyAdapter = provider as SpotifyAdapter

            // 이미 인증된 상태인지 확인
            if (spotifyAdapter!!.isAuthenticated()) {
                spotifyStatusView.text = "이미 인증됨! 플레이리스트 로딩 중..."
                loadSpotifyPlaylists()
            } else {
                // Chrome Custom Tab으로 로그인 페이지 열기
                spotifyAdapter!!.startAuthFlow(this)
            }
        } catch (e: Exception) {
            spotifyStatusView.text = "❌ 에러: ${e.message}\n\n${e.stackTraceToString().take(500)}"
        }
    }

    private fun setupSpotifyCallback() {
        SpotifyAuthCallbackActivity.onAuthResult = { success ->
            if (success) {
                spotifyStatusView.text = "✓ 로그인 성공! 플레이리스트 로딩 중..."
                loadSpotifyPlaylists()
            } else {
                spotifyStatusView.text = "❌ 로그인 실패\nSpotify Developer Dashboard에서:\n1. Redirect URI가 'runningapp://callback'인지 확인\n2. 앱 상태가 Development Mode인지 확인\n3. 테스트 계정이 등록되어 있는지 확인"
            }
        }
    }

    private fun checkSpotifyAuthOnResume() {
        val adapter = spotifyAdapter ?: return
        if (adapter.isAuthenticated() && spotifyStatusView.text.contains("로그인 시도")) {
            spotifyStatusView.text = "✓ 로그인 성공! 플레이리스트 로딩 중..."
            loadSpotifyPlaylists()
        }
    }

    private fun loadSpotifyPlaylists() {
        // 인증 후 새 어댑터 인스턴스로 갱신 (토큰이 다른 AuthManager 인스턴스에서 저장됐을 수 있으므로)
        val clientId = BuildConfig.SPOTIFY_CLIENT_ID
        val redirectUri = BuildConfig.SPOTIFY_REDIRECT_URI
        spotifyAdapter = SpotifyAdapter(applicationContext, clientId, redirectUri)
        val adapter = spotifyAdapter ?: return

        lifecycleScope.launch {
            try {
                val playlists = adapter.getPlaylists()
                if (playlists.isEmpty()) {
                    spotifyStatusView.text = "✓ 로그인 성공!\n\n플레이리스트가 비어있습니다.\n(Spotify 계정에 플레이리스트를 만들어보세요)"
                } else {
                    val listText = playlists.mapIndexed { i, pl ->
                        "${i + 1}. ${pl.name} (${pl.trackCount}곡)"
                    }.joinToString("\n")
                    spotifyStatusView.text = "✓ 로그인 성공!\n\n내 플레이리스트 (${playlists.size}개):\n$listText"
                }
            } catch (e: Exception) {
                spotifyStatusView.text = "✓ 로그인됨, 하지만 플레이리스트 로딩 실패:\n${e.message}\n\n${e.stackTraceToString().take(300)}"
            }
        }
    }

    // --- UI 헬퍼 (코드 뷰 구성용) ---

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private fun title(t: String) = TextView(this).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, 0, 0, dp(12))
    }

    private fun label(t: String) = TextView(this).apply {
        text = t
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(12), 0, dp(4))
    }

    private fun mono(size: Float) = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        typeface = Typeface.MONOSPACE
    }

    private fun card(child: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(12))
        setBackgroundColor(Color.parseColor("#F2F2F2"))
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6); bottomMargin = dp(6) }
        layoutParams = lp
        addView(child)
    }

    private fun row(vararg buttons: Button) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        buttons.forEach { b ->
            b.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(b)
        }
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        setOnClickListener { onClick() }
    }
}
