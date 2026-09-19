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
# BEGIN UBUNTU DETECTION
# PRoot-Distro 5 moved rootfs storage to containers/<name>/rootfs. Testing only
# the legacy path attempted to reinstall a live container ("container busy").
# Do not infer missing from a failed login: locked/broken installs must be kept.
meow_ensure_ubuntu() {
    local meow_runtime="$1"
    if [ -d "$meow_runtime/containers/ubuntu/rootfs" ] || [ -d "$meow_runtime/installed-rootfs/ubuntu" ]; then
        printf '%s\n' 'Existing Ubuntu found. Reusing your workspace.'
        return 0
    fi
    if [ -e "$meow_runtime/containers/ubuntu" ] || [ -L "$meow_runtime/containers/ubuntu" ] ||
       [ -e "$meow_runtime/installed-rootfs/ubuntu" ] || [ -L "$meow_runtime/installed-rootfs/ubuntu" ]; then
        printf '%s\n' 'Ubuntu exists but its filesystem is incomplete. Setup will not overwrite it. Keep the existing files and contact support.' >&2
        return 24
    fi
    printf '%s\n' 'No existing Ubuntu found. Downloading Ubuntu; keep Termux open.'
    proot-distro install ubuntu </dev/null || return $?
    if [ ! -d "$meow_runtime/containers/ubuntu/rootfs" ] && [ ! -d "$meow_runtime/installed-rootfs/ubuntu" ]; then
        printf '%s\n' 'Ubuntu installation did not create a recognized workspace. Setup stopped without starting a server.' >&2
        return 24
    fi
}
# END UBUNTU DETECTION
meow_termux_prefix="${TERMUX__PREFIX:-${PREFIX:-/data/data/com.termux/files/usr}}"
meow_ensure_ubuntu "$meow_termux_prefix/var/lib/proot-distro"
printf '%s\n' 'Checking Ubuntu and OpenCode…'
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
# Headless phone worker: use OpenCode's supported on-demand ripgrep backend.
# Avoid a persistent native search index/watcher inside PRoot while no task runs.
# File glob/grep tools remain available; no model, task or permission is disabled.
# Verified against packages/core/src/filesystem/search.ts in OpenCode v1.18.31.
export OPENCODE_DISABLE_FFF=1
export OPENCODE_CONFIG_CONTENT='\''{"permission":{"*":"ask"}}'\''
exec opencode serve --hostname 127.0.0.1 --port 4096 </dev/null
' 9>&- >"$HOME/.meow-computer/setup-server.log" 2>&1 &
meow_pid=$!
# Keep proot alive around its foreground worker. The app verifies readiness.
sleep 2
kill -0 "$meow_pid"
