# Unit of Work — 엔터테인먼트형 러닝 앱

> 프로젝트명: TBD  
> 목적: 팀원이 병렬로 개발할 수 있도록 책임과 의존성을 정의한다.

## 1. Decomposition

```text
U1. App Foundation
U2. Running Tracking
U3. Music Integration
U4. Guide & TTS
U5. Modes & Game Events
U6. Record & Report
U7. AI Agent & Personalization (Stretch)
```

권장 흐름:

```text
U1
├── U2
├── U3
└── U4
     ↓
U5
 ↓
U6
 ↓
U7 (optional)
```

U2/U3/U4는 병렬 개발하고 U5에서 통합한다.

---

# 2. U1 — App Foundation

## Goal
공통 앱 골격과 인터페이스를 만든다.

## Responsibilities
- 프로젝트 생성/navigation/theme
- 환경 변수/권한/공통 오류 처리
- Domain model
- Adapter interface
- Mock/Simulation 기반

## Related Requirements
FR-01, FR-02, FR-03, NFR-04, NFR-06

## Deliverables
- 실행 가능한 app shell
- 러닝 설정 화면
- `RunningSessionConfig`
- `MusicProvider`
- `LocationProvider`
- `TtsPlayer`
- Mock 구조

## Done
- 앱 실행/화면 이동
- 러닝 설정값 생성
- Mock provider 주입 가능

---

# 3. U2 — Running Tracking

## Goal
GPS로 시간, 거리, 현재/평균 페이스를 안정적으로 계산한다.

## Responsibilities
- 위치 권한
- GPS update
- 거리/pace 계산
- smoothing
- pause/resume
- accuracy 검증
- Fake GPS

## Related Requirements
FR-04, FR-05 일부, NFR-01~05

## Deliverables
- `RunningMetrics`
- 계산 unit tests
- 실제 GPS provider
- Fake GPS provider
- 러닝 화면 metric 표시

## Done
- 시작/정지/재개/종료
- 거리/평균 페이스 출력
- GPS jump 최소 필터
- Fake route 테스트

## Dependencies
U1

---

# 4. U3 — Music Integration

## Goal
플레이리스트를 선택하고 이벤트에 따라 음악 규칙을 변경한다.

## Responsibilities
- 음악 provider 인증/연동
- playlist 조회/선택
- play/pause/next
- Track metadata
- app-side music tags
- 상황별 next-track policy
- MockMusicAdapter

## Related Requirements
FR-01, FR-08, FR-15 일부

## Deliverables
- 플레이리스트 UI
- 기본 음악 재생
- 태그 기반 후보 선택
- `high-energy / recovery / love / dramatic`
- Mock provider

## Done
- playlist를 세션에 연결
- 테스트 이벤트로 다음 음악 규칙 변경
- API 실패가 앱 전체를 중단시키지 않음

## Dependencies
U1

---

# 5. U4 — Guide & TTS

## Goal
상황을 음성으로 재미있게 전달한다.

## Responsibilities
- TTS asset 구조
- eventType별 음성 매핑
- 랜덤 TTS 선택
- 반복 방지
- Audio Ducking
- 숫자/페이스 안내 조합

## Related Requirements
FR-05, FR-06, NFR-01, NFR-03

## Categories
`MILESTONE`, `PACE_INFO`, `ENCOURAGEMENT`, `RIVAL`, `FINAL_BOOST`, `FUN_RANDOM`, `SUCCESS`, `FAIL`, `FINISH`

## Deliverables
- TTS metadata JSON/DB
- TTS player
- 테스트 나레이션
- Audio Ducking
- 5분/1km 안내 prototype

## Done
- 이벤트 입력 → 음성 재생
- 동일 음성 연속 반복 방지
- TTS 중 음악 감소/복구

## Dependencies
U1, U3(최종 audio 통합)

---

# 6. U5 — Modes & Game Events

## Goal
앱의 핵심 차별점인 모드/랜덤 이벤트/미션/국가대표 경험 구현

## Responsibilities
### Event Engine
- 조건 평가
- random probability
- cooldown
- priority
- history

### Mode
- Basic
- Marathon
- Interval
- National Team

### Missions
- SPEED_BOOST
- PACE_KEEP
- LAST_SONG
- ONE_MORE_SONG

### Additional
- Pace Combo
- Ghost Runner
- Virtual Ranking

## Related Requirements
FR-03, FR-07~13

## Example Config
```json
{
  "eventType": "IDEAL_TYPE_APPEARED",
  "weight": 10,
  "cooldownSec": 900,
  "ttsCategory": "FUN_RANDOM",
  "musicTag": "love"
}
```

## Done
- 4개 모드가 서로 다른 규칙
- 최소 3개 랜덤 이벤트
- cooldown/priority
- Event → TTS + Music 연결
- 인터벌 자동 전환
- 국가대표 모드 라이벌 이벤트 1개 이상

## Dependencies
U2, U3, U4

---

# 7. U6 — Record & Report

## Goal
오늘의 러닝을 "스토리" 형태로 기록한다.

## Responsibilities
- RunRecord
- EventLog
- MissionResult
- TrackPlayLog
- summary
- report UI

## Related Requirements
FR-14, FR-15

## Done
종료 후 다음을 확인:
- 거리
- 시간
- 평균 페이스
- 최고 페이스
- 사용 모드
- 발생 이벤트
- 미션 결과

## Dependencies
U2, U3, U5

---

# 8. U7 — AI Agent & Personalization (Stretch)

## Goal
규칙 기반 MVP 위에 Agent 기반 개인화를 추가한다.

## Responsibilities
- 러닝 전 추천 모드
- 이벤트/미션 조합 추천
- 종료 후 한 줄 해설
- 장기 개인화

## Agent Tools Example
```text
getUserRunningHistory()
getPlaylistProfile()
getAvailableModes()
getAvailableEventTypes()
saveSessionRecommendation()
```

## Done
- Agent 실패 시에도 핵심 러닝 정상
- Agent 결과를 모드/이벤트 설정에 반영 가능
- 센서/safety 판정은 Agent 밖에서 수행

## Dependencies
U1, U5, U6

---

# 9. Dependency Matrix

| Unit | U1 | U2 | U3 | U4 | U5 | U6 | U7 |
|---|---:|---:|---:|---:|---:|---:|---:|
| U1 Foundation | - | | | | | | |
| U2 Tracking | Req | - | | | | | |
| U3 Music | Req | | - | | | | |
| U4 TTS | Req | | Int | - | | | |
| U5 Modes/Game | Req | Req | Req | Req | - | | |
| U6 Report | Req | Req | Useful | | Req | - | |
| U7 Agent | Req | | | | Req | Req | - |

---

# 10. Parallel Development Plan

## Phase A
공통: U1 Foundation / interface 합의

## Phase B — 병렬
- **Owner 1:** U2 Running Tracking
- **Owner 2:** U3 Music Integration
- **Owner 3:** U4 Guide & TTS

## Phase C
공동: U5 Modes & Game Events 통합

## Phase D
U6 Record & Report + QA + 데모

## Phase E
여유 시 U7 AI Agent

> 팀원이 3명이라면 U2/U3/U4 분배가 특히 자연스럽다.

---

# 11. Suggested Git Strategy

```text
main
develop
├── feature/u2-running-tracking
├── feature/u3-music
├── feature/u4-tts-guide
├── feature/u5-event-engine
└── feature/u6-report
```

- develop에서 feature branch 생성
- 작은 PR 단위
- 공통 model/interface 변경 시 공유
- U5 통합 전에 U2/U3/U4 interface 고정

---

# 12. Definition of Done

- [ ] 관련 Requirement ID가 있다.
- [ ] 정상 흐름이 구현되어 있다.
- [ ] 주요 오류/빈 상태가 처리되어 있다.
- [ ] 핵심 로직 unit test가 있다.
- [ ] 공통 interface를 깨지 않는다.
- [ ] 팀원이 실행할 수 있다.
- [ ] 실제 기능 또는 Mock으로 demo 가능하다.

---

# 13. MVP Integration Scenario

```text
1. 앱 실행
2. 플레이리스트 선택
3. 목표 5km
4. 국가대표 모드
5. 러닝 시작
6. GPS 또는 Fake GPS로 pace 생성
7. 5분 경과 → pace 안내 TTS
8. 랜덤 "이상형 등장" → love 음악 rule
9. pace 하락 → 격려 + high-energy rule
10. 마지막 구간 → Final Boost
11. 러닝 종료
12. 거리/pace/이벤트 리포트
```

이 시나리오가 성공하면 프로젝트의 핵심 가치가 한 번의 데모로 전달된다.

---

# 14. Scope Cut Order

일정이 부족하면 순서대로 제외:
1. AI Agent 개인화
2. 곡별 퍼포먼스
3. Pace Combo
4. 여러 미션
5. Ghost Runner 고도화
6. 랜덤 이벤트 종류 축소

### 끝까지 유지
- GPS Pace
- Playlist
- TTS
- 국가대표 모드
- 랜덤 이벤트
- 상황별 음악 변화
- 종료 리포트
