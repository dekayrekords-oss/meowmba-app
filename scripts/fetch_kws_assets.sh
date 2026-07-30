#!/usr/bin/env bash
# Fetch the sherpa-onnx wake-word runtime: native libraries + keyword-spotting model.
#
# These are gitignored (26MB of .so plus 5MB of model), so a fresh clone needs this once.
# Without them the app still builds and runs — WakeWordEngineFactory falls back to the
# old SpeechRecognizer loop — so this is not a build prerequisite, only a wake-word one.
#
# Usage:  scripts/fetch_kws_assets.sh
set -euo pipefail

SHERPA_VERSION="v1.13.4"
MODEL="sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JNI_DIR="$ROOT/app/src/main/jniLibs/arm64-v8a"
ASSET_DIR="$ROOT/app/src/main/assets/$MODEL"
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
  printf '%s\n' \
    "▁HE Y ▁ME OW" \
    "▁HE Y ▁ME M B A" \
    "▁HE Y ▁ME OW M B A" \
    > "$ASSET_DIR/keywords.txt"

  echo "    $(du -sh "$ASSET_DIR" | cut -f1) installed"
fi

echo
echo "Done. Wake word will use the keyword spotter on next build."
echo "Verify on device with:  adb logcat | grep SherpaKws"
