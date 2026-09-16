"""Read-only capability facts. Never launch apps, inspect projects or prompt for access."""
import ctypes
import plistlib
import sys
from pathlib import Path


def accessibility_trusted():
    """AXIsProcessTrusted reads the current grant; unlike the Options variant,
    it cannot open an OS permission prompt or change an authorization."""
    if sys.platform != "darwin":
        return None
    try:
        framework = ctypes.CDLL("/System/Library/Frameworks/ApplicationServices.framework/ApplicationServices")
        check = framework.AXIsProcessTrusted
        check.argtypes = []
        check.restype = ctypes.c_bool
        return bool(check())
    except (OSError, AttributeError):
        return None


def logic_pro_status(gui_enabled=False, logic_enabled=False, *, platform=None, app_roots=None, permission_check=None):
    platform = platform or sys.platform
    result = dict(installed=False, version=None, platform_supported=platform == "darwin",
                  ui_enabled=bool(gui_enabled) and platform == "darwin",
                  accessibility_trusted=None, automation_access="not_tested",
                  dedicated_adapter=platform == 'darwin', adapter_version=2 if platform == 'darwin' else None,
                  logic_enabled=bool(gui_enabled and logic_enabled) and platform == 'darwin', mix_master_verified=False)
    if platform != "darwin":
        return result
    # Deliberately fixed conventional app roots; callers cannot request arbitrary files.
    roots = app_roots if app_roots is not None else (Path("/Applications"), Path.home() / "Applications")
    for root in roots:
        for name in ("Logic Pro.app", "Logic Pro X.app"):
            info = Path(root) / name / "Contents" / "Info.plist"
            try:
                with info.open("rb") as source:
                    data = source.read(128 * 1024 + 1)
                if len(data) > 128 * 1024:
                    continue
                metadata = plistlib.loads(data)
                if not isinstance(metadata, dict):
                    continue
                if metadata.get("CFBundleIdentifier") != "com.apple.logic10":
                    continue
                result["installed"] = True
                version = metadata.get("CFBundleShortVersionString")
                result["version"] = str(version)[:64] if version else None
                break
            except (OSError, ValueError, plistlib.InvalidFileException):
                continue
        if result["installed"]:
            break
    if result["ui_enabled"]:
        try:
            value = (permission_check or accessibility_trusted)()
            result["accessibility_trusted"] = value if isinstance(value, bool) else None
        except Exception:
            # An unreadable grant is unknown, never "ready".
            pass
    return result
