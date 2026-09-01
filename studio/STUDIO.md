# Studio rules — READ BEFORE MAKING ANYTHING VISUAL

This folder is the Meowmba content studio: comics, videos, ads, posts.
The #1 rule that was being broken: **the agent claims it made something and
never shows it. That is a lie of the "faked tool result" kind this repo
already documents. Don't do it.**

## The show-your-work loop (MANDATORY)

Any work with a visual result — comic panel, game, video, ad creative,
post preview, landing page — is NOT DONE until the user has seen it:

```bash
# 1. Build it as an HTML page under studio/ (self-contained: inline CSS,
#    no CDN unless approved)
# 2. Start Chrome with remote debugging:
~/.claude/skills/browser-tools/browser-start.js
# 3. Open the file:
~/.claude/skills/browser-tools/browser-nav.js "file:///Users/bobrossjr/meowmba-app/studio/<path>"
# 4. LOOK at it yourself (proofreading pass):
~/.claude/skills/browser-tools/browser-screenshot.js
```

- Step 4 is not optional: screenshot = proofread. Fix what you see
  (overflow, broken art, typos), re-screenshot, THEN tell the user it's on
  screen.
- If Chrome can't start, say so honestly ("browser failed, here's the file
  path to open") — never say "done" without eyes on it.

## Character memory (comics AND cartoons share this)

- Every recurring character gets `characters/<name>.md`, copied from
  `_TEMPLATE.md`. Appearance is LOCKED there: hex colors, proportions,
  markings, outfit.
- Before drawing/writing any scene, READ the character sheet. Follow it
  exactly. No improvising colors or proportions.
- If a design legitimately evolves, update the sheet + change log FIRST,
  then draw. The sheet is the single source of truth so video/cartoon
  versions match comic versions.
- New characters start as a filled-in sheet before their first panel.

## Comics

- One folder per comic: `comics/<comic-name>/` containing `story.md`
  (script/outline) and `panels/` (one HTML page per panel or one page with
  all panels).
- Art style: self-contained HTML/CSS/SVG unless the user asks for generated
  images. Same character sheet regardless of medium.

## Videos & YouTube

- Scripts: `videos/scripts/<title>.md` — sections: HOOK (first 5s),
  SCRIPT (with timestamps), B-ROLL/visuals column, CTA.
- Ads: `videos/ads/<campaign>.md` — hook, variants (15s/30s), copy for
  description + pinned comment.
- Storyboards that need review go through the SAME show-your-work browser
  loop as comics.

## Reddit / social posts

- Draft first as a preview page (`posts/<slug>.html`) rendered like the
  platform, show it in the browser, get approval, then post. Never post
  unseen.
