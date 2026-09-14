# Dekay Records — site, channels, and GitHub integration

Owner direction, 2026-09-13: connect the music channel and the apps/coding/Meow server/AIOS channel to the existing Dekayrecords.com site and GitHub. The owner confirmed Weebly as the existing website platform. Extend the existing Weebly site once its editor is accessible; do not create a replacement website or change hosting based on this plan alone.

## Roles

| Destination | Purpose | Connects to |
|---|---|---|
| Dekayrecords.com | Central home for the music and technology work | Both channels, releases, project pages, guides, verified GitHub repositories |
| Music YouTube channel | Original releases and how we make the music | Song pages, credits/listening links, related studio stories |
| Technology YouTube channel | App ideas, builds, coding, debugging, Meow server, upcoming AIOS | Project pages, written guides, exact relevant code/release when public |
| GitHub | Available source, technical documentation, releases, and issue discussions | Corresponding site project page and relevant tutorials |

Visitors should be able to watch a demo, understand the project on the site, and reach usable code or a release if one exists. A music listener should reach the song and its story without needing to navigate engineering material.

## Proposed site sections

These are proposed page paths, not verified existing URLs. Inspect and preserve current navigation and music catalog before implementing or changing routes.

- Home: short introduction and two clear starting points—Listen and Explore Our Projects—with latest music and latest build story.
- Music (`/music`): releases, artists, performances, and studio stories; connect the music channel.
- Projects (`/projects`): all current and future apps/tools, labeled idea, prototype, working milestone, or released according to evidence.
- Meow server (`/projects/meow-server`): public overview, operations videos, and sanitized guides; no private control interface.
- AIOS (`/projects/aios`): upcoming purpose and bring-up journal; no invented capabilities or architecture.
- Watch (`/watch`): both channels, with Music and Building tabs or equivalent navigation.
- Journal (`/journal`): written build logs, troubleshooting walkthroughs, and music-making stories attached to their projects/releases.
- About: how the music, tools, and server work connect under Dekay Records.

## Connect each piece of content

Each project page: purpose, honest status, working demonstration when available, tutorials, current verified release/download, public repository, and help/issue link when supported.

Each music release page: artist/title, artwork, playable published media, credits, verified listening links, and making-of story. GitHub links belong only when there is relevant public technical material; do not force source-code links onto every song.

Each episode page: title, summary, video, related project or song, written steps or notes, and the version/revision demonstrated. Use a permalink to code for historical debugging stories rather than implying the latest branch still matches the video.

Each YouTube description: link to the corresponding published site page, then the relevant repository or listening destination. Add the site to both channel profiles once access works. Do not publish proposed page paths until those pages exist.

Each suitable repository README: explain purpose/status/setup, then link to its site page and tutorial. Do not promise source availability merely because a repository exists.

## Shared content record

Weebly is the website CMS. Maintain published pages there and keep the editorial link/metadata record in version-controlled planning files until a different workflow is explicitly chosen. No website Git repository is required for this manual first version. Fields: stable content ID, type (song/project/episode/guide), title, summary, status, site path, related IDs, channel/video URL, repository URL, demonstrated revision/version, artwork reference, publication date, and verified external links. Unknown values remain empty; unpublished records must not appear as live releases.

Use the same IDs to connect a song to its making-of video and a tool to its build/debug episodes. The first version can be maintained manually; add synchronization only after the website stack and publishing workflow are known. Do not assume GitHub hosts the site or that committing locally deploys it.

## Verified discovery and limits

- Site URL supplied by owner: https://dekayrecords.com. Direct inspection failed with a web-tool safe-open error; this does not establish downtime, a certificate problem, or its hosting provider.
- Public search found a Dekay Records Bandcamp page linking the domain: https://dekayrecords.bandcamp.com/. This is a candidate existing music destination, not proof of current account control or permission to republish its catalog.
- GitHub search through the connected account found these public repositories on 2026-09-13:
  - https://github.com/dekayrekords-oss/Meowmba — root contains LICENSE, README.md, and docs; do not equate it with the full local Android checkout.
  - https://github.com/dekayrekords-oss/Meow-Kernel-1 — root contains LICENSE only in the inspected listing; do not label it as working AIOS code.
  - https://github.com/dekayrekords-oss/Master-Plan — root includes Python app files, core, docs, tests, and planning files. Its name does not establish it as the website repository or the editorial planning destination.
- Technology channel supplied by owner: https://www.youtube.com/channel/UCjxT_IHbJ9OsysxHW1QxiqA.
- Owner confirmed the website is managed in Weebly. The music channel URL, current Weebly page structure, editor access, and publishing workflow remain unverified.
- Current local Git origin is a server checkout, not GitHub. The planning commits in this task are local; no GitHub push, site deployment, or channel edit is claimed.

## Weebly implementation sequence

1. Open the existing Dekayrecords.com site in the owner’s Weebly account. Inspect its current pages, branding, navigation, and publishing controls.
2. Map the proposed sections onto existing Weebly pages, retaining the current design and music catalog. Add Music, Projects, Watch, and Journal navigation only where equivalent pages do not already exist.
3. Add both verified channel destinations and relevant public repository links. Keep music-channel links unset until known.
4. Add the first complete song/project/episode records using real available media; keep AIOS marked upcoming.
5. Preview and check navigation, media embeds, mobile readability, and all outbound destinations.
6. Publish through the existing Weebly workflow and verify live pages. Then update channel descriptions and appropriate repository READMEs to point back to those actual pages.

No website code, remote README, access setting, DNS record, or channel profile was changed by this planning update.

## First Weebly editing pass

Prepare a homepage introduction connecting music and technology, two clear destinations (Music and Projects), a Watch section for both channels, and relevant GitHub links on project pages. Use the existing editor’s supported video/link components after inspecting them. Do not assume a GitHub-to-Weebly deployment integration or change DNS/hosting. The music-channel destination remains empty until supplied. This task has not entered the Weebly editor or published any changes.

## Live-site inspection update

The www homepage and signed-in dashboard became readable. Existing navigation includes music artist pages, Store, Blog, and Meow Assistant. Reuse Blog for written stories and retain the music catalog. WEEBLY_FIRST_EDIT.md provides the observed page map and concrete draft copy for Home, Projects, and Watch. The editor opened from the dashboard Edit control but rendered blank; no website edits have been made. Earlier site-inspection failures are historical, not current evidence that the public homepage is unavailable.
