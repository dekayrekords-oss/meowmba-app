extends Node2D
## Godot port of two clips from the Compose avatar system, for a side-by-side
## feel/feasibility comparison. Ported by hand from:
##   - AvatarRenderer.kt   (idle ambient layers: blink/breathe/ear-twitch/
##     gaze-drift/whisker-sway/mouth-wobble, and the draw layers for
##     ears/eyes/nose/paw/whiskers/mouth/tongue)
##   - CatAnimationRegistry (AvatarAnimation.kt) for idle timing constants
##   - CustomAnimation.kt BUILT_INS["paw lick"] for the paw-lick keyframes
##
## Deliberately NOT ported (out of scope for this two-clip prototype, since
## neither idle nor paw-lick ever exercises them): happy-eye arcs, mic/camera
## privacy marks, dimmed/faceAlpha, halo pulse (drawn static instead), sparkle
## particles, tail wag, body bounce, SPEAKING mouth mode, touch-driven gaze.
## Also simplified: eye/pupil corners are drawn square, not rounded (Godot has
## no built-in rounded-rect draw primitive); the halo is a flat soft circle,
## not a true radial-gradient falloff; the 420ms gaze-follow tween that sits
## between touch/drift and the rendered pupil position is collapsed away since
## there is no touch input here, so pupils track drift_x/drift_y directly.

const EYE_COLOR := Color(0xCF / 255.0, 0xF9 / 255.0, 0xFF / 255.0, 1.0)
const PUPIL_COLOR := Color(0x06 / 255.0, 0x23 / 255.0, 0x2B / 255.0, 1.0)
const NOSE_COLOR := Color(0xFF / 255.0, 0xA6 / 255.0, 0xC9 / 255.0, 1.0)
const WHISKER_COLOR := Color(0xCF / 255.0, 0xF9 / 255.0, 0xFF / 255.0, 0x66 / 255.0)
const HALO_COLOR := Color(0x58 / 255.0, 0xA6 / 255.0, 0xFF / 255.0, 1.0)
const TONGUE_COLOR_RGB := Color(1.0, 0x6B / 255.0, 0x8A / 255.0, 1.0)

# ── Pose fields (mirrors AvatarPose in AvatarAnimation.kt; only the subset
# these two clips ever touch) ────────────────────────────────────────────
var eye_open := 1.0
var head_tilt_deg := 0.0
var ear_perk := 1.0
var ear_droop_deg := 0.0
var whisker_flutter := false
var tongue_out := 0.0
const PUPIL_SIZE := 0.28  # neither clip animates pupil size; stays at the IDLE baseline

# ── Ambient/idle animation-layer state (mirrors the LaunchedEffect layers
# in AvatarRenderer.kt) ──────────────────────────────────────────────────
var blink_value := 1.0
var ear_twitch_l_deg := 0.0
var ear_twitch_r_deg := 0.0
var breathe := 1.0
var drift_x := -0.3
var drift_y := -0.25
var whisker_sway := -1.0
var mouth_wobble := 0.88
var talk_val := 0.15

var _paw_lick_playing := false

const PAW_LICK_FRAMES := [
	{"at": 0,    "head_tilt": 0.0,  "eye_open": 1.0,  "whisker": false, "tongue": 0.0},
	{"at": 350,  "head_tilt": 8.0,  "eye_open": 0.55, "whisker": false, "tongue": 0.0},
	{"at": 550,  "head_tilt": 10.0, "eye_open": 0.4,  "whisker": true,  "tongue": 0.9},
	{"at": 750,  "head_tilt": 10.0, "eye_open": 0.4,  "whisker": true,  "tongue": 0.3},
	{"at": 950,  "head_tilt": 10.0, "eye_open": 0.4,  "whisker": true,  "tongue": 0.9},
	{"at": 1150, "head_tilt": 10.0, "eye_open": 0.4,  "whisker": true,  "tongue": 0.3},
	{"at": 1400, "head_tilt": 4.0,  "eye_open": 0.75, "whisker": false, "tongue": 0.0},
	{"at": 1800, "head_tilt": 0.0,  "eye_open": 1.0,  "whisker": false, "tongue": 0.0},
]


func _ready() -> void:
	randomize()
	_run_blink_loop()
	_run_ear_twitch_loop()
	_run_breathe_loop()
	_run_gaze_drift_loop()
	_run_whisker_sway_loop()
	_run_mouth_wobble_loop()
	_run_talk_cycle_loop()
	_run_paw_lick_demo_loop()


func _process(_delta: float) -> void:
	queue_redraw()


# ── Idle ambient layers: randomized ones (blink, ear twitch) use the same
# delay-then-tween-then-repeat structure as the Kotlin LaunchedEffect loops;
# deterministic ones (breathe, drift, sway, wobble) use looping Tweens. ────

func _tween_to(prop: String, target: float, duration_sec: float, eased := false) -> void:
	var tw := create_tween()
	var t := tw.tween_property(self, prop, target, duration_sec)
	if eased:
		t.set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_IN_OUT)
	else:
		t.set_trans(Tween.TRANS_LINEAR)
	await tw.finished


func _run_blink_loop() -> void:
	while true:
		await get_tree().create_timer(randf_range(2.2, 5.2)).timeout
		await _tween_to("blink_value", 0.06, 0.09)
		await _tween_to("blink_value", 1.0, 0.11)
		if randi() % 4 == 0:
			await _tween_to("blink_value", 0.06, 0.08)
			await _tween_to("blink_value", 1.0, 0.10)


func _run_ear_twitch_loop() -> void:
	while true:
		await get_tree().create_timer(randf_range(3.0, 8.0)).timeout
		var prop := "ear_twitch_l_deg" if randi() % 2 == 0 else "ear_twitch_r_deg"
		await _tween_to(prop, 9.0, 0.07)
		await _tween_to(prop, 0.0, 0.12)
		if randi() % 3 == 0:
			await _tween_to(prop, 6.0, 0.06)
			await _tween_to(prop, 0.0, 0.11)


func _run_breathe_loop() -> void:
	var tw := create_tween()
	tw.set_loops()
	tw.tween_property(self, "breathe", 1.02, 2.6).set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_IN_OUT)
	tw.tween_property(self, "breathe", 0.98, 2.6).set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_IN_OUT)


func _run_gaze_drift_loop() -> void:
	var tx := create_tween()
	tx.set_loops()
	tx.tween_property(self, "drift_x", 0.3, 4.8).set_trans(Tween.TRANS_LINEAR)
	tx.tween_property(self, "drift_x", -0.3, 4.8).set_trans(Tween.TRANS_LINEAR)
	var ty := create_tween()
	ty.set_loops()
	ty.tween_property(self, "drift_y", 0.25, 3.4).set_trans(Tween.TRANS_LINEAR)
	ty.tween_property(self, "drift_y", -0.25, 3.4).set_trans(Tween.TRANS_LINEAR)


func _run_whisker_sway_loop() -> void:
	var tw := create_tween()
	tw.set_loops()
	tw.tween_property(self, "whisker_sway", 1.0, 1.5).set_trans(Tween.TRANS_LINEAR)
	tw.tween_property(self, "whisker_sway", -1.0, 1.5).set_trans(Tween.TRANS_LINEAR)


func _run_mouth_wobble_loop() -> void:
	var tw := create_tween()
	tw.set_loops()
	tw.tween_property(self, "mouth_wobble", 1.12, 1.9).set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_IN_OUT)
	tw.tween_property(self, "mouth_wobble", 0.88, 1.9).set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_IN_OUT)


func _run_talk_cycle_loop() -> void:
	# Mirrors speech.talk_cycle: it runs continuously in the Kotlin version
	# too (not gated on speaking), and paw-lick's whisker flutter rides it.
	var tw := create_tween()
	tw.set_loops()
	tw.tween_property(self, "talk_val", 1.0, 0.16).set_trans(Tween.TRANS_LINEAR)
	tw.tween_property(self, "talk_val", 0.15, 0.16).set_trans(Tween.TRANS_LINEAR)


# ── Paw lick: script-driven keyframe sampling, ported 1:1 from
# CustomAnimations.sample()'s per-field smoothstep lerp / step logic. ──────

func _sample_paw_lick(t_ms: float) -> Dictionary:
	var frames: Array = PAW_LICK_FRAMES
	if t_ms <= frames[0]["at"]:
		return frames[0]
	if t_ms >= frames[-1]["at"]:
		return frames[-1]
	for i in range(frames.size() - 1):
		var f0: Dictionary = frames[i]
		var f1: Dictionary = frames[i + 1]
		if t_ms >= f0["at"] and t_ms <= f1["at"]:
			var u := float(t_ms - f0["at"]) / float(f1["at"] - f0["at"])
			var e := u * u * (3.0 - 2.0 * u)  # smoothstep, matches CustomAnimations.sample()
			return {
				"head_tilt": lerp(float(f0["head_tilt"]), float(f1["head_tilt"]), e),
				"eye_open": lerp(float(f0["eye_open"]), float(f1["eye_open"]), e),
				"tongue": lerp(float(f0["tongue"]), float(f1["tongue"]), e),
				"whisker": f0["whisker"],
			}
	return frames[-1]


func play_paw_lick() -> void:
	if _paw_lick_playing:
		return
	_paw_lick_playing = true
	var duration_ms: float = PAW_LICK_FRAMES[-1]["at"]
	var start := Time.get_ticks_msec()
	while true:
		var elapsed := float(Time.get_ticks_msec() - start)
		if elapsed > duration_ms:
			break
		var s := _sample_paw_lick(elapsed)
		head_tilt_deg = s["head_tilt"]
		eye_open = s["eye_open"]
		tongue_out = s["tongue"]
		whisker_flutter = s["whisker"]
		await get_tree().process_frame
	head_tilt_deg = 0.0
	eye_open = 1.0
	tongue_out = 0.0
	whisker_flutter = false
	_paw_lick_playing = false


func _run_paw_lick_demo_loop() -> void:
	# No Kotlin<->Godot signal bridge exists in this prototype (out of scope,
	# see design note), so the clip auto-plays periodically for passive
	# side-by-side viewing instead of waiting for a trigger that can't reach it yet.
	while true:
		await get_tree().create_timer(4.0).timeout
		await play_paw_lick()


# ── Drawing (mirrors the Canvas layers in AvatarRenderer.kt) ───────────────

func _rotate_around(point: Vector2, pivot: Vector2, angle_rad: float) -> Vector2:
	return pivot + (point - pivot).rotated(angle_rad)


func _ellipse_points(center: Vector2, rx: float, ry: float, segments := 24) -> PackedVector2Array:
	var pts := PackedVector2Array()
	for i in range(segments):
		var a := TAU * i / float(segments)
		pts.append(Vector2(center.x + rx * cos(a), center.y + ry * sin(a)))
	return pts


func _draw_elliptical_arc(top_left: Vector2, size: Vector2, start_deg: float, sweep_deg: float, color: Color, width: float) -> void:
	var center := top_left + size / 2.0
	var rx := size.x / 2.0
	var ry := size.y / 2.0
	var segments := 16
	var pts := PackedVector2Array()
	for i in range(segments + 1):
		var a := deg_to_rad(start_deg + sweep_deg * i / float(segments))
		pts.append(Vector2(center.x + rx * cos(a), center.y + ry * sin(a)))
	draw_polyline(pts, color, width, true)


func _quad_bezier(p0: Vector2, p1: Vector2, p2: Vector2, u: float) -> Vector2:
	var a := p0.lerp(p1, u)
	var b := p1.lerp(p2, u)
	return a.lerp(b, u)


func _draw_ear(side: float, twitch_deg: float, cx: float, eye_cy: float, s: float, eye_dx: float, eye_w: float, base_eye_h: float) -> void:
	var base_y := eye_cy - base_eye_h * 0.75
	var inner_x := cx + side * (eye_dx - eye_w * 0.45)
	var outer_x := cx + side * (eye_dx + eye_w * 0.75)
	var tip_x := cx + side * (eye_dx + eye_w * 0.35)
	var tip_y := base_y - s * 0.20 * ear_perk
	var stroke := s * 0.014
	var pivot := Vector2((inner_x + outer_x) / 2.0, base_y)
	var rot := deg_to_rad(side * (ear_droop_deg + twitch_deg))

	var p1 := _rotate_around(Vector2(inner_x, base_y), pivot, rot)
	var p2 := _rotate_around(Vector2(tip_x, tip_y), pivot, rot)
	var p3 := _rotate_around(Vector2(outer_x, base_y + s * 0.02), pivot, rot)
	draw_polyline(PackedVector2Array([p1, p2, p3]), EYE_COLOR, stroke, true)

	var i1 := _rotate_around(Vector2(inner_x + side * eye_w * 0.18, base_y - s * 0.008), pivot, rot)
	var i2 := _rotate_around(Vector2(tip_x, tip_y + s * 0.045), pivot, rot)
	var i3 := _rotate_around(Vector2(outer_x - side * eye_w * 0.18, base_y + s * 0.008), pivot, rot)
	draw_colored_polygon(PackedVector2Array([i1, i2, i3]), Color(NOSE_COLOR.r, NOSE_COLOR.g, NOSE_COLOR.b, 0.35))


func _draw_eye(side: float, tilt_deg: float, cx: float, eye_cy: float, eye_dx: float, eye_w: float, eye_h: float) -> void:
	var ecx := cx + side * eye_dx
	var pivot := Vector2(ecx, eye_cy)
	var rot := deg_to_rad(tilt_deg)

	var plate_color := Color(EYE_COLOR.r, EYE_COLOR.g, EYE_COLOR.b, 0.18 * EYE_COLOR.a)
	var plate_w := eye_w * 1.24
	var plate_h := eye_h + eye_w * 0.24
	var plate_corners := [
		Vector2(ecx - plate_w / 2.0, eye_cy - plate_h / 2.0),
		Vector2(ecx + plate_w / 2.0, eye_cy - plate_h / 2.0),
		Vector2(ecx + plate_w / 2.0, eye_cy + plate_h / 2.0),
		Vector2(ecx - plate_w / 2.0, eye_cy + plate_h / 2.0),
	]
	var plate_pts := PackedVector2Array()
	for c in plate_corners:
		plate_pts.append(_rotate_around(c, pivot, rot))
	draw_colored_polygon(plate_pts, plate_color)

	var eye_corners := [
		Vector2(ecx - eye_w / 2.0, eye_cy - eye_h / 2.0),
		Vector2(ecx + eye_w / 2.0, eye_cy - eye_h / 2.0),
		Vector2(ecx + eye_w / 2.0, eye_cy + eye_h / 2.0),
		Vector2(ecx - eye_w / 2.0, eye_cy + eye_h / 2.0),
	]
	var eye_pts := PackedVector2Array()
	for c in eye_corners:
		eye_pts.append(_rotate_around(c, pivot, rot))
	draw_colored_polygon(eye_pts, EYE_COLOR)

	if blink_value > 0.3:
		var pw := eye_w * PUPIL_SIZE
		var ph := eye_h * 0.82
		var px := ecx + drift_x * eye_w * 0.22
		var py := eye_cy + drift_y * eye_h * 0.14
		var pupil_corners := [
			Vector2(px - pw / 2.0, py - ph / 2.0),
			Vector2(px + pw / 2.0, py - ph / 2.0),
			Vector2(px + pw / 2.0, py + ph / 2.0),
			Vector2(px - pw / 2.0, py + ph / 2.0),
		]
		var pupil_pts := PackedVector2Array()
		for c in pupil_corners:
			pupil_pts.append(_rotate_around(c, pivot, rot))
		draw_colored_polygon(pupil_pts, PUPIL_COLOR)


func _draw_mouth_idle(cx: float, mouth_y: float, s: float, stroke: float) -> void:
	var arc_w: float = s * 0.062 * clamp(2.0 - mouth_wobble, 0.9, 1.1)
	var arc_h := s * 0.038 * mouth_wobble
	for left_edge in [cx - arc_w, cx]:
		_draw_elliptical_arc(Vector2(left_edge, mouth_y), Vector2(arc_w, arc_h), 0.0, 180.0, EYE_COLOR, stroke)


func _draw() -> void:
	var vp := get_viewport_rect().size
	var w := vp.x
	var h := vp.y
	var s: float = min(w, h)

	var cx := w / 2.0 + drift_x * s * 0.034
	var cy := h * 0.47 + drift_y * s * 0.02

	var eye_dx := s * 0.26
	var eye_w := s * 0.19
	var base_eye_h := s * 0.24
	var eye_h: float = max(base_eye_h * eye_open * blink_value * breathe, s * 0.012)
	var eye_cy := cy - s * 0.06
	var stroke := s * 0.014

	# Halo: flat translucent circle behind the head (static — no LISTENING
	# state exists in this prototype to drive a pulse).
	draw_circle(Vector2(cx, cy), s * 0.85, Color(HALO_COLOR.r, HALO_COLOR.g, HALO_COLOR.b, 0.11))

	_draw_ear(-1.0, ear_twitch_l_deg, cx, eye_cy, s, eye_dx, eye_w, base_eye_h)
	_draw_ear(1.0, ear_twitch_r_deg, cx, eye_cy, s, eye_dx, eye_w, base_eye_h)

	_draw_eye(-1.0, -head_tilt_deg, cx, eye_cy, eye_dx, eye_w, eye_h)
	_draw_eye(1.0, head_tilt_deg, cx, eye_cy, eye_dx, eye_w, eye_h)

	var nose_y := cy + s * 0.115
	var nose_s := s * 0.032
	var nose_pts := PackedVector2Array([
		Vector2(cx - nose_s, nose_y - nose_s * 0.7),
		Vector2(cx + nose_s, nose_y - nose_s * 0.7),
		Vector2(cx, nose_y + nose_s * 0.9),
	])
	draw_colored_polygon(nose_pts, NOSE_COLOR)

	var paw_alpha: float = clamp(head_tilt_deg / 10.0, 0.0, 1.0)
	if paw_alpha > 0.01:
		var paw_cx := cx + s * 0.18 * (1.0 if head_tilt_deg > 0.0 else -1.0)
		var paw_cy := cy + s * 0.28
		var paw_s := s * 0.055
		var paw_color := Color(EYE_COLOR.r, EYE_COLOR.g, EYE_COLOR.b, paw_alpha)
		draw_colored_polygon(_ellipse_points(Vector2(paw_cx, paw_cy), paw_s * 0.7, paw_s * 0.45), paw_color)
		for i in range(-1, 2):
			draw_circle(Vector2(paw_cx + i * paw_s * 0.5, paw_cy - paw_s * 0.7), paw_s * 0.28, paw_color)

	var energetic := whisker_flutter
	var sway_amp := s * (0.016 if energetic else 0.009)
	var sway := (talk_val * 2.0 - 1.0) if energetic else whisker_sway
	for side in [-1.0, 1.0]:
		for i in range(-1, 2):
			var y0 := nose_y + i * s * 0.028
			var phase := sway * (1.0 - 0.3 * (i + 1))
			var from_pt := Vector2(cx + side * s * 0.10, y0)
			var to_pt := Vector2(
				cx + side * (s * 0.40 + phase * s * 0.004),
				y0 + i * s * 0.035 + phase * sway_amp * side
			)
			draw_line(from_pt, to_pt, WHISKER_COLOR, s * 0.006, true)

	var mouth_y := nose_y + s * 0.055
	_draw_mouth_idle(cx, mouth_y, s, stroke)

	if tongue_out > 0.01:
		var tongue_color := Color(TONGUE_COLOR_RGB.r, TONGUE_COLOR_RGB.g, TONGUE_COLOR_RGB.b, tongue_out)
		var tongue_len := s * 0.09 * tongue_out
		var tongue_w := s * 0.028 * tongue_out
		var tongue_top := mouth_y + s * 0.01
		var p0 := Vector2(cx - tongue_w, tongue_top)
		var p_tip := Vector2(cx, tongue_top + tongue_len)
		var p2 := Vector2(cx + tongue_w, tongue_top)
		var pts := PackedVector2Array()
		pts.append(p0)
		for step in range(1, 8):
			pts.append(_quad_bezier(p0, Vector2(cx - tongue_w * 1.2, tongue_top + tongue_len * 0.6), p_tip, step / 8.0))
		pts.append(p_tip)
		for step in range(1, 8):
			pts.append(_quad_bezier(p_tip, Vector2(cx + tongue_w * 1.2, tongue_top + tongue_len * 0.6), p2, step / 8.0))
		pts.append(p2)
		draw_colored_polygon(pts, tongue_color)
