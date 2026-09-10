import math
from pathlib import Path
import struct
import tempfile
import unittest
import wave
from audio_verify import verify_wave


class AudioVerifyTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.path = Path(self.temp.name) / 'bounce.wav'
    def tearDown(self): self.temp.cleanup()
    def pcm(self, samples, channels=1):
        with wave.open(str(self.path), 'wb') as f:
            f.setnchannels(channels); f.setsampwidth(2); f.setframerate(48000)
            f.writeframes(struct.pack('<' + 'h'*len(samples), *samples))

    def test_known_stereo_samples_are_measured_per_channel(self):
        self.pcm([16384,0,-16384,32767], 2); r = verify_wave(self.path)
        self.assertEqual(r['frames'], 2); self.assertAlmostEqual(r['duration_seconds'], 2/48000)
        self.assertAlmostEqual(r['channel_metrics'][0]['sample_peak_dbfs'], -6.020599913)
        self.assertEqual(r['channel_metrics'][1]['full_scale_samples'], 1)
        self.assertEqual(len(r['sha256']),64)

    def test_silence_is_explicit_not_a_successful_song(self):
        self.pcm([0]*100); r=verify_wave(self.path)
        self.assertFalse(r['signal_present']); self.assertIsNone(r['channel_metrics'][0]['rms_dbfs'])

    def test_truncated_or_oversized_header_is_rejected(self):
        self.pcm([1]*100); raw=self.path.read_bytes()
        for value in [raw[:-2], raw+b'extra', raw[:30]]:
            self.path.write_bytes(value)
            with self.assertRaises(ValueError):verify_wave(self.path)

    def test_empty_audio_is_rejected(self):
        self.pcm([])
        with self.assertRaises(ValueError): verify_wave(self.path)

    def test_finite_float_audio_and_invalid_samples(self):
        for sample in [.5, float('nan'), float('inf')]:
            fmt=struct.pack('<HHIIHH',3,1,48000,192000,4,32); data=struct.pack('<f',sample)
            content=b'WAVEfmt '+struct.pack('<I',len(fmt))+fmt+b'data'+struct.pack('<I',len(data))+data
            self.path.write_bytes(b'RIFF'+struct.pack('<I',len(content))+content)
            if math.isfinite(sample): self.assertTrue(verify_wave(self.path)['signal_present'])
            else:
                with self.assertRaises(ValueError):verify_wave(self.path)

    def test_inconsistent_format_is_rejected(self):
        self.pcm([1,2,3]); raw=bytearray(self.path.read_bytes());raw[28:32]=struct.pack('<I',12);self.path.write_bytes(raw)
        with self.assertRaises(ValueError):verify_wave(self.path)

if __name__ == '__main__': unittest.main()
