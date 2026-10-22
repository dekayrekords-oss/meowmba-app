# Play listing + App content (historical draft — review required)

Current submitted listing: [build 152](PLAY_LISTING_152.md), September 19, 2026.
The listing below is historical, including its app-content answers; do not reuse them.

Do not submit this draft unchanged. See `RELEASE_PREP_2026-09-05.md` for the
current package/signing gates. Reconcile the Memba app name, cloud/MCP/service
data flows, privacy disclosures and AccessibilityService policy before using
these older form answers. The privacy URL and graphics need live verification.

Use this for **step 5** (Store listing) and **step 6** (App content forms).
Package: `com.meow.meowmba`

---

## 5 — Store listing

### App name
Meow Assistant

### Short description (80 characters)
A voice cat companion on your phone. Talk, remember, and get things done.

Count: 73 characters.

### Full description

Meow Assistant is a voice-first cat companion that lives on your phone.

Talk to Meow. It listens, answers, and remembers what you care about. Chat, camera, and voice stay on the device unless you turn on a cloud brain in Settings.

What you can do
• Say “hey meow” (or tap the mic) and talk naturally
• Say “start taking notes” and Meow transcribes into a notebook on this phone
• Keep projects and tools, then pick them up later
• Open apps, set timers, search the web, and get directions
• Optional phone cleanup — you confirm every delete
• Optional compatible robot base, when physically attached
• Optional on-device or cloud AI — you choose, and you paste your own API key

Privacy
Microphone, camera, and location are only used for features you turn on. Chat, memories, and keys stay on this phone. Cloud backup of that data is off. If you add a cloud AI key, that provider sees what you send them — under their policy, not ours.

Meow Assistant is built for ages 13 and up.

### Category
**Productivity** (or Tools). Not a kids app.

### Tags / keywords (optional)
voice assistant, AI companion, cat, on-device AI, wake word

### Contact
Developer email: use the same address as your Play developer account.
Privacy policy URL (live):  
`https://dekayrekords-oss.github.io/Meowmba/privacy.html`

---

## 5 — Graphics

### App icon (512 × 512)
File: `docs/store/icon-512.png`.
Upload under Store listing → Graphics → App icon. Full square, no rounded corners.

### Feature graphic
File: `docs/store/feature-graphic.png` (1024 × 500).
Upload under Store listing → Graphics → Feature graphic.

### Phone screenshots (you take these on the Fold)

Play wants **at least 2**, JPEG or PNG, 16:9 or 9:16, between 320 px and 3840 px on each side.

Plug the phone in, open Meowmba, then run:

```bash
docs/store/capture-screenshots.sh
```

That grabs the **cover screen** (not the inner fold, which is black if closed).

Shoot these four, in order:

1. **Face** — just the cat, no menus.
2. **Chat** — swipe/open chat so a couple of bubbles show.
3. **Tiles** — open the tile menu (Settings, Privacy, Projects…).
4. **Privacy** — Privacy tile, so mic/camera toggles are visible.

If USB is dead: take the same four with the phone’s screenshot button and drop them in `docs/store/screenshots/`.

Do **not** show real API keys in the Settings shot.

---

## 6 — App content forms

### Privacy policy
Host `docs/PRIVACY.md` as a public **https** page.
Play Console → App content → Privacy policy → paste the URL.

No URL = no publish.

### Target audience
- Target age: **13 and up**
- Not designed for children
- Store presence: available to 13+

### News app
No.

### COVID-19
No.

### Data safety — start here

**Does your app collect or share user data?**
Answer **Yes** (microphone audio can go to Google’s speech service; optional cloud AI if the user adds a key). Then declare the rows below.

You (the developer) have **no backend**. Be honest about on-device vs third parties.

| Data type | Collected by you? | Shared? | Purpose | Optional? | Encrypted in transit? | Users can delete? |
|---|---|---|---|---|---|---|
| Approximate / precise location | No (used on device for nav) | Only if user opens Maps | App functionality | Yes | Yes (Maps is HTTPS) | Uninstall / clear app data |
| Photos and videos | No (camera for face track on device) | Only if user asks a cloud vision model | App functionality | Yes | Yes | Clear app data |
| Voice / audio | Processed on device for wake word and notes | **May be shared** with the device speech service (Google) while dictating | App functionality | Yes (mic can be off) | Yes | Clear app data |
| Biometric data (speaker voice print) | Stored **on device only** if the user enrolls a name | No | App functionality | Yes | N/A (never leaves) | Clear app data |
| Other user content (chat, notes) | Stored **on device only** | **Only if** user adds a cloud AI key (OpenRouter / OpenAI / etc.) | App functionality | Yes | Yes (HTTPS) | Delete chat / notes / clear app data |
| Photos and videos (cleanup scan) | On device when the user asks to tidy media | No (delete uses the system confirm sheet) | App functionality | Yes | N/A | Clear app data |
| Calendar | On device when user asks | No | App functionality | Yes | N/A | Clear app data |
| App activity / installed apps | On device for “open Spotify” and optional cleanup | No | App functionality | Yes | N/A | Clear app data |

**Data is encrypted in transit:** Yes (HTTPS). LAN HTTP is only for a server the user typed on their own network.

**Users can request deletion:** Yes — delete chat in-app, or uninstall / clear storage. You have no server copy.

**Account creation:** No.

**Independent security review:** No.

Do **not** check: ads, fraud prevention analytics, personalization sold to others. You don’t have ads or your own analytics.

### Permissions / sensitive APIs (declare these)

Play will ask why you use each. Paste:

**Microphone / foreground service (microphone)**  
“Optional wake phrase and talk-to-Meow. Off until the user enables the mic. Background listen is a separate toggle and shows a persistent notification.”

**Camera**  
“Optional face and hand tracking so the avatar can look at the user. Off if they disable the camera.”

**Location (fine / coarse)**  
“Optional. Used only when the user asks for directions or nearby places.”

**Calendar (read / write)**  
“Optional. Used only when the user asks to read or add events.”

**Exact alarms**  
“User-created routines and timers (for example play music at 7am).”

**Accessibility service**  
“Optional phone control. Off until the user turns it on in system Settings. Meow can then tap, type, or scroll when asked. Risky actions still need confirmation.”

**Media projection / screen capture**  
“Optional screen watch. Off until the user turns it on. Used so Meow can see the screen when asked.”

**Query installed apps**  
“So ‘open Spotify’ can find the user’s apps. We do not upload the app list.”

**Photos / videos / audio (READ_MEDIA_*)**  
“Optional phone cleanup. Used only when the user asks to tidy photos, videos, or music. Deletes go through the system confirm sheet — never silent.”

**Usage access (PACKAGE_USAGE_STATS)**  
“Optional phone cleanup. Used only when the user asks which unused apps are taking space. We do not upload usage data.”

**Notification access**  
“Optional road alerts. Off until the user enables notification access. Reads Waze / Maps notifications for speed-camera and police reports. No other notifications are stored.”

**Default Home / launcher**  
“Optional. The user can set Meowmba as their home screen in Android settings. Fully reversible.”

### Content rating (IARC) — tick these

- Violence: **None**
- Sexuality: **None**
- Language: **None** (users might type their own; not in the app)
- Controlled substances: **None**
- User interaction: users can communicate with an **AI assistant**; there is **no** social network, no user-to-user chat
- Digital goods: **No** in-app purchases
- Unrestricted internet: **Yes** — optional in-app browser / search
- Location sharing: user can open Maps
- Not a kids app

Expected rating: **Everyone** or **Teen** (internet + AI chat often lands Teen). Either is fine. Do not select “Designed for children.”

### Government apps / Financial features / Health
No / No / No.

### Advertising ID
No. Do not use the advertising ID.

---

## After you paste all of this

Dashboard should show Store listing + Privacy policy + Data safety + Content rating as complete (or waiting for review). Then you can send the app for review.
