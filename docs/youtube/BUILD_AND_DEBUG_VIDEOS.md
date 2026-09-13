# Tools, coding, and troubleshooting series

Added at the owner's request on 2026-09-13. These are episode candidates grounded in repository records. Historical fixes need their original diff and test evidence inspected before recording; nothing here claims fresh reproduction or newly completed repairs.

## First episode briefs

| Title | Story and footage | Evidence to inspect |
|---|---|---|
| Meow Said It Did the Task. Did It Actually? | Start with the reported false success; compare chat text with task audit evidence; explain the routing gap; show the documented fix and reproduce on a test build if available. | `AGENTS.md`, “The chat model can FAKE tool results”; referenced fixes and tests |
| How We Give Meow Its Own Animations | Show a performance, explain sparse keyframes and validation, walk through saving and replaying, then show a real test. | `AGENTS.md`, invented animations; `app/src/main/java/com/example/meowmba/core/agent/tools/AvatarAnimateTool.kt` |
| The Email Draft Bug: Why the Body Was Missing | Show the reported missing-body symptom; explain the measured Gmail intent behavior; walk through query encoding and regression coverage. Use dummy recipient/content; do not send a message for the demo. | `AGENTS.md`, email/app routing fix; `app/src/main/java/com/example/meowmba/core/agent/tools/EmailTools.kt` |
| Why a Fresh Build Couldn’t Hear Us | Explain ignored native voice assets, the missing-library error, and asset preparation; verify current instructions before demonstrating a build. | `AGENTS.md`, “A fresh worktree CANNOT hear” |
| A Week of Fixes Disappeared: Building the Wrong Revision | Diagram local checkout versus canonical remote; inspect historical refs; show how revision checks prevent stale releases. | `AGENTS.md`, sync state and fetch-before-build incident |
| Why a Tiny Animation Looked Like Nothing Happened | Explain the documented 60ms clip, minimum duration, and sparse follow-up context. Use a labeled reconstruction if original footage is absent. | `AGENTS.md`, context window and animation duration notes |
| How We Hand Coding Work Between AI Assistants | Walk through one actual task, bounded file ownership, a reviewed diff, a scoped commit, and a pass-down explaining what/how/why. | `AGENTS.md`; `docs/youtube/PASS_DOWN_2026-09-13.md`; actual task history chosen for recording |
| Making Meow Show the Comic It Created | Explain the difference between a chat claim and an artifact; follow the saved file into the visible preview. | `AGENTS.md`, Content studio; `app/src/main/java/com/example/meowmba/core/agent/tools/StudioTools.kt` |
| Shipping a Phone Update Without Sending the Whole APK | Walk through the documented verified delta transfer, why reused blocks help, and the final hash check. Verify the actual script before teaching commands. | `docs/characters/MEOW_BUILD_109_PASS_DOWN.md` |
| What We Learned When the Avatar Got Too Hot | Explain the failed endurance gate and what was measured; separate hypotheses from verified fixes. Do not invent an optimization success. | `docs/characters/MEOW_BUILD_109_PASS_DOWN.md` and its referenced build 108 handoff |

## Repeatable episode format

1. Show the tool's result or the bug's visible symptom in the first 15 seconds.
2. State the intended behavior in ordinary language.
3. Show the evidence: reproducible steps, relevant log, small code excerpt, or failing test.
4. Explain the decision or root cause and show the smallest useful diff.
5. Demonstrate the result and the test that distinguishes a real fix from an apparent one.
6. End with one lesson viewers can apply to their own work and any unresolved limits.

A development walkthrough can include unsuccessful approaches when they explain the final choice. Label historical reconstructions. Do not stage a fresh failure and imply it happened live, or present an untested proposal as a fix.

## Tools beyond Meow

Retain Meow Assistant as the channel name for now. The channel can cover utilities and companion projects we have built without renaming it. For each additional tool, first record its repository, actual purpose, working demo, setup requirements, and current status. References to a sibling app in this repository do not establish that its complete code or demo is available here.

## Capture during future coding work

Keep a short production note with: problem, starting revision, reproduction, investigation, changed files, test result, final revision, lesson, and footage locations. Use a clean demo workspace for screen recordings. Readable diffs and focused logs are more useful than an entire terminal session. Remove secrets and private user data before preparing public examples.

This is a content workflow, not authorization to run background recording or publish private task history automatically. No recordings, uploads, or new channel changes have been made.
