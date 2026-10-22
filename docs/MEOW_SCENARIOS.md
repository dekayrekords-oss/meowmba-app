# Meow scenarios — everything a companion who is always with you would notice

Owner request (2026-09-23): think of every scenario a user is in. Meow should remember everything
the user tells it, with full context, and always know what the user needs: location, driving,
surroundings, conversations, noise — everything a person with your phone, constantly with you,
could help with or remind you of.

This catalogue is the requirement for the context system (`docs/MEOW_CONTEXT_PLAN.md`).
Each scenario lists **how Meow knows** (signals), **what it keeps** (journal/memory), and
**how it helps** (an offer in Flow, a spoken line, a notification, or an answer when asked).
Signals in *italics* are not collected yet; §19 lists them with their cost.

Standing rules for every row: on the phone only (D8); offers, never silent actions (money,
messages, purchases always need a yes); the user can say "forget that" or pause noticing;
heat budget first (no new always-on sensor without a measured cost).

---

## 1. Daily rhythm

| Scenario | How Meow knows | What it keeps | How it helps |
|---|---|---|---|
| Waking up | First unlock after the night's longest screen-off at Home; alarm dismissed | Wake time per day (learns the pattern) | Morning line: weather that matters, first meeting, anything due today, who wrote overnight |
| Slept badly / woke early | Wake far off the usual; *sleep from watch (Health Connect)* | Sleep pattern | Softer day plan; no non-urgent interruptions before 9 |
| Morning routine at home | At Home, still/walking, before usual leave time | — | Holds errands for the drive; reminds of things to take ("gym bag — you said Tuesday") |
| Leaving home | Left Home geofence; car Bluetooth or IN_VEHICLE | Departure time | "Heading to work?" is inferred from weekday + time; checks things you said to bring |
| "I'm going to work now" | User says it | Statement + time + place + activity | Trip to Work started; ETA; errands that fit the route surface at the right exit |
| Arriving at work | Entered Work place; *office Wi-Fi* | Arrival time (learns hours) | Work items move up; personal errands hold until leaving |
| Lunch | Midday, left Work briefly or still + food app | Where/when you eat | Errands near where you are now; reminders you parked for "lunch" |
| Afternoon slump / long sitting | Still > 2 h, work app use | — | Optional movement nudge (off by default) |
| Leaving work | Left Work place / computer closed / evening | Work end (learns) | Work wraps up; the evening flow starts: errands on the way home, people to answer |
| Arriving home | Entered Home | Arrival | Home items (packages, chores due), "you said you'd call mom tonight" |
| Evening | After work, at Home | — | Leisure and life items; no work unless urgent |
| Going to bed | Screen off at Home late; charger connected | Bedtime pattern | Tomorrow's first thing; alarm check if an early event exists |
| Weekend / day off | No work pattern, calendar says off | — | Life-first flow; nothing about work unless flagged urgent |
| Different day (holiday, sick) | User says "I'm sick", "day off"; unusual pattern | The statement | Pauses work items; clears the day's nudges; offers to tell people you're out (drafts only) |

## 2. Getting around

| Scenario | How Meow knows | What it keeps | How it helps |
|---|---|---|---|
| Driving | Activity IN_VEHICLE + speed; car Bluetooth; *Android Auto* | Trips (from, to, times) | Voice-only, short, safe; interruptions only at stops or on the right exit |
| Where am I driving to? | Time + weekday + usual next place; nav destination if shared | Usual routes | "On your way to Work" as a fact for Flow; wrong-way notice only if asked |
| Running late | Calendar event start vs ETA | — | "You'll be 10 min late to the 3:30 — tell Dana?" (draft, needs a yes) |
| Passing a store you need | Errand + place ahead on route (Road providers) | — | "The hardware store usually carries it, on your right" (never claims stock) |
| Parking | IN_VEHICLE → WALKING transition, last car location | Parking spot + time | "Where did I park?" answered with a map point; meter time if you said it |
| Fuel / charge low | *OBD fuel level / EV battery*; user says | — | Suggests a stop on the route |
| Car problem | OBD trouble code; user says "check engine light" | Code + date + mileage | Explains plainly, tracks it, reminds to book service |
| Maintenance due | Mileage (OBD/trips) + last service you told Meow | Service history | "Oil change is about due (last one 4,800 mi ago)" |
| Passenger / carpool | Car + another phone? — mostly user says | Who you drive with | Pick-up reminders, "leave now to get Sam by 5" |
| Crash or hard stop | DrivingDetector crash check | Event | Asks if you're OK; offers emergency call; never auto-calls without the configured rule |
| Rideshare / taxi | Rideshare app notifications (sender/app only) | Trip | ETA awareness; "your ride is here" is the app's job — Meow stays quiet |
| Transit / train / bus | IN_VEHICLE without car Bluetooth; transit app | — | Quiet mode on transit; stop reminders if you ask |
| Walking / running / cycling | Activity transitions; *steps* | Workouts | No reading-heavy offers; voice if headphones; tracks "walked 3 km" if asked |
| Flights & airports | Airport place; boarding pass email; calendar | Flight details you share | Check-in, gate/time changes (from airline notifications), terminal → ride |
| Somewhere new | Unknown place, far from usual | Visited place | Offers local basics (open now, safe to walk?) only if asked |
| Lost / no signal | No network; unfamiliar area | — | Keeps the last route and parking spot offline |

## 3. Work

| Scenario | How Meow knows | What it keeps | How it helps |
|---|---|---|---|
| Meeting soon | Calendar; with people | — | Prep line 10 min before: who, what you said about it last time, join link |
| In a meeting | Calendar now + *ringer silent / DND* + still | — | Silent. Holds everything except urgent; no spoken lines |
| After a meeting | Event ended | Follow-ups you mention ("I'll send the deck") | Captures promises as tasks with the person's name |
| Deep focus | One work app long; DND | — | Batches everything; one summary when you surface |
| Deadline | Calendar/task due; user says "due Friday" | Deadline | Countdown with realistic slots; nothing nags twice |
| Promised to someone | You say "I'll get back to you tomorrow" (chat/voice) | Commitment + person + due | Surfaces before the due time, with context of what you promised |
| Waiting on someone | You say "waiting on Dana for the numbers" | Open loop | Asks after a sensible time whether it arrived |
| Working on the computer | Computer link / usage on phone; project open | Project + last activity | "Continue Meowmba" at the right time; builds finished (from Computer) |
| Switching tasks | App changes | — | Remembers where you left off per project |
| End of day | Leaving work / computer closed | — | Wrap-up: what's done, what moves to tomorrow, promises due |
| Work travel | Calendar + airport + hotel | Trip | Combines travel + meetings |

## 4. People

| Scenario | How Meow knows | What it keeps | How it helps |
|---|---|---|---|
| Someone messaged | Notification (sender/app only, never text) | Waiting-on-you list | "Dana wrote in WhatsApp" at a good moment; clears when you reply |
| Missed call | *Call log / call state* | Who, when | "Call back Sam?" when you're free (not driving) |
| On a call | *Call state* | — | Silent; never talks over a call |
| You promised to call | "I'll call mom tonight" | Commitment | Evening at home: "You said you'd call your mom" |
| Birthdays / anniversaries | Contacts/calendar; you tell Meow | Dates | A day ahead + morning of; gift idea only if asked |
| Facts about people | You mention them ("Dana's son is Leo") | Person notes | Context before you see or message them |
| Who you're with | Calendar attendees; you say; *nearby known devices* | Encounters (when told) | "Last time with Sam you said you'd lend the ladder" |
| Family logistics | You say "pick up the kids at 3"; school calendar | Routine | Leave-by reminders with traffic |
| Group plans | Messages (sender), calendar invites | Plan | Reminds of plans; conflict warning |
| Too many messages | Notification volume | — | One summary instead of many pings |
| Unknown sender / spam | No contact, automated pattern | — | Ignored (never a "Reply to" item) |

## 5. Errands & shopping

| Scenario | How Meow knows | What it keeps | How it helps |
|---|---|---|---|
| Ran out of something | You say "we're out of milk", "the rear light is out" | Need + store type | Offers when passing a place that usually carries it |
| Shopping list | Things you mention over days | List by store | At the store: the list; nothing typed required |
| Package on the way | Carrier/shop notification (sender/app) | Expected delivery | "Package arriving today"; at Home: "It was delivered" |
| Return window | You say "return by Friday"; receipt email subject | Deadline | Reminds before it closes, when near the store/post office |
| Pickup (pharmacy, dry cleaning) | Notification or you say | Pickup | On the route |
| Library/book due, subscriptions | You say / notification | Due | Before due |
| Near a place you wanted to try | You said "want to try that taco place" | Wish list | Nearby + open + you're free |

## 6. Home

| Scenario | How Meow knows | What it keeps | How it helps |
|---|---|---|---|
| Trash / recycling day | You tell once | Routine | Night before, at Home |
| Chores & maintenance | You mention ("filter every 3 months") | Last done | When due, at Home, not late at night |
| Leaving home checks | Leaving Home | Things you asked to be reminded about | "Stove? Windows?" only if you asked for it |
| Where things are | You say "keys are in the blue drawer" | Object → place | "Where are my keys?" answered |
| Guests coming | Calendar / you say | — | Prep list you gave |
| Pets & plants | You say "fed the cat" / "water plants Sunday" | Care log | "Did anyone feed the cat?" answered from the log |
| Bills at home | Bank/biller notifications (sender) | Due dates | See Money |
| Smart home | Connected devices (future) | — | Lights off when you leave, only if you set it up |

## 7. Money (never moves money)

| Scenario | How Meow knows | What it keeps | How it helps |
|---|---|---|---|
| Bill due | Biller/bank notification sender; you say | Due + amount if you said | "Card payment $412 due tomorrow" |
| Unusual charge | Bank alert notification (category) | — | Flags it for you to check in the bank app |
| Subscription renewing | Email/notification sender; you say | Renewal date | Before it renews |
| Payday / budget | You tell Meow | — | Only what you asked to track |
| Refund pending | You say | Open loop | Asks after the expected date |

## 8. Health & wellbeing (not medical advice)

| Scenario | How Meow knows | What it keeps | How it helps |
|---|---|---|---|
| Medication | You tell the schedule | Taken / not (when you say) | Reminder at time, in context (not while driving) |
| Symptoms you mention | "My back hurts" | Symptom + date | Asks later how it is; remembers for a doctor visit |
| Doctor appointments | Calendar | Questions you want to ask | Before the visit: your list |
| Sleep | Screen-off pattern; *watch sleep* | Pattern | Only if you ask, or a gentle note after many short nights |
| Movement | *Steps*, activity | Daily totals | Optional nudges |
| Water / breaks | Time + still | — | Optional, off by default |
| Heat / weather risk | Weather + outdoors + walking | — | "It's 108°, take water" when you're heading out |
| Loud places | Mic level when already listening | Exposure | Switches to text; notes long loud exposure |
| Stress / late nights | Late phone use pattern | — | Only if you've asked for it |
| Emergency | Crash, fall (*watch*), you say "help" | Event | Emergency contact flow you configured |

## 9. Food

| Scenario | How Meow knows | What it keeps | How it helps |
|---|---|---|---|
| Hungry / mealtime | Time + place + you say | Preferences ("no mushrooms") | Options near you, open now |
| Cooking | You ask | Recipes you liked | Timers, steps hands-free |
| Grocery | Needs you mention | List | At the store |
| What did I eat | Only if you tell it | Food log | Answers |

## 10. Projects, ideas & learning

| Scenario | How Meow knows | What it keeps | How it helps |
|---|---|---|---|
| Idea on the go | You say "idea: …" | Idea + where/when | Filed by project; brought back when you open it |
| Personal project | Projects in Meow; Computer link | Progress | "Continue X" when you have time |
| Reading / course | You say | Progress | Gentle next-step when free |
| Something to look up later | "Remind me to look up …" | Query | When you're at a screen and free |

## 11. Leisure

| Scenario | How Meow knows | What it keeps | How it helps |
|---|---|---|---|
| Music / podcasts | Media session | What you like | Continue where you left off in the car |
| Shows / games | You say | Watchlist | Evening suggestions (after work only) |
| Events nearby | You express interest | Interests | Weekend ideas |
| Plans with friends | Messages/calendar | Plan | Reminders; leave-by |

## 12. Travel

| Scenario | How Meow knows | What it keeps | How it helps |
|---|---|---|---|
| Trip coming up | Calendar/confirmation emails (subject) | Itinerary you share | Packing list, check-in, leave-for-airport time |
| In another city / time zone | Location far away; time zone change | Trip | Local time, hotel address, rental return |
| Foreign language | Place/country | — | Quick phrases if asked |

## 13. Surroundings

| Scenario | How Meow knows | What it keeps | How it helps |
|---|---|---|---|
| Loud (bar, street, car) | Mic level (when already listening) | — | Text instead of voice; shorter lines |
| Quiet place (library, meeting) | Calendar/place/DND | — | Silent |
| Others around | Voices when listening; *not alone* | — | Doesn't read private things aloud; uses notification |
| Headphones on | Audio route | — | Can speak privately |
| In the car speaker | Car Bluetooth | — | Speaks aloud, short |
| Dark / night | Time, light (camera off) | — | Dim night look; no bright interruptions |
| Weather changing | Weather | — | Rain before you leave; storm alerts |
| Battery low far from home | Battery + distance | — | "12% and 20 mi from home — charge soon" |
| No signal area | Network state | — | Keeps things offline; queues messages for later |

## 14. Safety

| Scenario | How Meow knows | What it keeps | How it helps |
|---|---|---|---|
| Crash / hard impact | DrivingDetector | Event | Check-in; emergency flow |
| Walking alone late | Time + walking + unfamiliar | — | Offers to share location with a chosen person (needs a yes) |
| Severe weather / alerts | Emergency alerts, weather | — | Clear, short, repeated if needed |
| Phone left behind | *Watch disconnect* / car leaves without phone | — | Watch/other device notice (future) |
| Distracted driving | Driving + screen interaction | — | Voice-only, minimal UI |

## 15. The phone itself

| Scenario | How Meow knows | What it keeps | How it helps |
|---|---|---|---|
| Phone hot | Thermal status | Heat history | Meow backs off first (Laya, animations) and says so |
| Battery | Level, charging | — | Charge before a long day/trip |
| Storage full | Storage stats | — | Offers what can go |
| Notification overload | Counts | — | Summaries |
| Screen time | Usage access | — | Only if asked |

## 16. Memory & recall ("a friend who remembers")

| Scenario | How Meow knows | What it keeps | How it helps |
|---|---|---|---|
| "Where did I park?" | Parking detection | Spot | Map point + time |
| "Where did I put…?" | You told it | Object → place | Answer with when you said it |
| "When did I say…?" / "what did I tell you about…" | Journal | Every sentence + stamp | Answer with time and place |
| "Where was I when…?" | Journal stamps | — | "Tuesday 8:12 AM, at home" |
| "What time did I get to work?" | Arrivals | Place events | Answer |
| "What was that restaurant?" | Places visited + what you said | — | Candidates |
| "What did Dana say?" | Messages are not read (sender only) | — | Honest: "I only know Dana wrote at 3:10; open WhatsApp?" |
| Forget something | "Forget that" / delete in journal | — | Deleted, confirmed |
| Secrets | Passwords, codes | Never stored in the journal (vault only) | — |

## 17. When to speak (etiquette)

- Never while you're talking, on a call, in a meeting, or when someone else is talking nearby.
- Driving: short spoken lines, only at safe moments; nothing to read.
- With others around or no headphones: nothing private aloud (money, health, people notes).
- Late night: nothing non-urgent.
- One interruption at a time, within the user's limits (D4).

## 18. Special life situations

| Scenario | How Meow knows | How it helps |
|---|---|---|
| Sick day | You say | Pauses work, cancels nudges, drafts "I'm out" messages (needs a yes) |
| Vacation | Calendar / you say | Life mode; work silent; travel help |
| Bad day | Your words, tone | Fewer, gentler lines; nothing trivial |
| Celebration | Dates / you say | Remembers and reminds |
| Caring for someone | You tell (their meds, appointments) | Their schedule alongside yours |
| Kids | School times, pick-ups | Leave-by with traffic |
| New job / move | New usual places | Relearns home/work, asks to confirm |

## 19. Signals — what exists, what's missing, cost

| Signal | Status | Cost / permission |
|---|---|---|
| Your words (chat, voice) | ✅ captured; **journal stamping in progress** | none |
| Time, calendar | ✅ | READ_CALENDAR (granted) |
| Messages / notifications (sender/app/category) | ✅ people only | notification access |
| Foreground app | ✅ | usage access |
| Weather | ✅ | — |
| Activity (in vehicle / walking / still) | **C1 (building)** | ACTIVITY_RECOGNITION; hardware-batched, ~0 battery |
| Location in background | **C1 (building)** | background location (D10); balanced power, 1–10 min |
| Places (home/work/usual) + arrive/leave | C3 | none extra (from samples; geofences) |
| Parking spot | C3 | from vehicle→walking transition |
| Sound level | C6 (only when mic already on) | none extra |
| Car / headphones Bluetooth, charging, Wi-Fi name | C6 | BLUETOOTH_CONNECT; Wi-Fi name needs location |
| Call state / missed calls | *new* | READ_PHONE_STATE / READ_CALL_LOG (sensitive; ask) |
| Ringer / Do Not Disturb | *new* | none (read-only) |
| Steps, sleep, heart rate | *new* | Health Connect (user picks what) |
| Car fuel / codes / mileage | ✅ with OBD adapter | Bluetooth OBD |
| Media playing | ✅ (music awareness) | notification access |
| Photos / screen / camera | only when you turn them on | — |

## 20. Build order (value first, heat-checked)

1. **C1–C2** activity, background location, journal with full stamps (in progress).
2. **C4** understanding situation statements ("going to work", "I'm at…", "I'll be home by 6", "keys are in…").
3. **C3** places, arrive/leave, parking spot, commute inference.
4. **C5** recall: when/where answers from the journal in chat and voice.
5. **C6** surroundings: sound level, Bluetooth, charging, DND, call state (ask first).
6. Health Connect (steps/sleep), missed calls, commitments-to-people tracking.
7. Physical acceptance: a real day with a commute on the phone, heat logged throughout.
