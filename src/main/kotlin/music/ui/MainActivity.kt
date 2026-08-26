package music.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import music.model.Track
import music.provider.MockMusicAdapter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MusicDemoScreen()
            }
        }
    }
}

@Composable
fun MusicDemoScreen() {
    val adapter = remember { MockMusicAdapter(defaultTrackDurationMs = 5_000) }
    val scope = rememberCoroutineScope()

    var playlists by remember { mutableStateOf<List<String>>(emptyList()) }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var statusText by remember { mutableStateOf("대기 중") }
    var currentTrack by remember { mutableStateOf<Track?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Running Music Demo",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(text = "상태: $statusText", style = MaterialTheme.typography.bodyLarge)

        currentTrack?.let { track ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("♪ 현재 재생 중", style = MaterialTheme.typography.labelMedium)
                    Text("${track.title} - ${track.artist}", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                scope.launch {
                    adapter.authenticate()
                    val pls = adapter.getPlaylists()
                    playlists = pls.map { "${it.name} (${it.trackCount}곡)" }
                    statusText = "인증 완료 — 플레이리스트 ${pls.size}개 로드됨"
                }
            }) {
                Text("연결")
            }

            Button(onClick = {
                scope.launch {
                    val loadedTracks = adapter.getPlaylistTracks("pl1")
                    tracks = loadedTracks
                    statusText = "트랙 ${loadedTracks.size}곡 로드됨"
                }
            }) {
                Text("트랙 로드")
            }

            Button(onClick = {
                scope.launch {
                    adapter.stop()
                    currentTrack = null
                    statusText = "정지"
                }
            }) {
                Text("정지")
            }
        }

        if (playlists.isNotEmpty()) {
            Text("플레이리스트:", style = MaterialTheme.typography.titleSmall)
            playlists.forEach { Text("  • $it") }
        }

        if (tracks.isNotEmpty()) {
            Text("트랙 목록 (탭하여 재생):", style = MaterialTheme.typography.titleSmall)
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(tracks) { track ->
                    OutlinedCard(
                        onClick = {
                            scope.launch {
                                adapter.playTrack(track.id)
                                currentTrack = track
                                statusText = "재생 중: ${track.title}"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(track.title, style = MaterialTheme.typography.bodyLarge)
                                Text(track.artist, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                "${track.durationMs / 1000}s",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}
