# Automated phone Computer setup (preview 140)

## User flow

1. Install the official Termux app and open it once.
2. Open Meow → Computer → This phone → Continue → Setup computer.
3. Approve Android's Termux command permission and Termux's external-app setting.
   These are security gates owned by Android/Termux, not permissions Meow can grant itself.
4. Enter a private connection password (12–256 characters). If a server is already
   running, use that server's password. This is not an Ubuntu login or a model API key.
5. Tap Setup computer. Keep the screen open while packages download. A ready message
   requires authenticated OpenCode health, not just successful command dispatch.

Ubuntu and OpenCode are reused if present. Missing components are installed from
Termux repositories and OpenCode's official installer. The worker binds only to
127.0.0.1:4096. Existing processes on that port are not killed. Projects are under
Ubuntu's ~/MeowProjects. This does not change any model provider or authorize spending.

## Boundaries and remaining work

- Stock Termux requires `allow-external-apps=true` in its private properties file.
  Official instructions are linked in the app. An install-and-password-only experience
  cannot be guaranteed with stock Termux; do not conceal the required opt-in.
- Credentials use encrypted app storage, including a separate resumable draft. They
  travel to the explicitly addressed Termux service via stdin, never shell arguments
  or a shared-storage script. Do not enable verbose Termux intent logging when using
  credentials; command logging is requested off for this background operation.
- Install/retry is serialized within the app and protected by a Termux file lock.
- Background worker lifetime on each OEM/Termux version still requires device testing.
  Termux/Android can stop the worker; tap Setup computer to restart/recheck it.
- Task submission, progress, permission replies and final assets are a separate
  integration. A healthy server alone does NOT make those features complete.
- No auto-approval flags. Provisioned OpenCode defaults to asking for tool permission.
  Existing per-agent/project configuration must be reviewed before actual task routing.

## Verification

Run PhoneComputerSetupContractTest (shell syntax, reuse and server safety contracts).
Real-device acceptance still needs fresh install, existing Ubuntu, wrong password,
external-app denial, killed activity, download interruption and restart scenarios.
Never uninstall Meow or clear its data to test setup.
