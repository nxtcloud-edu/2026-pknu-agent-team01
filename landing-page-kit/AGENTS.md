# AI 에이전트용 지시서 — 랜딩페이지 키트

> 이 폴더에서 "랜딩페이지 만들어줘" 요청을 받은 AI(Claude Code / Kiro / Cursor / Copilot 등)는
> **이 문서를 먼저 읽고 그대로 따른다.**

## 무엇을 만드는가

사용자의 아이디어를 **`landing.json` 한 개**로 정리하고, `kit/build.py` 를 돌려
**CSS·JS가 전부 인라인된 단일 HTML 파일**을 만든다. npm·빌드 도구·인터넷 연결 필요 없음.

```
사용자 아이디어 → landing.json → python3 landing-page-kit/kit/build.py → <프로젝트명>_landingPage.html
```

이 키트는 참가자의 **프로젝트 폴더 안에** 들어간다. `landing.json` 과 결과 HTML 은 둘 다
**프로젝트 루트**(= 이 키트 폴더의 부모)에 놓인다.

```
내-프로젝트/
├── landing-page-kit/          ← 이 키트
├── landing.json               ← AI가 만드는 내용 파일
└── 내-프로젝트_landingPage.html  ← 결과물 (단일 파일)
```

## 읽어야 할 파일

| 파일 | 내용 |
|---|---|
| `kit/themes.json` | 색상 템플릿 4종 (midnight / sand / forest / violet) |
| `kit/landing.schema.json` | landing.json 의 전체 필드 정의 |
| `kit/sections.md` | 섹션 카탈로그 + 상황별 목차 프리셋 4종 |
| `kit/copywriting.md` | 문장 작성 규칙 |
| `kit/checklist.md` | 내보내기 전 점검 항목 |
| `kit/customize.md` | 기본 템플릿에서 더 손볼 때 (색·여백·새 테마 추가) |
| `examples/demo.landing.json` | 완성된 실제 예시 |

## 반드시 지킬 6가지

1. **색상 템플릿 4개를 전부 보여주고 사용자가 고르게 한다.** 임의로 정하지 않는다.
   4종은 **시작점**이다 — 고른 뒤 `themeOverrides` 로 색을 바꾸거나 `kit/themes.json` 에
   테마를 새로 추가할 수 있다는 것도 알려준다 (`kit/customize.md`).
   브랜드 색을 이미 가진 사용자라면 선택을 건너뛰고 바로 맞춰준다.
2. **목차를 먼저 확정받고 나서 내용을 쓴다.** 한 번에 다 만들지 않는다.
3. **없는 것을 지어내지 않는다.** 후기·도입사 로고·실적 숫자가 없으면 그 섹션을 통째로 뺀다.
   목표치를 쓸 거면 라벨에 "목표"라고 밝힌다.
4. **자리표시자를 남기지 않는다.** `[TODO]`, `여기에 내용`, `Lorem ipsum` 금지. 모르면 사용자에게 묻는다.
5. **주 행동(primary CTA)은 페이지 전체에서 하나로 통일한다.** 버튼 라벨은 동사+목적어.
   "자세히 보기", "더 알아보기" 금지.
6. **수정은 `landing.json` 에서 하고 다시 빌드한다.** 생성된 HTML 을 직접 고치면 다음 빌드에 날아간다.

## 진행 순서

1. 사용자에게서 5가지를 확보한다 — 이름 / 대상 / 문제 / 달라지는 점 / 방문자가 할 행동 하나.
   기획서·README가 있으면 먼저 읽고 거기서 채운다. 이미 아는 건 다시 묻지 않는다.
2. `kit/themes.json` 을 읽고 4가지를 제시해 고르게 한다.
3. `kit/sections.md` 의 프리셋에서 목차를 골라 사용자에게 확인받는다.
4. `kit/landing.schema.json` + `kit/copywriting.md` 에 맞춰 **프로젝트 루트에** `landing.json` 을 작성한다.
   파일 이름을 직접 정하고 싶으면 `meta.slug` 를 넣는다 (`slug` → `slug_landingPage.html`).
5. 빌드하고 검사 결과를 처리한다:
   ```bash
   # 프로젝트 루트에서 실행
   python3 landing-page-kit/kit/build.py landing.json --open
   ```
   `[고쳐야 함]` 이 남아있으면 고치고 다시 빌드한다.
6. `kit/checklist.md` 를 훑고 사용자에게 결과물과 배포 방법을 안내한다.

## 수정 요청 대응표

| 요청 | landing.json 에서 고칠 곳 |
|---|---|
| 색 전체 변경 | `theme` |
| 특정 색만 변경 | `themeOverrides.tokens.primary` (다크는 `themeOverrides.dark.primary`) |
| 4종 중 어느 것도 안 맞음 | `kit/themes.json` 에 테마 추가 → `kit/customize.md` 4단계 |
| 모서리 각지게/둥글게 | `options.radius` — `sharp` / `soft` / `round` |
| 여백 조절 | `options.density` — `compact` / `normal` / `airy` |
| 다크모드 끄기 | `options.darkMode: "light-only"` |
| 애니메이션 끄기 | `options.animate: false` |
| 섹션 추가·삭제·순서 변경 | `sections` 배열 |
