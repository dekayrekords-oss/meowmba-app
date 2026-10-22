import math
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from audio_loudness import LoudnessMeter
from audio_staging import stage_audio


class LoudnessTest(unittest.TestCase):
    def measure(self, amplitude, channels=1, frequency=1000):
        with LoudnessMeter(channels,48000) as meter:
            if not meter.state:self.skipTest('Install libebur128 for native measurement acceptance')
            for block in range(30):
                values=[]
                for i in range(block*4800,(block+1)*4800):
                    value=amplitude*math.sin(2*math.pi*frequency*i/48000)
                    values.extend([value]*channels)
                meter.add(values,1)
            return meter.finish()

    def test_one_khz_calibration_and_stereo_channel_weighting(self):
        mono=self.measure(.1);stereo=self.measure(.1,2)
        # BS.1770 K-weighting: a -20 dBFS 1 kHz mono sine is about -23 LUFS.
        self.assertAlmostEqual(mono['integrated_lufs'],-23.0,delta=.05)
        self.assertAlmostEqual(stereo['integrated_lufs']-mono['integrated_lufs'],3.0103,delta=.02)

    def test_half_amplitude_changes_loudness_and_true_peak_by_six_db(self):
        a=self.measure(.1);b=self.measure(.05)
        self.assertAlmostEqual(a['integrated_lufs']-b['integrated_lufs'],6.0206,delta=.01)
        self.assertAlmostEqual(a['true_peak_dbtp'][0]-b['true_peak_dbtp'][0],6.0206,delta=.01)

    def test_silence_and_missing_library_do_not_invent_lufs(self):
        silent=self.measure(0)
        self.assertIsNone(silent['integrated_lufs']);self.assertEqual(silent['true_peak_dbtp'],[None])
        with patch.object(Path,'is_file',return_value=False),LoudnessMeter(2,48000) as meter:
            self.assertFalse(meter.finish()['available'])

    def test_multichannel_requires_real_layout(self):
        with LoudnessMeter(6,48000) as meter:
            self.assertFalse(meter.finish()['available'])
            self.assertIn('channel map',meter.finish()['reason'])


class StagingTest(unittest.TestCase):
    def test_source_is_unchanged_and_each_import_has_an_independent_copy(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);source=root/'source.wav';source.write_bytes(b'test audio')
            a=stage_audio(source,root);b=stage_audio(source,root)
            self.assertNotEqual(a['path'],b['path']);self.assertEqual(a['sha256'],b['sha256'])
            Path(a['path']).write_bytes(b'Logic metadata changes')
            self.assertEqual(source.read_bytes(),b'test audio')
            self.assertEqual(Path(b['path']).read_bytes(),b'test audio')

    def test_unknown_file_types_and_linked_import_root_are_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);source=root/'code.py';source.write_text('not audio')
            with self.assertRaises(ValueError):stage_audio(source,root)
            source=root/'source.wav';source.write_bytes(b'audio')
            (root/'logic-imports').symlink_to(root,target_is_directory=True)
            with self.assertRaises(ValueError):stage_audio(source,root)


if __name__=='__main__':unittest.main()
