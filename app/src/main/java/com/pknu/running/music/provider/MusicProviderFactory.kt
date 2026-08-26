package com.pknu.running.music.provider

import android.content.Context
import com.pknu.running.music.MusicProvider

/**
 * MusicProvider 구현체를 생성하는 팩토리.
 * Mock과 Spotify를 런타임에 전환할 수 있다.
 *
 * 사용 예:
 * ```
 * val provider = MusicProviderFactory.create(context, MusicProviderType.SPOTIFY)
 * ```
 */
object MusicProviderFactory {

    /**
     * 지정된 타입의 MusicProvider를 생성한다.
     *
     * @param context Android Context (Spotify 인증에 필요)
     * @param type 생성할 Provider 타입
     * @param clientId Spotify Client ID (SPOTIFY 타입일 때 필수)
     * @param redirectUri Spotify Redirect URI (SPOTIFY 타입일 때 필수)
     */
    fun create(
        context: Context,
        type: MusicProviderType,
        clientId: String = "",
        redirectUri: String = ""
    ): MusicProvider {
        return when (type) {
            MusicProviderType.MOCK -> MockMusicAdapter()
            MusicProviderType.SPOTIFY -> {
                require(clientId.isNotBlank()) {
                    "Spotify Client ID is required. Set SPOTIFY_CLIENT_ID in local.properties."
                }
                SpotifyAdapter(
                    context = context,
                    clientId = clientId,
                    redirectUri = redirectUri.ifBlank { "runningapp://callback" }
                )
            }
            MusicProviderType.SPOTIFY_YOUTUBE -> {
                require(clientId.isNotBlank()) {
                    "Spotify Client ID is required. Set SPOTIFY_CLIENT_ID in local.properties."
                }
                val spotifyAdapter = SpotifyAdapter(
                    context = context,
                    clientId = clientId,
                    redirectUri = redirectUri.ifBlank { "runningapp://callback" }
                )
                YouTubePlayerAdapter(
                    context = context,
                    spotifyAdapter = spotifyAdapter
                )
            }
        }
    }

    /**
     * BuildConfig 값을 사용하여 Spotify Provider를 생성하는 편의 메서드.
     * Client ID가 비어있으면 자동으로 Mock으로 fallback한다.
     */
    fun createWithFallback(
        context: Context,
        preferSpotify: Boolean = true,
        clientId: String = "",
        redirectUri: String = ""
    ): MusicProvider {
        return if (preferSpotify && clientId.isNotBlank()) {
            create(context, MusicProviderType.SPOTIFY, clientId, redirectUri)
        } else {
            create(context, MusicProviderType.MOCK)
        }
    }
}

/**
 * MusicProvider 구현체 타입.
 */
enum class MusicProviderType {
    /** 테스트/개발용 Mock 어댑터 */
    MOCK,
    /** Spotify Web API 연동 어댑터 (Premium 필요) */
    SPOTIFY,
    /** Spotify 메타데이터 + YouTube 재생 하이브리드 (Free 계정 OK) */
    SPOTIFY_YOUTUBE
}
