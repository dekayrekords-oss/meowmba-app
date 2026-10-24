package com.example.meowmba.core.computer

/**
 * Shared public links + short copy for **Your Computer** and first-run onboarding.
 *
 * Prefer F-Droid / GitHub. Do **not** use Play Store com.termux as the primary link
 * (Play listing is abandoned / unofficial forks).
 *
 * Onboarding workers should import these constants rather than duplicating URLs.
 */
object TermuxComputerLinks {
    const val TERMUX_FDROID = "https://f-droid.org/packages/com.termux/"
    const val TERMUX_GITHUB_RELEASES = "https://github.com/termux/termux-app/releases"
    /** Local NetBird sideload mirror on meow (same LAN / VPN as the phone). */
    const val TERMUX_NETBIRD = "http://100.77.97.236:8765/termux.apk"
    const val TERMUX_PACKAGE = "com.termux"
    /** In-app / docs deep path label — not a Play Store URI. */
    const val SETUP_GUIDE_DOC = "docs/YOUR_COMPUTER.md"

    /** OpenCode Zen key page — same destination Settings / CloudModelSettings use. */
    const val OPENCODE_ZEN_KEY_URL = "https://opencode.ai/zen"

    const val BUTTON_INSTALL_TERMUX = "Install Termux"
    const val BUTTON_GET_TERMUX = "Get Termux"
    const val BUTTON_GITHUB_DOWNLOAD = "GitHub download"
    const val BUTTON_NETBIRD = "NetBird"
    const val BUTTON_OPEN_TERMUX = "Open Termux"
    const val BUTTON_SHOW_APPS = "Show my apps"
    const val BUTTON_I_INSTALLED = "I installed it"

    /** Paste in Termux. Keep as two lines so a child can copy once. */
    const val BOOTSTRAP_COMMANDS =
        "pkg update && pkg install proot proot-distro openssh tmux\n" +
            "proot-distro install debian"
    const val BUTTON_SETUP_GUIDE = "Setup guide"

    /**
     * Kid-simple OpenCode bootstrap for the in-app Computer Terminal (PTY).
     * Installs only when `opencode` is missing, then launches it.
     * Official installer: https://opencode.ai/install — PATH may already include
     * ~/.opencode/bin on some guests; we still export it before launch.
     */
    const val OPENCODE_SETUP_COMMANDS =
        "export PATH=\"\$HOME/.opencode/bin:\$PATH\"; " +
            "if command -v opencode >/dev/null 2>&1; then echo \"OpenCode already installed.\"; " +
            "else echo \"Installing OpenCode…\"; curl -fsSL https://opencode.ai/install | bash; " +
            "export PATH=\"\$HOME/.opencode/bin:\$PATH\"; fi; " +
            "command -v opencode >/dev/null 2>&1 && opencode || " +
            "echo \"OpenCode not found yet. Open Termux, finish Computer setup, then try again.\""

    const val HOWTO_PHONE_SHORT =
        "Need a Linux workspace? Start on this phone with Termux (F-Droid or GitHub). " +
            "A remote server is optional."

    const val TERMUX_BOOTSTRAP_STEPS =
        "1. Install Termux from F-Droid or GitHub releases.\n" +
            "2. In Termux: pkg update && pkg install proot proot-distro openssh tmux\n" +
            "3. proot-distro install debian && proot-distro login debian\n" +
            "4. In Meowmba: Terminal → Computer (or Settings → Your Computer → Open Terminal)."

    const val SERVER_HOWTO_SHORT =
        "Optional: enter a Linux host, port, username, and password or key. " +
            "SSH is the day-to-day path. Tap Test connection, then Install Computer " +
            "to upload and run install.sh over SSH."

    const val HTTPS_RUNTIME_HINT =
        "HTTPS runtime is optional / advanced. The Fold cannot reach 127.0.0.1 on meow — " +
            "use an SSH profile, or set Host to a full URL the phone can reach " +
            "(e.g. http://100.77.97.236:18766 on NetBird) or ssh -L 18766:127.0.0.1:18766. " +
            "Worker port is 18766 (Desktop Bridge discovery stays on 8766)."

    const val MATURITY_PREVIEW_NOTE =
        "Same idea as a cloud computer agent: a real Linux workspace the app can use. " +
            "Maturity is preview — expect rough edges; agentic + Computer stay behind flags."

    const val INSTALL_PROMPT =
        "Install Meow Computer on this server? This adds a persistent Linux workspace " +
            "the app can use like a desktop computer."
}
