import plistlib
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from desktop_capabilities import logic_pro_status


class LogicPreflightTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)

    def tearDown(self):
        self.temp.cleanup()

    def app(self, metadata):
        info = self.root / "Logic Pro.app" / "Contents" / "Info.plist"
        info.parent.mkdir(parents=True, exist_ok=True)
        info.write_bytes(plistlib.dumps(metadata))

    def status(self, **kwargs):
        return logic_pro_status(platform="darwin", app_roots=[self.root], **kwargs)

    def test_installed_version_does_not_claim_editing_or_mastering(self):
        self.app(dict(CFBundleIdentifier="com.apple.logic10", CFBundleShortVersionString="12.3.1"))
        with patch("subprocess.run", side_effect=AssertionError("Must not launch anything")):
            result = self.status(gui_enabled=True, permission_check=lambda: True)
        self.assertTrue(result["installed"])
        self.assertEqual(result["version"], "12.3.1")
        self.assertTrue(result["accessibility_trusted"])
        self.assertEqual(result["automation_access"], "not_tested")
        self.assertTrue(result["dedicated_adapter"])
        self.assertFalse(result["logic_enabled"])
        self.assertFalse(result["mix_master_verified"])

    def test_disabled_gui_does_not_check_permission(self):
        def forbidden():
            raise AssertionError("No permission query while disabled")
        result = self.status(gui_enabled=False, permission_check=forbidden)
        self.assertIsNone(result["accessibility_trusted"])
        self.assertFalse(result["ui_enabled"])

    def test_missing_and_wrong_bundle_do_not_claim_installation(self):
        self.assertFalse(self.status()["installed"])
        self.app(dict(CFBundleIdentifier="com.example.other", CFBundleShortVersionString="99"))
        self.assertFalse(self.status()["installed"])

    def test_malformed_metadata_degrades_without_throwing(self):
        self.app(["not a metadata object"])
        self.assertFalse(self.status()["installed"])
        info = self.root / "Logic Pro.app" / "Contents" / "Info.plist"
        info.write_bytes(b"broken plist")
        self.assertFalse(self.status()["installed"])

    def test_other_platform_never_reads_app_or_permission(self):
        with patch.object(Path, "open", side_effect=AssertionError("No app read")):
            result = logic_pro_status(gui_enabled=True, platform="linux")
        self.assertFalse(result["platform_supported"])
        self.assertFalse(result["installed"])

    def test_unavailable_permission_probe_stays_unknown(self):
        def denied():
            raise OSError("unavailable")
        result = self.status(gui_enabled=True, permission_check=denied)
        self.assertIsNone(result["accessibility_trusted"])


if __name__ == "__main__":
    unittest.main()
