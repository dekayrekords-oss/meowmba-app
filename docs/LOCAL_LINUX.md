# Local Linux (Termux / PRoot guest / sandbox / SSH)

Honest limits for Meowmba’s companion Terminal and agentic `local_shell`.

## Goal (what “full headless micro Linux” means here)

Users want a **Grok-like** local loop against a **userspace Linux guest** that:

- Runs as **root inside the guest** (PRoot / `proot-distro` / `proot -0` / fakeroot)
- Can **`ssh` out** to external hosts (meow, etc.) via an OpenSSH **client** in that guest
- Does **not** require Android/device root, Magisk, or SELinux bypass

**Root-in-sandbox (guest) ≠ phone root.** The guest’s UID mapping is userspace emulation; the phone stays unrooted. Outbound `ssh` from the guest uses the **network**, same as any app.

The Android app can **also** open a [ConnectionProfile] SSH session directly (JSch) — guest `ssh` is **additional**, not a replacement.

## What Local can do

| Mode | Label | How | What you get |
|------|-------|-----|--------------|
| **Termux Linux** | **Termux Linux** | Termux `bash`/`sh` executable from Meowmba UID | Userspace Linux. Prefer **proot-distro** (or `proot -0`) for a distro rootfs with **root-in-guest** + `apt`. |
| **Local sandbox** | **Local sandbox** | App `/system/bin/sh` + Java `ls`/`pwd` builtins | App sandbox only. No real `apt`. UI: *Install Termux for a real local Linux; sandbox sh cannot do ls/apt*. |
| **SSH (app)** | **SSH · …** | ConnectionProfile / legacy host via JSch | Full remote machine from the APK (meow). |
| **SSH (guest)** | (inside Termux/proot) | `openssh` / `openssh-client` in Termux or proot guest | Guest runs `ssh user@host` over the network. |

## Preference order (legitimate only)

1. **Termux bash/sh** when present and executable.
2. **Persistent piped Termux shell** for the interactive Terminal tab.
3. Inside Termux: **PRoot guest as root-in-guest** — `proot-distro login <distro>`, or `proot -0 -r <rootfs> /bin/bash`, or fakeroot where appropriate.
4. **OpenSSH client** in Termux (`pkg install openssh`) and/or in the guest (`apt install openssh-client`) so the guest can ssh to meow.
5. Termux **`RUN_COMMAND`** when binaries exist but are not executable from Meowmba UID (allow-external-apps).
6. Fallback **sandbox sh** + builtins.

## Bootstrap (paste in Termux)

```sh
pkg install -y proot proot-distro openssh
proot-distro install ubuntu   # or debian, etc.
proot-distro login ubuntu     # root-in-guest — NOT phone root
# inside guest:
apt update && apt install -y openssh-client
ssh user@meow-host            # outbound over the network
```

App-side SSH: Settings → Neural / Lumen → Agentic connection profiles (or Terminal → **SSH · …**).

## What Local cannot / will not do

- Android / device root, Magisk, SELinux bypass, or sandbox escape  
- Read other apps’ private data  
- Claim guest root is phone root  
- Leave the APK sandbox without Termux/PRoot or an SSH profile  

## Grok-like agentic loop

With `agentic_runtime_v1` ON:

- `local_shell` → Termux first (then RUN_COMMAND, then sandbox) — operator can drive proot/apt/ssh **inside** that shell  
- `ssh_exec` → app ConnectionProfile SSH / HTTPS_RUNTIME (full remote machine)

Do **not** drive shells from the Lumen avatar frame loop.

## Settings copy (Neural / Lumen)

- Local APK cannot leave the Android sandbox  
- Termux + PRoot / proot-distro = userspace micro Linux with **root-in-guest** (not phone root)  
- Install **openssh-client** in the guest to ssh out; app SSH profiles also reach a full machine (meow)

## Related

- [`TERMINAL_CLONE_AGENTIC.md`](./TERMINAL_CLONE_AGENTIC.md) — SSH / Termux capability probe  
- [`INTEGRATION_LUMEN_AGENTIC.md`](./INTEGRATION_LUMEN_AGENTIC.md) — agentic runtime integration  
