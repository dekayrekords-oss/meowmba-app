#!/data/data/com.termux/files/usr/bin/bash
# Fixed, user-invoked installer, not a model-accessible shell executor.
set -eu
set +x
umask 077
IFS= read -r meow_password
[ -n "$meow_password" ] || exit 20
mkdir -p "$HOME/.meow-computer"
if ! command -v flock >/dev/null 2>&1; then pkg install -y util-linux </dev/null; fi
exec 9>"$HOME/.meow-computer/setup.lock"
flock -n 9 || exit 21
if ! command -v proot-distro >/dev/null 2>&1; then
    pkg update -y </dev/null
    pkg install -y proot-distro </dev/null
fi
if [ ! -d "$PREFIX/var/lib/proot-distro/installed-rootfs/ubuntu" ]; then
    proot-distro install ubuntu </dev/null
fi
# Read the password before package managers run; never interpolate it into code,
# a command argument, disk file, terminal transcript or public storage.
proot-distro login ubuntu -- bash -c '
set -eu
set +x
umask 077
export DEBIAN_FRONTEND=noninteractive
export PATH="$HOME/.opencode/bin:$PATH"
if ! command -v opencode >/dev/null 2>&1; then
    apt-get update </dev/null
    apt-get install -y curl ca-certificates unzip </dev/null
    meow_installer=$(mktemp)
    trap '\''rm -f "$meow_installer"'\'' EXIT
    curl --fail --location --proto "=https" --proto-redir "=https" --connect-timeout 30 --max-time 180 https://opencode.ai/install -o "$meow_installer" </dev/null
    bash "$meow_installer" </dev/null
fi
command -v opencode >/dev/null
mkdir -p "$HOME/.local/state/meow-computer" "$HOME/MeowProjects"
' </dev/null
# Fail closed on occupied port. Never kill or replace a user-managed server.
if (echo >/dev/tcp/127.0.0.1/4096) 2>/dev/null; then exit 22; fi
printf '%s\n' "$meow_password" | proot-distro login ubuntu -- bash -c '
set -eu
set +x
IFS= read -r OPENCODE_SERVER_PASSWORD
export OPENCODE_SERVER_PASSWORD
export OPENCODE_SERVER_USERNAME=opencode
export PATH="$HOME/.opencode/bin:$PATH"
cd "$HOME/MeowProjects"
export OPENCODE_CONFIG_CONTENT='\''{"permission":{"*":"ask"}}'\''
exec opencode serve --hostname 127.0.0.1 --port 4096 </dev/null
' 9>&- >"$HOME/.meow-computer/setup-server.log" 2>&1 &
meow_pid=$!
# Keep proot alive around its foreground worker. The app verifies readiness.
sleep 2
kill -0 "$meow_pid"
