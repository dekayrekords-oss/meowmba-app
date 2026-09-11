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
    /** In-app / docs deep path label — not a Play Store URI. */
    const val SETUP_GUIDE_DOC = "docs/YOUR_COMPUTER.md"

    const val BUTTON_INSTALL_TERMUX = "Install Termux"
    const val BUTTON_GET_TERMUX = "Get Termux"
    const val BUTTON_GITHUB_DOWNLOAD = "GitHub download"

    /** Paste in Termux. Keep as two lines so a child can copy once. */
    const val BOOTSTRAP_COMMANDS =
        "pkg update && pkg install proot proot-distro openssh tmux\n" +
            "proot-distro install debian"
    const val BUTTON_SETUP_GUIDE = "Setup guide"

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
            "Tap Test connection, then Install Computer to upload and run install.sh over SSH."

    const val INSTALL_PROMPT =
        "Install Meow Computer on this server? This adds a persistent Linux workspace " +
            "the app can use like a desktop computer."
}
