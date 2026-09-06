#!/usr/bin/env bash
# Fetch the sherpa-onnx voice runtime: native libraries, keyword-spotting model,
# VAD, speaker verification, and the always-on ASR model.
#
# These are gitignored (26MB of .so + 5MB KWS model + ~68MB ASR model), so a fresh
# clone needs this once. Release builds require the native libraries and fail
# preflight without them. Debug builds may fall back to SpeechRecognizer when
# they are absent, with SherpaAsrEngine.isAvailable false. Model assets below
# are debug-only; Play builds download model data after installation.
#
# Usage:  scripts/fetch_kws_assets.sh
set -euo pipefail

SHERPA_VERSION="v1.13.4"
MODEL="sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JNI_DIR="$ROOT/app/src/main/jniLibs/arm64-v8a"
# Debug-only: Play builds download these on first launch so the AAB stays under 200MB.
ASSET_DIR="$ROOT/app/src/debug/assets/$MODEL"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# Only arm64-v8a. The other ABIs would add ~80MB to the APK for hardware we do not target.
ABI="arm64-v8a"

echo "==> native libraries ($SHERPA_VERSION, $ABI)"
mkdir -p "$JNI_DIR"
if [ -f "$JNI_DIR/libsherpa-onnx-jni.so" ] && [ -f "$JNI_DIR/libonnxruntime.so" ]; then
  echo "    already present, skipping"
else
  curl -fL --retry 10 --retry-all-errors --retry-delay 3 \
    -o "$WORK/android.tar.bz2" \
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/$SHERPA_VERSION/sherpa-onnx-$SHERPA_VERSION-android.tar.bz2"
  tar -xjf "$WORK/android.tar.bz2" -C "$WORK"
  # Only the JNI bridge and onnxruntime are needed; the c-api/cxx-api variants are not.
  cp "$WORK/jniLibs/$ABI/libsherpa-onnx-jni.so" "$WORK/jniLibs/$ABI/libonnxruntime.so" "$JNI_DIR/"
  echo "    $(du -ch "$JNI_DIR"/*.so | tail -1 | cut -f1) installed"
fi

echo "==> keyword-spotting model"
mkdir -p "$ASSET_DIR"
if [ -f "$ASSET_DIR/tokens.txt" ] && [ -f "$ASSET_DIR/keywords.txt" ]; then
  echo "    already present, skipping"
else
  curl -fL --retry 10 --retry-all-errors --retry-delay 3 \
    -o "$WORK/kws.tar.bz2" \
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/$MODEL.tar.bz2"
  tar -xjf "$WORK/kws.tar.bz2" -C "$WORK"
  src="$WORK/$MODEL"

  # int8 variants only: ~5MB instead of ~13.6MB. SherpaKwsEngine expects these exact
  # names — a mismatch is fatal, the native loader aborts the process rather than
  # failing gracefully.
  cp "$src/encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx" "$ASSET_DIR/"
  cp "$src/decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx" "$ASSET_DIR/"
  cp "$src/joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx" "$ASSET_DIR/"
  cp "$src/tokens.txt" "$ASSET_DIR/"

  # Built-in wake phrases, tokenised with the model's own bpe.model. Regenerate with
  # scripts/kws_tokenise.py (its self-check reproduces the model's shipped keywords.txt
  # 9/9, so the tokeniser is known to agree with the model).
  # Per-keyword tuning: ":boost #threshold" (no space after the prefix). Higher boost
  # and lower threshold both make a keyword easier to trigger. These are more sensitive
  # than sherpa's 1.5/0.25 defaults because a MISSED wake word was the complaint.
  # MEASURED on device, not guessed. Playing each phrase at the same volume, only
  # "hey kitty" and the model's own "hello world" triggered; "hey meow", "hey robot",
  # "hey buddy" and "hey computer" did not. "MEOW" is rare in GigaSpeech, which this
  # model was trained on, so its token sequence is acoustically weak. "hey kitty" is
  # listed FIRST as the phrase known to work; "hey meow" is kept with a stronger boost
  # and lower threshold, and may still be unreliable.
  # Wake phrases. "yomeow" as one word leads; the spaced and casual variants follow so
  # the companion answers however it is addressed. Tokens come from the model's own
  # bpe.model via scripts/kws_tokenise.py. ":boost #threshold" — more sensitive than
  # sherpa's 1.5/0.25 defaults, since a missed wake is the complaint being fixed.
  printf '%s\n' \
    "▁ Y OM E OW :3.5 #0.08" \
    "▁ Y O ▁ME OW :3.5 #0.08" \
    "▁HE Y ▁ME OW :3.5 #0.08" \
    "▁O K ▁ME OW :3.5 #0.08" \
    "▁OKAY ▁ME OW :3.5 #0.08" \
    "▁ Y OM A O :3.5 #0.08" \
    "▁ Y OM E O :3.5 #0.08" \
    "▁ Y O ▁MA O :3.5 #0.08" \
    "▁ Y O ▁ME O :3.5 #0.08" \
    "▁ Y O ▁MO W :3.5 #0.08" \
    "▁ Y O ▁ME W :3.5 #0.08" \
    "▁ Y O ▁MI A O :3.5 #0.08" \
    "▁HE Y ▁MA O :3.5 #0.08" \
    "▁HE Y ▁ME O :3.5 #0.08" \
    "▁HE Y ▁ME W :3.5 #0.08" \
    "▁O K ▁MA O :3.5 #0.08" \
    "▁O K ▁ME W :3.5 #0.08" \
    "▁OKAY ▁MA O :3.5 #0.08" \
    "▁E Y ▁ME OW :3.5 #0.08" \
    "▁E H ▁ME OW :3.5 #0.08" \
    "▁E Y ME OW :3.5 #0.08" \
    "▁HE Y ME OW :3.5 #0.08" \
    "▁HA Y ME OW :3.5 #0.08" \
    "▁HA Y ▁ME OW :3.5 #0.08" \
    "▁HI ▁ME OW :3.5 #0.08" \
    "▁HE Y ▁ME A OW :3.5 #0.08" \
    "▁HE Y ▁ME E OW :3.5 #0.08" \
    "▁HE Y ▁MI A O :3.5 #0.08" \
    "▁HE Y ▁ME ▁ OW :3.5 #0.08" \
    "▁O K ME OW :3.5 #0.08" \
    "▁O KE Y ▁ME OW :3.5 #0.08" \
    "▁O K ▁ME ▁ OW :3.5 #0.08" \
    "▁HE Y ▁K IT TY :2.0 #0.15" \
    "▁ Y O ▁ME M B A :2.5 #0.12" \
    "▁HE Y ▁ME M B A :2.5 #0.12" \
    "▁O I ▁ME OW :2.5 #0.12" \
    > "$ASSET_DIR/keywords.txt"

  echo "    $(du -sh "$ASSET_DIR" | cut -f1) installed"
fi

echo "==> VAD + speaker models"
ROOT_ASSETS="$ROOT/app/src/debug/assets"
mkdir -p "$ROOT_ASSETS"
# NOTE: sherpa requires these at the assets ROOT, not in a subdirectory.
if [ -f "$ROOT_ASSETS/silero_vad.onnx" ]; then
  echo "    silero_vad.onnx already present"
else
  curl -fL --retry 10 --retry-all-errors --retry-delay 3 -o "$ROOT_ASSETS/silero_vad.onnx" \
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"
  echo "    silero_vad.onnx installed ($(du -h "$ROOT_ASSETS/silero_vad.onnx" | cut -f1))"
fi
if [ -f "$ROOT_ASSETS/speaker.onnx" ]; then
  echo "    speaker.onnx already present"
else
  # wespeaker CAM++ / VoxCeleb (English). Only needed for "is that you?" verification;
  # without it the wake word still works, just ungated by speaker.
  curl -fL --retry 10 --retry-all-errors --retry-delay 3 -o "$ROOT_ASSETS/speaker.onnx" \
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/wespeaker_en_voxceleb_CAM++.onnx"
  echo "    speaker.onnx installed ($(du -h "$ROOT_ASSETS/speaker.onnx" | cut -f1))"
fi

echo "==> always-on ASR model (streaming zipformer2, 2023-06-26 — better accuracy)"
ASR_MODEL="sherpa-onnx-streaming-zipformer-en-2023-06-26"
ASR_ASSET_DIR="$ROOT_ASSETS/$ASR_MODEL"
if [ -f "$ASR_ASSET_DIR/tokens.txt" ] && [ -f "$ASR_ASSET_DIR/encoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx" ]; then
  echo "    already present, skipping"
else
  mkdir -p "$ASR_ASSET_DIR"
  # ~296MB archive (it ships both int8 and fp32 variants); only the four
  # int8 files below are needed. A prior manual attempt at this got
  # interrupted mid-download and left a corrupt partial tar that
  # VoiceModelStore's isComplete() check quietly treated as "missing", which
  # permanently forced fallback to the weaker 20M pack — always stage under
  # .part and verify with `tar -tjf` before trusting the archive.
  curl -fL --retry 10 --retry-all-errors --retry-delay 3 \
    -o "$WORK/asr.tar.bz2.part" \
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$ASR_MODEL.tar.bz2"
  mv "$WORK/asr.tar.bz2.part" "$WORK/asr.tar.bz2"
  tar -tjf "$WORK/asr.tar.bz2" > /dev/null  # fail loudly on a truncated archive
  tar -xjf "$WORK/asr.tar.bz2" -C "$WORK"
  src="$WORK/$ASR_MODEL"
  cp "$src/encoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx" "$ASR_ASSET_DIR/"
  cp "$src/decoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx" "$ASR_ASSET_DIR/"
  cp "$src/joiner-epoch-99-avg-1-chunk-16-left-128.int8.onnx" "$ASR_ASSET_DIR/"
  cp "$src/tokens.txt" "$ASR_ASSET_DIR/"
  echo "    $(du -sh "$ASR_ASSET_DIR" | cut -f1) installed"
fi

echo
echo "Done. Wake word will use the keyword spotter on next build."
echo "Verify on device with:  adb logcat | grep SherpaKws"
