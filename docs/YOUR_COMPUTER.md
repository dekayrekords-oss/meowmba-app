# Your Computer

Meowmba can use a **Computer** — a Linux workspace the app treats like a desktop.

## Prefer: on this phone (no server)

You do **not** need a remote server. Termux + PRoot gives a real Linux guest on-device.

### 1. Install Termux

Use **F-Droid** or **GitHub releases** (not the abandoned Play Store listing):

- F-Droid: https://f-droid.org/packages/com.termux/
- GitHub: https://github.com/termux/termux-app/releases

In Meowmba: **Settings → Your Computer → Install Termux** / **Setup guide**.

### 2. Bootstrap Linux tools

In Termux:

```bash
pkg update
pkg install proot proot-distro openssh tmux
proot-distro install debian
proot-distro login debian
```

Inside the Debian guest you are **root-in-guest** (not Android/device root).

### 3. Use it in Meowmba

Open **Chat → Terminal → Computer**, or Settings → Your Computer → **Open Terminal**.
Power **On / Idle / Off** controls whether the guest is held.

Optional: install OpenSSH **client** in Termux/guest so the guest can `ssh` out.

## Optional: Linux server

If you have a VPS / PC / NAS:

1. Settings → **Your Computer** → **Linux server (optional)**
2. Enter display name, host, port (22), username, password or key ref
3. Tap **Test connection**
4. Tap **Install Computer** — the app uploads and runs `computer/install.sh` over SSH
5. When Ready, use **Open Terminal** (and **Open Desktop** if a VNC URL is found)

Status meanings:

| Status | Meaning |
|--------|---------|
| Not set up | No phone guest detected and no server profile |
| Connected | SSH or local guest session is live |
| Installing | `install.sh` is streaming |
| Ready | Workspace + worker healthy |
| Error | Human-readable failure (no secrets) |

Server install creates `/opt/meow-computer` or `$HOME/meow-computer` and a small
HTTP worker on `127.0.0.1:8766` matching `HttpsRuntimeClient`.

See also: `computer/README.md`, `docs/LOCAL_LINUX.md` (if present).

## Shared links (for onboarding / Settings)

| Label | URL |
|-------|-----|
| Termux (F-Droid) | https://f-droid.org/packages/com.termux/ |
| Termux (GitHub) | https://github.com/termux/termux-app/releases |
| Setup guide (in-app) | Settings → Your Computer |

Do **not** recommend Play Store `com.termux` as the primary install path.
