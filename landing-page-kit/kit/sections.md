# 섹션 카탈로그 — 랜딩페이지에 뭐가 들어가야 하나

랜딩페이지는 "위에서 아래로 읽으면 설득이 끝나는" 한 장짜리 문서다.
섹션은 아무 순서로나 놓는 게 아니라 **방문자의 질문 순서**를 따라간다.

```
이거 뭐야?      → hero
믿을 만해?      → logos / stats
내 문제 맞아?   → problem
어떻게 해결해?  → steps / features
진짜 그래?      → showcase / testimonials
누가 만들었어?  → team
얼마야?         → pricing
그런데 이건…?   → faq
그래서 뭘 하지? → cta
```

---

## 프리셋 — 상황별 추천 목차

무엇을 만드는지 모르겠으면 아래에서 고른다. 아직 출시 전인 새 서비스라면 **A 또는 D**가 대부분 맞다.

| 프리셋 | 상황 | 섹션 순서 |
|---|---|---|
| **A. 제품 소개 (기본)** | 앱·웹서비스 아이디어 발표 | nav → hero → stats → problem → features → steps → testimonials → faq → cta → footer |
| **B. 신청 모집** | 캠프·세미나·챌린지 참가자 모집 | nav → hero → stats → problem → steps → features → faq → form → footer |
| **C. 상품 판매** | 유료 서비스·구독 | nav → hero → logos → problem → features → showcase → testimonials → pricing → faq → cta → footer |
| **D. 프로젝트 발표** | 만든 것을 보여주고 팀을 소개 | nav → hero → problem → steps → showcase → features → team → cta → footer |

프리셋 D의 흐름을 풀어 쓰면 이렇다.

```
hero       한 줄로 뭘 만들었는지          ← 이게 없으면 아래를 못 읽는다
problem    왜 만들었나 (문제)
steps      어떻게 풀었나 (해결 방식 3단계)
showcase   실제 화면 · 데모 영상
features   구현한 핵심 기능
team       누가 만들었나
cta        지금 해볼 수 있는 것 (데모 / 저장소 / 문의)
footer
```

**분량 기준**: 섹션 8~11개. 6개 미만이면 설득이 부족하고, 13개를 넘으면 아무도 끝까지 안 읽는다.

---

## 규칙 3가지 (어기면 페이지가 무너짐)

1. **hero 와 footer 는 필수.** hero 없으면 뭘 파는지 모르고, footer 없으면 미완성으로 보인다.
2. **배경을 번갈아 준다.** 이웃한 섹션의 `background` 가 같으면 경계가 사라져 한 덩어리로 보인다.
   `base → alt → base → alt` 로 번갈아 가고, 강조할 CTA 한 곳만 `primary` 를 쓴다.
3. **CTA 는 같은 행동으로 통일한다.** 페이지 전체에서 주 행동(primary)은 하나여야 한다.
   "신청하기"와 "구매하기"와 "문의하기"가 섞이면 아무도 아무것도 안 누른다.

---

## 섹션별 상세

### `nav` — 상단 고정 메뉴
스크롤해도 따라오는 헤더. 좌측 로고, 우측 메뉴 + 버튼 1개.
- **쓰는 필드**: `primaryCta`
- 메뉴 항목은 각 섹션의 `navLabel` 을 채우면 자동으로 생성된다. 3~5개가 적당.
- 모바일에서는 메뉴가 접히고 버튼만 남는다.

### `hero` — 첫 화면 (가장 중요)
방문자가 3초 안에 "이게 나한테 뭘 해주는가"를 알아야 한다.
- **쓰는 필드**: `eyebrow`, `heading`(필수), `subheading`, `primaryCta`(필수), `secondaryCta`, `note`, `image` 또는 `video`, `imageAlt`
- `heading` 은 **기능이 아니라 결과**. ("AI 기반 일정 관리 플랫폼" ✗ / "회의 잡느라 카톡 30번 하지 마세요" ✓)
- `subheading` 은 1~2문장으로 무엇인지 + 누구를 위한 것인지.
- `image` 는 실제 화면 스크린샷이 가장 강력하다. 없으면 생략해도 되고, 생략하면 가운데 정렬 레이아웃이 된다.
- `video` 에 유튜브 링크를 넣으면 첫 화면에 바로 데모 영상이 들어간다.
- `background` 는 `hero` 로 두는 걸 권장 (테마의 그라데이션이 적용됨).

### `logos` — 사용 기관/파트너 로고
- **쓰는 필드**: `heading`(짧게, 예 "이런 곳에서 쓰고 있어요"), `items[].label`, `items[].image`
- 이미지가 없으면 회사명 텍스트로 표시된다. 4~8개.
- **없는 실적을 지어내지 않는다.** 아직 도입처가 없으면 이 섹션은 빼고 `stats` 로 대체한다.
- **기술 스택을 보여줄 때도 이 섹션을 쓴다.** `heading` 을 "이런 기술로 만들었습니다"로 두고
  `items[].label` 에 기술 이름을 넣으면 된다.

### `stats` — 숫자로 보여주는 신뢰
- **쓰는 필드**: `items[].value`, `items[].label`
- 3~4개가 적당. `value` 는 짧게(`1,200+`, `98%`, `3분`), `label` 은 그 숫자가 뭔지.
- 진짜 숫자만. 목표치를 실적처럼 쓰면 안 된다. 예상치라면 label 에 "목표"라고 밝힌다.

### `problem` — 문제 제기
"당신 이런 상황이죠?" 하고 방문자가 고개를 끄덕이게 만드는 구간.
- **쓰는 필드**: `eyebrow`, `heading`, `body`, `items[].icon`, `items[].title`, `items[].body`
- 항목 3개. 각각 **방문자가 실제로 겪는 장면**으로 쓴다. ("비효율적인 프로세스" ✗ / "엑셀 파일이 다섯 개로 갈라져 있다" ✓)

### `steps` — 어떻게 동작하나 (3단계)
- **쓰는 필드**: `heading`, `items[].title`, `items[].body`
- 번호는 자동으로 붙는다. **3단계**가 정석, 최대 4단계.
- 각 단계는 사용자가 하는 행동으로. ("데이터 파이프라인 구축" ✗ / "파일을 끌어다 놓는다" ✓)

### `features` — 핵심 기능/특징
- **쓰는 필드**: `heading`, `subheading`, `columns`, `items[].icon`, `items[].title`, `items[].body`
- 3개 또는 6개(2줄). `icon` 은 이모지 1개.
- 각 항목의 `title` 은 기능명, `body` 는 **그래서 뭐가 좋아지는지** 1~2문장.

### `showcase` — 화면/데모 보여주기
텍스트 옆에 이미지나 영상을 크게 놓는 좌우 분할 섹션.
- **쓰는 필드**: `heading`, `body`, `image` 또는 `video`, `imageAlt`, `primaryCta`
- `video` 에 **유튜브·비메오 링크를 그대로 붙여넣으면** 영상이 박힌다. `.mp4` 경로도 된다.
  (`image` 와 같이 있으면 `video` 가 우선)
- 여러 개 쓰면 좌우가 자동으로 번갈아 배치된다.
- 영상 임베드는 인터넷 연결이 필요하다. 오프라인 발표라면 화면 캡처(`image`)를 같이 준비한다.
- **파일을 더블클릭해서 열면(`file://`) 유튜브가 "오류 153"을 띄운다.** 고장이 아니라 유튜브 정책이다.
  웹에 올리면 정상 재생된다. 사용자가 이걸 보고 놀라지 않도록 미리 알려준다.

### `team` — 만든 사람들
- **쓰는 필드**: `heading`, `columns`, `items[].title`(이름), `items[].role`(역할), `items[].body`(한 줄 소개), `items[].image`(얼굴 사진), `items[].url`(프로필 링크), `items[].label`(링크 글자)
- 사진이 없으면 이름 첫 글자가 동그란 배경에 대신 들어간다. 사진은 **정사각형**으로 잘라 넣는다.
- `role` 은 맡은 일을 구체적으로. ("팀원" ✗ / "백엔드 · 인프라" ✓)
- `body` 는 한 줄이면 충분하다. 이력서가 아니다.
- 보통 페이지 뒤쪽, `cta` 바로 앞에 둔다.
- 사진과 이름은 **본인 동의를 받고** 넣는다.

### `testimonials` — 사용자 후기
- **쓰는 필드**: `heading`, `items[].quote`, `items[].author`, `items[].role`
- 2~3개. `role` 이 구체적일수록 믿음이 간다.
- **후기를 지어내지 않는다.** 실제 인터뷰나 베타 테스터 코멘트가 없으면 이 섹션을 통째로 뺀다.

### `pricing` — 가격표
- **쓰는 필드**: `heading`, `subheading`, `items[].title`, `items[].price`, `items[].period`, `items[].body`, `items[].features`, `items[].featured`, `items[].cta`
- 플랜 2~3개. `featured: true` 는 **딱 하나만** (가운데 플랜을 권장 플랜으로).
- 무료 플랜이 있으면 맨 왼쪽에.

### `faq` — 자주 묻는 질문
- **쓰는 필드**: `heading`, `items[].q`, `items[].a`
- 4~6개. 클릭하면 펼쳐진다.
- **진짜 반론을 적는다.** "언제 출시되나요?" 같은 편한 질문 말고 "기존 것과 뭐가 다른가요", "제 데이터는 안전한가요", "돈 내야 하나요" 같은 걸림돌을 정면으로 다룬다.

### `cta` — 마지막 행동 유도
- **쓰는 필드**: `heading`, `subheading`, `primaryCta`(필수), `secondaryCta`, `note`
- `background: "primary"` 로 두어 페이지에서 가장 눈에 띄게 만든다.
- hero 의 버튼과 **같은 행동**이어야 한다.

### `form` — 신청/문의 폼
- **쓰는 필드**: `heading`, `subheading`, `action`, `items[]`(각각 하나의 입력칸), `note`
- 입력칸 필드: `name`(필수), `label`, `fieldType`, `required`, `placeholder`, `options`(select 용)
- **입력칸은 최대 5개.** 하나 늘어날 때마다 제출률이 떨어진다. 이름·연락처만으로 충분한 경우가 많다.
- `action` 이 없으면 제출 시 "관리자에게 전달되도록 연결하세요" 안내가 뜬다. Formspree·Google Form·Tally 등의 URL을 넣으면 바로 동작한다.
- 개인정보를 받으면 `note` 에 수집 목적과 보관 기간을 반드시 적는다.

### `footer` — 마무리
- **쓰는 필드**: `body`(선택), `items[]`(링크: `label` + `url`)
- 브랜드명·연락처·소셜은 `brand` 에서 자동으로 가져온다.
- 저작권 줄은 자동 생성된다.
