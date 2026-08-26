package com.pknu.running.music.provider

import org.json.JSONArray
import org.json.JSONObject

/**
 * Spotify Web API 응답을 파싱하기 위한 DTO 클래스들.
 * Android 내장 org.json을 사용하여 외부 의존성을 최소화한다.
 */

// --- 인증 ---

data class SpotifyTokenResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Int,
    val refreshToken: String?,
    val scope: String
) {
    companion object {
        fun fromJson(json: JSONObject) = SpotifyTokenResponse(
            accessToken = json.getString("access_token"),
            tokenType = json.getString("token_type"),
            expiresIn = json.getInt("expires_in"),
            refreshToken = json.optString("refresh_token", null),
            scope = json.optString("scope", "")
        )
    }
}

// --- 플레이리스트 ---

data class SpotifyPlaylistItem(
    val id: String,
    val name: String,
    val trackCount: Int,
    val imageUrl: String?
) {
    companion object {
        fun fromJson(json: JSONObject) = SpotifyPlaylistItem(
            id = json.getString("id"),
            name = json.getString("name"),
            trackCount = json.getJSONObject("tracks").getInt("total"),
            imageUrl = json.optJSONArray("images")
                ?.optJSONObject(0)
                ?.optString("url")
        )

        fun listFromJson(json: JSONObject): List<SpotifyPlaylistItem> {
            val items = json.getJSONArray("items")
            return (0 until items.length()).map { fromJson(items.getJSONObject(it)) }
        }
    }
}

// --- 트랙 ---

data class SpotifyTrackItem(
    val id: String,
    val name: String,
    val artists: List<String>,
    val durationMs: Long,
    val albumImageUrl: String?,
    val uri: String
) {
    companion object {
        fun fromJson(json: JSONObject): SpotifyTrackItem {
            val artists = json.getJSONArray("artists")
            val artistNames = (0 until artists.length()).map {
                artists.getJSONObject(it).getString("name")
            }
            return SpotifyTrackItem(
                id = json.getString("id"),
                name = json.getString("name"),
                artists = artistNames,
                durationMs = json.getLong("duration_ms"),
                albumImageUrl = json.optJSONObject("album")
                    ?.optJSONArray("images")
                    ?.optJSONObject(0)
                    ?.optString("url"),
                uri = json.getString("uri")
            )
        }

        /**
         * /playlists/{id}/tracks 응답에서 트랙 리스트를 파싱한다.
         * 각 item은 { track: {...} } 구조이다.
         */
        fun listFromPlaylistJson(json: JSONObject): List<SpotifyTrackItem> {
            val items = json.getJSONArray("items")
            return (0 until items.length()).mapNotNull { i ->
                val trackJson = items.getJSONObject(i).optJSONObject("track")
                // null track (삭제된 곡 등) 건너뛰기
                if (trackJson != null && !trackJson.isNull("id")) {
                    fromJson(trackJson)
                } else null
            }
        }
    }
}

// --- 오디오 특성 (메타데이터) ---

data class SpotifyAudioFeatures(
    val trackId: String,
    val bpm: Float,
    val energy: Float,
    val valence: Float,
    val danceability: Float
) {
    companion object {
        fun fromJson(json: JSONObject) = SpotifyAudioFeatures(
            trackId = json.getString("id"),
            bpm = json.getDouble("tempo").toFloat(),
            energy = json.getDouble("energy").toFloat(),
            valence = json.getDouble("valence").toFloat(),
            danceability = json.getDouble("danceability").toFloat()
        )

        /**
         * /audio-features?ids=... 응답에서 리스트를 파싱한다.
         */
        fun listFromJson(json: JSONObject): List<SpotifyAudioFeatures> {
            val features = json.getJSONArray("audio_features")
            return (0 until features.length()).mapNotNull { i ->
                val item = features.optJSONObject(i)
                if (item != null && !item.isNull("id")) fromJson(item) else null
            }
        }
    }
}

// --- 현재 재생 상태 ---

data class SpotifyPlaybackState(
    val isPlaying: Boolean,
    val progressMs: Long,
    val trackId: String?,
    val deviceId: String?
) {
    companion object {
        fun fromJson(json: JSONObject): SpotifyPlaybackState {
            val item = json.optJSONObject("item")
            val device = json.optJSONObject("device")
            return SpotifyPlaybackState(
                isPlaying = json.optBoolean("is_playing", false),
                progressMs = json.optLong("progress_ms", 0),
                trackId = item?.optString("id"),
                deviceId = device?.optString("id")
            )
        }
    }
}

// --- 장르 정보 (아티스트에서 추출) ---

data class SpotifyArtist(
    val id: String,
    val name: String,
    val genres: List<String>
) {
    companion object {
        fun fromJson(json: JSONObject): SpotifyArtist {
            val genreArray = json.optJSONArray("genres") ?: JSONArray()
            return SpotifyArtist(
                id = json.getString("id"),
                name = json.getString("name"),
                genres = (0 until genreArray.length()).map { genreArray.getString(it) }
            )
        }
    }
}
