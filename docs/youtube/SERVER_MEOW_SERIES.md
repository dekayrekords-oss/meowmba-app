# Running Meow Server

Owner addition, 2026-09-13: cover Meow the server and how we run and manage it, alongside all our app projects. This is a recurring channel series, not a separate channel. Meow server/main AI and the Android Meow Assistant app must be identified clearly for viewers.

## Episode candidates

| Episode | Viewer takeaway | Evidence needed before recording |
|---|---|---|
| Meet Meow: The Server Behind Our Apps | Understand what the machine does and which projects rely on it | Current hardware, actual service inventory, and a simple verified architecture diagram |
| How We Start and Check Meow’s Services | Distinguish a running process from a working application | Actual service manager, service status, and one successful application request; do not assume systemd or containers |
| How Our Apps Talk to Meow | Follow one request from app to server and back | Verified endpoint role, client configuration, and a harmless successful request |
| How We Organize Projects on Meow | Understand source checkouts, build workspaces, and shared work | Current repository layout and ownership; AGENTS.md's canonical-remote history is a starting reference |
| From Code Change to Running Update | Follow revision selection, build, deployment, and verification | Current release process and a verified change; historical references include docs/store release handoffs |
| How We Monitor Meow’s Workload | Read CPU, memory, disk, and GPU usage where applicable | Actual hardware and observed measurements under a named workload; no assumed GPU or fabricated benchmarks |
| A Service Stops Responding: Where We Look First | Use symptoms, status, logs, and requests to narrow the cause | A real incident or clearly labeled isolated reproduction; no disruption of production for footage |
| How We Back Up—and Test Recovery | Understand the difference between having a copy and restoring successfully | Verify the backup setup first; demonstrate recovery to an isolated destination before claiming it works |
| How We Manage Access to Meow | Understand who can connect and what each role needs | Current access design, explained using sanitized examples rather than private keys or account details |
| How We Update Meow and Recover from Problems | Understand prechecks, update verification, and recovery options | Actual maintenance process and demonstrated recovery path; proposals must be labeled as proposals |
| How We Pass Work to Meow’s Main AI | See how instructions, Git changes, and pass-downs keep work understandable | A real non-private task and a verified handoff; writing a local file alone does not prove server receipt |

These topics are candidates, not claims that every listed practice is already configured. This planning task has not connected to, audited, reconfigured, or restarted the server.

## Recording format

Start with the practical question: “Is this service working?” or “How does this update reach the app?” Explain the relevant part of the setup, show a small readable command or dashboard, interpret the actual output, and finish by checking the user-visible result. Define unfamiliar terms when they first appear. A service-management video should identify the service being demonstrated and its role before showing commands.

For each episode, retain a private production note with the date, environment, revision/configuration context, observed result, footage location, and unresolved limits. Prepare public examples that omit credentials, private task content, and unnecessary connection details. Do not present a historical configuration as the current server setup.

## Rotation with app content

Example sequence: simple app idea → build a small feature → tour Meow server → demo another app → solve a real bug → explain deploying an update. Keep server episodes focused enough that viewers can follow them without watching the entire project history.
