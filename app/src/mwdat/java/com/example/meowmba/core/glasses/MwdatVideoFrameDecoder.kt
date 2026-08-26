package com.example.meowmba.core.glasses

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import com.meta.wearable.dat.camera.types.VideoFrame
import java.io.ByteArrayOutputStream

/**
 * Decodes [VideoFrame]s from the MWDAT camera stream into [Bitmap]s.
 *
 * The SDK's public API (verified via javap against the real 0.8.0 .aar) gives
 * NO decode helper and NO explicit pixel/codec format field on VideoFrame —
 * only a `buffer: ByteBuffer` + `isCompressed: Boolean` + dimensions. Two
 * paths, both using only standard Android platform APIs (nothing SDK-private
 * or guessed):
 *
 * 1. isCompressed == true → assumed H.264 Annex-B elementary stream (the
 *    universal choice for this kind of low-power wearable camera pipeline).
 *    Fed to a real android.media.MediaCodec "video/avc" decoder outputting to
 *    an ImageReader; each decoded YUV_420_888 frame is converted to a JPEG
 *    Bitmap via the standard YuvImage path. If frames are actually a
 *    different codec, MediaCodec will report a configuration/queue error,
 *    which is logged rather than silently producing garbage.
 *
 * 2. isCompressed == false → raw buffer. There is no format field to confirm
 *    NV21 vs I420 vs RGBA, so this path only proceeds when the buffer size
 *    exactly matches a known layout for the given width/height (NV21/I420 =
 *    w*h*3/2, RGBA = w*h*4); anything else is logged and dropped rather than
 *    reinterpreted as pixels that could corrupt what reaches the vision model.
 */
class MwdatVideoFrameDecoder(private val onBitmap: (Bitmap) -> Unit) {

    private var codec: MediaCodec? = null
    private var imageReader: ImageReader? = null
    private var configuredWidth = -1
    private var configuredHeight = -1
    private var loggedRawFallback = false

    fun handle(frame: VideoFrame) {
        if (frame.isCompressed) {
            handleCompressed(frame)
        } else {
            handleRaw(frame)
        }
    }

    private fun ensureDecoder(width: Int, height: Int) {
        if (codec != null && width == configuredWidth && height == configuredHeight) return
        release()
        try {
            val reader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 2)
            reader.setOnImageAvailableListener({ r ->
                val image: Image? = r.acquireLatestImage()
                if (image != null) {
                    runCatching { onBitmap(image.toJpegBitmap()) }
                        .onFailure { Log.w("MeowGlasses", "MWDAT decode: image convert failed: ${it.localizedMessage}") }
                    image.close()
                }
            }, null)

            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
            val c = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            c.configure(format, reader.surface, null, 0)
            c.start()

            codec = c
            imageReader = reader
            configuredWidth = width
            configuredHeight = height
            Log.i("MeowGlasses", "MWDAT: H.264 decoder started (${width}x$height)")
        } catch (e: Exception) {
            Log.e("MeowGlasses", "MWDAT: decoder init failed (${width}x$height): ${e.localizedMessage}")
            release()
        }
    }

    private fun handleCompressed(frame: VideoFrame) {
        ensureDecoder(frame.width, frame.height)
        val c = codec ?: return
        try {
            val inIndex = c.dequeueInputBuffer(10_000L)
            if (inIndex < 0) return   // decoder busy; drop this frame rather than block the collector
            val inBuf = c.getInputBuffer(inIndex) ?: return
            inBuf.clear()
            inBuf.put(frame.buffer.duplicate())
            val flags = if (frame.isCodecConfig) MediaCodec.BUFFER_FLAG_CODEC_CONFIG else 0
            c.queueInputBuffer(inIndex, 0, frame.buffer.remaining(), frame.presentationTimeUs, flags)

            val bufferInfo = MediaCodec.BufferInfo()
            var outIndex = c.dequeueOutputBuffer(bufferInfo, 0)
            while (outIndex >= 0) {
                c.releaseOutputBuffer(outIndex, true)  // true = render to the ImageReader surface
                outIndex = c.dequeueOutputBuffer(bufferInfo, 0)
            }
        } catch (e: Exception) {
            Log.w("MeowGlasses", "MWDAT: decode step failed: ${e.localizedMessage}")
        }
    }

    private fun handleRaw(frame: VideoFrame) {
        val w = frame.width
        val h = frame.height
        val size = frame.buffer.remaining()
        val bmp = when (size) {
            w * h * 3 / 2 -> {
                // MWDAT 0.8.0 raw frames are often planar I420 [Y...][U...][V...],
                // but YuvImage only supports NV21 [Y...][VU VU...]. Converting
                // planar to interleaved here prevents the "green/purple split"
                // caused by treating planar chroma as interleaved pairs.
                val nv21 = i420ToNv21(frame.buffer.duplicate(), w, h)
                nv21ToBitmap(nv21, w, h)
            }
            w * h * 4 -> rgba8888ToBitmap(frame, w, h)
            else -> null
        }
        if (bmp != null) {
            onBitmap(bmp)
        } else if (!loggedRawFallback) {
            loggedRawFallback = true
            Log.w("MeowGlasses", 
                "MWDAT: raw frame ${w}x$h, $size bytes doesn't match a known " +
                "layout (YUV=${w * h * 3 / 2}, RGBA=${w * h * 4}) — dropping " +
                "rather than guess the pixel format."
            )
        }
    }

    private fun i420ToNv21(buffer: java.nio.ByteBuffer, w: Int, h: Int): ByteArray {
        val ySize = w * h
        val chromaSize = ySize / 4
        val out = ByteArray(ySize + chromaSize * 2)
        
        // Copy Luma (Y) from the beginning of the buffer
        val yBuf = buffer.duplicate().apply { position(0) }
        yBuf.get(out, 0, ySize)
        
        // Interleave U and V into VU pairs
        val uStart = ySize
        val vStart = ySize + chromaSize
        val uBuf = buffer.duplicate().apply { position(uStart) }
        val vBuf = buffer.duplicate().apply { position(vStart) }
        
        var outPos = ySize
        for (i in 0 until chromaSize) {
            out[outPos++] = vBuf.get() // V
            out[outPos++] = uBuf.get() // U
        }
        return out
    }

    private fun nv21ToBitmap(bytes: ByteArray, w: Int, h: Int): Bitmap? = try {
        val yuv = YuvImage(bytes, ImageFormat.NV21, w, h, null)
        val out = ByteArrayOutputStream()
        yuv.compressToJpeg(Rect(0, 0, w, h), 90, out)
        val jpeg = out.toByteArray()
        android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
    } catch (e: Exception) {
        Log.w("MeowGlasses", "MWDAT: YUV convert failed: ${e.localizedMessage}")
        null
    }

    private fun rgba8888ToBitmap(frame: VideoFrame, w: Int, h: Int): Bitmap? = try {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(frame.buffer.duplicate())
        bmp
    } catch (e: Exception) {
        Log.w("MeowGlasses", "MWDAT: RGBA convert failed: ${e.localizedMessage}")
        null
    }

    /**
     * YUV_420_888 → NV21, stride-aware. ImageReader/MediaCodec output commonly
     * has row padding (rowStride > width) and/or non-1 pixelStride on the
     * chroma planes — naive plane concatenation silently produces the right
     * luma but corrupted (shifted/wrong) color on many devices. This copies
     * row-by-row and sample-by-sample using the real strides, which is the
     * documented-correct way to do this conversion.
     */
    private fun Image.toJpegBitmap(): Bitmap {
        val w = width
        val h = height
        val nv21 = ByteArray(w * h * 3 / 2)

        // Y plane → first w*h bytes, respecting row stride.
        val yPlane = planes[0]
        val yBuf = yPlane.buffer
        val yRowStride = yPlane.rowStride
        var pos = 0
        for (row in 0 until h) {
            yBuf.position(row * yRowStride)
            yBuf.get(nv21, pos, w)
            pos += w
        }

        // U/V planes → interleaved VU (NV21) for the remaining h/2*w bytes,
        // respecting both row stride and pixel stride (chroma is often
        // supplied with pixelStride=2 already interleaved with the other
        // plane, or pixelStride=1 fully planar — this handles both).
        val uPlane = planes[1]
        val vPlane = planes[2]
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride
        val chromaW = w / 2
        val chromaH = h / 2
        var outPos = w * h
        for (row in 0 until chromaH) {
            for (col in 0 until chromaW) {
                val vIndex = row * vRowStride + col * vPixelStride
                val uIndex = row * uRowStride + col * uPixelStride
                nv21[outPos++] = vBuf.get(vIndex)   // NV21 = Y then interleaved VU
                nv21[outPos++] = uBuf.get(uIndex)
            }
        }

        val yuv = YuvImage(nv21, ImageFormat.NV21, w, h, null)
        val out = ByteArrayOutputStream()
        yuv.compressToJpeg(Rect(0, 0, w, h), 90, out)
        val jpeg = out.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
    }

    fun release() {
        runCatching { codec?.stop(); codec?.release() }
        runCatching { imageReader?.close() }
        codec = null
        imageReader = null
        configuredWidth = -1
        configuredHeight = -1
    }
}
