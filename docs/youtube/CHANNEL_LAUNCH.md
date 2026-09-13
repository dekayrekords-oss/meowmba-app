# Meow Assistant — YouTube launch

Updated 2026-09-13. The owner supplied the channel URL below after the creation instructions. Treat it as the selected destination; do not create a duplicate. Public-page verification was throttled, so its displayed name, handle, branding, playlists, and uploads remain unverified. The owner selected their Google account in the task; do not publish the login email as contact information without their instruction.

## Channel identity

- Channel URL (owner supplied): https://www.youtube.com/channel/UCjxT_IHbJ9OsysxHW1QxiqA
- Channel ID: `UCjxT_IHbJ9OsysxHW1QxiqA`

- Name: **Meow Assistant**
- Preferred handle: **@MeowAssistantApp** (availability unverified)
- Alternatives: @MeetMeowAssistant, @MeowAssistantAndroid
- Banner copy: **Building Meow. Sharing what we learn.**
- Supporting line: **Tools we build • Coding walkthroughs • Real bug fixes**
- Profile image candidate: existing `docs/store/icon-512.png`; inspect its current branding before upload.
- Public-facing name is Meow Assistant. Memba and Meowmba are names viewers may encounter in older builds; explain this once in the setup video.

## Ready-to-paste channel description

Follow the making of Meow Assistant and the tools we build along the way.

We share app demos, coding walkthroughs, and real troubleshooting: what we wanted to build, what went wrong, how we investigated it, and what finally worked.

Explore Meow’s features, see how we work with AI coding assistants, and learn from the decisions, tests, and fixes behind each update. We show working results and explain what is still experimental.

Whether you want to use our tools or understand how we make them, start here.

## Channel organization

1. Start Here — channel introduction, Meow overview, first setup.
2. Tools We Build — each app, utility, or integration gets a practical demo showing the problem it solves.
3. Coding Meow — feature implementation, design decisions, code walkthroughs, and tests.
4. Debugging Diaries — one real symptom, investigation, fix, and verification per episode.
5. Building with AI — how we write tasks, inspect suggestions, coordinate changes, review diffs, and hand work down to Meow.
6. Meow Feature Guides — retain all individual feature tutorials in `FEATURE_VIDEOS.md`.
7. Meow Lab — experimental behavior, research, measurements, and unresolved work.

These three central topics—tools, coding, and troubleshooting—share the channel alongside individual feature guides. Aim for 2–4 minutes for feature demos and 5–10 minutes for a focused coding or debugging story. These are editorial targets, not platform requirements. Other tools belong here when we can demonstrate what we actually built; inventory their repositories before promising episodes about them.

## Initial release order

1. “We’re Building Meow Assistant—and Sharing the Process” — revised trailer.
2. “Meet Meow Assistant: What It Can Do” — real app demo.
3. “Meow Said It Did the Task. Did It Actually?” — documented action-verification bug.
4. “How We Give Meow Its Own Animations” — one feature from request to working result.
5. “The Email Draft Bug: Why the Body Was Missing” — investigation and measured fix.
6. “How We Hand Coding Work Between AI Assistants” — scoped changes, Git, review, and pass-downs.

Then rotate one tool/feature demo, one coding walkthrough, and one troubleshooting story. Publish when each episode has real footage and verified evidence; no posting automation is configured.

See `BUILD_AND_DEBUG_VIDEOS.md` for source-backed episode briefs and a reusable recording format.

## Setup once browser access works

Sign into the owner-selected Google account and select channel `UCjxT_IHbJ9OsysxHW1QxiqA`. Verify its displayed name and handle. Do not create another channel or rename an unrelated channel. Apply the description, icon, and banner, then create the playlist structure as videos become available. Add only verified public app/download links; the older Play listing is explicitly a historical draft and does not establish public availability.

YouTube documents channel creation under Settings → Add or manage channel(s), with a separate option for a business name: https://support.google.com/youtube/answer/1646861?hl=en
Profile editing is in YouTube Studio → Customization → Profile: https://support.google.com/youtube/answer/2657964?hl=en

## Recording and publishing workflow

For every episode, record the installed version, phone, chosen model/provider, prerequisites, exact steps, actual output, and one limitation. Source code establishes a candidate feature; it does not prove that the installed release can demonstrate it. Capture a successful end-to-end run before calling an episode ready.

Use real screen recordings with readable captions and narration. Start with the result, show the setup, repeat the action at normal speed, then show how to undo or stop it where relevant. Keep deliberate waiting visible or label a time cut. Show test content and hide credentials, private memories, account details, and notifications. Preserve meaningful errors instead of editing a failed action into apparent success.

Capture portrait footage for phone tutorials; place it in a landscape edit with a close-up of the relevant control when needed. Extract the strongest 20–40 seconds for a short. Thumbnail: real app image, one action, and 2–4 words such as “MEOW REMEMBERS” or “MAKE IT YOURS.”

Upload description template:

> In this video, we use [feature] in Meow Assistant to [outcome].
> App version: [recorded version]. Device: [device].
> Requires: [model/service/hardware, or none].
> Steps: [actual chapter timestamps after editing].
> Known limits: [observed limits].
> Try Meow: [verified public URL; omit until available].
> Next tutorial: [published video URL].

Track each episode as candidate → device verified → recorded → edited → uploaded → public. Do not fill in fake timestamps, links, metrics, or completion states.
