# Meow Assistant media gallery - publication receipt

Owner request: add more screenshots and video clips to the existing webpage.
Published September 20, 2026 through the existing Weebly site/editor.

Live section: https://www.dekayrecords.com/meow-assistant.html#mx-gallery

## Added media

Eight images and two silent video clips were uploaded to the existing theme assets:

- Build 152 signed emulator screenshot: rabbit companion with MIC OFF and Camera off.
- Build 141 phone screenshot: avatar controls, idle changes and four setup tabs.
- Build 133 native setup-screen preview: provider selection without credentials.
- Five build-148 native landscape background previews retained in 152: Saffron dunes,
  Alpine cableway, Coral twilight, Lantern courtyard and Crystal cavern.
- Build 50 Lumen Cat animation demo, 29.07 seconds.
- Build 55 natural companion-motion demo, 30 seconds, with scripted attention and forms.

Captions identify screenshots versus renderer previews and their actual build numbers.
The clips are explicitly earlier animation demos, not build-152 recordings or live
camera-tracking evidence. Local source images and sampled frames across both clips
were inspected before selection. No credentials, chats or personal documents appear.

Image pixels are unchanged. Videos were remuxed with stream copy and fast-start metadata;
no re-encoding, invented frames, audio or generated imagery was added. Total uploaded
payload is 4,254,511 bytes. Source paths, sizes, hashes and published URLs are in
`media-manifest-20260920.json`.

## Layout and playback

The new “Take a closer look.” section sits after the build-152 update and before the
earlier feature section. Desktop layout uses three image columns, two scenery columns
and two video columns. At widths below 760px these stack in one column. Images are
lazy-loaded, preserve their proportions and link to full-size assets. Captions, alt
text and visible keyboard focus are provided. Videos use native controls, playsinline
and preload=none; no autoplay is enabled.

Weebly confirmed “Website Published!”. The public page was reloaded, and all eight
image links and both clip destinations appeared. The screenshots, scenery and video
posters rendered. Both new videos played through to their ends (29.07 and 30 seconds).
Desktop and a 400px responsive viewport were inspected for wrapping, stacking and
readable controls. Developer tools/device emulation were closed afterward.

The existing release information, capability caveats, older media, beta links and
other website pages remain. No app build, Play change, account/plan change or public
Android source push was performed in this pass.

## Maintenance source and archive

`meow-assistant-embed-20260920.html` is the complete current custom HTML block, including
the original loader, background fixes, prior feature sections and the new gallery.
It was recovered from an HTML-only browser save of the published page. Its 20,885
characters match the submitted embed fingerprint (FNV-1a over UTF-16 code units:
61b7263c). This avoids the native accessibility textarea's 10,000-character truncation
when editing the block again. Do not replace the full block with a truncated AX value.

Full public page HTML and receipts:
`/Users/prog/Meowmba-Releases/website-media-20260920-receipts/`.
Upload-ready media:
`/Users/prog/Meowmba-Releases/website-media-20260920/`.

Editing route: Theme > Edit HTML / CSS > Assets + > Upload File(s), then Save.
The native file chooser accepted multiple files via Shift+Down selection; Cmd+A did
not select this file list. Then Pages > Meow Assistant > Build > existing Custom HTML
block > Edit Custom HTML. Preserve the complete current embed, save, Publish and
verify the public page. Asset paths are `/files/theme/<filename>`.
