# Weebly publication — 2026-09-13

## Live result

Published through the existing Weebly editor; the UI confirmed “Website Published!”.

- https://www.dekayrecords.com/ — new introductory block before the existing news/music content, linking Bandcamp, Projects, and Watch.
- https://www.dekayrecords.com/projects.html — new standard page covering all app/tool work, Meow Assistant, Meow server, and upcoming AIOS. Links to the existing Meow page, owner-supplied technology channel, and public GitHub account.
- https://www.dekayrecords.com/watch.html — new standard page linking the technology channel, Projects, existing Bandcamp catalog, and Blog. Music-channel videos described as in preparation; music YouTube URL is still missing.

Both new pages are visible in site navigation and use No Header with headings inside their content. The Projects page has a short native-text introduction followed by a custom HTML block. Watch uses one custom HTML block. Home gained a custom HTML block above the existing news link. Existing artist/music/store/blog content was not intentionally edited.

## Verification

Opened the published homepage, followed its Projects link, and inspected Watch. Live DOM contained the intended copy, headings, and exact link targets. Screenshots of Projects and Watch at the existing 590px browser width showed readable text and wrapping without the editor's horizontal clipping. Existing music embeds were still present in the homepage DOM. No broad desktop/device matrix or playback testing is claimed.

The publisher also displayed an existing-plan notice about Real Time Shipping requiring an upgrade. No plan, payment, shipping, or store setting was changed. This notice did not prevent the Website Published confirmation or live page verification.

## How to maintain the blocks

1. Open the existing Weebly site and select the page under Pages.
2. Click the custom HTML block, then Edit Custom HTML.
3. Replace the intended fragment through the displayed source textarea, leave editing to save, and inspect the rendered result.
4. Publish and verify the corresponding public URL.

A native rich-text block was initially tried on Projects. HTML paste was treated as literal text, and multiline text collapsed on blur. The final implementation uses Weebly's supported Embed Code element with explicit paragraphs and links. The temporary text was replaced with the single sentence “Apps, tools, and experiments from Dekay Records.” No literal HTML or collapsed feature descriptions remained in the published Projects DOM.

Future agents: do not assume a tool action completed the save. Allow the page to settle, then read the source textarea or rendered DOM. The custom HTML source textarea's setValue path worked reliably. Reopening the editor directly after the owner signed in resolved the earlier blank-editor problem; no credential or browser-security changes were needed.

## Next

Obtain the music channel URL and add it to Watch. Add finished videos and app-specific releases as they exist. Channel profile backlinks and GitHub README backlinks remain separate pending work; no remote GitHub or YouTube changes occurred during this publication. These local Git records are not a GitHub push.
