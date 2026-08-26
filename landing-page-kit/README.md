# 랜딩페이지 키트

**AI와 함께 랜딩페이지를 만드는 도구입니다.**

디자인을 몰라도, 코드를 몰라도 됩니다. AI에게 아이디어를 설명하면
색상 템플릿을 고르게 하고, 들어가야 할 내용을 순서대로 물어본 뒤,
바로 배포할 수 있는 **HTML 파일 하나**를 프로젝트 루트에 만들어 줍니다.

```
내-프로젝트/
├── landing-page-kit/             ← 이 폴더
├── landing.json                  ← 페이지 내용 (AI가 만들어 줍니다)
└── 내-프로젝트_landingPage.html   ← 결과물
```

---

## 준비물

- **Python 3** — 맥/리눅스는 이미 깔려 있습니다. 윈도우는 [python.org](https://python.org)에서 설치.
- **AI 코딩 도구** — Claude Code, Kiro, Cursor 중 아무거나.

터미널에서 확인:
```bash
python3 --version
```

## 시작하기

### 1. 이 폴더를 내 프로젝트 안에 넣는다

```
내-프로젝트/
└── landing-page-kit/   ← 이 폴더를 통째로 복사
```

그리고 **내 프로젝트 폴더**를 AI 도구로 엽니다.

Claude Code라면 프로젝트 폴더에서 `claude` 를 실행하면 됩니다.
Kiro·Cursor 등 다른 도구는 `landing-page-kit/AGENTS.md` 를 읽으라고 한 번 알려주면 됩니다.

> 만들 프로젝트가 아직 없다면, 이 폴더를 아무 폴더 안에 넣기만 하면 됩니다.
> 그 폴더 이름이 그대로 파일 이름이 됩니다.

### 2. 이렇게 말한다

```
landing-page-kit 폴더 읽고 랜딩페이지 만들어줘
```

이 한 줄이면 됩니다. 길게 설명할 필요 없습니다.

그러면 AI가 **몇 가지를 물어봅니다.** 아는 대로 답하면 됩니다.

- 서비스나 팀 이름이 뭔가요
- 누구를 위한 건가요
- 그 사람이 지금 겪는 문제는 뭔가요
- 이걸 쓰면 뭐가 달라지나요
- 방문자가 여기서 할 행동 하나는 뭔가요

답이 끝나면 **색상 템플릿 4가지**를 보여주고 고르게 하고,
목차를 제안해서 확인받은 뒤, 내용을 채워서 페이지를 만들어 줍니다.

> 기획서·발표자료·README가 이미 있다면 그 파일을 같이 알려주세요.
> AI가 먼저 읽고, 거기서 알 수 있는 건 묻지 않습니다.

### 3. 결과 확인

프로젝트 루트에서:

```bash
python3 landing-page-kit/kit/build.py landing.json --open
```

`내-프로젝트_landingPage.html` 이 생기고 브라우저가 열립니다.
파일 이름 앞부분은 **프로젝트 폴더 이름**을 자동으로 따라갑니다.
다른 이름을 쓰고 싶으면 `landing.json` 의 `meta.slug` 에 원하는 이름을 넣으세요.

---

## 색상 템플릿 4종

처음에 하나를 고르고, **나중에 언제든 바꿀 수 있습니다.**
(`landing.json` 의 `theme` 값 한 줄만 고치고 다시 빌드)

| | 이름 | 느낌 | 잘 어울리는 것 |
|---|---|---|---|
| 🔵 | **미드나이트 블루** | 신뢰 · 전문성 | B2B, 금융, 보안, 클라우드 |
| 🟠 | **웜 샌드** | 따뜻함 · 사람 중심 | 교육, 커뮤니티, 로컬 비즈니스 |
| 🟢 | **포레스트 민트** | 성장 · 건강 | 헬스케어, 친환경, 생산성 도구 |
| 🟣 | **모노 바이올렛** | 모던 · 첨단 | AI, 개발자 도구, 스타트업 |

전부 **다크 모드**를 지원합니다. 페이지 우상단 버튼으로 전환됩니다.

### 색을 더 손보고 싶다면

4종은 **시작점**입니다. 여기서 얼마든지 발전시킬 수 있습니다.

```bash
# 파일을 안 고치고 다른 색으로 한 번 뽑아보기
python3 landing-page-kit/kit/build.py landing.json --theme forest -o /tmp/forest.html

# 쓸 수 있는 테마 목록 보기
python3 landing-page-kit/kit/build.py --list-themes
```

- **주 색상만 우리 브랜드 색으로** → `landing.json` 의 `themeOverrides`
- **모서리를 각지게 / 여백을 넓게** → `landing.json` 의 `options`
- **4종 중 어느 것도 안 맞음** → `kit/themes.json` 에 우리 테마 추가

방법은 `kit/customize.md` 에 단계별로 정리해 뒀습니다. AI에게 그냥 말해도 됩니다.

## 페이지에 들어가는 것

방문자가 위에서 아래로 읽으면 설득이 끝나도록 순서가 정해져 있습니다.

```
이거 뭐야?      →  첫 화면 (hero)
믿을 만해?      →  숫자 · 로고
내 문제 맞아?   →  문제 제기
어떻게 해결해?  →  3단계 · 핵심 기능
진짜 그래?      →  화면 · 데모 영상 · 후기
누가 만들었어?  →  팀원 소개
얼마야?         →  가격
그런데 이건…?   →  FAQ
그래서 뭘 하지? →  신청 / 마지막 버튼
```

상황별 추천 목차 4종(제품 소개 / 모집 / 판매 / 데모·프로토타입)은 `kit/sections.md` 에 있습니다.
**이것도 시작점입니다.** 섹션을 빼거나 순서를 바꾸거나 새로 조합해도 됩니다.

## 수정하고 싶을 때

**생성된 HTML 을 직접 고치지 마세요.** 다시 빌드하면 날아갑니다.
`landing.json` 을 고치고 다시 빌드합니다. AI에게 그냥 말해도 됩니다.

```
색을 포레스트 민트로 바꿔줘
가격 섹션은 빼줘
FAQ에 "환불 되나요?" 추가해줘
여백이 답답해, 좀 넓혀줘
```

## 배포 (3가지 중 아무거나)

**HTML 파일 하나**가 전부입니다. 이미지를 넣었다면 그 파일들만 같이 옮기면 됩니다.

1. **그냥 열기** — 파일을 더블클릭. 발표할 때는 이걸로 충분합니다.
2. **Netlify Drop** — 빈 폴더를 하나 만들어서 HTML 파일을 복사해 넣고 이름을 `index.html` 로 바꾼 뒤,
   [app.netlify.com/drop](https://app.netlify.com/drop) 에 그 폴더를 끌어다 놓습니다. 가입 없이 몇 초 만에 URL이 나옵니다.
3. **GitHub Pages** — 저장소에 올리고 Settings → Pages 켜기.

## 폴더 구조

```
내-프로젝트/
├── landing.json                  ← 페이지 내용 (여기에 만들어집니다)
├── 내-프로젝트_landingPage.html   ← 결과물
└── landing-page-kit/
    ├── README.md                 ← 지금 읽는 문서
    ├── AGENTS.md                 ← AI 에이전트가 읽는 지시서
    ├── CLAUDE.md                 ← Claude Code 진입점
    ├── .claude/skills/landing-page/SKILL.md   ← Claude Code 전용 스킬
    ├── kit/
    │   ├── themes.json           ← 색상 템플릿 4종
    │   ├── landing.schema.json   ← 내용 구조 정의
    │   ├── sections.md           ← 섹션 카탈로그 + 목차 프리셋
    │   ├── copywriting.md        ← 문장 쓰는 법
    │   ├── checklist.md          ← 내보내기 전 점검표
    │   ├── customize.md          ← 기본값에서 더 발전시키는 법
    │   └── build.py              ← 빌더 (표준 라이브러리만 사용)
    └── examples/
        ├── demo.landing.json     ← 완성 예시
        └── preview.html          ← 결과물이 어떻게 생겼는지 미리보기
```

## 자주 막히는 곳

**"python3: command not found"**
윈도우는 `python3` 대신 `python` 을 써 보세요. 그래도 안 되면 Python을 설치해야 합니다.

**"JSON 문법이 잘못되었습니다"**
쉼표가 빠졌거나 하나 더 붙었을 확률이 높습니다. 에러 메시지에 몇 번째 줄인지 나옵니다.
AI에게 "landing.json 문법 고쳐줘"라고 하면 됩니다.

**글자가 안 보이고 빈 화면**
브라우저에서 JavaScript가 막혀 있을 때 생길 수 있습니다. 새로고침해 보세요.

**유튜브 영상 자리에 "동영상 플레이어 구성 오류 (오류 153)"**
고장이 아닙니다. 파일을 더블클릭해서 여는 방식(`file://`)에서는 유튜브가 재생을 막습니다.
**웹에 올리면 정상 재생됩니다.** 오프라인에서 발표해야 한다면 영상 대신 화면 캡처(`image`)를 쓰세요.

**AI가 후기나 숫자를 지어냈다**
"실제로 없는 후기랑 로고는 빼줘"라고 말하세요. 심사위원이 가장 먼저 알아채는 부분입니다.

---

## 혼자서 직접 만들고 싶다면

AI 없이 손으로 해도 됩니다.

```bash
# 프로젝트 루트에서
cp landing-page-kit/examples/demo.landing.json landing.json
# landing.json 을 편집기로 열어서 내용을 바꾼다
python3 landing-page-kit/kit/build.py landing.json --open
```

빌더가 문제를 찾아서 알려줍니다:

```bash
python3 landing-page-kit/kit/build.py landing.json --check
```
