# 랜딩페이지 키트

이 폴더는 랜딩페이지를 만들 때 쓰는 키트다.

**"랜딩페이지 만들어줘" 같은 요청을 받으면 `landing-page` 스킬을 실행한다**
(`.claude/skills/landing-page/SKILL.md`). 스킬을 못 쓰는 상황이면 `AGENTS.md` 를 읽고 그대로 따른다.

핵심 규칙 (자세한 건 스킬 문서에):
1. 색상 템플릿 4개를 전부 보여주고 사용자가 고르게 한다 — 임의로 정하지 않는다
2. 목차를 먼저 확정받고 나서 내용을 쓴다
3. 없는 후기·로고·실적 숫자를 지어내지 않는다 — 없으면 그 섹션을 뺀다
4. 수정은 `landing.json` 을 고쳐서 다시 빌드한다 — 생성된 HTML 을 직접 고치지 않는다

`landing.json` 은 **프로젝트 루트**(이 키트 폴더의 부모)에 만든다.
결과물은 그 옆에 `<프로젝트폴더명>_landingPage.html` 로 생성된다.

빌드: `python3 landing-page-kit/kit/build.py landing.json --open`
