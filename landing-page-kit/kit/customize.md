# 발전시키기 — 기본값에서 내 것으로

색상 템플릿 4종과 목차 프리셋 4종은 **시작점**이지 정답이 아니다.
아래 순서로 손을 대면 된다. 위에 있을수록 간단하고, 아래로 갈수록 자유도가 높다.

---

## 1단계 — 스위치만 돌리기 (`options`)

`landing.json` 의 `options` 값 하나만 바꾼다.

```json
"options": {
  "darkMode": "auto",
  "radius": "soft",
  "density": "normal",
  "animate": true
}
```

| 옵션 | 값 | 효과 |
|---|---|---|
| `radius` | `sharp` / `soft` / `round` | 버튼·카드 모서리. 각질수록 딱딱하고 전문적, 둥글수록 친근함 |
| `density` | `compact` / `normal` / `airy` | 섹션 위아래 여백. 내용이 많으면 `compact`, 고급스럽게 보이려면 `airy` |
| `darkMode` | `auto` / `light-only` / `dark-only` | `auto` 는 방문자 설정을 따라가고 토글 버튼이 생긴다 |
| `animate` | `true` / `false` | 스크롤 진입 효과 |

## 2단계 — 테마 4개를 눈으로 비교하기

`landing.json` 을 고치지 않고 한 번만 다른 색으로 뽑아볼 수 있다.

```bash
python3 landing-page-kit/kit/build.py landing.json --theme forest -o /tmp/forest.html
python3 landing-page-kit/kit/build.py landing.json --theme sand   -o /tmp/sand.html
```

마음에 드는 걸 정했으면 `landing.json` 의 `theme` 값을 그걸로 바꾼다.

```bash
python3 landing-page-kit/kit/build.py --list-themes   # 목록과 설명 보기
```

## 3단계 — 색 몇 개만 바꾸기 (`themeOverrides`)

전체 분위기는 마음에 드는데 **주 색상만** 브랜드 색으로 바꾸고 싶을 때.
고른 테마 위에 덮어쓰는 방식이라, 적은 것만 바뀌고 나머지는 그대로 유지된다.

```json
"theme": "midnight",
"themeOverrides": {
  "tokens": { "primary": "#e2231a", "primary-hover": "#b81b14", "primary-soft": "#fde8e6" },
  "dark":   { "primary": "#ff6b60", "primary-hover": "#ff8a80", "primary-soft": "#3a1512" }
}
```

**가장 많이 건드리는 3개:**

| 토큰 | 어디에 쓰이나 |
|---|---|
| `primary` | 주 버튼, 링크, 강조 숫자, 아이콘 — 브랜드 색이 있다면 여기 |
| `accent` | 보조 강조. `primary` 와 대비되는 색 |
| `hero-bg` | 첫 화면 배경. 단색(`#0d1b2a`)이나 그라데이션(`linear-gradient(...)`) 둘 다 가능 |

**주의 2가지**
- 라이트 모드만 바꾸면 다크 모드에서 안 어울린다. `dark` 쪽도 같이 적는다.
- `primary` 를 밝은 색(노랑·연두 등)으로 바꾸면 흰 글자가 안 보인다. 그때는 `on-primary` 를 어두운 색으로 같이 바꾼다.

## 4단계 — 테마를 통째로 새로 만들기

브랜드 가이드가 따로 있어서 4종 중 어느 것도 안 맞을 때.
`kit/themes.json` 의 `themes` 배열에 항목을 하나 추가한다.

기존 항목 하나를 통째로 복사해서 `id` 와 색만 바꾸는 게 가장 빠르다.

```json
{
  "id": "mybrand",
  "name": "우리 브랜드",
  "mood": "…",
  "bestFor": ["…"],
  "avoidFor": [],
  "swatch": ["#111111", "#e2231a", "#ffb400", "#fafafa"],
  "font": { "sans": "…", "display": "…" },
  "tokens": { "…18개 전부…" },
  "dark":   { "…18개 전부…" }
}
```

`tokens` 와 `dark` 는 **18개 키를 전부** 가지고 있어야 한다. 하나라도 빠지면 그 색이 비어 렌더링이 깨진다.
추가한 뒤 `landing.json` 의 `theme` 을 `"mybrand"` 로 바꾼다.

검사:
```bash
python3 landing-page-kit/kit/build.py --list-themes    # 목록에 뜨는지 확인
```

## 5단계 — 목차를 다시 짜기

프리셋 A~D는 자주 쓰이는 조합일 뿐이다. `sections` 배열은 자유롭게 만든다.

- 섹션 순서 = 화면의 위아래 순서. 배열에서 위치만 옮기면 된다
- 필요 없는 섹션은 통째로 지운다 (후기·가격·로고는 없으면 빼는 게 맞다)
- 같은 타입을 여러 번 써도 된다 — `showcase` 를 3개 넣으면 좌우가 번갈아 배치된다
- 쓸 수 있는 14가지 섹션과 각각의 필드는 `kit/sections.md` 참고

## 6단계 — 그 이상

`build.py` 가 못 만드는 구조(캐러셀, 동영상 배경, 커스텀 애니메이션)가 필요하면
생성된 HTML 을 직접 손봐도 된다. CSS·JS가 전부 그 파일 안에 있다.

**단, 그 순간부터 `landing.json` 을 다시 빌드하면 손본 게 전부 날아간다.**
직접 손보기 전에 파일을 따로 복사해 두거나, 더 이상 빌드하지 않겠다고 정하고 시작한다.
