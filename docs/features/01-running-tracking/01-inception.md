# Feature 01 — 러닝 기록·상태 인식 (Running Tracking)

> AI-DLC / Inception 산출물
> 담당 모듈: `Location & Pace Engine (C-03)` + `Event Engine (C-05)`의 Time/Distance/Pace 부분
> 상위 요구사항: FR-04(GPS 기반 기록 측정), FR-05(주기적 가이드의 트리거), NFR-06(테스트 가능성)

## 1. 이 기능의 역할

다른 기능(음악, TTS, 랜덤 이벤트, 리포트)이 소비할 **러닝 데이터를 생산하는 기반 모듈**이다.
GPS 위치 스트림을 입력받아 실시간 러닝 지표(`RunningMetrics`)와 러닝 이벤트(`RunningEvent`)를 만들어 방출한다.

이 모듈은 UI, 음악, TTS 재생을 직접 하지 않는다. 오직 데이터/이벤트만 만든다.

## 2. 범위 (Scope)

### 포함
- GPS 기반 현재 위치 / 이동거리 측정
- 경과 시간, 총 거리, 현재 페이스, 평균 페이스, 스무딩 페이스 계산
- 목표 거리 / 시간 / 페이스 설정 입력 (모두 optional)
- 페이스 변화 및 이벤트 감지
  - 5분 경과 (반복)
  - 1km 통과 (반복)
  - 목표 페이스보다 느려짐
  - 마지막 500m 진입
- 세션 생명주기 제어: READY → RUNNING → PAUSED → RUNNING → FINISHED
- 실제 GPS 연동(`FusedLocationProvider`)을 인터페이스 뒤로 분리한 구현
- Fake GPS(시뮬레이션) 제공으로 실제 달리기 없이 테스트 가능
- 단위 테스트

### 제외 (다른 담당자 / 이후 작업)
- 러닝 진행 UI 화면 (기능 4 담당)
- 음악/TTS 재생 (기능 2, 3 담당)
- 랜덤 이벤트 연출, 미션, 콤보, Ghost Runner (기능 3 담당)
- 기록 영속화(DB 저장) — 이 모듈은 in-memory 결과만 제공, 저장은 기능 4

## 3. 확정된 결정 사항 (Inception Q&A 결과)

| ID | 항목 | 결정 |
|----|------|------|
| A1 | 위치 업데이트 주기 | 1초 간격 |
| A2 | metrics 방출 주기 | 1초 고정 주기 (정지 중에도 시간은 흐름) |
| B1 | 거리 계산식 | Haversine |
| B2 | 현재 페이스 산출 창 | 최근 10초 이동 데이터 |
| B3 | 페이스 스무딩 | 지수이동평균(EMA) |
| C1 | 정확도 필터 | accuracy > 30m 샘플은 거리 계산에서 제외 |
| C2 | 속도 이상치(점프) 제거 | 순간 속도 > 12 m/s (≈ 2'20"/km) 구간 폐기 |
| D1 | 5분(시간) 이벤트 | 5분마다 반복 (5, 10, 15분 …) |
| D2 | 1km(거리) 이벤트 | 1km마다 반복 (1, 2, 3km …) |
| D3 | 목표 페이스 저하 판정 | smoothed pace가 목표보다 10% 이상 느림이 5초 지속 시 발생, 30초 쿨다운 |
| D4 | 마지막 500m 진입 | 목표 거리가 있을 때 `총거리 >= 목표 - 500m` 진입 시 1회 |
| E1 | 일시정지 동작 | 시간·거리 누적 중지, 재개 시 이어서 계산 |
| E2 | 출력 형태 | `StateFlow<RunningMetrics>` + `SharedFlow<RunningEvent>` |
| E3 | 목표 입력 | 거리(m)/시간(sec)/페이스(sec/km) 모두 optional |
| F1 | 산출물 범위 | 순수 엔진 + Fake GPS + 단위 테스트 + FusedLocation 연동 (UI 제외) |

## 4. 튜닝 가능한 파라미터 (기본값)

모두 `TrackingConfig`로 주입 가능하게 설계한다. 아래는 기본값.

| 파라미터 | 기본값 | 설명 |
|----------|--------|------|
| `locationIntervalMs` | 1000 | GPS 요청 주기 |
| `metricsTickMs` | 1000 | metrics 방출 주기 |
| `currentPaceWindowSec` | 10 | 현재 페이스 계산 창 |
| `emaAlpha` | 0.2 | 스무딩 EMA 계수 (0~1, 클수록 반응 빠름) |
| `maxAccuracyMeter` | 30.0 | 이 값보다 부정확한 샘플은 거리 제외 |
| `maxSpeedMps` | 12.0 | 이 값 초과 이동은 GPS 점프로 간주하여 폐기 |
| `timeEventIntervalSec` | 300 | 시간 이벤트 반복 주기 (5분) |
| `distanceEventIntervalMeter` | 1000 | 거리 이벤트 반복 주기 (1km) |
| `paceDropRatio` | 0.10 | 목표 대비 이 비율 이상 느리면 저하로 간주 |
| `paceDropSustainSec` | 5 | 저하가 이만큼 지속되어야 이벤트 발생 |
| `paceDropCooldownSec` | 30 | 저하 이벤트 재발생 억제 시간 |
| `lastStretchMeter` | 500 | 마지막 구간 진입 거리 |

## 5. Open Decisions (이후에 정할 것)

- 실기기에서 최소 이동 거리(minDisplacement) 적용 여부 — 배터리 절약용, MVP에서는 미적용
- 백그라운드(화면 꺼짐) 지속을 위한 Foreground Service — 매니페스트 권한만 준비, 서비스 구현은 기능 4 통합 시점에 결정
