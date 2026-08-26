# Tasks — U3 Music Integration

> 기술 스택: Android Native (Kotlin)
> 음악 서비스: Spotify (MVP는 Mock 먼저)
> 테스트: JUnit5 + MockK

---

## 구현 순서

```text
T1 → T2 → T3 → T4 → T5 → T6 → T7
```

---

## T1. 공통 타입 및 인터페이스 정의

### 목표
음악 모듈의 모든 데이터 클래스와 인터페이스를 정의한다.

### 파일
- `music/model/MusicModels.kt` — data class들
- `music/MusicProvider.kt` — 외부 음악 서비스 인터페이스

### 내용
- `MusicTag` enum (normal, highEnergy, recovery, love, dramatic, favorite)
- `PlaybackState` enum (idle, playing, paused, loading)
- `Track` data class
- `TrackMetadata` data class (bpm, genre, energy)
- `TaggedTrack` data class
- `Playlist` data class
- `MusicRule` data class (preferredTags, fallbackTag, source)
- `TrackPlayLog` data class
- `MusicProvider` interface (authenticate, getPlaylists, playTrack, pause, resume, setVolume, onTrackEnd)

### 완료 조건
- [ ] 컴파일 통과
- [ ] 다른 클래스에서 import 가능

---

## T2. TrackTagger 구현

### 목표
트랙의 BPM/장르를 기반으로 MusicTag를 부여한다.

### 파일
- `music/TrackTagger.kt`
- `music/TrackTaggerTest.kt`

### 로직
```text
BPM > 130 → highEnergy
BPM 100~130 → normal
BPM < 100 → recovery
장르 romance/ballad/love → love 추가
장르 epic/cinematic/orchestral → dramatic 추가
즐겨찾기 → favorite 추가
BPM 없음 → normal
```

### 테스트
- BPM 150 → highEnergy
- BPM 85, 장르 ballad → recovery + love
- BPM null → normal
- 즐겨찾기 → 기존 태그 + favorite

---

## T3. TrackSelector 구현

### 목표
현재 MusicRule 기반으로 다음 곡을 선택한다.

### 파일
- `music/TrackSelector.kt`
- `music/TrackSelectorTest.kt`

### 로직
```text
1. rule.preferredTags 기준으로 후보 필터
2. 최근 재생 이력 제외
3. 후보 중 랜덤 선택
4. 후보 없으면 → fallbackTag로 재시도
5. 여전히 없으면 → 순서대로 다음 곡
```

### 테스트
- highEnergy 규칙 → highEnergy 태그 곡 선택
- 후보 없음 → fallback
- 최근 재생 곡 제외 확인
- 빈 플레이리스트 → null

---

## T4. MockMusicAdapter 구현

### 목표
테스트/데모용 가짜 음악 Provider.

### 파일
- `music/provider/MockMusicAdapter.kt`
- `music/provider/MockMusicAdapterTest.kt`

### Mock 데이터
| id | title | BPM | genre | 기대 태그 |
|---|---|---|---|---|
| m1 | 달려라 | 140 | pop | highEnergy |
| m2 | Love Song | 85 | ballad | recovery, love |
| m3 | Sprint! | 160 | electronic | highEnergy |
| m4 | Chill Wave | 95 | ambient | recovery |
| m5 | Final Boss | 135 | cinematic | highEnergy, dramatic |

### 동작
- 재생 시 설정된 duration(기본 10초) 후 onTrackEnd 콜백
- 볼륨은 내부 상태 관리
- 하이라이트 30초 재생 모드 지원

### 테스트
- playTrack → 상태 playing
- duration 후 → onTrackEnd 호출
- setVolume → 내부 값 변경

---

## T5. AudioDucker 구현

### 목표
TTS 시 음악 볼륨 30%로 fade, 종료 후 복구.

### 파일
- `music/AudioDucker.kt`
- `music/AudioDuckerTest.kt`

### 로직
```text
duck(): 볼륨 → 0.3 (300ms fade)
restore(): 0.3 → 1.0 (300ms fade)
중복 호출 무시
```

### 테스트
- duck → setVolume(0.3) 호출
- restore → setVolume(1.0) 호출
- 중복 duck → 두 번째 무시

---

## T6. MusicController 구현

### 목표
모든 컴포넌트 조합. 음악 모듈 진입점.

### 파일
- `music/MusicController.kt`
- `music/MusicControllerTest.kt`

### 핵심 흐름
```text
loadPlaylist(id):
  tracks = provider.getPlaylistTracks(id)
  taggedTracks = tagger.tagPlaylist(tracks)

play():
  next = selector.selectNext(playlist, rule, history)
  provider.playTrack(next.id)

onTrackEnd():
  이벤트 랜덤 판정
  → 이벤트 발생: 테마곡 하이라이트 30초 재생 → 끝나면 원래 플레이리스트 복귀
  → 이벤트 미발생: 다음 곡 재생

setPlayMode(shuffle/sequential):
  재생 모드 변경

duckVolume() / restoreVolume():
  AudioDucker 위임
```

### 이벤트 30초 하이라이트 처리
```text
1. 곡 끝 → 랜덤 판정 (확률 기반)
2. 발생 시 → 이벤트 테마에 맞는 곡 선택
3. 하이라이트 구간부터 30초만 재생
4. 30초 후 → 원래 플레이리스트 다음 곡으로 복귀
```

### 테스트
- loadPlaylist → 태깅 확인
- play → 첫 곡 재생
- 곡 끝 → 다음 곡 자동 재생
- 이벤트 발생 시 → 30초 하이라이트 재생 후 복귀
- shuffle/sequential 전환
- duck/restore 위임 확인

---

## T7. 통합 테스트

### 목표
Mock 환경에서 전체 플로우 검증.

### 파일
- `music/MusicIntegrationTest.kt`

### 시나리오
```text
1. MockMusicAdapter로 MusicController 생성
2. loadPlaylist → 태깅 완료
3. play() → 첫 곡 재생
4. 곡 종료 → 이벤트 미발생 → 다음 곡
5. 곡 종료 → 이벤트 발생 → 30초 하이라이트 → 복귀
6. duckVolume → 30% 확인
7. restoreVolume → 100% 확인
8. shuffle 모드 전환 확인
9. stop → idle
```

---

## 요약

| Task | 핵심 | 의존 |
|---|---|---|
| T1 타입/인터페이스 | data class, interface | 없음 |
| T2 TrackTagger | BPM/장르 → 태그 | T1 |
| T3 TrackSelector | 규칙 기반 선곡 | T1 |
| T4 MockMusicAdapter | 가짜 재생기 | T1 |
| T5 AudioDucker | 볼륨 fade | T1 |
| T6 MusicController | 전체 조합 + 이벤트 30초 | T1~T5 |
| T7 통합 테스트 | end-to-end 검증 | T1~T6 |
