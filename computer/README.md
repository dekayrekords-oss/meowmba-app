# Meow Computer (server package)

Gives Meowmba a **persistent Linux workspace** on a machine you already SSH into.
This is the same idea as Grok Bot’s computer: a real Linux runtime the app can use.

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

## Health check

```bash
curl -s http://127.0.0.1:8766/health
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
