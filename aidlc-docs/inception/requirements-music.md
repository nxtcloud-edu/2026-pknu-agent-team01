# Requirements — U3 Music Integration

> 목적: 러닝 상태와 이벤트에 맞춰 음악 경험을 바꾸는 모듈의 요구사항 정의

---

## 1. Overview

사용자가 자신의 플레이리스트를 선택하여 러닝 중 음악을 듣되, 페이스·이벤트·모드 상황에 따라 **다음 곡의 분류가 자동으로 변경**되는 기능을 제공한다.

### 핵심 역할
러닝 상태와 이벤트에 맞춰 음악 경험을 바꾸는 모듈

---

## 2. Functional Requirements

### MU-01. 플레이리스트 선택

**User Story:** 사용자는 평소 듣는 플레이리스트를 러닝에 사용하고 싶다.

- **WHEN** 러닝 준비 화면에 진입하면
  **THE SYSTEM SHALL** 연결된 음악 서비스에서 사용자의 플레이리스트 목록을 표시한다.
- **WHEN** 사용자가 플레이리스트를 선택하면
  **THE SYSTEM SHALL** 해당 플레이리스트를 현재 러닝 세션에 연결한다.
- **WHEN** 음악 서비스 연동에 실패하면
  **THE SYSTEM SHALL** 오류를 알리고 재연결 또는 Mock 재생을 제공한다.

#### Acceptance Criteria
- [ ] 플레이리스트 목록이 화면에 표시된다
- [ ] 선택한 플레이리스트가 세션에 저장된다
- [ ] 연동 실패 시 앱이 크래시되지 않는다

---

### MU-02. 기본 플레이리스트 재생

**User Story:** 러닝이 시작되면 선택한 음악이 자동으로 재생되어야 한다.

- **WHEN** 러닝이 시작되면
  **THE SYSTEM SHALL** 선택된 플레이리스트의 첫 곡(또는 셔플 첫 곡)을 재생한다.
- **WHEN** 현재 곡이 끝나면
  **THE SYSTEM SHALL** 현재 음악 규칙에 따라 다음 곡을 재생한다.
- **WHEN** 러닝이 일시정지되면
  **THE SYSTEM SHALL** 음악도 일시정지한다.
- **WHEN** 러닝이 재개되면
  **THE SYSTEM SHALL** 음악도 재개한다.

#### Acceptance Criteria
- [ ] 러닝 시작 시 음악이 자동 재생된다
- [ ] 곡 종료 시 다음 곡이 자동 재생된다
- [ ] 일시정지/재개가 음악과 동기화된다

---

### MU-03. 상황별 음악 분류 (Music Tags)

**User Story:** 러닝 상황에 맞는 분위기의 음악이 재생되어야 한다.

- **WHEN** 플레이리스트가 로드되면
  **THE SYSTEM SHALL** 각 트랙에 음악 태그를 분류한다.
- **WHEN** 태그 분류 기준은 다음과 같다:
  - `normal` — 일반 음악 (기본 상태)
  - `high-energy` — BPM 높은 음악 (스피드업, 스퍼트)
  - `recovery` — BPM 낮은 음악 (회복 구간)
  - `love` — 사랑 노래 (이상형 이벤트 등)
  - `dramatic` — 마지막 스퍼트용 음악 (Final Boost)
  - `favorite` — 사용자 즐겨찾기

#### 태그 분류 방식 (MVP)
1. BPM 기반 자동 분류: high(>130) / normal(100~130) / low(<100)
2. 메타데이터 기반 보조 분류 (장르, 분위기)
3. 사용자 수동 태깅 (추후 확장)

#### Acceptance Criteria
- [ ] 모든 트랙에 최소 1개 태그가 부여된다
- [ ] BPM 기반 자동 분류가 동작한다
- [ ] 태그가 없는 곡은 `normal`로 기본 분류된다

---

### MU-04. 페이스 기반 다음 곡 전환

**User Story:** 내 달리기 속도에 맞는 음악이 자동으로 선곡되어야 한다.

- **WHEN** 현재 곡이 끝나고 다음 곡을 선택할 때
  **THE SYSTEM SHALL** 현재 페이스와 음악 규칙을 기반으로 적절한 태그의 곡을 선택한다.
- **WHEN** 페이스가 목표보다 빠르면
  **THE SYSTEM SHALL** 현재 리듬을 유지하는 곡(`normal` 또는 `high-energy`)을 우선한다.
- **WHEN** 페이스가 목표보다 느리면
  **THE SYSTEM SHALL** `high-energy` 태그 곡을 우선 추천한다.
- **WHEN** 회복 구간이면
  **THE SYSTEM SHALL** `recovery` 태그 곡을 우선한다.
- **WHEN** 해당 태그 후보가 없으면
  **THE SYSTEM SHALL** 일반 순서대로 다음 곡을 재생한다.

#### Acceptance Criteria
- [ ] 페이스 상태에 따라 다음 곡의 태그 우선순위가 변경된다
- [ ] 태그 후보 없을 때 fallback으로 일반 재생된다
- [ ] 현재 곡을 즉시 끊지 않고 다음 곡부터 반영한다

---

### MU-05. 이벤트 기반 음악 규칙 변경

**User Story:** 랜덤 이벤트가 발생하면 분위기에 맞는 음악으로 바뀌어야 한다.

- **WHEN** Event Engine에서 음악 태그 변경 요청이 오면
  **THE SYSTEM SHALL** 다음 곡부터 해당 태그 우선으로 선곡한다.
- **WHEN** `IDEAL_TYPE_APPEARED` 이벤트가 발생하면
  **THE SYSTEM SHALL** `love` 태그 음악을 다음 곡으로 우선한다.
- **WHEN** `RIVAL_CHASING` 이벤트가 발생하면
  **THE SYSTEM SHALL** `high-energy` 태그 음악을 다음 곡으로 우선한다.
- **WHEN** `FINAL_BOOST` 이벤트가 발생하면
  **THE SYSTEM SHALL** `dramatic` 태그 음악을 다음 곡으로 우선한다.
- **WHEN** 이벤트 효과 시간이 만료되면
  **THE SYSTEM SHALL** 기본 음악 규칙으로 복귀한다.

#### Event → Music Tag 매핑
| Event | Music Tag |
|---|---|
| IDEAL_TYPE_APPEARED | love |
| RIVAL_CHASING | high-energy |
| PACE_DROP | high-energy |
| GOOD_RHYTHM | normal (유지) |
| FINAL_BOOST | dramatic |
| INTERVAL_WORK | high-energy |
| INTERVAL_RECOVERY | recovery |

#### Acceptance Criteria
- [ ] 이벤트 수신 시 음악 규칙이 변경된다
- [ ] 현재 곡은 끊지 않고 다음 곡부터 적용된다
- [ ] 이벤트 효과 만료 후 기본 규칙으로 복귀한다

---

### MU-06. Audio Ducking (TTS 협력)

**User Story:** TTS 안내가 나올 때 음악이 작아져야 들린다.

- **WHEN** TTS 재생이 시작되면
  **THE SYSTEM SHALL** 음악 볼륨을 일시적으로 낮춘다 (예: 30%).
- **WHEN** TTS 재생이 종료되면
  **THE SYSTEM SHALL** 음악 볼륨을 원래대로 복구한다.

#### Acceptance Criteria
- [ ] TTS 시작 시 음악 볼륨이 감소한다
- [ ] TTS 종료 시 음악 볼륨이 복구된다
- [ ] 볼륨 전환이 부드럽다 (급격한 변화 없음)

---

## 3. 추후 확장 (Post-MVP)

### MU-07. 곡별 러닝 퍼포먼스 분석
- 각 곡 재생 구간의 평균 페이스를 기록한다.
- 데이터가 충분하면 "나를 가장 빠르게 뛰게 만드는 곡 TOP N"을 제공한다.

### MU-08. 개인별 러닝 음악 추천
- 러닝 기록 기반으로 BPM-페이스 상관관계를 분석한다.
- AI Agent를 활용해 개인화된 음악 추천을 제공한다.

---

## 4. Non-Functional Requirements

### NFR-MU-01. 음악 전환 자연스러움
- 현재 곡을 도중에 끊지 않는다 (다음 곡부터 규칙 적용).
- 볼륨 변경은 페이드 처리한다.

### NFR-MU-02. 외부 API 장애 대응
- 음악 서비스 연결 실패 시에도 러닝 자체는 정상 동작한다.
- Mock 재생 또는 오프라인 대체 수단을 제공한다.

### NFR-MU-03. 테스트 가능성
- 음악 Provider는 인터페이스로 분리하여 Mock 주입 가능해야 한다.
- 태그 분류, 선곡 로직은 단위 테스트 가능해야 한다.

### NFR-MU-04. 배터리 효율
- 음악 재생이 불필요한 백그라운드 처리를 하지 않는다.

---

## 5. Dependencies

| 의존 대상 | 내용 |
|---|---|
| U1 Foundation | MusicProvider 인터페이스, 앱 구조 |
| U2 Running Tracking | 현재 페이스 데이터 (RunningMetrics) |
| U4 TTS | Audio Ducking 협력 |
| U5 Event Engine | 이벤트 기반 음악 태그 변경 요청 |
| External Music API | 플레이리스트/재생 (Spotify 등) |

---

## 6. Success Criteria

- [ ] 플레이리스트 선택 → 러닝 시작 → 음악 자동 재생
- [ ] 페이스 변화 시 다음 곡의 태그 우선순위 변경 확인
- [ ] 테스트 이벤트 발생 시 음악 규칙 변경 확인
- [ ] TTS 재생 중 음악 볼륨 감소/복구
- [ ] API 실패 시에도 앱 크래시 없음
- [ ] Mock 환경에서 전체 플로우 데모 가능
