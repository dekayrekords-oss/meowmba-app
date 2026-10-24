# Computer P0 — Fold E2E checklist

Preview maturity: same idea as a cloud computer agent. Flags stay default **OFF**.

## Before you start

- [ ] Fold on NetBird (or same path that reaches `meow`)
- [ ] Stock Termux available (F-Droid / GitHub) — no FlyWire / custom proot forks
- [ ] Desktop Bridge (if used) still owns UDP **8766**; Computer worker is **18766**

## Path A — phone Termux Computer (primary)

1. [ ] Termux On — install + `pkg update && pkg install proot proot-distro openssh tmux`
2. [ ] Optional: OpenCode in Terminal (`Settings` / Terminal OpenCode bootstrap)
3. [ ] Meowmba → Terminal → Computer (power On)
4. [ ] Agentic flag ON only for this test (`Settings → Neural / Lumen`)
5. [ ] Chat: `Run on Computer: uname -a` → truncated output in chat (no secrets)
6. [ ] Cancel mid-run if a long goal is ticking → stops further ticks

## Path B — SSH to meow (day-to-day remote)

1. [ ] SSH meow from Fold (Termux or app SSH profile) — host / user / credential ref
2. [ ] Settings → Your Computer → Save SSH profile → **Test** → Connected
3. [ ] **Install Computer** → streams `install.sh` (workspace + worker on **18766**)
4. [ ] On meow: `curl -s http://127.0.0.1:18766/health` → `{"ok":true,...}`
5. [ ] Agentic ON → create goal / chat shell → `computer_exec` and/or `ssh_exec`
6. [ ] Destructive argv → WAITING_APPROVAL → Approve in Agentic tasks
7. [ ] Chat shows truncated stdout; credential material never appears

## Path C — HTTPS_RUNTIME (advanced / optional)

Only if you need the HTTP worker from the phone:

1. [ ] Either bind worker: `bash computer/install.sh --bind <NetBird-IP>`
2. [ ] Or forward: `ssh -L 18766:127.0.0.1:18766 user@meow`
3. [ ] Profile type HTTPS runtime; Host = full URL e.g. `http://100.77.97.236:18766`
4. [ ] **Test** → health OK (or clear reachability error if still on 127.0.0.1)

## Do not

- [ ] Do not punch raw shell into Desktop Bridge
- [ ] Do not drive Computer from the Lumen frame loop
- [ ] Do not leave secrets in events / chat / install logs
