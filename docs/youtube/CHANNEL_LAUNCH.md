# App-building channel — YouTube launch

Updated 2026-09-13. The owner supplied the channel URL below after the creation instructions. Treat it as the selected destination; do not create a duplicate. Public-page verification was throttled, so its displayed name, handle, branding, playlists, and uploads remain unverified. The owner selected their Google account in the task; do not publish the login email as contact information without their instruction.

## Channel identity

- Channel URL (owner supplied): https://www.youtube.com/channel/UCjxT_IHbJ9OsysxHW1QxiqA
- Channel ID: `UCjxT_IHbJ9OsysxHW1QxiqA`

- Current working name: **Meow Assistant**; broader channel name remains undecided. Do not rename the live channel without the owner choosing a name.
- Earlier handle proposal: **@MeowAssistantApp** (availability unverified; revisit after choosing broader branding)
- Alternatives: @MeetMeowAssistant, @MeowAssistantAndroid
- Banner copy: **Small ideas. Real apps. Build with us.**
- Supporting line: **App ideas • Coding • Running Meow server • Real fixes**
- Profile image candidate: existing `docs/store/icon-512.png`; inspect its current branding before upload.
- Meow Assistant is one featured project, alongside all our other current and future apps. Memba and Meowmba are older names viewers may encounter in Meow-specific videos.

## Ready-to-paste channel description

We turn simple ideas into real apps—and share how we do it.

Follow along as we build useful tools, try new app ideas, work with AI coding assistants, and troubleshoot what breaks. Some videos start with a tiny everyday problem. Others explore a bigger project, including Meow Assistant and the other apps we’re making.

We also take you behind the scenes of Meow, our server: how we run it, manage our projects and services, deploy updates, and investigate problems.

Expect a mix of ideas, step-by-step builds, working demos, server operations, and honest lessons from the code. See what works, what needs another try, and what we learn along the way.

## Channel organization

1. Simple App Ideas — one everyday problem, a small app concept, and the first useful version.
2. Build an App with Us — idea → design → implementation → testing → working demo.
3. Apps We’re Making — demonstrations and progress across all current and future projects.
4. Debugging Diaries — one real symptom, investigation, fix, and verification per episode.
5. Building with AI — writing tasks, reviewing suggestions, coordinating changes, and handing work down.
6. Feature Guides — individual tutorials organized into additional per-app playlists as needed; retain Meow’s backlog.
7. Running Meow Server — server tours, services, deployments, resource management, maintenance, and troubleshooting.
8. Experiments — prototypes, research, measurements, and ideas that may or may not become apps.

The channel follows both building our apps and operating the server behind our work. Distinguish Meow the server/main AI from the Meow Assistant Android app in titles and introductions. Meow is a recurring project, not the boundary of the channel. Keep episodes understandable without requiring viewers to watch earlier installments. Use a project name in each title where it helps distinguish apps.

## Mixed initial release order

1. “Small Ideas, Real Apps: What We’re Building” — channel introduction.
2. “A One-Button Idea Notebook: Designing the First Version” — proposed new app concept.
3. “Meet Meow Assistant: What It Can Do” — existing-project demo.
4. “From Idea to Working Notebook” — build episode, only after actually implementing the concept.
5. “Meow Said It Did the Task. Did It Actually?” — documented debugging story.
6. “Three Tiny Apps for Everyday Problems” — clearly labeled ideas, not finished products.
7. A verified demo from another existing app — select after inventorying its repository and working build.
8. “Meet Meow: The Server Behind Our Apps” — a verified overview of its actual roles and services.
9. “How We Check AI-Written Code Before Keeping It” — a real reviewed change.
10. “How We Check That Meow’s Services Are Working” — an actual service and application health walkthrough.

Rotate ideas, builds, app demos, server operations, and troubleshooting instead of finishing every Meow tutorial before featuring another project. Try not to publish more than two consecutive episodes about the same app. These are editorial choices, not scheduled automations or claims that proposed apps have been built.

Aim for 2–4 minutes for concepts and demos, 5–10 minutes for focused builds or troubleshooting. Split larger builds at a working milestone. See `MIXED_APP_SERIES.md` for the idea backlog and episode template; `BUILD_AND_DEBUG_VIDEOS.md` retains the source-backed engineering stories. `SERVER_MEOW_SERIES.md` defines the server operations series.

## Setup once browser access works

Sign into the owner-selected Google account and select channel `UCjxT_IHbJ9OsysxHW1QxiqA`. Verify its displayed name and handle. Do not create another channel or rename an unrelated channel. Apply the description, icon, and banner, then create the playlist structure as videos become available. Add only verified public app/download links; the older Play listing is explicitly a historical draft and does not establish public availability.

YouTube documents channel creation under Settings → Add or manage channel(s), with a separate option for a business name: https://support.google.com/youtube/answer/1646861?hl=en
Profile editing is in YouTube Studio → Customization → Profile: https://support.google.com/youtube/answer/2657964?hl=en

## Recording and publishing workflow

For every episode, record the installed version, phone, chosen model/provider, prerequisites, exact steps, actual output, and one limitation. Source code establishes a candidate feature; it does not prove that the installed release can demonstrate it. Capture a successful end-to-end run before calling an episode ready.

Use real screen recordings with readable captions and narration. Start with the result, show the setup, repeat the action at normal speed, then show how to undo or stop it where relevant. Keep deliberate waiting visible or label a time cut. Show test content and hide credentials, private memories, account details, and notifications. Preserve meaningful errors instead of editing a failed action into apparent success.

Capture portrait footage for phone tutorials; place it in a landscape edit with a close-up of the relevant control when needed. Extract the strongest 20–40 seconds for a short. Thumbnail: real app image, one action, and 2–4 words such as “MEOW REMEMBERS” or “MAKE IT YOURS.”

Upload description template:

> In this video, we [build/use/debug/manage] [app, tool, or server service] to [outcome].
> Version/revision: [recorded version]. Environment: [device or server setup].
> Requires: [model/service/hardware, or none].
> Steps: [actual chapter timestamps after editing].
> Known limits: [observed limits].
> Project or guide: [verified public URL; omit until available].
> Next tutorial: [published video URL].

Track each episode as candidate → device verified → recorded → edited → uploaded → public. Do not fill in fake timestamps, links, metrics, or completion states.
