# Meow Computer (server package)

Gives Meowmba a **persistent Linux workspace** on a machine you already SSH into.
This is the same idea as a cloud computer agent: a real Linux runtime the app can use.
**Maturity: preview.** Stock Termux only; no FlyWire / custom proot forks.

## What you get

- Workspace under `/opt/meow-computer` (if writable) or `$HOME/meow-computer`
- Stock tools: OpenSSH **client**, **tmux**, curl, git, python3
- Optional desktop (TigerVNC / noVNC) when `apt` + passwordless sudo allow it
- Small HTTP worker compatible with the app’s `HttpsRuntimeClient`:
  - `GET /health`
  - `POST /v1/jobs`
  - `GET /v1/jobs/{id}`
  - `GET /v1/jobs/{id}/logs`
- `systemd --user` unit **or** `docker-compose.yml` to keep the worker alive

## Install (from a server shell)

```bash
curl -fsSL https://example.invalid/meow-computer/install.sh | bash
# or, from this repo:
bash computer/install.sh
```

Or from the Meowmba app: **Settings → Your Computer →** enter SSH details →
**Test connection** → **Install Computer**.

The script runs as **your SSH user**. It never requests phone root and never
writes secrets into git.

## Port / bind

Default worker port is **18766** (Desktop Bridge discovery stays on UDP **8766**).
Default bind is `127.0.0.1`. Override:

```bash
MEOW_COMPUTER_HOST=100.77.97.236 MEOW_COMPUTER_PORT=18766 bash computer/install.sh
# or
bash computer/install.sh --bind 100.77.97.236 --port 18766
```

Fold cannot hit meow's localhost — use NetBird bind, SSH `-L`, or prefer SSH profiles.

## Health check

```bash
curl -s http://127.0.0.1:18766/health
```

## Power / service

```bash
systemctl --user start meow-computer   # On
systemctl --user stop meow-computer    # Off
# or: docker compose -f ~/meow-computer/docker-compose.yml up -d
```

## Safety

- No secrets in this package
- No custom proot/tmux forks — stock packages only
- Guest root inside a proot on phone ≠ Android root (phone path is separate)
