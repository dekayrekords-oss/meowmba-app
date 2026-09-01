package com.example.meowmba.core.audio

import java.io.File

/**
 * On-device wake-word files. Not shipped in the Play AAB — first launch
 * copies them from a debug asset pack or downloads them from sherpa's
 * GitHub releases (same URLs as scripts/fetch_kws_assets.sh).
 */
object VoiceModelPack {

    const val KWS_DIR = "sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01"
    const val ENCODER = "encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
    const val DECODER = "decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
    const val JOINER = "joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
    const val TOKENS = "tokens.txt"
    const val KEYWORDS = "keywords.txt"
    const val VAD = "silero_vad.onnx"
    const val SPEAKER = "speaker.onnx"

    /**
     * Streaming English ASR for face-mode always-on (not the keyword spotter).
     *
     * Primary is the 2023-06-26 zipformer2 pack (better accuracy). The
     * 20M/2023-02-17 pack stays as a loadable fallback: a genuinely
     * incomplete/zero-byte unpack native-aborts onnxruntime with "Protobuf
     * parsing failed" and kills the process, so [AsrPack.minBytes] guards
     * against that. Never open the fp32 encoder (`…left-128.onnx`, ~250MB)
     * — that path is the other abort.
     */
    const val ASR_DIR = "sherpa-onnx-streaming-zipformer-en-2023-06-26"
    const val ASR_ENCODER = "encoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx"
    const val ASR_DECODER = "decoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx"
    const val ASR_JOINER = "joiner-epoch-99-avg-1-chunk-16-left-128.int8.onnx"
    const val ASR_TOKENS = "tokens.txt"
    const val ASR_TAR_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$ASR_DIR.tar.bz2"
    val asrRequired = listOf(ASR_ENCODER, ASR_DECODER, ASR_JOINER, ASR_TOKENS)

    data class AsrPack(
        val dir: String,
        val encoder: String,
        val decoder: String,
        val joiner: String,
        val tokens: String,
        val modelType: String,
        val minBytes: Map<String, Long>,
    ) {
        val required: List<String> get() = listOf(encoder, decoder, joiner, tokens)

        fun isComplete(packDir: File): Boolean = required.all { name ->
            val file = File(packDir, name)
            file.isFile && file.length() >= (minBytes[name] ?: 1L)
        }
    }

    val ASR_PRIMARY = AsrPack(
        dir = ASR_DIR,
        encoder = ASR_ENCODER,
        decoder = ASR_DECODER,
        joiner = ASR_JOINER,
        tokens = ASR_TOKENS,
        modelType = "zipformer2",
        minBytes = mapOf(
            ASR_ENCODER to 60_000_000L, // official ~68MB
            // The genuine official int8 decoder for THIS pack is ~540KB, not
            // 1.3MB — verified 2026-08-22 by re-downloading and extracting
            // the real k2-fsa release archive fresh. This decoder is a small
            // vocab-sized lookup, near-identical in size to the 20M fallback
            // pack's decoder (539KB) — that similarity is coincidental, not a
            // sign of a wrong file. The old 1_000_000L floor rejected every
            // correctly-downloaded primary decoder as "truncated", silently
            // forcing permanent fallback to the weaker 20M ASR pack.
            ASR_DECODER to 400_000L,
            ASR_JOINER to 200_000L,     // official 254KB
            ASR_TOKENS to 1_000L,
        ),
    )

    val ASR_FALLBACK = AsrPack(
        dir = "sherpa-onnx-streaming-zipformer-en-20M-2023-02-17",
        encoder = "encoder-epoch-99-avg-1.int8.onnx",
        decoder = "decoder-epoch-99-avg-1.int8.onnx",
        joiner = "joiner-epoch-99-avg-1.int8.onnx",
        tokens = ASR_TOKENS,
        modelType = "zipformer",
        minBytes = mapOf(
            "encoder-epoch-99-avg-1.int8.onnx" to 30_000_000L,
            "decoder-epoch-99-avg-1.int8.onnx" to 400_000L,
            "joiner-epoch-99-avg-1.int8.onnx" to 200_000L,
            ASR_TOKENS to 1_000L,
        ),
    )

    /**
     * Whisper base.en (int8) — the offline/batch transcriber.
     *
     * Why this exists: the streaming zipformer above trades accuracy for
     * latency and measurably mishears (its own engine KDoc says so). Whisper
     * is a batch model — no live partials — but it is far more accurate, and
     * it runs entirely locally: no network, no API key.
     *
     * The native libsherpa-onnx-jni.so already ships Whisper + the full
     * OfflineRecognizer JNI API (verified: offline-whisper-model.cc and
     * Java_..._OfflineRecognizer_* symbols are present), so this is a model
     * pack + Kotlin wrapper, not a new native dependency.
     *
     * Sizes are the int8 files: encoder ~29MB, decoder ~130MB. The fp32
     * encoder (95MB) / decoder (196MB) are also in the tarball — do NOT load
     * them; int8 is the supported mobile path.
     */
    const val WHISPER_DIR = "sherpa-onnx-whisper-base.en"
    const val WHISPER_ENCODER = "base.en-encoder.int8.onnx"
    const val WHISPER_DECODER = "base.en-decoder.int8.onnx"
    const val WHISPER_TOKENS = "base.en-tokens.txt"

    /**
     * Size floors, same reason as [AsrPack.minBytes]: a truncated unpack
     * still has length > 0 and then native-aborts onnxruntime with
     * "Protobuf parsing failed", killing the process.
     */
    val WHISPER_MIN_BYTES: Map<String, Long> = mapOf(
        WHISPER_ENCODER to 25_000_000L,  // official ~29MB
        WHISPER_DECODER to 100_000_000L, // official ~130MB
        WHISPER_TOKENS to 500_000L,      // official ~835KB
    )

    val whisperRequired = listOf(WHISPER_ENCODER, WHISPER_DECODER, WHISPER_TOKENS)

    const val WHISPER_TAR_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$WHISPER_DIR.tar.bz2"

    /** True only when every Whisper file meets its published size floor. */
    fun whisperComplete(dir: File): Boolean = whisperRequired.all { name ->
        File(dir, name).let { it.isFile && it.length() >= (WHISPER_MIN_BYTES[name] ?: 1L) }
    }

    const val KWS_TAR_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/$KWS_DIR.tar.bz2"
    const val VAD_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"
    const val SPEAKER_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/wespeaker_en_voxceleb_CAM++.onnx"

    /** Files the keyword spotter will abort the process without. */
    val kwsRequired = listOf(ENCODER, DECODER, JOINER, TOKENS, KEYWORDS)

    fun kwsRel(name: String): String = "$KWS_DIR/$name"

    /**
     * Same phrases as scripts/fetch_kws_assets.sh. Written locally so a Play
     * install does not depend on our custom keywords.txt being on GitHub.
     */
    val keywordsText: String = """
        ▁ Y OM E OW :3.5 #0.08
        ▁ Y O ▁ME OW :3.5 #0.08
        ▁HE Y ▁ME OW :3.5 #0.08
        ▁O K ▁ME OW :3.5 #0.08
        ▁OKAY ▁ME OW :3.5 #0.08
        ▁ Y OM A O :3.5 #0.08
        ▁ Y OM E O :3.5 #0.08
        ▁ Y O ▁MA O :3.5 #0.08
        ▁ Y O ▁ME O :3.5 #0.08
        ▁ Y O ▁MO W :3.5 #0.08
        ▁ Y O ▁ME W :3.5 #0.08
        ▁ Y O ▁MI A O :3.5 #0.08
        ▁HE Y ▁MA O :3.5 #0.08
        ▁HE Y ▁ME O :3.5 #0.08
        ▁HE Y ▁ME W :3.5 #0.08
        ▁O K ▁MA O :3.5 #0.08
        ▁O K ▁ME W :3.5 #0.08
        ▁OKAY ▁MA O :3.5 #0.08
        ▁E Y ▁ME OW :3.5 #0.08
        ▁E H ▁ME OW :3.5 #0.08
        ▁E Y ME OW :3.5 #0.08
        ▁HE Y ME OW :3.5 #0.08
        ▁HA Y ME OW :3.5 #0.08
        ▁HA Y ▁ME OW :3.5 #0.08
        ▁HI ▁ME OW :3.5 #0.08
        ▁HE Y ▁ME A OW :3.5 #0.08
        ▁HE Y ▁ME E OW :3.5 #0.08
        ▁HE Y ▁MI A O :3.5 #0.08
        ▁HE Y ▁ME ▁ OW :3.5 #0.08
        ▁O K ME OW :3.5 #0.08
        ▁O KE Y ▁ME OW :3.5 #0.08
        ▁O K ▁ME ▁ OW :3.5 #0.08
        ▁HE Y ▁K IT TY :2.0 #0.15
        ▁ Y O ▁ME M B A :2.5 #0.12
        ▁HE Y ▁ME M B A :2.5 #0.12
        ▁O I ▁ME OW :2.5 #0.12
    """.trimIndent() + "\n"
}
