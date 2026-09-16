"""Decode and measure a local PCM/float WAV bounce without third-party binaries.

Sample integrity is always checked. Optional libebur128 supplies separately
labelled LUFS and true peak; neither is a claim about artistic quality.
"""
import array
import hashlib
import math
from pathlib import Path
import struct
import sys
import time
from contextlib import ExitStack
from audio_loudness import LoudnessMeter


def verify_wave(path):
    path = Path(path)
    with path.open('rb') as f, ExitStack() as resources:
        before = path.stat()
        if not 44 <= before.st_size <= 2_000_000_000: raise ValueError('WAV must be nonempty and under 2 GB')
        header = f.read(12)
        if header[:4] != b'RIFF' or header[8:] != b'WAVE': raise ValueError('Choose a PCM or float RIFF/WAVE bounce; other formats need a decoder adapter')
        riff_end = struct.unpack('<I', header[4:8])[0] + 8
        if riff_end != before.st_size: raise ValueError('WAV size header does not match the actual file')
        fmt = None; data = None; chunks = 0
        while f.tell() < riff_end:
            chunks += 1
            if chunks > 10000: raise ValueError('WAV has too many chunks')
            chunk = f.read(8)
            if len(chunk) != 8: raise ValueError('Truncated WAV chunk header')
            kind, size = struct.unpack('<4sI', chunk); offset = f.tell()
            end = offset + size + (size & 1)
            if end > riff_end: raise ValueError('Truncated WAV chunk')
            if kind == b'fmt ':
                if fmt is not None or not 16 <= size <= 4096: raise ValueError('Invalid or duplicate WAV format')
                raw = f.read(size); fmt = struct.unpack('<HHIIHH', raw[:16])
                tag, channels, rate, byte_rate, align, bits = fmt
                if tag == 0xfffe:
                    if size < 40 or struct.unpack('<H', raw[16:18])[0] < 22: raise ValueError('Incomplete extensible WAV format')
                    valid_bits = struct.unpack('<H', raw[18:20])[0]
                    if valid_bits != bits: raise ValueError('Packed valid-bit formats are not supported yet')
                    guid = raw[24:40]
                    if guid[4:] != bytes.fromhex('00001000800000aa00389b71'): raise ValueError('Unknown WAV subformat')
                    tag = struct.unpack('<I', guid[:4])[0]
                if not (tag == 1 and bits in (8,16,24,32) or tag == 3 and bits in (32,64)):
                    raise ValueError('WAV encoding is not supported; export PCM or IEEE float')
                if not 1 <= channels <= 8 or not 8000 <= rate <= 384000 or align != channels * (bits//8) or byte_rate != rate * align:
                    raise ValueError('Inconsistent WAV sample format')
                fmt = tag, channels, rate, byte_rate, align, bits
            elif kind == b'data':
                if data is not None: raise ValueError('Multiple WAV data chunks are not supported')
                data = offset, size
            f.seek(end)
        if fmt is None or data is None: raise ValueError('WAV is missing format or audio data')
        tag, channels, rate, _, align, bits = fmt
        if data[1] == 0 or data[1] % align: raise ValueError('WAV has no complete audio frames')
        frames = data[1] // align
        if frames / rate > 4 * 3600: raise ValueError('Verify bounces under four hours')
        peaks = [0.] * channels; squares = [0.] * channels; sums = [0.] * channels; clipped = [0] * channels
        loudness = resources.enter_context(LoudnessMeter(channels, rate))
        deadline = time.monotonic() + 120
        digest = hashlib.sha256()
        f.seek(0)
        while block := f.read(1_048_576): digest.update(block)
        source_hash = digest.hexdigest()
        f.seek(data[0]); remaining = data[1]
        while remaining:
            if time.monotonic() > deadline: raise ValueError('Audio verification timed out')
            block = f.read(min(16384 * align, remaining))
            if not block or len(block) % align: raise ValueError('Audio changed or ended during verification')
            remaining -= len(block)
            if tag == 3:
                values = array.array('f' if bits == 32 else 'd', block)
                if sys.byteorder != 'little': values.byteswap()
                scale = 1.; max_positive = 1.
            elif bits == 24:
                values = [int.from_bytes(block[i:i+3], 'little', signed=True) for i in range(0, len(block), 3)]
                scale = float(1 << 23); max_positive = scale - 1
            elif bits == 8:
                values = [v - 128 for v in block]; scale = 128.; max_positive = 127.
            else:
                values = array.array('h' if bits == 16 else 'i', block)
                if sys.byteorder != 'little': values.byteswap()
                scale = float(1 << (bits - 1)); max_positive = scale - 1
            for i, raw in enumerate(values):
                value = raw / scale
                if not math.isfinite(value): raise ValueError('WAV contains NaN or infinite audio samples')
                c = i % channels
                peaks[c] = max(peaks[c], abs(value)); squares[c] += value * value; sums[c] += value
                if raw >= max_positive or raw <= -scale: clipped[c] += 1
            loudness.add(values, scale)
        # Hash the file again: source changes invalidate measured results.
        digest = hashlib.sha256(); f.seek(0)
        while block := f.read(1_048_576): digest.update(block)
        after = path.stat()
        if (before.st_ino, before.st_size, before.st_mtime_ns) != (after.st_ino, after.st_size, after.st_mtime_ns) or digest.hexdigest() != source_hash:
            raise ValueError('Audio changed while it was being verified')
        def db(value): return 20 * math.log10(value) if value > 0 else None
        if not all(math.isfinite(v) for v in peaks + squares + sums):
            raise ValueError('WAV sample magnitudes overflow the measurement range')
        return dict(verified_audio=True, path=str(path), sha256=source_hash, bytes=before.st_size, modified_at=before.st_mtime,
                    sample_rate=rate, channels=channels, bits=bits, encoding='PCM' if tag == 1 else 'IEEE float',
                    frames=frames, duration_seconds=frames/rate, signal_present=max(peaks) > 1e-7,
                    channel_metrics=[dict(sample_peak_dbfs=db(peaks[c]), rms_dbfs=db(math.sqrt(squares[c]/frames)),
                                          dc_offset=sums[c]/frames, full_scale_samples=clipped[c]) for c in range(channels)],
                    loudness=loudness.finish(),
                    limitations=['Sample peak and RMS are separate from the optional libebur128 true-peak/LUFS measurements.',
                                 'Decoding proves file integrity, not song content, artistic quality or a finished master.'])
