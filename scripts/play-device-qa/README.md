# Disposable Play release QA

This SDK-only instrumentation checks the actual minified release APK without changing production R8 rules or packaging test libraries in the app. AndroidX release instrumentation could not start because R8 removes shared Kotlin/AndroidX facade classes that its runner expects. The memory dialog's AndroidX UI test runs against debug separately.

Use a new disposable Android emulator, with no accounts or personal data. The runner rejects physical-device serials and checks the emulator property; the instrumentation checks its hardware. Never use this harness to test with real keys, messages, location history or files.

1. Create QA copies of the reviewed beta152 and final191 APKs signed with the same existing **debug** keystore. These are emulator-only copies and must never be uploaded to Play or installed on a user's phone.
2. Install QA152. On the empty emulator, grant its old `READ_MEDIA_IMAGES` and `READ_MEDIA_VIDEO` permissions to simulate an existing tester. Run `seed`: it creates only synthetic preference/file/SQL fixtures and a real memory row in the old database.
3. Update in place to QA191 with `adb -s emulator-… install -r …`. Run `picker`.
4. Inspect the saved result and images. Pull the target's external-files `play191-qa` folder into the local validation directory.

Example harness invocation, using explicit local paths:

```sh
python3 scripts/play-device-qa/run.py \
  --serial emulator-5554 \
  --sdk /path/to/android-sdk \
  --java-home /path/to/jdk \
  --qa-keystore /path/to/existing-debug.keystore \
  --work-dir /path/to/local-validation \
  --phase picker
```

`seed` requires152. `verify` checks191, the real Room10-to15 upgrade, retained fixtures/memory, removed media permissions and excluded Accessibility service. `inspect-files` also follows the optional first-run path and opens the menu. `picker` navigates Menu → Files, creates one synthetic gallery image, chooses it with Android's picker, checks a newly imported copy byte for byte, and checks that the original is unchanged. It deletes only its own gallery image. Android may give the imported selection an opaque filename, so the test matches new bytes rather than assuming the old name.

The harness does not download models, access the upload key or publish anything. Generated APKs, images and logs belong outside the repository. Signing, all active-track replacements, Play declarations, Google review and public availability remain separate checks.
