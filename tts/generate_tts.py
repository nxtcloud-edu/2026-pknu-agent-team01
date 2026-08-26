"""
RunFunMan 미리듣기용 TTS 배치 생성 스크립트

manifest.json 에 정의된 모든 문장을 Audio8 TTS 모델로 한 번에 생성해서
이 폴더(tts/)에 <파일명>.wav 로 저장한다.

준비물:
  1) 참조 음성 파일 하나:  tts/reference.wav
     - 미리듣기 나레이션이 이 목소리로 만들어진다.
  2) 참조 음성이 말하는 내용(전사):  아래 REFERENCE_TEXT 를 실제 내용으로 바꾼다.
  3) 파이썬 패키지:  torch, transformers, soundfile
     (설치 명령은 tts/requirements.txt 참고)

실행 (프로젝트 루트에서):
  & "$env:LOCALAPPDATA\Programs\Python\Python312\python.exe" tts/generate_tts.py

옵션:
  --force   이미 있는 wav도 다시 생성 (기본은 있으면 건너뜀)
  --only national_rival.wav,basic_pace.wav   특정 파일만 생성

GPU가 있으면 자동으로 CUDA를 쓴다. 없으면 CPU로 동작(느릴 수 있음).
"""

import argparse
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent

# 참조 음성과 그 전사. reference.wav 를 이 폴더에 두고,
# 아래 텍스트를 그 음성이 실제로 말하는 문장으로 바꾼다.
REFERENCE_AUDIO = str(HERE / "reference.wav")
REFERENCE_TEXT = "이대로만 계속 뛰어봅시다."

MODEL_ID = "Audio8/Audio8-TTS-Preview-0.1b"


def load_manifest():
    data = json.loads((HERE / "manifest.json").read_text(encoding="utf-8"))
    clips = data.get("clips", [])
    if not clips:
        sys.exit("manifest.json 에 clips 가 비어 있습니다.")
    return clips


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true", help="이미 있는 wav도 다시 생성")
    ap.add_argument("--only", default="", help="쉼표로 구분한 파일명만 생성")
    args = ap.parse_args()

    clips = load_manifest()
    if args.only:
        wanted = {s.strip() for s in args.only.split(",") if s.strip()}
        clips = [c for c in clips if c["file"] in wanted]
        if not clips:
            sys.exit(f"--only 로 지정한 파일이 manifest 에 없습니다: {args.only}")

    if not Path(REFERENCE_AUDIO).exists():
        sys.exit(
            f"참조 음성이 없습니다: {REFERENCE_AUDIO}\n"
            f"  tts/reference.wav 를 넣고, generate_tts.py 의 REFERENCE_TEXT 를 그 음성 내용으로 맞춘 뒤 다시 실행하세요."
        )

    # 생성할 게 남았는지 먼저 확인 (모델 로딩 전에)
    todo = [c for c in clips if args.force or not (HERE / c["file"]).exists()]
    if not todo:
        print("생성할 파일이 없습니다. (모두 존재 — 다시 만들려면 --force)")
        return

    # 무거운 import 는 여기서 (환경 문제를 위 검사보다 늦게 만나도록)
    import soundfile as sf
    import torch
    from transformers import AutoModel, AutoProcessor

    device = "cuda" if torch.cuda.is_available() else "cpu"
    dtype = torch.bfloat16 if device == "cuda" else torch.float32
    print(f"장치: {device} / dtype: {dtype}")
    print(f"모델 로딩 중: {MODEL_ID} ...")

    processor = AutoProcessor.from_pretrained(MODEL_ID, trust_remote_code=True)
    model = (
        AutoModel.from_pretrained(MODEL_ID, trust_remote_code=True, dtype=dtype)
        .eval()
        .to(device)
    )

    total = len(todo)
    print(f"생성 대상: {total}개\n")

    for i, clip in enumerate(todo, 1):
        out_path = HERE / clip["file"]
        text = clip["text"]
        print(f"[{i}/{total}] {clip['file']}  <-  {text}")

        inputs = processor(
            text=[text],
            reference_audio=[REFERENCE_AUDIO],
            reference_text=[REFERENCE_TEXT],
            return_tensors="pt",
        )
        inputs = {name: value.to(device) for name, value in inputs.items()}

        with torch.inference_mode():
            output = model.generate(
                **inputs,
                max_new_tokens=512,
                temperature=0.7,
                top_p=0.9,
                top_k=50,
                do_sample=True,
                return_dict_in_generate=True,
            )
            waveforms, waveform_lengths = model.decode_audio(output.codes)

        audio = waveforms[0, : int(waveform_lengths[0])].float().cpu().numpy()
        sf.write(str(out_path), audio, model.config.codec_sample_rate)

    print(f"\n완료. tts/ 폴더에 {total}개 wav 를 만들었습니다.")
    print("이제 랜딩페이지 미리듣기에서 해당 음성이 재생됩니다. (없는 클립은 브라우저 음성으로 폴백)")


if __name__ == "__main__":
    main()
