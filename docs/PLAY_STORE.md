# Play Store submission

**Historical submission notes — not a current release checklist.** Follow
[the September 5 release checklist](store/RELEASE_PREP_2026-09-05.md) for
1.3.20 / code 24. The current artifact is unsigned and uses
`com.example.meowmba`. Package, signing, privacy/data-safety and policy review
remain publication gates. Older version/size/declaration claims below must
not be copied into Console without revalidation.

## Already done in the app

- Earlier proposed release application id: `com.meow.meowmba`; not current.
- Release minify + resource shrink + native debug symbols.
- Debug broadcast that could write API keys is **debug builds only**.
- Runtime IPC: same-signer permission + certificate check.
- Cloud backup of chat / keys / files **off**.
- HTTP only to LAN / localhost; public HTTP refused.
- WebView: no `file://`, no mixed content, Safe Browsing on.
- HF download token no longer rides in Intent extras.
- Camera / mic / BT / GPS marked **not required** so more devices can install.

## You must do

1. **Preserve the installed app and its data.** Confirm package and signing
   identity first. A different package cannot update the existing app in place;
   do not uninstall it as part of release preparation.
2. Host `docs/PRIVACY.md` as a public **https** page. Paste that URL in
   Play Console → App content → Privacy policy.
3. Play Console → Create app → package `com.meow.meowmba`.
4. **Play App Signing** — upload
   `build-out/meowmba-release-1.1.0.aab` (not the APK). Keep
   `meowmba-release.keystore` offline; never commit it.
   Release name: `1.1.0`. Notes: live notes, on-device speech, remembers
   your name, optional cleanup and road alerts.
5. **Data safety** form (short answers):
   - Collected: optional — chat/audio to a cloud model *you* configured.
   - Not collected by us: location, contacts, photos (processed on device
     unless you send them to a model).
   - Encrypted in transit: yes (HTTPS; LAN HTTP is user-typed).
   - Users can request deletion: yes — uninstall + clear app data.
6. **Permissions declarations**
   - Accessibility: “optional phone control, user-enabled, confirm first.”
   - Foreground mic: “wake word while the screen is off, user toggle.”
   - Media projection: “screen watch, user toggle.”
   - Exact alarms: routines / timers the user set.
7. Store listing + App content: copy from `docs/store/PLAY_LISTING.md`.
   App icon is `docs/store/icon-512.png`. Feature graphic is
   `docs/store/feature-graphic.png`.
8. Target audience 13+.

## This build

- Version **1.1.0** (versionCode **2**). Last store AAB was 1.0.0 / 1.
- Package **`com.meow.meowmba`**. USB debug stays `com.example.meowmba`.
- Privacy (live): https://dekayrekords-oss.github.io/Meowmba/privacy.html
- Target SDK 36 (required for new Play uploads from 31 Aug 2026).
- Native libs are 16 KB ELF-aligned.

## Size

Release AAB is **154 MB** (same as 1.0.0, under Play’s upload cap).

- Voice models (KWS / VAD / speaker, ~34 MB) download on first launch.
- Only **arm64-v8a** native libs ship (x86 / x86_64 / armeabi-v7a stripped).
- Debug USB builds still bundle the models via `src/debug/assets`.

## Build the store artifact

```bash
./gradlew :app:bundleRelease
# output: app/build/outputs/bundle/release/app-release.aab
```

Release signing needs `keystore.properties` (gitignored) pointing at
`meowmba-release.keystore`.
