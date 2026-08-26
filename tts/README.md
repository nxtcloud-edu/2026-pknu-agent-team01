# RunFunMan 미리듣기 TTS

랜딩페이지 미리듣기의 나레이션 음성을 Audio8 TTS 모델로 미리 만들어 이 폴더에 넣는다.
위젯은 여기 있는 wav를 우선 재생하고, 없는 클립은 브라우저 음성으로 대신 읽는다.

## 파일 구성
- `manifest.json` — 생성할 문장 ↔ 파일명 매핑 (스크립트와 위젯이 공유하는 단일 소스)
- `generate_tts.py` — 매핑을 읽어 wav를 일괄 생성
- `requirements.txt` — 필요한 파이썬 패키지
- `reference.wav` — (직접 넣어야 함) 나레이션 목소리가 될 참조 음성
- `*.wav` — 생성 결과 (git에는 올리지 않음)

## 준비

이 PC는 NVIDIA GPU가 없고 **Intel Arc 내장 그래픽**이라, TTS는 **CPU로 동작**한다.
클립 18개짜리 일회성 배치라 CPU로도 가능하지만 다소 느릴 수 있다.

파이썬은 이미 설치된 3.12를 쓴다:
`C:\Users\<사용자>\AppData\Local\Programs\Python\Python312\python.exe`

### 1) 패키지 설치 (CPU용 torch)

```powershell
$py = "$env:LOCALAPPDATA\Programs\Python\Python312\python.exe"
& $py -m pip install --upgrade pip
& $py -m pip install torch --index-url https://download.pytorch.org/whl/cpu
& $py -m pip install -r tts/requirements.txt
```

> 모델은 `trust_remote_code=True` 로 원격 코드를 내려받아 실행한다.
> 실행 중 특정 패키지가 없다는 오류가 나오면 그 패키지를 `pip install` 로 추가한다.

### 2) 참조 음성 넣기

- 원하는 목소리로 5~10초 정도 또렷하게 말한 녹음을 `tts/reference.wav` 로 저장한다.
- `generate_tts.py` 의 `REFERENCE_TEXT` 를 그 녹음이 **실제로 말하는 문장**과 똑같이 맞춘다.
  (전사가 음성 내용과 맞아야 목소리 복제 품질이 좋다.)

### 3) 생성

```powershell
& $py tts/generate_tts.py
```

- 이미 있는 wav는 건너뛴다. 다시 만들려면 `--force`.
- 일부만 만들려면 `--only national_rival.wav,basic_pace.wav`.

## 확인

생성 후 랜딩페이지를 웹서버로 열면 미리듣기에서 해당 음성이 재생된다.
`file://` (파일 더블클릭)에서는 브라우저가 로컬 오디오 로드를 막을 수 있으니,
로컬 서버로 여는 것을 권장한다:

```powershell
& $py -m http.server 8000
# 브라우저에서 http://localhost:8000/RunFunMan_landingPage.html
```
