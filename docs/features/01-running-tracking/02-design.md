# Feature 01 — 설계 (Design)

> AI-DLC / Construction 직전 설계 문서
> 이 문서는 다른 기능(음악/TTS/리포트) 담당자가 참고할 **공개 계약(contract)** 이다.

## 1. 패키지 구조

```
com.pknu.running.tracking
├── model
│   ├── LocationSample.kt      // GPS 입력 1건
│   ├── RunningMetrics.kt      // 실시간 지표 (출력)
│   ├── RunningTarget.kt       // 목표 (입력)
│   ├── RunningEvent.kt        // 러닝 이벤트 (출력)
│   ├── RunningState.kt        // 세션 상태 enum
│   └── TrackingConfig.kt      // 튜닝 파라미터
├── math
│   ├── GeoDistance.kt         // Haversine 거리
│   └── PaceCalculator.kt      // 페이스 계산 + EMA 스무딩
├── filter
│   └── LocationFilter.kt      // 정확도/속도 이상치 필터
├── event
│   └── EventDetector.kt       // 시간/거리/페이스저하/마지막500m 감지
├── location
│   ├── LocationProvider.kt        // GPS 소스 인터페이스
│   ├── FusedLocationProvider.kt   // 실제 Android 구현
│   └── FakeLocationProvider.kt    // 시뮬레이션 구현 (테스트/데모)
└── RunningTracker.kt          // 세션 엔진 (조립 + Flow 방출)
```

## 2. 데이터 모델 (공개 계약)

### LocationSample — 입력
```
LocationSample {
  latitude: Double
  longitude: Double
  timestampMs: Long
  accuracyMeter: Float     // GPS 오차 반경
  speedMps: Float?         // 기기가 제공하는 속도 (optional)
}
```

### RunningTarget — 입력 (모두 optional)
```
RunningTarget {
  distanceMeter: Double?
  durationSec: Long?
  paceSecPerKm: Double?
}
```

### RunningMetrics — 출력 (application-design.md의 RunningMetrics와 정합)
```
RunningMetrics {
  timestampMs: Long
  elapsedTimeSec: Long
  totalDistanceMeter: Double
  currentPaceSecPerKm: Double?    // 이동 없음/데이터 부족 시 null
  smoothedPaceSecPerKm: Double?
  averagePaceSecPerKm: Double?
  gpsAccuracyMeter: Float
  state: RunningState
}
```
> 페이스 단위는 sec/km. 멈춰 있거나 표본이 부족하면 null (0 나눗셈/무한대 방지).

### RunningEvent — 출력
```
RunningEvent {
  type: RunningEventType
  occurredAtMs: Long
  elapsedTimeSec: Long
  totalDistanceMeter: Double
  metadata: Map<String, Any>   // 예: {"km": 2}, {"minute": 5}, {"currentPace":..., "targetPace":...}
}

enum RunningEventType {
  TIME_MILESTONE,      // 5분 경과 (반복)
  DISTANCE_MILESTONE,  // 1km 통과 (반복)
  PACE_DROP,           // 목표보다 느려짐
  LAST_STRETCH         // 마지막 500m 진입
}
```
> 다른 기능(음악/TTS)은 이 `RunningEventType`을 구독해서 자기 규칙을 실행한다.

### RunningState — 세션 상태
```
enum RunningState { READY, RUNNING, PAUSED, FINISHED }
```

## 3. 핵심 계산 로직

### 3.1 거리 (GeoDistance, Haversine)
두 위경도 사이 구면 거리(m)를 계산한다. 지구 반지름 6,371,000m 기준.

### 3.2 필터 (LocationFilter)
새 샘플이 들어오면 순서대로:
1. `accuracyMeter > maxAccuracyMeter(30)` → **거리 누적 제외** (샘플은 보관하되 거리 미반영)
2. 직전 유효 샘플과의 `distance / dt`로 순간 속도 계산 → `> maxSpeedMps(12)` → **점프로 간주, 폐기**
3. 통과한 샘플만 거리 누적 및 페이스 계산에 사용

### 3.3 페이스 (PaceCalculator)
- **현재 페이스**: 최근 `currentPaceWindowSec(10)`초 구간의 (거리, 시간)으로 sec/km 산출
- **평균 페이스**: 누적 총거리 / 누적 경과시간
- **스무딩 페이스**: 현재 페이스에 EMA 적용 `s = alpha*x + (1-alpha)*s_prev`
- 거리 0 또는 시간 0이면 해당 페이스는 null

pace(sec/km) = (elapsedSec / distanceMeter) * 1000

### 3.4 이벤트 감지 (EventDetector)
매 tick마다 현재 metrics를 받아 판정한다.

- **TIME_MILESTONE**: `elapsedTimeSec`이 300의 배수를 새로 넘으면 발생. `lastFiredMinuteMark`로 중복 방지.
- **DISTANCE_MILESTONE**: `totalDistanceMeter`가 1000의 배수를 새로 넘으면 발생. `lastFiredKmMark`로 중복 방지.
- **PACE_DROP**: target.paceSecPerKm 존재 시. `smoothedPace > targetPace * (1 + paceDropRatio)` 상태가 `paceDropSustainSec(5)`초 연속 지속되면 발생. 발생 후 `paceDropCooldownSec(30)`초 억제.
- **LAST_STRETCH**: target.distanceMeter 존재 시. `totalDistanceMeter >= targetDistance - lastStretchMeter(500)` 최초 진입 시 1회.

> PAUSED 상태에서는 시간/거리 누적과 속도 기반 이벤트를 중지한다 (FR-04, NFR-05).

## 4. 세션 엔진 (RunningTracker)

### 공개 API
```
class RunningTracker(config: TrackingConfig = TrackingConfig()) {
  val metrics: StateFlow<RunningMetrics>
  val events: SharedFlow<RunningEvent>
  val state: StateFlow<RunningState>

  fun start(target: RunningTarget = RunningTarget())
  fun pause()
  fun resume()
  fun finish(): RunRecordSummary   // 요약 반환 (리포트 팀이 사용)
  fun onLocation(sample: LocationSample)   // LocationProvider가 호출
}
```

### finish 반환 요약
```
RunRecordSummary {
  totalDistanceMeter: Double
  elapsedTimeSec: Long
  averagePaceSecPerKm: Double?
  bestPaceSecPerKm: Double?
  events: List<RunningEvent>
}
```
> DB 저장은 하지 않는다. 리포트/기록 담당(기능 4)이 이 요약을 받아 저장한다.

### 동작
- `start()` → 상태 RUNNING, 1초 tick 시작 (coroutine)
- tick마다: 경과시간 갱신 → 현재 metrics 재계산 → `metrics` 방출 → `EventDetector`로 이벤트 판정 → 있으면 `events` 방출
- `onLocation()` → 필터 통과 시 거리 누적 및 페이스 표본 추가
- `pause()`/`resume()` → tick 및 누적 일시중지/재개
- `finish()` → tick 종료, 요약 반환

## 5. LocationProvider (GPS 추상화)

```
interface LocationProvider {
  val samples: Flow<LocationSample>
  suspend fun start()
  fun stop()
}
```
- `FusedLocationProvider`: Google Play Services 기반 실제 구현 (1초 주기, 고정밀)
- `FakeLocationProvider`: 미리 정의된 페이스 시나리오로 위치를 생성. 시뮬레이션 테스트/데모용.
  - 예: `0~60s 6'30"/km, 60~120s 5'30"/km, 120~150s 4'50"/km`

## 6. 테스트 전략 (NFR-06)

| 대상 | 테스트 |
|------|--------|
| GeoDistance | 알려진 좌표쌍 거리 검증 (오차 허용) |
| PaceCalculator | 등속 이동 시 페이스, 정지 시 null, EMA 수렴 |
| LocationFilter | 저정확도 제외, 순간 텔레포트 폐기 |
| EventDetector | 5분/1km 반복 발생, 페이스 저하 지속·쿨다운, 마지막 500m 1회 |
| RunningTracker | FakeGps 시나리오 주입 → metrics/이벤트 순서 검증, pause/resume 누적 정지 |

`kotlinx-coroutines-test`의 가상 시계로 시간 기반 로직을 결정적으로 테스트한다.

## 7. 다른 기능과의 인터페이스 요약 (계약)

- **음악(기능 2)**: `events`에서 `PACE_DROP`, `LAST_STRETCH` 등을 구독 → 음악 태그 전환
- **TTS/이벤트(기능 3)**: `events`의 `TIME_MILESTONE`, `DISTANCE_MILESTONE` 구독 → 안내 재생, `metrics`의 현재 페이스 사용
- **리포트(기능 4)**: `finish()`의 `RunRecordSummary` 사용 → 저장/표시
