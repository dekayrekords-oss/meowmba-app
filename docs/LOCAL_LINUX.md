# Computer (Linux guest — Termux / PRoot / sandbox / SSH)

Honest limits for Meowmba’s companion Terminal **Computer** target and agentic `local_shell`.

**Computer is a Linux guest (root inside only). It can SSH out. Off = no CPU. Idle = asleep.**

## Power: Off | Idle | On

| State | What happens | CPU |
|-------|----------------|-----|
| **On** | Start/keep the guest session (Termux/PRoot if available, else sandbox `sh`). Terminal **Connect** / **Wake** works. Inactivity timer runs. | Guest processes |
| **Idle** | After N minutes unused (default **5**, setting **1–30**): disconnect PTY, stop guest processes, **keep disk/state**. UI shows Idle. Cheap. | Asleep |
| **Off** | User toggle (Terminal header **Off** or Settings → Neural / Lumen → Computer). Kill session. **No background ticks** for Computer. No Termux/PRoot held by the app. Disk/state kept — Turn **On** reconnects (no reinstall). | None |

Inactivity is last **stdin or stdout** time on the Computer guest. SSH profiles are separate and are not killed by Computer Idle/Off.

Turning **On** from Idle or Off reconnects the guest. Do **not** implement Android / phone root.

## How to switch power

1. **Terminal header** (Chat → Terminal, Computer selected): **On** / **Off**. Status shows `on` / `idle` / `off`. **Wake** or **Connect** turns On and opens the guest.
2. **Settings → Neural / Lumen → Computer**: Off / Idle / On buttons + idle-minutes slider (1–30).

## Goal (userspace micro Linux)

- Runs as **root inside the guest** (PRoot / `proot-distro` / `proot -0` / fakeroot)
- Can **`ssh` out** via an OpenSSH **client** in that guest
- Does **not** require Android/device root, Magisk, or SELinux bypass

**Guest root ≠ phone root.** Outbound `ssh` uses the network. App ConnectionProfile SSH is additional.

## Chat → Computer (Agentic ON)

When **Settings → Neural / Lumen → Agentic** is enabled:

- Chat can use Computer when Agentic is on.
- Type a shell-looking line (`ls`, `git status`, `uname -a`, …) or `Run on Computer: <cmd>`.
- Planner tool `computer_exec` wakes Computer from Idle/Off, runs via the Terminal session (or one-shot), and shows output in **chat** and **Terminal** scrollback.
- Destructive patterns (`rm -rf`, …) need Agentic approval; remote `ssh_exec` still uses WAITING_APPROVAL for side effects.
- Idle timeout still applies after agentic use. No Android root. No FlyWire.

## What Computer can do

| Mode | Picker label | How | What you get |
|------|----------------|-----|--------------|
| **Termux guest** | **Computer** | Termux `bash`/`sh` executable from Meowmba UID | Userspace Linux. Prefer **proot-distro** for root-in-guest + `apt`. |
| **Sandbox** | **Computer** | App `/system/bin/sh` + Java `ls`/`pwd` | App sandbox only. No real `apt`. |
| **SSH (app)** | **SSH · …** | ConnectionProfile / legacy host via JSch | Full remote machine (meow). |
| **SSH (guest)** | (inside Termux/proot) | `openssh-client` in the guest | Guest runs `ssh user@host` over the network. |

## Preference order (legitimate only)

1. Termux bash/sh when executable.
2. Persistent piped Termux shell for the Terminal tab (Computer On).
3. PRoot guest as root-in-guest — `proot-distro login`, `proot -0`, or fakeroot.
4. OpenSSH client in Termux / guest so the guest can ssh to meow.
5. Termux `RUN_COMMAND` when binaries exist but are not executable from this UID.
6. Fallback sandbox sh + builtins.

## Bootstrap (paste in Termux)

```sh
pkg install -y proot proot-distro openssh
proot-distro install ubuntu
proot-distro login ubuntu     # root-in-guest — NOT phone root
apt update && apt install -y openssh-client
ssh user@meow-host
```

## What Computer will not do

- Android / device root, Magisk, SELinux bypass, or sandbox escape
- Read other apps’ private data
- Claim guest root is phone root
- Hold Termux/PRoot while **Off**

## Related

- [`TERMINAL_CLONE_AGENTIC.md`](./TERMINAL_CLONE_AGENTIC.md)
- [`INTEGRATION_LUMEN_AGENTIC.md`](./INTEGRATION_LUMEN_AGENTIC.md)
