#!/usr/bin/env python3
"""Read-only idle sampler: process + per-thread CPU, frame counts, thermal.

Usage: scripts/sample_idle_threads.py <package> <seconds> <out.json>
Nothing on the phone is changed: no gfxinfo reset, no permission or setting changes.
"""
import json, os, re, subprocess, sys, time

ADB = os.environ.get("ADB", "adb")
pkg, seconds, out = sys.argv[1], int(sys.argv[2]), sys.argv[3]
HZ = 100  # USER_HZ on Android kernels


def sh(cmd, timeout=30):
    return subprocess.run([ADB, "shell", cmd], capture_output=True, text=True, timeout=timeout).stdout


def pid_of(p):
    return sh(f"pidof {p}").strip().split(" ")[0]


def threads(pid):
    # name is in parentheses and may contain spaces; fields after it are fixed.
    raw = sh(f"for t in /proc/{pid}/task/*; do cat $t/stat 2>/dev/null; echo; done")
    res = {}
    for line in raw.splitlines():
        m = re.match(r"(\d+) \((.*)\) (.*)", line.strip())
        if not m:
            continue
        tid, name, rest = m.group(1), m.group(2), m.group(3).split()
        # rest[0] is state (field 3); utime field 14 -> rest[11], stime field 15 -> rest[12]
        res[tid] = (name, int(rest[11]) + int(rest[12]))
    return res


def proc_ticks(pid):
    f = sh(f"cat /proc/{pid}/stat").strip()
    rest = re.match(r"\d+ \(.*\) (.*)", f).group(1).split()
    return int(rest[11]) + int(rest[12])


def frames():
    raw = sh(f"dumpsys gfxinfo {pkg}")
    tot = re.search(r"Total frames rendered: (\d+)", raw)
    jank = re.search(r"Janky frames: (\d+)", raw)
    return (int(tot.group(1)) if tot else None, int(jank.group(1)) if jank else None)


def thermal():
    raw = sh("dumpsys thermalservice")
    st = re.search(r"^Thermal Status: (\d+)", raw, re.M)
    skin = re.search(r"mValue=([\d.]+), mType=3, mName=SKIN", raw)
    bat = sh("dumpsys battery")
    bt = re.search(r"temperature: (\d+)", bat)
    return {
        "status": int(st.group(1)) if st else None,
        "skin": float(skin.group(1)) if skin else None,
        "battery": int(bt.group(1)) / 10 if bt else None,
    }


def foreground():
    raw = sh("dumpsys activity activities | grep topResumedActivity")
    return pkg in raw


pid = pid_of(pkg)
if not pid:
    sys.exit(f"{pkg} is not running")
t_start = time.monotonic()
th0, p0, f0, therm0, fg0 = threads(pid), proc_ticks(pid), frames(), thermal(), foreground()
fg_samples = [fg0]
while time.monotonic() - t_start < seconds:
    time.sleep(5)
    fg_samples.append(foreground())
th1, p1, f1, therm1 = threads(pid), proc_ticks(pid), frames(), thermal()
elapsed = time.monotonic() - t_start
if pid_of(pkg) != pid:
    sys.exit("process restarted during sample; discard")

per_thread = []
for tid, (name, ticks) in th1.items():
    before = th0.get(tid, (name, 0))[1] if tid in th0 else 0
    d = ticks - before
    if d > 0:
        per_thread.append({"tid": tid, "name": name, "core_pct": round(d / HZ / elapsed * 100, 2)})
per_thread.sort(key=lambda r: -r["core_pct"])
# Group coroutine/pool workers by name family.
fam = {}
for r in per_thread:
    key = re.sub(r"[-_ ]?\d+$", "", r["name"])
    fam[key] = round(fam.get(key, 0) + r["core_pct"], 2)
result = {
    "package": pkg,
    "pid": pid,
    "seconds": round(elapsed, 1),
    "process_core_pct": round((p1 - p0) / HZ / elapsed * 100, 2),
    "frames_per_sec": round((f1[0] - f0[0]) / elapsed, 2) if f0[0] is not None and f1[0] is not None else None,
    "janky_frames": (f1[1] - f0[1]) if f0[1] is not None and f1[1] is not None else None,
    "total_frames": (f1[0] - f0[0]) if f0[0] is not None and f1[0] is not None else None,
    "foreground_all": all(fg_samples),
    "thermal_start": therm0,
    "thermal_end": therm1,
    "thread_families": dict(sorted(fam.items(), key=lambda kv: -kv[1])),
    "top_threads": per_thread[:25],
}
with open(out, "w") as fh:
    json.dump(result, fh, indent=2)
print(json.dumps({k: result[k] for k in ("package", "seconds", "process_core_pct", "frames_per_sec", "janky_frames", "foreground_all", "thermal_start", "thermal_end")}, indent=1))
print("families:", json.dumps(dict(list(result["thread_families"].items())[:14])))
