# Cat Assistant - floating cat features glowing in the dark.
#
# There is no head. Eyes, ears, nose, mouth and whiskers hover in black space,
# arranged around an implied skull that is never drawn. Everything is generated
# from parameters in _draw() and animated procedurally: layered-sine hover,
# mouse-following gaze with saccades, asymmetric blinks, independent ear
# twitches and spring-lagged whiskers.
#
# The soft glow comes from the WorldEnvironment in main.tscn. Colours here are
# HDR (components above 1.0) so they cross the glow threshold and bloom.
#
# Controls while running:
#   1-6     set mood (Idle, Listening, Thinking, Happy, Curious, Sleepy)
#   Space   toggle speaking
#   T       toggle desktop overlay (borderless transparent window)
#
#   Actions - one-shot behaviours that play over the current mood:
#   Q slow blink   W purr     E startle   R yawn     A groom
#   S stalk        D chirp    F head bonk Z sneeze   X loaf

# @tool makes _draw() and _process() run inside the editor viewport as well as
# at runtime. Without it a Node2D that draws procedurally looks completely empty
# in the editor - there is no mesh or sprite to show, only draw calls.
@tool
extends Node2D

enum Mood { IDLE, LISTENING, THINKING, HAPPY, CURIOUS, SLEEPY, UPSET }

## Timed behaviours, as opposed to Mood which is a state you sit in. An action
## has a beginning, a middle and an end, and plays over whatever mood is active.
enum Action {
	NONE, SLOW_BLINK, PURR, STARTLE, YAWN, GROOM,
	STALK, CHIRP, HEAD_BONK, SNEEZE, LOAF,
	# --- second wave ---
	BLINK_QUICK, DOUBLE_TAKE, EAR_SWIVEL, LOOK_AWAY, SNIFF,
	FLEHMEN, HISS, GROWL, TRILL, MEOW_DEMAND,
	SILENT_MEOW, BLEP, LICK_LIPS, TOOTH_CHATTER, HEAD_SHAKE,
	SHIVER, STRETCH, WAKE_UP, FALL_ASLEEP, NOD_OFF,
	DREAM_TWITCH, PERK_UP, TRACK, POUNCE, RECOIL,
	CROUCH, ZOOMIES, WINK, SMUG, CONFUSED,
	SAD, PLEAD, EXCITED, CONTENT_SIGH, DISGUST,
	SNEER, ALERT_SOUND, SLOW_TURN, BUNT, KNEAD,
	SQUINT_SUN, STARE, GLANCE_BACK, SURPRISE, RELIEF,
	CURIOUS_SNIFF, PLAY_WIGGLE, ANNOYED_FLICK, GREET, SETTLE
}

const ACTION_NAMES := ["none", "slow blink", "purr", "startle", "yawn",
	"groom", "stalk", "chirp", "head bonk", "sneeze", "loaf",
	"quick blink", "double take", "ear swivel", "look away", "sniff",
	"flehmen", "hiss", "growl", "trill", "demand meow",
	"silent meow", "blep", "lick lips", "tooth chatter", "head shake",
	"shiver", "stretch", "wake up", "fall asleep", "nod off",
	"dream twitch", "perk up", "track", "pounce", "recoil",
	"crouch", "zoomies", "wink", "smug", "confused",
	"sad", "plead", "excited", "content sigh", "disgust",
	"sneer", "alert to sound", "slow turn", "bunt", "knead",
	"squint", "stare", "glance back", "surprise", "relief",
	"curious sniff", "play wiggle", "annoyed flick", "greet", "settle"]

## Seconds per action, indexed by the enum. A table beats a match here - sixty
## branches that each return one float is noise.
const ACTION_DUR := [
	0.0, 2.4, 3.2, 1.5, 2.6, 3.4, 3.2, 2.0, 1.7, 1.5, 3.6,
	0.45, 1.6, 2.2, 2.0, 1.8, 2.4, 1.6, 2.4, 1.8, 1.9,
	1.7, 2.6, 1.1, 1.5, 1.0, 1.4, 2.8, 3.0, 3.2, 3.0,
	3.4, 1.2, 2.6, 1.9, 0.9, 2.2, 1.3, 1.0, 2.6, 2.2,
	3.0, 2.8, 1.8, 2.8, 1.6, 2.0, 2.2, 2.6, 1.8, 2.8,
	2.4, 3.0, 1.7, 1.4, 2.6, 2.4, 1.6, 1.5, 3.0, 3.2]

# --- Look -------------------------------------------------------------------
@export var head_radius: float = 130.0
@export var eye_color: Color = Color(0.30, 1.00, 0.52)
@export var ear_color: Color = Color(1.00, 0.52, 0.60)
@export var nose_color: Color = Color(1.00, 0.48, 0.56)
@export var mouth_color: Color = Color(0.95, 0.70, 0.82)
@export var whisker_color: Color = Color(0.72, 0.90, 1.00)

## Global multiplier on every mood's eye slant. The per-mood values carry the
## expression; this just scales the whole set.
@export var eye_slant_scale: float = 1.0
## Degrees the outer corner rides above the inner one. The drop toward the nose
## is what reads as determined rather than merely awake.
@export var eye_slant: float = 16.0

## Multiplier pushing colours above 1.0 so the environment glow picks them up.
## Keep this modest: past ~1.4 every channel clips and the tonemapper flattens
## saturated colours to white, so the glow eats the hue instead of amplifying it.
@export var glow_boost: float = 1.3
@export var line_width: float = 3.5

## Off by default - the brief is features only. Flip on to bring the tail back.
@export var show_tail: bool = false

# --- Behaviour --------------------------------------------------------------
@export var mood: Mood = Mood.IDLE
@export var bob_amplitude: float = 14.0
@export var follow_mouse: bool = true

# --- Live pose (springs toward the mood's target) ---------------------------
var _eye_open := 1.0
var _pupil := 0.45
var _ear_pitch := 0.0
var _ear_splay := 12.0
var _mouth_curve := 0.30
var _tail_energy := 1.0
var _bob_speed := 1.0
var _head_tilt := 0.0
var _eye_w := 1.0
var _eye_h := 1.0
var _eye_tilt := 0.0
var _lower_lid := 0.0
# Eye silhouette, now per-mood. A flat hard top over a straight underside reads
# as anger; a domed top over a deep round belly reads as friendly.
var _eye_top := 2.2      # upper lid flatness exponent
var _eye_round := 0.50   # lower lid fullness
var _eye_slant := 6.0    # degrees the outer corner rides high
var _eye_up := 1.45      # upper lid height multiplier
var _whisk := 1.0
var _whisk_pitch := 0.0
var _nose_flare := 1.0

## Nose twitch, on its own random schedule like the ear twitches.
var _nose_twitch := 0.0
var _nose_timer := 0.0
var _nose_next := 2.5

# --- Action state -----------------------------------------------------------
var _action: int = Action.NONE
var _act_t := 0.0
var _act_dur := 1.0
## Pose overrides contributed by the running action this frame.
var _act := {}
var _act_offset := Vector2.ZERO
var _act_scale := 1.0
var _act_tilt := 0.0
var _act_lock_gaze := false
## -1 shuts the left eye, +1 the right. Winks and one-sided squints need to
## break the symmetry that every other override preserves.
var _act_wink := 0.0
## Explicit gaze direction while an action drives the eyes somewhere specific.
var _act_gaze := Vector2.ZERO
var _act_gaze_w := 0.0
## Degrees of OPPOSING ear rotation. Ear swivels and annoyed flicks are defined
## by one ear moving alone, which a symmetric ear channel can never express.
var _act_ear_split := 0.0
## Raises one corner of the mouth. Sneers and smirks are one-sided by nature.
var _act_mouth_split := 0.0
## Suppress the tongue even with the mouth wide - what makes a silent meow silent.
var _act_no_tongue := false
## Hold the eyes open through an action. A stare that blinks is not a stare.
var _act_no_blink := false

# --- Jaw rig ------------------------------------------------------------------
# The mouth used to be a hole that scaled, which is why fourteen mouth-driven
# actions were indistinguishable: a bigger hole is a bigger hole. These three
# give the mouth its own vocabulary independent of how far it is open.
## 0..1 jaw rotation about the hinge. Overrides the speech amplitude when set.
var _act_jaw := 0.0
## 0..1 upper-lip raise, baring the fangs. Snarls, sneers, the flehmen face.
var _act_lip_curl := 0.0
## Horizontal scale of the mouth. Below 1 purses it into an "o" for a meow;
## above 1 stretches it wide for a hiss.
var _act_mouth_w := 1.0

# --- Paws ---------------------------------------------------------------------
# Floating paws, same conceit as the rest of the face: no arms, they simply hover
# where an arm would put them. Position is in head-radius units, face-local.
# "on" doubles as the fade so they can rise into frame rather than popping.
var _paw_l := {"on": 0.0, "pos": Vector2.ZERO, "rot": 0.0, "size": 1.0}
var _paw_r := {"on": 0.0, "pos": Vector2.ZERO, "rot": 0.0, "size": 1.0}

@export var paw_color: Color = Color(1.00, 0.52, 0.60)
@export var pad_color: Color = Color(1.00, 0.42, 0.52)
## Index shown by the action browser, so all sixty are reachable from the keys.
var _browse := 1
## A live channel composition, when one is playing in place of a named preset.
var _composed := {}

## Spring velocities, keyed by pose parameter.
var _vel := {}
## Small fixed per-eye offsets. Two perfectly identical eyes read as a machine.
var _asym := [0.0, 0.0]

# --- Animation state --------------------------------------------------------
var _t := 0.0
var _bob := 0.0
var _sway := 0.0
var _tilt := 0.0
var _squash := 0.0
var _squash_vel := 0.0
var _prev_bob := 0.0
var _bob_vel := 0.0

var _gaze := Vector2.ZERO           # smoothed, normalised -1..1 per axis
## Gaze WITHOUT the saccade component, and slower. Drives the head projection:
## a saccade moves the eyes, not the skull, and feeding saccades into the
## projection shears the ears away from the eyes on random frames.
var _head_gaze := Vector2.ZERO
var _saccade := Vector2.ZERO
var _saccade_timer := 0.0
var _next_saccade := 1.2

var _blink := 1.0                   # 1 = open, 0 = shut
var _blink_timer := 0.0
var _next_blink := 3.0
var _blinking := false
var _blink_elapsed := 0.0
var _queued_blinks := 0

var _ear_twitch := [0.0, 0.0]
var _ear_timer := [0.0, 0.0]
var _ear_next := [2.0, 3.1]

var _whisker_offset := Vector2.ZERO
var _whisker_vel := Vector2.ZERO
var _prev_tilt := 0.0

var _speaking := false
var _talk := 0.0
var _talk_target := 0.0

var _overlay := false

## Radial falloff sprite used for every soft glow. Generated once - a filled
## circle has a hard rim no matter how low its alpha, which is what makes a
## drawn halo look cheap.
var _glow_tex: ImageTexture = null

## Drifting dust motes, for depth and atmosphere.
var _motes: Array = []

const BLINK_CLOSE := 0.075
const BLINK_OPEN := 0.16
const MOTE_COUNT := 54


func _ready() -> void:
	_next_blink = randf_range(2.4, 5.5)
	_next_saccade = randf_range(0.7, 2.2)
	_ear_next = [randf_range(1.8, 5.0), randf_range(1.8, 5.0)]
	_apply_pose(_pose_for(mood), 0.0, true)
	_asym = [randf_range(-1.0, 1.0), randf_range(-1.0, 1.0)]
	_glow_tex = _make_glow_texture()
	_init_motes()
	if not Engine.is_editor_hint():
		RenderingServer.set_default_clear_color(Color.BLACK)


## White sprite whose alpha falls off with a quintic smootherstep. The high
## order is deliberate: linear or even smoothstep falloff still leaves a
## perceptible edge against black.
func _make_glow_texture(size: int = 128) -> ImageTexture:
	var img := Image.create(size, size, false, Image.FORMAT_RGBA8)
	var c := (size - 1) * 0.5
	for y in range(size):
		for x in range(size):
			var d: float = Vector2(x - c, y - c).length() / c
			var a: float = clampf(1.0 - d, 0.0, 1.0)
			a = a * a * a * (a * (a * 6.0 - 15.0) + 10.0)
			img.set_pixel(x, y, Color(1.0, 1.0, 1.0, a))
	return ImageTexture.create_from_image(img)


func _init_motes() -> void:
	_motes.clear()
	for i in range(MOTE_COUNT):
		_motes.append({
			"x": randf_range(-1.0, 1.0),
			"y": randf_range(-1.0, 1.0),
			"depth": randf_range(0.22, 1.0),      # drives parallax and size
			"size": randf_range(0.7, 2.6),
			"phase": randf_range(0.0, TAU),
			"speed": randf_range(0.04, 0.20)})


func _process(delta: float) -> void:
	_t += delta
	_apply_pose(_pose_for(mood), delta)
	_update_action(delta)
	_update_float(delta)
	_update_gaze(delta)
	_update_blink(delta)
	_update_ears(delta)
	_update_whiskers(delta)
	_update_mouth(delta)
	# Last, so an action wins over both the mood pose and the idle mouth.
	_apply_action()
	queue_redraw()


func _unhandled_input(event: InputEvent) -> void:
	# Never swallow keys in the editor - 1..6 and T are editor shortcuts.
	if Engine.is_editor_hint():
		return
	if event is InputEventKey and event.pressed and not event.echo:
		match event.keycode:
			KEY_1: mood = Mood.IDLE
			KEY_2: mood = Mood.LISTENING
			KEY_3: mood = Mood.THINKING
			KEY_4: mood = Mood.HAPPY
			KEY_5: mood = Mood.CURIOUS
			KEY_6: mood = Mood.SLEEPY
			KEY_7: mood = Mood.UPSET
			KEY_SPACE: set_speaking(not _speaking)
			KEY_T: _toggle_overlay()
					# Actions.
			KEY_Q: play_action(Action.SLOW_BLINK)
			KEY_W: play_action(Action.PURR)
			KEY_E: play_action(Action.STARTLE)
			KEY_R: play_action(Action.YAWN)
			KEY_A: play_action(Action.GROOM)
			KEY_S: play_action(Action.STALK)
			KEY_D: play_action(Action.CHIRP)
			KEY_F: play_action(Action.HEAD_BONK)
			KEY_Z: play_action(Action.SNEEZE)
			KEY_X: play_action(Action.LOAF)
			# Sixty actions will not fit on named keys, so the rest are reached
			# by stepping through the list and playing the selection.
			KEY_BRACKETLEFT:
				_browse = wrapi(_browse - 1, 1, ACTION_NAMES.size())
				print("action %d/%d: %s" % [_browse, ACTION_NAMES.size() - 1, ACTION_NAMES[_browse]])
			KEY_BRACKETRIGHT:
				_browse = wrapi(_browse + 1, 1, ACTION_NAMES.size())
				print("action %d/%d: %s" % [_browse, ACTION_NAMES.size() - 1, ACTION_NAMES[_browse]])
			KEY_ENTER, KEY_KP_ENTER:
				print("playing: %s" % ACTION_NAMES[_browse])
				play_action(_browse)


# ---------------------------------------------------------------------------
# Public API - what an assistant layer would drive
# ---------------------------------------------------------------------------
func set_mood(new_mood: Mood) -> void:
	mood = new_mood


func set_speaking(value: bool) -> void:
	_speaking = value
	if not _speaking:
		_talk_target = 0.0


## Feed 0..1 per frame from a TTS amplitude envelope.
func set_talk_amplitude(amount: float) -> void:
	_talk_target = clampf(amount, 0.0, 1.0)


func blink_now() -> void:
	if not _blinking:
		_blinking = true
		_blink_elapsed = 0.0


# ---------------------------------------------------------------------------
# Poses
# ---------------------------------------------------------------------------
# lid_in / lid_out drop the inner and outer corners of the upper lid, in
# fractions of the eye radius. lower raises the lower lid. Those three numbers
# carry nearly all of the emotion - dropping the inner corner reads as
# concentration, the outer corner as sadness, and raising the lower lid is what
# makes a smile reach the eyes.
# With no iris or pupil, emotion lives entirely in the silhouette of the eye:
# ew/eh scale it, etilt rotates it (inner corner down reads as a scowl), and
# lower raises a bright lower-lid cap - the squashed-from-below shape that makes
# a smile reach the eyes.
func _pose_for(m: int) -> Dictionary:
	match m:
		Mood.LISTENING:
			# Ears hard forward, eyes stretched tall. Pushed past realistic on
			# purpose - animation poses have to read at a glance.
			return {"open": 1.06, "pupil": 0.66, "ear": 28.0, "splay": 2.0,
					"mouth": 0.16, "tail": 0.45, "bob": 0.8, "tilt": 4.0,
					"ew": 0.98, "eh": 1.22, "etilt": -3.0, "lower": 0.04,
					"whisk": 1.14, "wpitch": -0.05, "nose": 1.06,
					"etop": 2.4, "eround": 0.46, "eslant": 7.0, "eup": 1.45}
		Mood.THINKING:
			# Inner corners driven down hard, ears flicked back and out.
			return {"open": 1.0, "pupil": 0.26, "ear": -15.0, "splay": 26.0,
					"mouth": -0.14, "tail": 1.7, "bob": 0.9, "tilt": 13.0,
					"ew": 1.08, "eh": 0.60, "etilt": 16.0, "lower": 0.20,
					"whisk": 0.88, "wpitch": 0.06, "nose": 0.98,
					"etop": 5.0, "eround": 0.62, "eslant": 15.0, "eup": 1.05}
		Mood.HAPPY:
			# The full Duchenne squash: eyes crushed from below into arcs.
			return {"open": 1.0, "pupil": 0.78, "ear": 24.0, "splay": 16.0,
					"mouth": 1.0, "tail": 2.3, "bob": 1.6, "tilt": 0.0,
					"ew": 1.14, "eh": 0.50, "etilt": -2.0, "lower": 0.74,
					"whisk": 1.20, "wpitch": -0.09, "nose": 1.10,
					"etop": 1.9, "eround": 0.42, "eslant": 3.0, "eup": 1.60}
		Mood.CURIOUS:
			# Saucer eyes, ears straight up, big head cock.
			return {"open": 1.0, "pupil": 0.92, "ear": 32.0, "splay": 5.0,
					"mouth": 0.10, "tail": 1.3, "bob": 1.1, "tilt": 21.0,
					"ew": 0.94, "eh": 1.32, "etilt": -11.0, "lower": 0.0,
					"whisk": 1.22, "wpitch": -0.11, "nose": 1.12,
					"etop": 1.9, "eround": 0.42, "eslant": 2.0, "eup": 1.62}
		Mood.SLEEPY:
			# Almost shut, ears folded right down, whiskers hanging.
			return {"open": 1.0, "pupil": 0.18, "ear": -30.0, "splay": 30.0,
					"mouth": 0.20, "tail": 0.30, "bob": 0.55, "tilt": -9.0,
					"ew": 1.04, "eh": 0.24, "etilt": 7.0, "lower": 0.32,
					"whisk": 0.80, "wpitch": 0.15, "nose": 0.94,
					"etop": 3.2, "eround": 0.58, "eslant": 9.0, "eup": 1.20}
		Mood.UPSET:
			# The angular look: a hard flat lid driven down over a straight
			# underside, ears pinned, pupils shut to threads. Every parameter
			# that softened Idle is inverted here.
			return {"open": 1.0, "pupil": 0.05, "ear": -34.0, "splay": 30.0,
					"mouth": -0.55, "tail": 2.0, "bob": 1.15, "tilt": 0.0,
					"ew": 1.10, "eh": 0.82, "etilt": 14.0, "lower": 0.22,
					"whisk": 0.86, "wpitch": 0.18, "nose": 1.08,
					"etop": 9.0, "eround": 0.84, "eslant": 28.0, "eup": 0.72}
		_:
			return {"open": 1.0, "pupil": 0.45, "ear": 0.0, "splay": 12.0,
					"mouth": 0.30, "tail": 1.0, "bob": 1.0, "tilt": 0.0,
					"ew": 1.0, "eh": 1.0, "etilt": 0.0, "lower": 0.12,
					"whisk": 1.0, "wpitch": 0.0, "nose": 1.0,
					"etop": 2.0, "eround": 0.44, "eslant": 5.0, "eup": 1.55}


## Underdamped spring. A lerp eases in and dies, which is why interpolated
## poses always look like software. Real expression snaps past its target and
## settles back - that overshoot is most of what reads as "alive".
func _spring(key: String, cur: float, target: float, delta: float,
		stiffness: float = 185.0, bounce: float = 0.42) -> float:
	var damping := 2.0 * sqrt(stiffness) * bounce
	var v: float = _vel.get(key, 0.0)
	v += (target - cur) * stiffness * delta - v * damping * delta
	_vel[key] = v
	return cur + v * delta


func _apply_pose(p: Dictionary, delta: float, snap: bool = false) -> void:
	if snap:
		_eye_open = p["open"]; _pupil = p["pupil"]
		_ear_pitch = p["ear"]; _ear_splay = p["splay"]
		_mouth_curve = p["mouth"]; _tail_energy = p["tail"]
		_bob_speed = p["bob"]; _head_tilt = p["tilt"]
		_eye_w = p["ew"]; _eye_h = p["eh"]
		_eye_tilt = p["etilt"]; _lower_lid = p["lower"]
		_whisk = p["whisk"]; _whisk_pitch = p["wpitch"]; _nose_flare = p["nose"]
		_eye_top = p["etop"]; _eye_round = p["eround"]
		_eye_slant = p["eslant"]; _eye_up = p["eup"]
		return

	# Ears and eye shape get the loosest springs - they are the parts an
	# audience reads first, so they carry the most follow-through.
	_eye_open = _spring("open", _eye_open, p["open"], delta)
	_pupil = _spring("pupil", _pupil, p["pupil"], delta, 120.0, 0.7)
	_ear_pitch = _spring("ear", _ear_pitch, p["ear"], delta, 150.0, 0.32)
	_ear_splay = _spring("splay", _ear_splay, p["splay"], delta, 150.0, 0.34)
	_mouth_curve = _spring("mouth", _mouth_curve, p["mouth"], delta, 170.0, 0.40)
	_tail_energy = _spring("tail", _tail_energy, p["tail"], delta, 90.0, 0.9)
	_bob_speed = _spring("bob", _bob_speed, p["bob"], delta, 70.0, 1.0)
	_head_tilt = _spring("htilt", _head_tilt, p["tilt"], delta, 110.0, 0.38)
	_eye_w = _spring("ew", _eye_w, p["ew"], delta, 200.0, 0.38)
	_eye_h = _spring("eh", _eye_h, p["eh"], delta, 200.0, 0.36)
	_eye_tilt = _spring("etilt", _eye_tilt, p["etilt"], delta, 170.0, 0.40)
	_lower_lid = _spring("lower", _lower_lid, p["lower"], delta, 190.0, 0.38)
	# Whiskers are the loosest of all - they are light and trail everything else.
	_whisk = _spring("whisk", _whisk, p["whisk"], delta, 90.0, 0.28)
	_whisk_pitch = _spring("wpitch", _whisk_pitch, p["wpitch"], delta, 85.0, 0.26)
	_nose_flare = _spring("nose", _nose_flare, p["nose"], delta, 210.0, 0.35)
	# Silhouette morphs a touch slower than the pose, so the shape change reads
	# as a separate beat rather than getting lost in the movement.
	_eye_top = _spring("etop", _eye_top, p["etop"], delta, 130.0, 0.55)
	_eye_round = _spring("eround", _eye_round, p["eround"], delta, 130.0, 0.55)
	_eye_slant = _spring("eslant", _eye_slant, p["eslant"], delta, 150.0, 0.42)
	_eye_up = _spring("eup", _eye_up, p["eup"], delta, 140.0, 0.48)


# ---------------------------------------------------------------------------
# Actions - timed behaviours layered on top of the persistent mood
# ---------------------------------------------------------------------------

## Start an action. Interrupts whatever was playing.
func play_action(act: int) -> void:
	_action = act
	_act_t = 0.0
	_act_dur = _action_duration(act)


func is_acting() -> bool:
	return _action != Action.NONE


func _action_duration(act: int) -> float:
	if act > 0 and act < ACTION_DUR.size():
		return ACTION_DUR[act]
	return 1.0


## Repeating pulse, 0..1, n cycles across the action. For chatter, kneading and
## anything else with a beat.
func _pulse(t: float, n: float) -> float:
	return maxf(0.0, sin(t * PI * n))


## One-shot spike that peaks at `at` and decays. For hits and snaps.
func _spike(t: float, at: float, fall: float = 2.0) -> float:
	if t < at:
		return CatDraw.smoothstep01(t / maxf(0.001, at))
	return pow(clampf(1.0 - (t - at) / maxf(0.001, 1.0 - at), 0.0, 1.0), fall)


## Ease in and out, so an action never pops on or off mid-pose.
func _bell(t: float) -> float:
	return CatDraw.smoothstep01(minf(t / 0.18, 1.0)) \
		* CatDraw.smoothstep01(minf((1.0 - t) / 0.22, 1.0))


func _update_action(delta: float) -> void:
	_act = {}
	_act_offset = Vector2.ZERO
	_act_scale = 1.0
	_act_tilt = 0.0
	_act_lock_gaze = false
	_act_wink = 0.0
	_act_gaze = Vector2.ZERO
	_act_gaze_w = 0.0
	_act_ear_split = 0.0
	_act_mouth_split = 0.0
	_act_no_tongue = false
	_act_no_blink = false
	_act_jaw = 0.0
	_act_lip_curl = 0.0
	_act_mouth_w = 1.0
	_paw_l = {"on": 0.0, "pos": Vector2.ZERO, "rot": 0.0, "size": 1.0}
	_paw_r = {"on": 0.0, "pos": Vector2.ZERO, "rot": 0.0, "size": 1.0}

	# A composition stands in for a preset while it runs.
	if not _composed.is_empty():
		_update_composed(delta, head_radius)
		return

	if _action == Action.NONE:
		return

	_act_t += delta
	if _act_t >= _act_dur:
		_action = Action.NONE
		return

	var t: float = _act_t / _act_dur
	var R := head_radius

	match _action:
		Action.SLOW_BLINK:
			# The cat "kiss" - a long deliberate close, a hold, a slower open.
			# Cats aim this at people they trust, so the timing has to be
			# unhurried or it just reads as an ordinary blink.
			var e: float
			if t < 0.35:
				e = 1.0 - CatDraw.smoothstep01(t / 0.35)
			elif t < 0.55:
				e = 0.0
			else:
				e = CatDraw.smoothstep01((t - 0.55) / 0.45)
			_act = {"w": 1.0, "open": e, "lower": 0.28, "mouth": 0.45, "ear": 6.0}

		Action.PURR:
			# Half-lidded contentment plus a fast, tiny buzz through the face.
			# Differentiator: a hard mouth curve and forward whiskers, plus a buzz
			# with enough travel to actually see.
			var b := _bell(t)
			_act = {"w": b, "open": 0.44, "lower": 0.58, "mouth": 1.0,
					"pupil": 0.55, "ear": 14.0, "splay": 12.0,
					"whisk": 1.20, "wpitch": -0.10}
			var buzz := sin(_act_t * 42.0) * R * 0.020 * b
			_act_offset = Vector2(buzz, buzz * 0.6)

		Action.STARTLE:
			# Instant spike then a slow climb down. Fear has no ease-in, so this
			# one deliberately skips the bell curve on the way up.
			var d := pow(1.0 - t, 2.2)
			_act = {"w": d, "open": 1.35, "pupil": 1.0, "ear": -34.0, "splay": 34.0,
					"whisk": 0.70, "wpitch": 0.20, "eh": 1.45, "ew": 1.10}
			_act_offset = Vector2(0.0, -R * 0.17 * d)
			_act_scale = 1.0 - 0.05 * d
			_act_tilt = deg_to_rad(sin(_act_t * 30.0) * 3.0 * d)

		Action.YAWN:
			# The widest, slowest jaw in the set with no lip curl at all - a yawn
			# is the opposite of a snarl even though both open the mouth.
			var o: float = sin(clampf(t / 0.62, 0.0, 1.0) * PI)
			_act = {"w": 1.0, "jaw": o, "mw": 1.18, "open": 1.0 - o * 0.95,
					"lower": 0.30 + o * 0.40, "mouth": -0.20, "ear": -10.0 * o,
					"whisk": 1.0 + 0.25 * o, "nose": 1.0 + 0.10 * o}
			_act_tilt = deg_to_rad(-6.0 * o)

		Action.GROOM:
			# Repeated licks, head swinging to follow the tongue.
			# Differentiator: eyes fully shut and the head swung hard to one side
			# to meet the tongue, rather than a neutral face with a small mouth.
			var lick: float = maxf(0.0, sin(t * PI * 6.0))
			_act = {"w": _bell(t), "talk": lick * 0.85, "open": 0.10,
					"lower": 0.45, "mouth": 0.30, "ear": 2.0, "splay": 20.0}
			_act_tilt = deg_to_rad(sin(t * PI * 3.0) * 13.0)
			_act_offset = Vector2(sin(t * PI * 3.0) * R * 0.16, R * 0.04)
			# The paw being washed - held up to the mouth, twisting as it is licked.
			# Below the chin, not over it. At mouth height the paw simply erased
			# the muzzle it was supposed to be washing.
			_paw_r = {"on": _bell(t), "pos": Vector2(0.30, 1.00),
					"rot": -22.0 + lick * 14.0, "size": 1.0}

		Action.STALK:
			# Locked stare, pupils to slits, and the pre-pounce wiggle building.
			# Gaze tracking is suspended - a stalking cat does not glance around.
			# Differentiator from pounce: this one stays LOW and narrow-eyed. The
			# pounce is the release; the stalk is the compression before it.
			_act_lock_gaze = true
			_act_no_blink = true
			var ramp := CatDraw.smoothstep01(t / 0.7)
			_act = {"w": _bell(t), "open": 0.92, "pupil": 0.04, "ear": 30.0,
					"splay": 2.0, "whisk": 1.25, "wpitch": -0.12, "mouth": -0.05,
					"eh": 0.80, "ew": 1.15}
			_act_offset = Vector2(sin(_act_t * 11.0) * R * 0.09 * ramp,
				R * 0.10 * ramp)
			_act_scale = 1.0 - 0.06 * ramp

		Action.CHIRP:
			# The stuttering ek-ek-ek at a bird through a window.
			# Very fast, very narrow jaw stutter - the ek-ek-ek.
			# Baseline jaw keeps the mouth parted between stutters. Driving it
			# purely off the oscillator meant the mouth was fully shut at every
			# zero crossing, which is most of the action.
			var chir: float = maxf(0.0, sin(t * PI * 18.0))
			_act = {"w": _bell(t), "jaw": 0.16 + chir * 0.30, "mw": 0.70, "open": 1.10,
					"pupil": 0.80, "ear": 30.0, "splay": 3.0, "whisk": 1.20,
					"wpitch": -0.14}
			_act_offset = Vector2(0.0, sin(t * PI * 18.0) * R * 0.013)

		Action.HEAD_BONK:
			# Anticipate back, drive forward, settle. Contact shuts the eyes.
			var f: float
			if t < 0.30:
				f = -0.35 * CatDraw.smoothstep01(t / 0.30)
			elif t < 0.55:
				f = lerpf(-0.35, 1.0, CatDraw.smoothstep01((t - 0.30) / 0.25))
			else:
				f = 1.0 - CatDraw.smoothstep01((t - 0.55) / 0.45)
			# Differentiator from bunt and knead: this is the biggest forward
			# drive in the set.
			_act_scale = 1.0 + 0.18 * f
			_act_offset = Vector2(0.0, R * 0.16 * f)
			_act = {"w": clampf(f, 0.0, 1.0), "open": 0.20, "lower": 0.62,
					"mouth": 0.85, "ear": 14.0 - 26.0 * maxf(0.0, f)}

		Action.SNEEZE:
			# Long wind-up, sharp snap, quick recovery. All the comedy is in the
			# ratio between those three.
			if t < 0.55:
				var a := t / 0.55
				_act = {"w": a, "open": 0.45, "nose": 1.0 + 0.22 * a,
						"whisk": 1.0 - 0.20 * a, "lower": 0.35, "mouth": -0.20}
				_act_tilt = deg_to_rad(-8.0 * a)
			else:
				var b := pow(1.0 - (t - 0.55) / 0.45, 2.0)
				_act = {"w": b, "open": 0.0, "talk": 0.90 * b, "ear": -22.0,
						"whisk": 1.30, "wpitch": -0.20, "nose": 1.25}
				_act_offset = Vector2(0.0, R * 0.13 * b)
				_act_tilt = deg_to_rad(9.0 * b)
				# Paw flies up to the nose on the snap, exactly as it fires.
				_paw_r = {"on": b, "pos": Vector2(0.38, 0.96 - 0.06 * b),
						"rot": -40.0, "size": 0.92}

		Action.LOAF:
			# Differentiator: the eyes squeeze into wide flat slots rather than
			# simply narrowing, and the whole face sits low.
			var s := CatDraw.smoothstep01(t / 0.5)
			_act = {"w": _bell(t), "open": 0.40, "pupil": 0.30, "ear": -4.0,
					"splay": 22.0, "lower": 0.30, "mouth": 0.50, "whisk": 0.82,
					"wpitch": 0.14, "ew": 1.30, "eh": 0.55}
			_act_offset = Vector2(0.0, R * 0.10 * s)

		Action.BLINK_QUICK:
			_act = {"w": 1.0, "open": 1.0 - sin(t * PI)}

		Action.DOUBLE_TAKE:
			# Glance off, then snap back wider than it started. The overshoot on
			# the return is the entire gag.
			if t < 0.40:
				var a := CatDraw.smoothstep01(t / 0.40)
				_act = {"w": _bell(t), "open": 0.85}
				_act_tilt = deg_to_rad(-15.0 * a)
				_act_gaze = Vector2(-0.9, 0.1)
				_act_gaze_w = a
			else:
				var b := CatDraw.smoothstep01((t - 0.40) / 0.22)
				_act = {"w": _bell(t), "open": 1.0 + 0.30 * b, "pupil": 0.9,
						"ear": 26.0, "eh": 1.0 + 0.25 * b}
				_act_tilt = deg_to_rad(-15.0 * (1.0 - b) + 4.0 * b)
				_act_gaze = Vector2(-0.9 * (1.0 - b), 0.0)
				_act_gaze_w = 1.0

		Action.EAR_SWIVEL:
			# Differentiator: the ears move in OPPOSITION and the head holds
			# still. Symmetric ear motion is indistinguishable from a mood.
			_act_ear_split = sin(t * PI * 3.0) * 34.0
			_act = {"w": _bell(t), "ear": 16.0, "splay": 8.0, "open": 1.05,
					"pupil": 0.45}

		Action.LOOK_AWAY:
			var a := sin(t * PI)
			_act = {"w": _bell(t), "open": 0.80, "lower": 0.25, "ear": -4.0}
			_act_tilt = deg_to_rad(-13.0 * a)
			_act_gaze = Vector2(-0.85, 0.15)
			_act_gaze_w = a
			_act_offset = Vector2(-R * 0.05 * a, 0.0)

		Action.SNIFF:
			# Nose working in fast bursts, head making tiny stabs after it.
			var p := _pulse(t, 9.0)
			_act = {"w": _bell(t), "nose": 1.0 + 0.30 * p, "open": 0.85,
					"whisk": 1.12, "wpitch": -0.06}
			_act_offset = Vector2(0.0, -R * 0.02 * p)

		Action.FLEHMEN:
			# The stink face: mouth cracked open and simply held there.
			var h := _bell(t)
			# Maximum lip curl with almost no jaw - unique to this pose.
			_act = {"w": h, "jaw": 0.18, "curl": 0.95, "mw": 0.92, "open": 0.55,
					"lower": 0.30, "mouth": -0.35, "nose": 1.15, "ear": -8.0,
					"whisk": 0.9}
			_act_tilt = deg_to_rad(5.0 * h)

		Action.HISS:
			# Everything arrives on frame two, holds, and drops. No wind-up.
			var s := _spike(t, 0.12, 1.4)
			# Jaw wide, lip fully curled, mouth stretched: fangs out.
			_act = {"w": s, "jaw": 0.95, "curl": 1.0, "mw": 1.35, "open": 1.15,
					"pupil": 0.03, "ear": -40.0, "splay": 34.0, "mouth": -1.0,
					"whisk": 0.65, "wpitch": 0.24, "eh": 0.90, "etilt": 18.0}
			_act_offset = Vector2(0.0, R * 0.06 * s)
			_act_scale = 1.0 + 0.06 * s

		Action.GROWL:
			# A hostile purr: same vibration, opposite everything else.
			# Differentiator from crouch: the mouth is open on a sustained note
			# and the whole face vibrates with it.
			var g := _bell(t)
			# Against the hiss: the jaw barely parts but the lip stays curled and
			# held. A growl is a sustained threat, not an explosion.
			_act = {"w": g, "jaw": 0.26, "curl": 0.85, "mw": 1.06, "open": 0.66,
					"pupil": 0.10, "ear": -30.0, "splay": 28.0, "mouth": -0.85,
					"whisk": 0.78, "wpitch": 0.14, "etilt": 14.0}
			_act_offset = Vector2(sin(_act_t * 30.0) * R * 0.016 * g, 0.0)

		Action.TRILL:
			# Rising mrrp, with the chin lifting through it.
			var r := CatDraw.smoothstep01(t)
			# Narrow mouth fluttering fast over a tiny range.
			_act = {"w": _bell(t), "jaw": 0.14 + _pulse(t, 12.0) * 0.24, "mw": 0.74,
					"open": 1.05, "ear": 26.0, "splay": 5.0, "mouth": 0.60,
					"whisk": 1.18}
			_act_tilt = deg_to_rad(-5.0 * r)
			_act_offset = Vector2(0.0, -R * 0.03 * r)

		Action.MEOW_DEMAND:
			# Differentiator from the silent meow: the head drives forward into
			# your face and the ears flatten with the effort.
			var m := sin(clampf(t / 0.7, 0.0, 1.0) * PI)
			# Pursed into an "o". The narrow mouth is what separates a meow from
			# every other open-jaw action.
			_act = {"w": 1.0, "jaw": m * 0.80, "mw": 0.66, "open": 0.95,
					"pupil": 0.55, "ear": 22.0 - 30.0 * m, "splay": 18.0,
					"mouth": 0.20}
			_act_scale = 1.0 + 0.12 * m
			_act_offset = Vector2(0.0, R * 0.11 * m)

		Action.SILENT_MEOW:
			# Mouth opens fully and nothing comes out.
			# Differentiator: widest opening of any action and NO tongue. Without
			# suppressing the tongue this was the demand meow with one pink dot
			# removed, which is not what "silent" should mean.
			var m := sin(clampf(t / 0.65, 0.0, 1.0) * PI)
			_act_no_tongue = true
			# Widest jaw in the set, held wide, and no tongue.
			_act = {"w": 1.0, "jaw": m, "mw": 1.10, "open": 1.20, "pupil": 0.90,
					"ear": 26.0, "splay": 5.0, "mouth": 0.35, "lower": 0.05}

		Action.BLEP:
			# Tongue out, then simply left there. That is the whole joke, so it
			# holds far longer than any other mouth pose.
			var b := CatDraw.smoothstep01(minf(t / 0.18, 1.0)) \
				* CatDraw.smoothstep01(minf((1.0 - t) / 0.12, 1.0))
			# Jaw essentially shut with the tongue lolling out past it - the only
			# pose in the set with a tongue and no opening.
			_act = {"w": 1.0, "jaw": 0.16 * b, "mw": 0.86, "open": 0.95,
					"mouth": 0.55, "lower": 0.18}

		Action.LICK_LIPS:
			var l := _pulse(t, 2.0)
			_act = {"w": _bell(t), "talk": l * 0.55, "open": 0.70, "lower": 0.40,
					"mouth": 0.45, "nose": 1.05}

		Action.TOOTH_CHATTER:
			# Differentiator: the mouth is genuinely open and juddering, plus a
			# high-frequency shake. Previously this was a static neutral face.
			_act_lock_gaze = true
			var ch := _bell(t)
			# Teeth showing with the jaw juddering over a small range - the only
			# action pairing a curled lip with a high-frequency jaw.
			_act = {"w": ch, "jaw": 0.15 + _pulse(t, 26.0) * 0.16, "curl": 0.55,
					"mw": 0.88, "open": 1.10, "pupil": 0.45, "ear": 30.0,
					"splay": 3.0, "whisk": 1.24, "wpitch": -0.14}
			_act_offset = Vector2(sin(_act_t * 52.0) * R * 0.014 * ch,
				sin(_act_t * 60.0) * R * 0.010 * ch)

		Action.HEAD_SHAKE:
			# Differentiator: large lateral travel. At the old amplitude the shake
			# moved about six pixels and was invisible.
			var k := sin(t * PI * 6.0) * (1.0 - t)
			_act = {"w": _bell(t), "open": 0.72, "ear": -12.0, "splay": 22.0,
					"whisk": 1.15}
			_act_tilt = deg_to_rad(24.0 * k)
			_act_offset = Vector2(R * 0.20 * k, 0.0)

		Action.SHIVER:
			var v := _bell(t)
			_act = {"w": v, "open": 0.65, "ear": -18.0, "splay": 26.0,
					"lower": 0.30, "whisk": 0.85}
			# Differentiator from sad: a fast tremor with real travel, and the
			# ears buzzing out of phase with each other.
			_act_ear_split = sin(_act_t * 38.0) * 16.0 * v
			_act_offset = Vector2(sin(_act_t * 40.0) * R * 0.035 * v,
				cos(_act_t * 47.0) * R * 0.022 * v)

		Action.STRETCH:
			var s := sin(clampf(t / 0.75, 0.0, 1.0) * PI)
			_act = {"w": 1.0, "open": 1.0 - 0.90 * s, "lower": 0.35 + 0.30 * s,
					"ear": -14.0 * s, "mouth": 0.30, "whisk": 1.0 + 0.30 * s}
			_act_scale = 1.0 + 0.13 * s
			_act_tilt = deg_to_rad(-9.0 * s)

		Action.WAKE_UP:
			# Prised open in stages with a stall partway - waking is not monotonic.
			var e: float
			if t < 0.35:
				e = 0.10 + 0.35 * CatDraw.smoothstep01(t / 0.35)
			elif t < 0.50:
				e = 0.10
			else:
				e = CatDraw.smoothstep01((t - 0.50) / 0.35) * 1.05
			_act = {"w": 1.0, "open": e, "pupil": 0.25 + 0.50 * t,
					"ear": -20.0 + 40.0 * CatDraw.smoothstep01(t)}

		Action.FALL_ASLEEP:
			var e := 1.0 - CatDraw.smoothstep01(t)
			_act = {"w": 1.0, "open": 0.08 + e * 0.92, "pupil": 0.20,
					"ear": -6.0 - 20.0 * (1.0 - e),
					"lower": 0.20 + 0.25 * (1.0 - e),
					"whisk": 1.0 - 0.25 * (1.0 - e), "wpitch": 0.20 * (1.0 - e)}
			# Differentiator: the head visibly droops and tips as it goes under.
			_act_offset = Vector2(0.0, R * 0.17 * (1.0 - e))
			_act_tilt = deg_to_rad(10.0 * (1.0 - e))

		Action.NOD_OFF:
			# Sinks, catches itself, sinks again. Three beats.
			var seg := fposmod(t * 3.0, 1.0)
			var drop: float = seg if seg < 0.75 else (1.0 - (seg - 0.75) / 0.25)
			_act = {"w": _bell(t), "open": 0.35 - 0.25 * drop, "pupil": 0.20,
					"ear": -16.0, "lower": 0.35}
			# Differentiator: a big vertical fall and snap-back, three times over.
			_act_offset = Vector2(0.0, R * 0.24 * drop)
			_act_tilt = deg_to_rad(14.0 * drop)

		Action.DREAM_TWITCH:
			# Shut tight, with involuntary jerks. The high power keeps them
			# sparse and sudden rather than a steady wobble.
			# Differentiator from the other closed-eye poses: the jerks are
			# ASYMMETRIC - one ear and one whisker set kick at a time.
			var j := pow(_pulse(t, 7.0), 6.0)
			_act_ear_split = 30.0 * j
			_act = {"w": 1.0, "open": 0.04, "ear": -10.0 + 14.0 * j,
					"whisk": 1.0 + 0.30 * j, "lower": 0.30, "nose": 1.0 + 0.15 * j}
			_act_tilt = deg_to_rad(9.0 * j)
			_act_offset = Vector2(R * 0.04 * j, 0.0)

		Action.PERK_UP:
			var p := _spike(t, 0.18, 1.2)
			_act = {"w": p, "open": 1.20, "pupil": 0.85, "ear": 34.0, "splay": 2.0,
					"whisk": 1.28, "wpitch": -0.16, "eh": 1.25}
			_act_offset = Vector2(0.0, -R * 0.06 * p)

		Action.TRACK:
			# Eyes sweep across with the head lagging behind. Eyes always lead.
			# Differentiator: the pupils actually travel. Gaze is the entire
			# content of this action and it was previously barely weighted.
			_act_gaze = Vector2(sin(t * PI * 2.0) * 1.4, 0.0)
			_act_gaze_w = 1.0
			_act = {"w": _bell(t), "open": 1.10, "pupil": 0.50, "ear": 26.0,
					"splay": 5.0}
			_act_tilt = deg_to_rad(9.0 * sin(t * PI * 2.0 - 0.6))
			_act_offset = Vector2(sin(t * PI * 2.0 - 0.6) * R * 0.06, 0.0)

		Action.POUNCE:
			# Coil, hold, launch. The hold is what makes the launch land.
			var f: float
			if t < 0.45:
				f = -0.5 * CatDraw.smoothstep01(t / 0.45)
			elif t < 0.55:
				f = -0.5
			elif t < 0.72:
				f = lerpf(-0.5, 1.0, CatDraw.smoothstep01((t - 0.55) / 0.17))
			else:
				f = 1.0 - CatDraw.smoothstep01((t - 0.72) / 0.28)
			_act_scale = 1.0 + 0.15 * f
			_act_offset = Vector2(0.0, R * 0.13 * f)
			_act_lock_gaze = true
			_act_no_blink = true
			_act = {"w": 1.0, "open": 1.15, "pupil": 0.06, "ear": 30.0,
					"splay": 2.0, "whisk": 1.30, "wpitch": -0.18, "mouth": -0.10}
			# Paws thrown forward on the launch only - tucked during the coil.
			var reach: float = maxf(0.0, f)
			# Low and wide. At the old height these sat across both eyes.
			_paw_l = {"on": reach, "pos": Vector2(-0.82 - 0.10 * reach, 1.04 - 0.10 * reach),
					"rot": 26.0, "size": 1.05}
			_paw_r = {"on": reach, "pos": Vector2(0.82 + 0.10 * reach, 1.04 - 0.10 * reach),
					"rot": -26.0, "size": 1.05}

		Action.RECOIL:
			var d := pow(1.0 - t, 1.8)
			_act = {"w": d, "open": 1.30, "pupil": 0.95, "ear": -30.0,
					"splay": 30.0, "eh": 1.35}
			_act_offset = Vector2(0.0, -R * 0.20 * d)
			_act_scale = 1.0 - 0.10 * d

		Action.CROUCH:
			# Differentiator: silent, mouth shut, and dropped much further than
			# any other pose - crouching is defined by the fall, not the face.
			var c := _bell(t)
			_act = {"w": c, "open": 1.05, "pupil": 0.12, "ear": 24.0, "splay": 6.0,
					"whisk": 1.20, "wpitch": -0.10, "eh": 0.92, "mouth": 0.0}
			_act_scale = 1.0 - 0.16 * c
			_act_offset = Vector2(0.0, R * 0.26 * c)

		Action.ZOOMIES:
			var z := _bell(t)
			_act = {"w": z, "open": 1.25, "pupil": 0.95, "ear": 20.0,
					"splay": 16.0, "mouth": 0.60, "whisk": 1.30}
			_act_offset = Vector2(sin(_act_t * 21.0) * R * 0.09 * z,
				sin(_act_t * 27.0 + 1.0) * R * 0.05 * z)
			_act_tilt = deg_to_rad(sin(_act_t * 18.0) * 12.0 * z)

		Action.WINK:
			# One eye only. Everything else holds still, which is the point.
			# Blinking is suppressed: an idle blink landing mid-wink shuts the
			# other eye too and the whole gag disappears.
			var w := sin(clampf(t, 0.0, 1.0) * PI)
			_act_wink = w
			_act_no_blink = true
			_act = {"w": 1.0, "mouth": 0.70, "lower": 0.30 + 0.20 * w}
			_act_tilt = deg_to_rad(4.0 * w)

		Action.SMUG:
			# Differentiator: BOTH eyes half-lidded with a one-sided smirk. An
			# asymmetric squint reads as a wink, not as smugness.
			var s := _bell(t)
			_act_mouth_split = 0.9 * s
			_act = {"w": s, "open": 0.52, "lower": 0.52, "pupil": 0.40,
					"mouth": 0.75, "ear": 10.0, "splay": 16.0, "etilt": 8.0}
			_act_tilt = deg_to_rad(9.0 * s)

		Action.CONFUSED:
			# Head cocks one way and the eyes disagree about it.
			# Differentiator: a real head TILT plus mismatched ears. The old
			# version leaned on an eye asymmetry that just read as a wink.
			var c := sin(t * PI)
			_act_ear_split = 26.0 * c
			_act = {"w": _bell(t), "open": 1.08, "pupil": 0.75, "ear": 8.0,
					"splay": 20.0, "mouth": -0.15, "etilt": -6.0}
			_act_tilt = deg_to_rad(26.0 * c)
			_act_offset = Vector2(R * 0.06 * c, 0.0)

		Action.SAD:
			var s := _bell(t)
			_act = {"w": s, "open": 0.62, "pupil": 0.80, "ear": -26.0,
					"splay": 28.0, "mouth": -0.75, "lower": 0.10,
					"whisk": 0.80, "wpitch": 0.22, "etilt": -14.0}
			_act_offset = Vector2(0.0, R * 0.06 * s)
			_act_tilt = deg_to_rad(-4.0 * s)

		Action.PLEAD:
			# Saucer eyes, ears forward, head tipped. Maximum leverage.
			var p := _bell(t)
			_act = {"w": p, "open": 1.25, "pupil": 1.00, "ear": 28.0, "splay": 8.0,
					"mouth": 0.40, "eh": 1.30, "etilt": -10.0, "lower": 0.05}
			_act_tilt = deg_to_rad(13.0 * p)
			_act_offset = Vector2(0.0, -R * 0.03 * p)
			# Both paws pressed together under the chin. Begging needs hands.
			_paw_l = {"on": p, "pos": Vector2(-0.25, 1.06), "rot": 16.0, "size": 0.88}
			_paw_r = {"on": p, "pos": Vector2(0.25, 1.06), "rot": -16.0, "size": 0.88}

		Action.EXCITED:
			var e := _bell(t)
			_act = {"w": e, "open": 1.20, "pupil": 0.90, "ear": 30.0, "splay": 8.0,
					"mouth": 0.95, "whisk": 1.25}
			_act_offset = Vector2(0.0, sin(_act_t * 13.0) * R * 0.055 * e)
			_act_scale = 1.0 + 0.05 * sin(_act_t * 13.0) * e
			# Paws up and bouncing in antiphase with the head.
			var bo := sin(_act_t * 13.0 + PI)
			_paw_l = {"on": e, "pos": Vector2(-0.62, 1.02 + 0.09 * bo), "rot": 20.0, "size": 0.92}
			_paw_r = {"on": e, "pos": Vector2(0.62, 1.02 - 0.09 * bo), "rot": -20.0, "size": 0.92}

		Action.CONTENT_SIGH:
			# Differentiator: the exhale is visible - the mouth parts and the
			# whiskers sag rather than lifting.
			var s := sin(clampf(t / 0.8, 0.0, 1.0) * PI)
			_act = {"w": 1.0, "open": 0.45 - 0.20 * s, "lower": 0.50,
					"mouth": 0.65, "ear": 6.0 - 18.0 * s, "talk": 0.42 * s,
					"whisk": 1.0 - 0.24 * s, "wpitch": 0.26 * s}
			_act_offset = Vector2(0.0, R * 0.12 * s)

		Action.DISGUST:
			# Differentiator: pulls back and away with the nose wrinkled hard and
			# the mouth cracked in distaste.
			var d := _spike(t, 0.25, 1.6)
			_act_mouth_split = -0.5 * d
			_act = {"w": d, "jaw": 0.20 * d, "curl": 0.65, "mw": 0.90,
					"open": 0.46, "lower": 0.45, "pupil": 0.20,
					"ear": -24.0, "splay": 28.0, "mouth": -0.95, "nose": 1.35,
					"whisk": 0.75, "wpitch": 0.16, "etilt": 12.0}
			_act_offset = Vector2(-R * 0.06 * d, -R * 0.16 * d)
			_act_tilt = deg_to_rad(-11.0 * d)
			_act_scale = 1.0 - 0.07 * d

		Action.SNEER:
			# Differentiator: a one-sided upper-lip raise, which is what a sneer
			# actually is - not a squint.
			# Split kept moderate: at full magnitude one lip hooked back past its
			# own control point and the mouth rendered as a crossed X. The lip
			# raise plus a cracked jaw carries the sneer without it.
			var s := _bell(t)
			_act_mouth_split = -0.5 * s
			_act = {"w": s, "jaw": 0.18, "curl": 0.30, "mw": 0.94, "open": 0.72,
					"pupil": 0.25, "ear": -14.0, "splay": 24.0, "mouth": -0.30,
					"nose": 1.22, "etilt": 14.0}
			_act_tilt = deg_to_rad(-6.0 * s)

		Action.ALERT_SOUND:
			# Ears find it first; the head arrives a beat later.
			var ear_t := CatDraw.smoothstep01(minf(t / 0.25, 1.0))
			var head_t := CatDraw.smoothstep01(clampf((t - 0.28) / 0.35, 0.0, 1.0))
			_act = {"w": _bell(t), "open": 1.15, "pupil": 0.80,
					"ear": 8.0 + 26.0 * ear_t, "splay": 14.0 - 11.0 * ear_t}
			_act_tilt = deg_to_rad(15.0 * head_t)
			_act_gaze = Vector2(0.8, -0.1)
			_act_gaze_w = head_t

		Action.SLOW_TURN:
			var a := sin(t * PI)
			_act = {"w": _bell(t), "open": 1.0, "pupil": 0.50}
			_act_tilt = deg_to_rad(20.0 * a)
			_act_offset = Vector2(R * 0.09 * a, 0.0)
			_act_gaze = Vector2(0.7, 0.0)
			_act_gaze_w = a

		Action.BUNT:
			# A press rather than a shove - softer than the head bonk.
			# Differentiator: a sideways lean rather than a forward drive - a
			# cheek press, not a shove.
			var f := sin(clampf(t, 0.0, 1.0) * PI)
			_act_scale = 1.0 + 0.06 * f
			_act_offset = Vector2(R * 0.20 * f, R * 0.03 * f)
			# Kept shallow deliberately: a big tilt on a nearly-shut eye rotates
			# the lid into a diagonal slash across the face.
			_act_tilt = deg_to_rad(11.0 * f)
			_act = {"w": 1.0, "open": 0.42 - 0.18 * f, "lower": 0.58,
					"mouth": 0.85, "ear": 12.0}

		Action.KNEAD:
			# Differentiator: a slow rhythmic vertical push, alternating sides so
			# it reads as paws working rather than a bob.
			var k := _pulse(t, 5.0)
			_act_ear_split = sin(t * PI * 5.0) * 12.0
			_act = {"w": _bell(t), "open": 0.40, "lower": 0.58, "pupil": 0.45,
					"mouth": 0.75, "ear": 8.0, "splay": 16.0}
			_act_offset = Vector2(sin(t * PI * 5.0) * R * 0.07, R * 0.13 * k)
			_act_tilt = deg_to_rad(sin(t * PI * 5.0) * 9.0)
			# Two paws pushing alternately - the actual kneading. They are in
			# antiphase, which is the whole read.
			var kn := sin(t * PI * 5.0)
			_paw_l = {"on": _bell(t), "pos": Vector2(-0.50, 1.08 - 0.09 * kn),
					"rot": 12.0 * kn, "size": 0.95}
			_paw_r = {"on": _bell(t), "pos": Vector2(0.50, 1.08 + 0.09 * kn),
					"rot": 12.0 * kn, "size": 0.95}

		Action.SQUINT_SUN:
			# Differentiator: this is turning AWAY from glare - head tips back and
			# aside, gaze goes with it, nose wrinkles.
			var s := _bell(t)
			_act = {"w": s, "open": 0.30, "lower": 0.52, "pupil": 0.06,
					"ear": -2.0, "splay": 20.0, "mouth": 0.20, "nose": 1.24,
					"ew": 1.20}
			_act_tilt = deg_to_rad(-8.0 * s)
			_act_offset = Vector2(-R * 0.08 * s, -R * 0.04 * s)
			_act_gaze = Vector2(0.55, -0.5)
			_act_gaze_w = s

		Action.STARE:
			# Nothing moves. No saccades, no drift, no blink.
			# Differentiator: absolutely nothing moves, and the pupils go full
			# round. Ear swivel is all ear motion; this is the absence of it.
			_act_lock_gaze = true
			_act_no_blink = true
			_act = {"w": _bell(t), "open": 1.18, "pupil": 1.0, "ear": 18.0,
					"splay": 10.0, "mouth": 0.0, "eh": 1.12, "ew": 1.06}

		Action.GLANCE_BACK:
			var a := sin(t * PI)
			_act_gaze = Vector2(-0.95, -0.1)
			_act_gaze_w = a
			_act = {"w": _bell(t), "open": 1.10, "pupil": 0.70, "ear": 18.0}
			_act_tilt = deg_to_rad(-10.0 * a)

		Action.SURPRISE:
			var s := _spike(t, 0.10, 1.5)
			_act = {"w": s, "open": 1.40, "pupil": 1.00, "ear": 32.0, "splay": 4.0,
					"eh": 1.45, "ew": 1.12, "mouth": 0.0, "talk": 0.35 * s,
					"whisk": 1.30, "wpitch": -0.20}
			_act_offset = Vector2(0.0, -R * 0.07 * s)

		Action.RELIEF:
			# Differentiator: the head tips back on the out-breath, chin up.
			var r := sin(clampf(t / 0.7, 0.0, 1.0) * PI)
			_act = {"w": 1.0, "open": 0.50 - 0.35 * r, "lower": 0.50,
					"mouth": 0.60, "ear": 4.0 - 14.0 * r, "talk": 0.48 * r}
			_act_offset = Vector2(0.0, -R * 0.05 * r)
			_act_tilt = deg_to_rad(-16.0 * r)

		Action.CURIOUS_SNIFF:
			# Leans in first, then works the nose over whatever it found.
			var lean := CatDraw.smoothstep01(minf(t / 0.35, 1.0))
			var p := _pulse(t, 10.0) * lean
			_act = {"w": _bell(t), "open": 1.12, "pupil": 0.85, "ear": 30.0,
					"splay": 5.0, "nose": 1.0 + 0.28 * p, "whisk": 1.22,
					"wpitch": -0.14, "etilt": -8.0}
			_act_scale = 1.0 + 0.10 * lean
			_act_offset = Vector2(0.0, R * 0.07 * lean)
			_act_tilt = deg_to_rad(9.0 * lean)

		Action.PLAY_WIGGLE:
			# Differentiator: big side-to-side travel. The old 0.045R amplitude
			# was about six pixels - the peak frame looked like a static face.
			var w := _bell(t)
			_act_lock_gaze = true
			_act = {"w": w, "open": 1.15, "pupil": 0.15, "ear": 30.0, "splay": 4.0,
					"whisk": 1.30, "mouth": 0.30}
			_act_offset = Vector2(sin(_act_t * 13.0) * R * 0.17 * w,
				absf(sin(_act_t * 13.0)) * R * 0.04 * w)
			_act_tilt = deg_to_rad(sin(_act_t * 13.0) * 13.0 * w)
			# Paws planted low and wide - the braced, about-to-spring stance.
			_paw_l = {"on": w, "pos": Vector2(-0.66, 1.10), "rot": 8.0, "size": 1.0}
			_paw_r = {"on": w, "pos": Vector2(0.66, 1.10), "rot": -8.0, "size": 1.0}

		Action.ANNOYED_FLICK:
			# Differentiator: ONE ear snaps back on its own. That single asymmetric
			# flick is the whole identity of this action.
			var f := _spike(t, 0.2, 2.5)
			_act_ear_split = -46.0 * f
			_act = {"w": _bell(t), "open": 0.72, "pupil": 0.20,
					"ear": -8.0, "splay": 24.0, "mouth": -0.35, "etilt": 10.0}
			_act_gaze = Vector2(0.0, -0.85)
			_act_gaze_w = f

		Action.GREET:
			# A slow blink running straight into a trill - the full hello.
			if t < 0.5:
				var e := 1.0 - sin(CatDraw.smoothstep01(t / 0.5) * PI)
				_act = {"w": 1.0, "open": e, "mouth": 0.60, "lower": 0.30,
						"ear": 12.0}
			else:
				var b := (t - 0.5) / 0.5
				_act = {"w": 1.0, "talk": _pulse(b, 6.0) * 0.40, "open": 1.05,
						"ear": 26.0, "splay": 6.0, "mouth": 0.80, "whisk": 1.20}
				_act_offset = Vector2(0.0, -R * 0.03 * sin(b * PI))
				# A raised paw waving on the second half - the hello lands here.
				# Clear of the eye. At x 0.70 the paw landed squarely on the right
				# pupil - the eye's outer edge already reaches 0.90.
				_paw_r = {"on": sin(b * PI), "pos": Vector2(1.16, 0.26),
						"rot": -18.0 + sin(b * PI * 5.0) * 22.0, "size": 1.0}

		Action.SETTLE:
			# Differentiator: ends lower than any other pose in the set - this is
			# the resolved bottom of a downward drift.
			var s := CatDraw.smoothstep01(t)
			_act = {"w": _bell(t), "open": 0.55 - 0.20 * s, "lower": 0.45,
					"pupil": 0.35, "ear": 4.0 - 10.0 * s, "splay": 14.0,
					"mouth": 0.55, "whisk": 0.95}
			_act_offset = Vector2(0.0, R * 0.20 * s)
			_act_scale = 1.0 - 0.05 * s

	# Body layer, applied over whatever the action set for itself. Every action
	# selects a motion, so none can be static by omission - which is how roughly
	# a third of the set ended up frozen at its peak frame.
	var motion: Array = BODY_FOR.get(_action, [Body.DRIFT, 1.0])
	_body(int(motion[0]), t, R, float(motion[1]))

	# An action's own motion and the body layer's motion STACK, and unbounded
	# they compound into displacement larger than the spacing between features -
	# at which point the parts stop grouping and it reads as debris, not a face.
	_act_tilt = clampf(_act_tilt, -deg_to_rad(24.0), deg_to_rad(24.0))
	_act_scale = clampf(_act_scale, 0.84, 1.20)
	_act_offset.x = clampf(_act_offset.x, -R * 0.26, R * 0.26)
	_act_offset.y = clampf(_act_offset.y, -R * 0.26, R * 0.30)


# ---------------------------------------------------------------------------
# Composable expression channels
# ---------------------------------------------------------------------------
# The 60 named actions are curated presets - hand-tuned, and worth keeping. This
# is the other half of the Loona/Looi model: independent channels that COMBINE,
# so the expression count is a product rather than a list somebody had to author.
#
#   12 eye x 10 ear x 9 jaw x 20 body = 21,600 distinct expressions
#
# from about fifty definitions. Call play_composed() to drive any combination;
# the assistant layer can pick channels from intent rather than hunting for a
# preset that happens to match.

enum EyeL { NEUTRAL, WIDE, SAUCER, NARROW, SLIT, CLOSED, SQUINT, SOFT, HARD, SLEEPY, WOUNDED, ALERT }
enum EarL { NEUTRAL, FORWARD, PERK, BACK, FLAT, SPLAY, DROOP, SWIVEL, ONE_BACK, ALERT }
enum JawL { SHUT, PARTED, WIDE, CHATTER, PULSE, SNARL, SNEER, LICK, PURSE }


func _eye_layer(k: int, t: float) -> Dictionary:
	match k:
		EyeL.WIDE:    return {"open": 1.15, "eh": 1.18, "pupil": 0.70}
		EyeL.SAUCER:  return {"open": 1.30, "eh": 1.40, "ew": 1.08, "pupil": 1.0}
		EyeL.NARROW:  return {"open": 0.70, "eh": 0.72, "ew": 1.10, "lower": 0.35}
		EyeL.SLIT:    return {"open": 1.05, "eh": 0.80, "ew": 1.16, "pupil": 0.05}
		EyeL.CLOSED:  return {"open": 0.06, "lower": 0.30}
		EyeL.SQUINT:  return {"open": 0.34, "eh": 0.60, "ew": 1.18, "lower": 0.52, "pupil": 0.10}
		EyeL.SOFT:    return {"open": 0.55, "eh": 0.70, "lower": 0.55, "pupil": 0.60}
		EyeL.HARD:    return {"open": 0.95, "eh": 0.86, "etilt": 16.0, "pupil": 0.08}
		EyeL.SLEEPY:  return {"open": 0.26, "eh": 0.34, "lower": 0.30, "pupil": 0.20}
		EyeL.WOUNDED: return {"open": 0.70, "eh": 0.92, "etilt": -15.0, "pupil": 0.90}
		EyeL.ALERT:   return {"open": 1.12, "eh": 1.22, "pupil": 0.80}
		_:            return {"open": 1.0, "eh": 1.0, "pupil": 0.45}


func _ear_layer(k: int, t: float) -> Dictionary:
	match k:
		EarL.FORWARD:  return {"ear": 24.0, "splay": 6.0}
		EarL.PERK:     return {"ear": 34.0, "splay": 2.0}
		EarL.BACK:     return {"ear": -22.0, "splay": 22.0}
		EarL.FLAT:     return {"ear": -38.0, "splay": 32.0}
		EarL.SPLAY:    return {"ear": 2.0, "splay": 28.0}
		EarL.DROOP:    return {"ear": -28.0, "splay": 26.0}
		EarL.ALERT:    return {"ear": 30.0, "splay": 4.0}
		EarL.SWIVEL:
			# Opposing sweep - the only ear pose that needs the split channel.
			_act_ear_split = sin(t * PI * 3.0) * 34.0
			return {"ear": 16.0, "splay": 8.0}
		EarL.ONE_BACK:
			_act_ear_split = -44.0 * _spike(t, 0.25, 2.2)
			return {"ear": -6.0, "splay": 22.0}
		_:             return {"ear": 0.0, "splay": 12.0}


func _jaw_layer(k: int, t: float) -> Dictionary:
	match k:
		JawL.PARTED:  return {"jaw": 0.26, "mw": 1.0}
		JawL.WIDE:    return {"jaw": 0.92, "mw": 1.14}
		JawL.PURSE:   return {"jaw": 0.62, "mw": 0.66}
		JawL.CHATTER: return {"jaw": 0.12 + _pulse(t, 24.0) * 0.20, "mw": 0.88, "curl": 0.5}
		JawL.PULSE:   return {"jaw": 0.14 + _pulse(t, 9.0) * 0.28, "mw": 0.80}
		JawL.SNARL:   return {"jaw": 0.80, "mw": 1.30, "curl": 1.0}
		JawL.SNEER:   return {"jaw": 0.16, "mw": 0.94, "curl": 0.30}
		JawL.LICK:    return {"jaw": 0.22 + _pulse(t, 3.0) * 0.30, "mw": 0.92}
		_:            return {"jaw": 0.0, "mw": 1.0}


## Play an expression assembled from independent channels rather than a preset.
## This is the combinatorial path - the assistant can choose each channel from
## intent instead of searching for a named action that happens to fit.
func play_composed(eye: int, ear: int, jaw: int, body: int,
		duration: float = 1.6, body_amp: float = 1.0) -> void:
	_composed = {"eye": eye, "ear": ear, "jaw": jaw, "body": body, "amp": body_amp}
	_action = Action.NONE
	_act_t = 0.0
	_act_dur = maxf(0.2, duration)


func _update_composed(delta: float, R: float) -> void:
	_act_t += delta
	if _act_t >= _act_dur:
		_composed = {}
		return

	var t: float = _act_t / _act_dur
	var merged := {"w": _bell(t)}
	for src in [_eye_layer(int(_composed["eye"]), t),
			_ear_layer(int(_composed["ear"]), t),
			_jaw_layer(int(_composed["jaw"]), t)]:
		for key in src:
			merged[key] = src[key]
	_act = merged
	_body(int(_composed["body"]), t, R, float(_composed["amp"]))


# ---------------------------------------------------------------------------
# Body layer - an independently addressable motion channel
# ---------------------------------------------------------------------------
# Loona and Looi do not ship a thousand hand-authored animations; they combine a
# handful of channels (face x ears x body x voice) and the count falls out of the
# product. This is the first of those channels extracted from the per-action
# bundles: every action now SELECTS a body motion rather than hand-rolling one,
# which is why no action can be static any more.

enum Body {
	DRIFT, BOB, SWAY, NOD, LEAN_IN, PULL_BACK, SINK, RISE,
	SHAKE_H, SHAKE_V, BUZZ, WIGGLE, ORBIT, COIL, RECOIL,
	SNAP_FWD, STAGGER, TIP, FLOAT_UP, LURCH,
}

## Body motion + amplitude for every action. Nothing maps to "nothing".
const BODY_FOR := {
	1: [Body.DRIFT, 0.7], 2: [Body.BUZZ, 1.0], 3: [Body.RECOIL, 1.0],
	4: [Body.FLOAT_UP, 1.0], 5: [Body.SWAY, 1.1], 6: [Body.COIL, 0.8],
	7: [Body.NOD, 0.8], 8: [Body.SNAP_FWD, 1.0], 9: [Body.LURCH, 1.0],
	10: [Body.SINK, 1.0], 11: [Body.DRIFT, 0.5], 12: [Body.STAGGER, 1.0],
	13: [Body.DRIFT, 0.6], 14: [Body.SWAY, 1.0], 15: [Body.NOD, 0.7],
	16: [Body.TIP, 0.9], 17: [Body.SNAP_FWD, 0.9], 18: [Body.BUZZ, 1.2],
	19: [Body.FLOAT_UP, 0.8], 20: [Body.LEAN_IN, 1.0], 21: [Body.LEAN_IN, 0.7],
	22: [Body.DRIFT, 0.8], 23: [Body.SWAY, 1.2], 24: [Body.BUZZ, 1.4],
	25: [Body.SHAKE_H, 1.0], 26: [Body.BUZZ, 1.6], 27: [Body.FLOAT_UP, 1.2],
	28: [Body.RISE, 1.0], 29: [Body.SINK, 1.2], 30: [Body.NOD, 1.6],
	31: [Body.STAGGER, 0.8], 32: [Body.RISE, 1.0], 33: [Body.ORBIT, 0.9],
	34: [Body.COIL, 1.3], 35: [Body.RECOIL, 1.2], 36: [Body.SINK, 1.3],
	37: [Body.WIGGLE, 1.6], 38: [Body.NOD, 0.6], 39: [Body.TIP, 0.8],
	40: [Body.TIP, 1.2], 41: [Body.SINK, 0.9], 42: [Body.LEAN_IN, 0.8],
	43: [Body.BOB, 1.4], 44: [Body.SINK, 0.8], 45: [Body.PULL_BACK, 1.1],
	46: [Body.TIP, 0.7], 47: [Body.TIP, 1.0], 48: [Body.SWAY, 1.3],
	49: [Body.LURCH, 0.8], 50: [Body.NOD, 1.0], 51: [Body.TIP, 0.8],
	52: [Body.DRIFT, 0.4], 53: [Body.SWAY, 1.1], 54: [Body.RECOIL, 0.9],
	55: [Body.FLOAT_UP, 0.9], 56: [Body.LEAN_IN, 1.2], 57: [Body.WIGGLE, 1.3],
	58: [Body.SHAKE_H, 0.7], 59: [Body.BOB, 0.9], 60: [Body.SINK, 1.0],
}


## Apply a body motion. Additive on top of whatever the action already set, so an
## action can still hand-author extra motion where it needs something specific.
func _body(kind: int, t: float, R: float, amp: float) -> void:
	var e := _bell(t)          # fades the motion in and out with the action
	match kind:
		Body.DRIFT:
			# Deliberately not "still": a slow wander so a held pose still
			# breathes. This is what stops the quiet actions reading as frozen.
			_act_offset += Vector2(sin(_act_t * 0.9) * R * 0.030,
				sin(_act_t * 0.7 + 1.1) * R * 0.022) * amp * e
			_act_tilt += deg_to_rad(sin(_act_t * 0.6) * 2.2 * amp * e)

		Body.BOB:
			_act_offset += Vector2(0.0, sin(t * PI * 4.0) * R * 0.085 * amp * e)
			_act_scale += 0.030 * sin(t * PI * 4.0) * amp * e

		Body.SWAY:
			_act_offset += Vector2(sin(t * PI * 2.0) * R * 0.13 * amp * e, 0.0)
			_act_tilt += deg_to_rad(sin(t * PI * 2.0) * 9.0 * amp * e)

		Body.NOD:
			var n := sin(t * PI * 3.0)
			_act_offset += Vector2(0.0, n * R * 0.075 * amp * e)
			_act_tilt += deg_to_rad(n * 6.0 * amp * e)

		Body.LEAN_IN:
			var l := sin(t * PI)
			_act_scale += 0.13 * l * amp
			_act_offset += Vector2(0.0, R * 0.10 * l * amp)

		Body.PULL_BACK:
			var p := sin(t * PI)
			_act_scale -= 0.10 * p * amp
			_act_offset += Vector2(0.0, -R * 0.13 * p * amp)

		Body.SINK:
			var s := CatDraw.smoothstep01(t)
			_act_offset += Vector2(0.0, R * 0.16 * s * amp)
			_act_scale -= 0.045 * s * amp

		Body.RISE:
			var r := CatDraw.smoothstep01(t)
			_act_offset += Vector2(0.0, -R * 0.13 * r * amp)
			_act_scale += 0.045 * r * amp

		Body.SHAKE_H:
			var k := sin(t * PI * 9.0) * (1.0 - t)
			_act_offset += Vector2(k * R * 0.17 * amp, 0.0)
			_act_tilt += deg_to_rad(k * 15.0 * amp)

		Body.SHAKE_V:
			var k2 := sin(t * PI * 9.0) * (1.0 - t)
			_act_offset += Vector2(0.0, k2 * R * 0.12 * amp)

		Body.BUZZ:
			# Fast and small. Reads as vibration rather than displacement.
			_act_offset += Vector2(sin(_act_t * 38.0) * R * 0.016,
				cos(_act_t * 44.0) * R * 0.011) * amp * e

		Body.WIGGLE:
			# Builds through the action - the pre-pounce shimmy.
			var ramp := CatDraw.smoothstep01(t / 0.75)
			_act_offset += Vector2(sin(_act_t * 14.0) * R * 0.15 * ramp * amp,
				absf(sin(_act_t * 14.0)) * R * 0.035 * ramp * amp)
			_act_tilt += deg_to_rad(sin(_act_t * 14.0) * 11.0 * ramp * amp)

		Body.ORBIT:
			_act_offset += Vector2(sin(t * PI * 2.0), cos(t * PI * 2.0) * 0.55) \
				* R * 0.10 * amp * e

		Body.COIL:
			# Compress, hold, release. The hold is what makes the release land.
			var c: float
			if t < 0.45:
				c = -CatDraw.smoothstep01(t / 0.45)
			elif t < 0.60:
				c = -1.0
			else:
				c = lerpf(-1.0, 0.55, CatDraw.smoothstep01((t - 0.60) / 0.40))
			_act_scale += 0.11 * c * amp
			_act_offset += Vector2(0.0, R * 0.10 * c * amp)

		Body.RECOIL:
			var d := pow(1.0 - t, 1.9)
			_act_offset += Vector2(0.0, -R * 0.17 * d * amp)
			_act_scale -= 0.075 * d * amp
			_act_tilt += deg_to_rad(sin(_act_t * 26.0) * 3.5 * d * amp)

		Body.SNAP_FWD:
			var f := _spike(t, 0.30, 2.2)
			_act_scale += 0.16 * f * amp
			_act_offset += Vector2(0.0, R * 0.13 * f * amp)

		Body.STAGGER:
			# Irregular jerks rather than a clean oscillation.
			var j := pow(_pulse(t, 5.0), 4.0)
			_act_offset += Vector2(sin(_act_t * 9.0) * R * 0.10 * j * amp,
				R * 0.05 * j * amp)
			_act_tilt += deg_to_rad(sin(_act_t * 11.0) * 9.0 * j * amp)

		Body.TIP:
			# Rotate over and hold there - the head-cock family.
			var tp := sin(t * PI)
			_act_tilt += deg_to_rad(17.0 * tp * amp)
			_act_offset += Vector2(R * 0.055 * tp * amp, 0.0)

		Body.FLOAT_UP:
			var u := sin(t * PI)
			_act_offset += Vector2(0.0, -R * 0.09 * u * amp)
			_act_tilt += deg_to_rad(-7.0 * u * amp)
			_act_scale += 0.05 * u * amp

		Body.LURCH:
			# Asymmetric: wind back slowly, throw forward fast.
			var lu: float
			if t < 0.55:
				lu = -0.45 * CatDraw.smoothstep01(t / 0.55)
			else:
				lu = pow(1.0 - (t - 0.55) / 0.45, 2.0)
			_act_offset += Vector2(0.0, R * 0.14 * lu * amp)
			_act_tilt += deg_to_rad(-9.0 * lu * amp)
			_act_scale += 0.08 * maxf(0.0, lu) * amp


## Blend the action's overrides over the sprung mood pose. Weighted rather than
## assigned, so actions fade in and out instead of snapping.
func _apply_action() -> void:
	if _act.is_empty():
		return
	var w: float = clampf(_act.get("w", 1.0), 0.0, 1.0)
	if _act.has("open"):   _eye_open = lerpf(_eye_open, _act["open"], w)
	if _act.has("pupil"):  _pupil = lerpf(_pupil, _act["pupil"], w)
	if _act.has("ear"):    _ear_pitch = lerpf(_ear_pitch, _act["ear"], w)
	if _act.has("splay"):  _ear_splay = lerpf(_ear_splay, _act["splay"], w)
	if _act.has("mouth"):  _mouth_curve = lerpf(_mouth_curve, _act["mouth"], w)
	if _act.has("lower"):  _lower_lid = lerpf(_lower_lid, _act["lower"], w)
	if _act.has("whisk"):  _whisk = lerpf(_whisk, _act["whisk"], w)
	if _act.has("wpitch"): _whisk_pitch = lerpf(_whisk_pitch, _act["wpitch"], w)
	if _act.has("ew"):     _eye_w = lerpf(_eye_w, _act["ew"], w)
	if _act.has("eh"):     _eye_h = lerpf(_eye_h, _act["eh"], w)
	if _act.has("etilt"):  _eye_tilt = lerpf(_eye_tilt, _act["etilt"], w)
	if _act.has("nose"):   _nose_flare = lerpf(_nose_flare, _act["nose"], w)
	if _act.has("talk"):   _talk = lerpf(_talk, _act["talk"], w)
	if _act.has("jaw"):    _act_jaw = _act["jaw"] * w
	if _act.has("curl"):   _act_lip_curl = _act["curl"] * w
	if _act.has("mw"):     _act_mouth_w = lerpf(1.0, _act["mw"], w)


# ---------------------------------------------------------------------------
# Animation layers
# ---------------------------------------------------------------------------
func _update_float(delta: float) -> void:
	var s := _bob_speed
	# Two offset sines: a single one reads mechanical, two read like breathing.
	_bob = sin(_t * 1.55 * s) * bob_amplitude + sin(_t * 0.73 * s + 1.3) * bob_amplitude * 0.42
	_sway = sin(_t * 0.62 * s + 0.7) * bob_amplitude * 0.55
	_tilt = deg_to_rad(sin(_t * 0.48 * s + 2.1) * 3.4 + _head_tilt)

	# Squash and stretch, critically damped so it never rings.
	_bob_vel = (_bob - _prev_bob) / maxf(0.0001, delta)
	_prev_bob = _bob
	var target := clampf(_bob_vel / 900.0, -0.6, 0.6) * 0.06
	var stiffness := 120.0
	var damping := 2.0 * sqrt(stiffness)
	_squash_vel += (target - _squash) * stiffness * delta - _squash_vel * damping * delta
	_squash = clampf(_squash + _squash_vel * delta, -0.12, 0.12)


func _update_gaze(delta: float) -> void:
	var want := Vector2.ZERO
	# A stalking cat does not glance around - the stare is the whole point.
	if _act_lock_gaze:
		_saccade = _saccade.lerp(Vector2.ZERO, clampf(delta * 6.0, 0.0, 1.0))
		_gaze = _gaze.lerp(Vector2.ZERO, clampf(delta * 4.0, 0.0, 1.0))
		_head_gaze = _head_gaze.lerp(Vector2.ZERO, clampf(delta * 4.0, 0.0, 1.0))
		return
	if follow_mouse:
		var to_mouse := get_global_mouse_position() - _head_center()
		# Normalise by a comfortable radius so nearby motion still reads.
		want = (to_mouse / (head_radius * 3.0)).limit_length(1.0)

	# Saccades: eyes jump and resettle rather than tracking perfectly smoothly.
	_saccade_timer += delta
	if _saccade_timer >= _next_saccade:
		_saccade_timer = 0.0
		_next_saccade = randf_range(0.55, 2.4)
		var spread := 0.42 if mood == Mood.THINKING else 0.14
		_saccade = Vector2(randf_range(-spread, spread), randf_range(-spread * 0.6, spread * 0.6))
	_saccade = _saccade.lerp(Vector2.ZERO, clampf(delta * 1.4, 0.0, 1.0))

	var target := (want + _saccade).limit_length(1.0)
	# An action steering the eyes somewhere specific overrides the mouse.
	if _act_gaze_w > 0.001:
		target = target.lerp(_act_gaze, clampf(_act_gaze_w, 0.0, 1.0))
	_gaze = _gaze.lerp(target, clampf(delta * 9.0, 0.0, 1.0))
	# The head tracks only the sustained component, and lazily.
	var head_target := want.limit_length(1.0)
	if _act_gaze_w > 0.001:
		head_target = head_target.lerp(_act_gaze, clampf(_act_gaze_w, 0.0, 1.0))
	_head_gaze = _head_gaze.lerp(head_target, clampf(delta * 3.0, 0.0, 1.0))


func _update_blink(delta: float) -> void:
	if not _blinking:
		_blink_timer += delta
		if _blink_timer >= _next_blink:
			_blink_timer = 0.0
			_next_blink = randf_range(2.2, 6.0)
			_blinking = true
			_blink_elapsed = 0.0
			_queued_blinks = 1 if randf() < 0.18 else 0   # cats often double-blink

	if _blinking:
		_blink_elapsed += delta
		if _blink_elapsed <= BLINK_CLOSE:
			_blink = 1.0 - (_blink_elapsed / BLINK_CLOSE)      # fast, near-linear
		elif _blink_elapsed <= BLINK_CLOSE + BLINK_OPEN:
			# Opening is slower and eased. The asymmetry is what sells it.
			_blink = CatDraw.smoothstep01((_blink_elapsed - BLINK_CLOSE) / BLINK_OPEN)
		else:
			_blink = 1.0
			_blinking = false
			if _queued_blinks > 0:
				_queued_blinks -= 1
				_blinking = true
				_blink_elapsed = 0.0


func _update_ears(delta: float) -> void:
	for i in range(2):
		_ear_timer[i] += delta
		if _ear_timer[i] >= _ear_next[i]:
			_ear_timer[i] = 0.0
			_ear_next[i] = randf_range(1.6, 5.5)
			_ear_twitch[i] = 1.0
		if _ear_twitch[i] > 0.0:
			_ear_twitch[i] = maxf(0.0, _ear_twitch[i] - delta * 3.2)

	# Nose twitch, on its own schedule. Small, frequent, and almost subliminal -
	# it is the kind of idle tic that stops a face looking paused.
	_nose_timer += delta
	if _nose_timer >= _nose_next:
		_nose_timer = 0.0
		_nose_next = randf_range(1.3, 4.5)
		_nose_twitch = 1.0
	if _nose_twitch > 0.0:
		_nose_twitch = maxf(0.0, _nose_twitch - delta * 4.5)


func _update_whiskers(delta: float) -> void:
	# Whiskers lag the head: feed this frame's motion into a damped spring.
	# _bob_vel is cached in _update_float because _prev_bob has already been
	# advanced by the time this runs.
	var impulse := Vector2(_tilt - _prev_tilt, _bob_vel * 0.0004)
	_prev_tilt = _tilt

	var stiffness := 46.0
	var damping := 2.0 * sqrt(stiffness) * 0.85
	_whisker_vel += (impulse * 40.0 - _whisker_offset * stiffness) * delta
	_whisker_vel -= _whisker_vel * damping * delta
	_whisker_offset += _whisker_vel * delta
	_whisker_offset = _whisker_offset.limit_length(0.35)


func _update_mouth(delta: float) -> void:
	# Chatter on our own while speaking with no external amplitude feed.
	if _speaking and _talk_target <= 0.001:
		var chatter := sin(_t * 17.0) * 0.5 + 0.5
		var envelope := sin(_t * 3.3) * 0.35 + 0.65
		_talk_target = clampf(chatter * envelope, 0.0, 1.0)
	_talk = lerpf(_talk, _talk_target, clampf(delta * 22.0, 0.0, 1.0))


func _head_center() -> Vector2:
	var vp := get_viewport_rect().size
	return Vector2(vp.x * 0.5, vp.y * 0.5)


func _toggle_overlay() -> void:
	# Would reconfigure the editor's own window if allowed to run at edit time.
	if Engine.is_editor_hint():
		return
	_overlay = not _overlay
	var w := get_window()
	w.borderless = _overlay
	w.transparent = _overlay
	w.always_on_top = _overlay


# ---------------------------------------------------------------------------
# Draw
# ---------------------------------------------------------------------------

## Push a colour above 1.0 so it crosses the environment's glow threshold.
## Multiplying the Color directly would scale alpha too and blow out fades.
func _hdr(c: Color, k: float = 1.0) -> Color:
	var g := glow_boost * k
	return Color(c.r * g, c.g * g, c.b * g, c.a)


## Stroke a closed shape as a glowing line.
func _stroke(poly: PackedVector2Array, c: Color, width: float) -> void:
	if poly.size() < 2:
		return
	var closed := poly
	closed.append(poly[0])
	draw_polyline(closed, c, width, true)


## Soft radial glow via the falloff sprite. Use this for anything atmospheric -
## never a filled circle, which always shows its rim.
func _soft_glow(center: Vector2, radius: float, c: Color, alpha: float) -> void:
	if _glow_tex == null:
		return
	var s := radius * 2.0
	draw_texture_rect(_glow_tex, Rect2(center - Vector2(s, s) * 0.5, Vector2(s, s)),
		false, Color(c.r, c.g, c.b, alpha))


## Project a face-local point onto a sphere and rotate it by the current gaze,
## returning the screen offset plus a depth scale. This is what sells the
## almost-3D read: features swing around a form instead of sliding on a plane,
## and the ones rotating away shrink.
func _project(local: Vector2, R: float) -> Dictionary:
	var rs := R * 1.25
	var z := sqrt(maxf(0.0, rs * rs - local.x * local.x - local.y * local.y))

	# Kept modest on purpose. At 24 degrees the ears - which sit furthest from
	# the pivot - swing far enough apart that the face stops reading as one head,
	# and the per-eye depth scale diverges enough to look like a sizing bug.
	var yaw := _head_gaze.x * deg_to_rad(14.0)
	var pitch := -_head_gaze.y * deg_to_rad(10.0)

	# Yaw about the vertical axis.
	var x2 := local.x * cos(yaw) + z * sin(yaw)
	var z2 := -local.x * sin(yaw) + z * cos(yaw)
	# Pitch about the horizontal axis.
	var y2 := local.y * cos(pitch) - z2 * sin(pitch)
	var z3 := local.y * sin(pitch) + z2 * cos(pitch)

	return {"pos": Vector2(x2, y2), "scale": lerpf(0.93, 1.05, clampf(z3 / rs, 0.0, 1.0))}


func _draw() -> void:
	var R := head_radius
	var center := _head_center() + Vector2(_sway, _bob) + _act_offset
	var scale := Vector2(1.0 - _squash * 0.5, 1.0 + _squash) * _act_scale

	# Motes sit in screen space so the head's tilt does not drag them around.
	draw_set_transform(_head_center(), 0.0, Vector2.ONE)
	_draw_motes(R)

	# Wide ambient bloom behind the whole face, unrotated so it stays stable.
	draw_set_transform(center, 0.0, Vector2.ONE)
	# Two nested falloffs: a very wide, very faint bed plus a tighter core. One
	# radius alone either reads as a hard blob or as nothing at all.
	_soft_glow(Vector2(0.0, R * 0.05), R * 3.4, eye_color, 0.05)
	_soft_glow(Vector2(0.0, R * 0.05), R * 1.9, eye_color, 0.035)

	draw_set_transform(center, _tilt + _act_tilt, scale)

	if show_tail:
		_draw_tail(R)
	_draw_ear(R, -1.0)
	_draw_ear(R, 1.0)
	_draw_eye(R, -1.0)
	_draw_eye(R, 1.0)
	_draw_nose_and_mouth(R)
	_draw_whiskers(R, -1.0)
	_draw_whiskers(R, 1.0)
	# Paws last: they are in front of the face, and a paw over the muzzle should
	# actually cover it.
	_draw_paw(R, _paw_l, -1.0)
	_draw_paw(R, _paw_r, 1.0)


func _draw_motes(R: float) -> void:
	var ext := R * 3.2
	for m in _motes:
		var depth: float = m["depth"]
		# Slow upward drift, wrapped. Parallax scales with depth.
		var y: float = fposmod(m["y"] * ext - _t * m["speed"] * R, ext * 2.0) - ext
		var p := Vector2(m["x"] * ext + _sway * depth * 1.1, y - _bob * depth * 0.5)
		var twinkle: float = 0.40 + 0.60 * sin(_t * 1.5 + m["phase"])
		_soft_glow(p, m["size"] * depth * 7.5, whisker_color, 0.095 * twinkle * depth)


## Rotate a locally-built polygon into place. Used by every feature that has to
## pivot about its own root rather than the face centre.
func _place(poly: PackedVector2Array, origin: Vector2, rot: float) -> PackedVector2Array:
	var out := PackedVector2Array()
	for p in poly:
		out.append(origin + (p.rotated(rot) if rot != 0.0 else p))
	return out


func _draw_ear(R: float, side: float) -> void:
	var idx: int = 0 if side < 0.0 else 1
	var asym: float = _asym[idx]
	var pj := _project(Vector2(side * R * 0.58, -R * 0.72), R)
	var base: Vector2 = pj["pos"]
	var sc: float = pj["scale"]

	var twitch: float = _ear_twitch[idx]
	# Kept small on purpose: an idle twitch landing on one ear while the other
	# is still was producing 25px height mismatches between the pair, which
	# reads as broken rigging rather than as a twitch.
	var wobble := sin((1.0 - twitch) * PI * 5.0) * twitch * 7.0
	# Idle drift offset per ear, so the pair never swings in unison.
	var idle := sin(_t * 1.15 + asym * 2.6 + float(idx) * 2.4) * 2.4
	# Perking forward (positive pitch) pulls the ear upright, reducing splay.
	var split := _act_ear_split * (1.0 if idx == 0 else -1.0)
	var rot := deg_to_rad(side * (_ear_splay - _ear_pitch * 0.5) + wobble + idle + asym * 2.0 + split)

	var hw := R * 0.30 * sc * (1.0 + asym * 0.03)
	var hh := R * 0.62 * sc * (1.0 - asym * 0.02)

	var p_out := _place(CatDraw.rounded_triangle(Vector2.ZERO, hw, hh, 0.30, 30), base, rot)
	var p_mid := _place(CatDraw.rounded_triangle(Vector2(0.0, -hh * 0.05), hw * 0.80, hh * 0.82, 0.32, 26), base, rot)
	var p_in := _place(CatDraw.rounded_triangle(Vector2(0.0, -hh * 0.09), hw * 0.60, hh * 0.66, 0.34, 24), base, rot)
	var p_core := _place(CatDraw.rounded_triangle(Vector2(0.0, -hh * 0.13), hw * 0.38, hh * 0.46, 0.36, 20), base, rot)

	_soft_glow(base + Vector2(0.0, -hh * 0.35), hh * 1.20, ear_color, 0.13)

	# Nested washes brightening inward. The ear then reads as a funnel with a lit
	# throat rather than a flat outline with a tint dropped inside it.
	draw_colored_polygon(p_out, Color(ear_color.r * 0.12, ear_color.g * 0.06, ear_color.b * 0.07, 0.88))
	draw_colored_polygon(p_mid, Color(ear_color.r, ear_color.g, ear_color.b, 0.14))
	draw_colored_polygon(p_in, Color(ear_color.r, ear_color.g, ear_color.b, 0.28))
	draw_colored_polygon(p_core, _hdr(ear_color, 0.28))

	# Fur tufts sprouting from the inner edge.
	for i in range(3):
		var t := 0.26 + 0.20 * float(i)
		var q := base + Vector2(-side * hw * (0.52 - t * 0.28), -hh * t).rotated(rot)
		var q2 := q + Vector2(side * hw * 0.36, -hh * 0.11).rotated(rot)
		draw_line(q, q2, Color(ear_color.r, ear_color.g, ear_color.b, 0.45),
			line_width * 0.38 * sc, true)

	_stroke(p_in, _hdr(ear_color, 0.50), line_width * 0.55 * sc)
	_stroke(p_out, _hdr(ear_color, 0.95), line_width * sc)
## Fill a shape but only where it overlaps the eye aperture. Lets the round
## iris live inside a pointed almond without spilling past the corners.
func _clip(shape: PackedVector2Array, aperture: PackedVector2Array, col: Color) -> void:
	for piece in Geometry2D.intersect_polygons(shape, aperture):
		draw_colored_polygon(piece, col)


## Blink curve sampled at a per-eye time offset. Two eyes that shut on exactly
## the same frame look mechanical; a couple of hundredths of lag looks alive.
func _blink_value(offset: float) -> float:
	if not _blinking:
		return 1.0
	var e := _blink_elapsed - offset
	if e <= 0.0:
		return 1.0
	if e <= BLINK_CLOSE:
		return 1.0 - e / BLINK_CLOSE
	if e <= BLINK_CLOSE + BLINK_OPEN:
		return CatDraw.smoothstep01((e - BLINK_CLOSE) / BLINK_OPEN)
	return 1.0


func _draw_eye(R: float, side: float) -> void:
	var idx: int = 0 if side < 0.0 else 1
	var pj := _project(Vector2(side * R * 0.50, -R * 0.08), R)
	var eye: Vector2 = pj["pos"]
	var sc: float = pj["scale"]

	# Fixed per-eye offset plus a slow independent breath, so the pair is never
	# perfectly symmetrical on any frame.
	var asym: float = _asym[idx]
	var breath: float = 1.0 + 0.03 * sin(_t * 1.7 + asym * 3.0)
	var blink := 1.0 if _act_no_blink else _blink_value(0.022 * float(idx))
	var openness: float = clampf(blink * _eye_open, 0.0, 1.3)

	# A wink shuts one eye and leaves the other alone - the only override in the
	# rig that deliberately breaks the pair.
	if not is_zero_approx(_act_wink):
		var shut: bool = (_act_wink < 0.0) == (side < 0.0)
		if shut:
			openness *= clampf(1.0 - absf(_act_wink), 0.0, 1.0)

	# The mass sits below the lid line: a shallow flat top over a deep rounded
	# underside, rather than a symmetric shape straddling the centre.
	# Width narrows as the eye shuts. Holding full width through a close leaves a
	# long thin streak, and once any head tilt rotates that streak it reads as a
	# slash across the face rather than a closed eye.
	var hw := R * 0.40 * sc * _eye_w * (1.0 + asym * 0.025) * breath \
		* lerpf(0.72, 1.0, clampf(openness, 0.0, 1.0))

	# ...but that asymmetry has to unwind as the eye shuts. Scaling both halves
	# by openness keeps their ratio, so a closed eye collapsed onto the deeper
	# lower half and the lid line ended up hanging below the eye's centre - which
	# is why winks read as a stray stroke and near-shut eyes crossed each other
	# at the midline. Converging the halves puts the closed lid on centre.
	var closing := 1.0 - clampf(openness, 0.0, 1.0)
	var up_base := R * 0.23 * _eye_up
	var lo_base := R * 0.37
	var mid_base := (up_base + lo_base) * 0.5
	up_base = lerpf(up_base, mid_base, closing)
	lo_base = lerpf(lo_base, mid_base, closing)

	var up := up_base * sc * _eye_h * maxf(0.075, openness) * (1.0 - _lower_lid * 0.18)
	var lo := lo_base * sc * _eye_h * maxf(0.075, openness) * (1.0 - _lower_lid * 0.85)

	# Outer corner rides above the inner one, mirrored per side; mood tilt adds
	# on top of that.
	# Slant relaxes as the eye shuts. A closed lid is a flat line; holding the
	# full slant turns it into a dagger, and with the eyes set this close the two
	# inner tips then overlap.
	var slant_k: float = lerpf(0.30, 1.0, clampf(openness, 0.0, 1.0))
	var rot := deg_to_rad(-side * (_eye_slant * eye_slant_scale * slant_k + _eye_tilt) + asym * 1.5)

	# The lower lid is held markedly straighter than the upper. That asymmetry is
	# what makes it a wedge; matching them gives a symmetric leaf, or at the
	# extreme a diamond.
	var ap := CatDraw.almond(eye, hw, up, lo, 0.92, _eye_round,
		side * 0.06, 56, rot, _eye_top, 1.9)

	_soft_glow(eye, hw * 3.2, eye_color, 0.16)
	draw_colored_polygon(ap, Color(0.030, 0.070, 0.055, 1.0))

	if openness > 0.06:
		var iris_pos := eye + _gaze * (hw * 0.30)
		var ir: float = minf(hw * 0.66, (up + lo) * 0.60)

		# Foreshortening: an iris swung off-axis is seen at an angle.
		var fx: float = lerpf(1.0, 0.70, absf(_gaze.x))
		var fy: float = lerpf(1.0, 0.86, absf(_gaze.y))

		# Limbus dark, brightening toward the pupil.
		_clip(CatDraw.ellipse(iris_pos, Vector2(ir * fx, ir * fy), 44), ap, _hdr(eye_color, 0.20))
		_clip(CatDraw.ellipse(iris_pos, Vector2(ir * 0.86 * fx, ir * 0.86 * fy), 42), ap, _hdr(eye_color, 0.34))
		_clip(CatDraw.ellipse(iris_pos, Vector2(ir * 0.66 * fx, ir * 0.66 * fy), 40), ap, _hdr(eye_color, 0.54))
		_clip(CatDraw.ellipse(iris_pos, Vector2(ir * 0.44 * fx, ir * 0.44 * fy), 36), ap, _hdr(eye_color, 0.74))

		# Radial fibres, clipped as polylines so they stop at the lids.
		for i in range(24):
			var a := float(i) / 24.0 * TAU + 0.11
			var d := Vector2(cos(a) * fx, sin(a) * fy)
			var seg := PackedVector2Array([iris_pos + d * (ir * 0.32), iris_pos + d * (ir * 0.95)])
			for piece in Geometry2D.intersect_polyline_with_polygon(seg, ap):
				draw_polyline(piece, Color(eye_color.r, eye_color.g, eye_color.b, 0.15), 1.6, true)

		# Bounce light low in the iris - the strongest wet-sphere cue.
		_clip(CatDraw.ellipse(iris_pos + Vector2(0.0, ir * 0.42 * fy),
			Vector2(ir * 0.62 * fx, ir * 0.26 * fy), 28), ap,
			Color(eye_color.r, eye_color.g, eye_color.b, 0.30))

		# Slit pupil: a spindle with knife points, hung from above the top lid and
		# running past the bottom one so the aperture crops both ends. A rounded
		# ellipse centred in the iris reads as a camera stop; a cat's pupil spans
		# the whole eye and touches the upper lid.
		var pw: float = ir * lerpf(0.10, 0.56, clampf(_pupil, 0.0, 1.0))
		var p_top := eye.y - up * 1.10
		var p_bot := eye.y + lo * 1.10
		var steps := 26
		var slit := PackedVector2Array()
		for i in range(steps + 1):
			var t := float(i) / float(steps)
			# Widest at the middle, tapering to a point at each end. The inner
			# exponent keeps the taper sharp rather than lens-shaped.
			var w := pw * pow(1.0 - pow(absf(t * 2.0 - 1.0), 1.7), 0.48)
			slit.append(Vector2(iris_pos.x + w * fx, lerpf(p_top, p_bot, t)))
		# Endpoints skipped on the way back - both sides meet at width zero there.
		for i in range(steps - 1, 0, -1):
			var t := float(i) / float(steps)
			var w := pw * pow(1.0 - pow(absf(t * 2.0 - 1.0), 1.7), 0.48)
			slit.append(Vector2(iris_pos.x - w * fx, lerpf(p_top, p_bot, t)))
		_clip(slit, ap, Color(0.0, 0.0, 0.0, 1.0))

		# Shadow thrown across the top of the eyeball by the upper lid.
		_clip(CatDraw.ellipse(eye + Vector2(0.0, -up * 1.02), Vector2(hw * 1.35, up * 0.92), 34),
			ap, Color(0.0, 0.0, 0.0, 0.38))

		# Crescent catchlight - a flat dot reads as a sticker.
		var cl := iris_pos + Vector2(side * ir * 0.26, -ir * 0.34)
		var cres := PackedVector2Array()
		for i in range(17):
			var a: float = lerpf(PI * 0.12, PI * 1.18, float(i) / 16.0)
			cres.append(cl + Vector2(cos(a), sin(a)) * (ir * 0.30))
		for i in range(17):
			var a: float = lerpf(PI * 1.18, PI * 0.12, float(i) / 16.0)
			cres.append(cl + Vector2(0.0, ir * 0.06) + Vector2(cos(a), sin(a)) * (ir * 0.21))
		_soft_glow(cl, ir * 0.95, Color(1, 1, 1), 0.20)
		_clip(cres, ap, _hdr(Color(1, 1, 1), 1.05))

		# Dim secondary opposite it, standing in for bounced fill.
		_clip(CatDraw.ellipse(iris_pos + Vector2(-side * ir * 0.34, ir * 0.38),
			Vector2(ir * 0.11, ir * 0.11), 16), ap, _hdr(Color(0.8, 0.9, 1.0), 0.45))

	_stroke(ap, _hdr(eye_color, 0.65), line_width * 0.75 * sc)
	_soft_glow(eye, hw * 2.3, eye_color, 0.12 * clampf(openness + 0.3, 0.0, 1.0))


func _draw_nose_and_mouth(R: float) -> void:
	var pj := _project(Vector2(0.0, R * 0.52), R)
	var a: Vector2 = pj["pos"]
	var sc: float = pj["scale"]

	# --- nose ---------------------------------------------------------------
	var flare := _nose_flare * (1.0 + _nose_twitch * 0.12)
	var nw := R * 0.115 * sc * flare
	var nh := R * 0.125 * sc * flare
	var nudge := Vector2(sin(_nose_twitch * PI * 3.0) * R * 0.014 * _nose_twitch, 0.0)
	# A curled lip drags the nose up with it, the way a real snarl does.
	var nc := a + Vector2(0.0, -R * (0.05 + 0.035 * _act_lip_curl) * sc) + nudge

	_soft_glow(nc, nw * 4.2, nose_color, 0.13)

	var n_out := _place(CatDraw.rounded_triangle(Vector2.ZERO, nw, nh, 0.44, 28), nc, PI)
	var n_mid := _place(CatDraw.rounded_triangle(Vector2(0.0, -nh * 0.07), nw * 0.74, nh * 0.74, 0.46, 24), nc, PI)
	draw_colored_polygon(n_out, _hdr(nose_color, 0.42))
	draw_colored_polygon(n_mid, _hdr(nose_color, 0.70))
	draw_colored_polygon(
		CatDraw.ellipse(nc + Vector2(-nw * 0.30, -nh * 0.20), Vector2(nw * 0.28, nh * 0.17), 18),
		_hdr(Color(1, 1, 1), 0.80))
	_stroke(n_out, _hdr(nose_color, 1.0), line_width * 0.60 * sc)

	# --- mouth --------------------------------------------------------------
	# The jaw is a hinge, not a scaling hole. The upper lip is fixed to the
	# skull; the lower lip swings away from it. That separation is what lets a
	# snarl (nearly shut, lip up, fangs out) differ from a yawn (wide, lip down)
	# instead of both being "mouth open by N".
	var jaw: float = clampf(maxf(_talk, _act_jaw), 0.0, 1.0)
	var curl: float = clampf(_act_lip_curl, 0.0, 1.0)
	var mw: float = clampf(_act_mouth_w, 0.55, 1.6)

	_soft_glow(a + Vector2(0.0, R * 0.15 * sc), R * 0.36 * sc, mouth_color, 0.09)

	var lift := R * 0.105 * clampf(_mouth_curve, -1.0, 1.0)
	var apex := a + Vector2(0.0, R * (0.105 - 0.030 * curl)) * sc

	# Upper lip: the "w". Curl raises the corners and pulls them outward.
	var lip := []
	for si in range(2):
		var s: float = -1.0 if si == 0 else 1.0
		var lop: float = 1.0 + _asym[si] * 0.07
		# Clamped. Unclamped, a hard mouth_split plus a curl drove one corner past
		# the other and the lower lip crossed through the upper - the mouth then
		# renders as a crossed X instead of a mouth.
		var corner: float = clampf(
			lift + _act_mouth_split * R * 0.075 * (1.0 if si == 1 else -0.25) \
				+ R * 0.060 * curl,
			-R * 0.105, R * 0.115)
		lip.append(CatDraw.smooth_path(PackedVector2Array([
			apex,
			a + Vector2(s * R * 0.078 * mw, R * 0.190 * lop) * sc,
			a + Vector2(s * R * 0.180 * mw, (R * 0.156 - corner) * lop) * sc]), 20))

	var left: PackedVector2Array = lip[0]
	var right: PackedVector2Array = lip[1]

	var lip_bottom := -1.0e9
	for p in left:
		lip_bottom = maxf(lip_bottom, p.y)
	for p in right:
		lip_bottom = maxf(lip_bottom, p.y)

	if jaw > 0.02:
		# The jaw swings about a hinge set back and above the mouth, so the chin
		# travels on an arc: it drops AND narrows as it opens, rather than the
		# opening simply getting taller.
		var drop := R * (0.03 + 0.34 * jaw) * sc
		var narrow: float = lerpf(1.0, 0.86, jaw)
		var floor_y := lip_bottom + drop

		var poly := PackedVector2Array()
		for i in range(left.size() - 1, -1, -1):
			poly.append(left[i])
		for i in range(1, right.size()):
			poly.append(right[i])

		var arc := CatDraw.smooth_path(PackedVector2Array([
			right[right.size() - 1],
			Vector2(a.x + R * 0.125 * sc * mw * narrow, lip_bottom + drop * 0.72),
			Vector2(a.x, floor_y),
			Vector2(a.x - R * 0.125 * sc * mw * narrow, lip_bottom + drop * 0.72),
			left[left.size() - 1]]), 26)
		for i in range(1, arc.size() - 1):
			poly.append(arc[i])

		draw_colored_polygon(poly, Color(0.115, 0.012, 0.045, 0.97))

		if not _act_no_tongue:
			# Seated so the tongue's lower edge stays inside the jaw line. At the
			# old offset it hung below the lip outline and read as a dropped blob.
			var th := R * sc * (0.034 + 0.062 * jaw)
			var tw := R * sc * (0.058 + 0.048 * jaw) * mw
			draw_colored_polygon(
				CatDraw.ellipse(Vector2(a.x, floor_y - th * 1.10), Vector2(tw, th), 26),
				_hdr(Color(1.0, 0.44, 0.54), 0.60))

		# Lower lip line, so the jaw reads as an edge rather than a void.
		draw_polyline(arc, _hdr(mouth_color, 0.55), line_width * 0.55 * sc, true)

	# --- fangs --------------------------------------------------------------
	# Only ever visible on a curled lip. They hang from the upper lip and are
	# the single clearest signal separating a threat from a vocalisation.
	if curl > 0.05:
		var fang_len := R * 0.115 * sc * curl
		for s in [-1.0, 1.0]:
			var root := a + Vector2(s * R * 0.072 * mw, R * 0.150 - lift * 0.5) * sc
			var fang := PackedVector2Array([
				root + Vector2(-R * 0.032 * sc, 0.0),
				root + Vector2(R * 0.032 * sc, 0.0),
				root + Vector2(s * R * 0.008 * sc, fang_len)])
			_soft_glow(root + Vector2(0.0, fang_len * 0.4), R * 0.10 * sc,
				Color(1, 1, 1), 0.18 * curl)
			draw_colored_polygon(fang, _hdr(Color(1.0, 0.98, 0.95), 0.95))
			_stroke(fang, _hdr(Color(1.0, 0.85, 0.85), 0.5), line_width * 0.35 * sc)

	# Lip strokes last, so they always read on top of the opening.
	for si in range(2):
		var path: PackedVector2Array = lip[si]
		var widths := PackedFloat32Array()
		for k in range(path.size()):
			var t := float(k) / float(path.size() - 1)
			widths.append(R * (0.005 + 0.021 * sin(t * PI)) * sc)
		draw_colored_polygon(CatDraw.ribbon(path, widths), _hdr(mouth_color, 0.90))


## A floating paw: rounded palm, four toe bumps, pink pads. Built pointing up
## (-Y) then rotated and placed, so an action only has to supply a position and
## an angle.
func _draw_paw(R: float, paw: Dictionary, side: float) -> void:
	var on: float = clampf(paw["on"], 0.0, 1.0)
	if on <= 0.02:
		return

	var s: float = R * 0.195 * float(paw["size"])
	var rot := deg_to_rad(float(paw["rot"]))
	var origin: Vector2 = (paw["pos"] as Vector2) * R
	# Rises into place rather than popping: at low "on" it sits lower and small.
	origin += Vector2(0.0, R * 0.10 * (1.0 - on))
	s *= lerpf(0.80, 1.0, on)

	var wash := Color(paw_color.r, paw_color.g, paw_color.b, 0.16 * on)
	var rim := _hdr(paw_color, 0.85 * on)
	var pad := _hdr(pad_color, 0.55 * on)

	_soft_glow(origin, s * 2.6, paw_color, 0.10 * on)

	# Palm.
	var palm := _place(CatDraw.squircle(Vector2(0.0, s * 0.16), Vector2(s, s * 0.80), 2.7, 40),
		origin, rot)
	draw_colored_polygon(palm, wash)
	_stroke(palm, rim, line_width * 0.75)

	# Toes across the top, the outer two set slightly lower so the spread reads.
	var toe_x := [-0.62, -0.21, 0.21, 0.62]
	var toe_y := [-0.52, -0.72, -0.72, -0.52]
	for i in range(4):
		var c := Vector2(float(toe_x[i]) * s, float(toe_y[i]) * s)
		var toe := _place(CatDraw.ellipse(c, Vector2(s * 0.27, s * 0.31), 20), origin, rot)
		draw_colored_polygon(toe, wash)
		_stroke(toe, rim, line_width * 0.55)
		# Toe bean.
		draw_colored_polygon(
			_place(CatDraw.ellipse(c + Vector2(0.0, s * 0.05), Vector2(s * 0.15, s * 0.17), 16),
				origin, rot), pad)

	# Main pad.
	draw_colored_polygon(
		_place(CatDraw.squircle(Vector2(0.0, s * 0.22), Vector2(s * 0.52, s * 0.42), 2.4, 26),
			origin, rot), pad)


func _draw_whiskers(R: float, side: float) -> void:
	var idx: int = 0 if side < 0.0 else 1
	var asym: float = _asym[idx]
	var pj := _project(Vector2(side * R * 0.56, R * 0.44), R)
	var root: Vector2 = pj["pos"]
	var sc: float = pj["scale"]

	var tint := _hdr(whisker_color, 0.8)
	_soft_glow(root, R * 0.32 * sc, whisker_color, 0.07)

	# Follicle spots on the muzzle - a tiny detail, but it anchors the whiskers
	# to a surface instead of leaving them sprouting from nothing.
	for i in range(3):
		for j in range(2):
			var d := root + Vector2(side * R * (0.015 + 0.048 * j), R * (-0.035 + 0.042 * i)) * sc
			draw_colored_polygon(CatDraw.ellipse(d, Vector2(R * 0.0075 * sc, R * 0.0075 * sc), 10),
				Color(whisker_color.r, whisker_color.g, whisker_color.b, 0.32))

	var pitches := [-0.18, 0.0, 0.20]
	var lengths := [0.60, 0.68, 0.57]

	for i in range(3):
		var L: float = R * lengths[i] * sc * _whisk
		# Each whisker flutters on its own frequency. Moving them as one rigid
		# fan is what makes drawn whiskers look like plastic.
		var flutter := sin(_t * (1.7 + 0.31 * float(i)) + asym * 2.0 + float(i) * 1.9) * 0.032
		var p: float = pitches[i] * _whisk + _whisk_pitch + _whisker_offset.y * 0.5 + flutter
		var swing: float = _whisker_offset.x * 0.6
		var ctrl := PackedVector2Array([
			root,
			root + Vector2(side * L * 0.38, L * (p * 0.55 - 0.06) + swing * L * 0.1),
			root + Vector2(side * L * 0.74, L * (p * 0.95 + 0.02) + swing * L * 0.2),
			root + Vector2(side * L * 1.02, L * (p * 1.3 + 0.14) + swing * L * 0.3)])
		var path := CatDraw.smooth_path(ctrl, 20)

		var widths := PackedFloat32Array()
		for k in range(path.size()):
			var t := float(k) / float(path.size() - 1)
			widths.append(R * 0.030 * sc * pow(1.0 - t, 0.45) + R * 0.005 * sc)

		# Per-vertex alpha so the tips dissolve instead of stopping dead. The
		# ribbon runs down the left edge then back up the right, so the colour
		# array has to mirror that order.
		var cols := PackedColorArray()
		for k in range(path.size()):
			var t := float(k) / float(path.size() - 1)
			cols.append(Color(tint.r, tint.g, tint.b, 1.0 - t * 0.62))
		for k in range(path.size() - 1, -1, -1):
			var t := float(k) / float(path.size() - 1)
			cols.append(Color(tint.r, tint.g, tint.b, 1.0 - t * 0.62))

		draw_polygon(CatDraw.ribbon(path, widths), cols)


func _draw_tail(R: float) -> void:
	var pts := PackedVector2Array()
	var widths := PackedFloat32Array()
	var n := 26
	var speed: float = 2.35 * lerpf(0.6, 1.35, clampf(_tail_energy * 0.5, 0.0, 1.0))

	for i in range(n):
		var t := float(i) / float(n - 1)
		var falloff := pow(t, 1.45)
		var phase := _t * speed - t * 3.1
		var base := Vector2(R * (0.52 + 1.10 * t), R * 1.16 - R * 0.98 * t * t)
		pts.append(base + Vector2(
			sin(phase * 0.72 + 1.1) * R * 0.13 * _tail_energy * falloff,
			sin(phase) * R * 0.34 * _tail_energy * falloff))
		widths.append(R * (0.20 * (1.0 - t) + 0.055))

	_stroke(CatDraw.ribbon(pts, widths), _hdr(whisker_color, 0.5), line_width * 0.8)
