# Foldable cover avatar

In **Settings → Phone & device**, enable **Cover-screen avatar**. The switch
defaults off and is saved across restarts. **Set up cover-screen avatar** opens
manufacturer-aware instructions. It controls the in-app cover layout, not the
manufacturer's lock/home screen, automatic fold launch, microphone, camera,
brightness or always-on display.

On supported Galaxy Z Flip phones, enable Meow in Samsung Good Lock →
MultiStar → I love Galaxy Foldable → Launcher widget / Enable Launcher.
Add the launcher to the cover screen, then launch Meow from it. Labels vary
by One UI version. Complete Meow onboarding on the main display first.

When enabled, Meow uses a compact avatar surface on Samsung SM-F7xx display 1
with a window up to 600 dp in each dimension. The physical Flip 6 reports
FLAG_PRESENTATION on this display; the explicit family/display check handles it
without treating every connected monitor as a cover. Other manufacturers use
a conservative compact secondary-touch-display check excluding presentation
displays. Those devices are best-effort, not physically verified. Tall cover
screens retain the standard avatar-first layout. The existing avatar renderer,
motion inputs, selected appearance, privacy state and shared approval overlays
are retained. Replies scroll below the avatar. This does not replace Samsung's
lock screen, bypass its unlock rules, or automatically launch an app on fold.

Reference: https://www.samsung.com/sg/support/mobile-devices/explore-new-cover-screen-widgets-on-the-galaxy-z-flip5/

Pending acceptance: actual Z Flip cover launch, camera cutout insets, fold/unfold,
microphone permission, approval visibility, and screen-off animation lifecycle.
