# 2D shape helpers for the cat assistant.
#
# Everything the cat is made of reduces to four primitives: filled ellipses,
# partial ellipse caps (eyelids), smooth curves through control points, and
# tapered ribbons swept along a curve (tail and whiskers).
#
# Note Godot 2D is Y-DOWN: negative Y is up the screen.

class_name CatDraw
extends RefCounted


## Filled ellipse outline. Sweep a partial arc with start_angle/end_angle.
static func ellipse(center: Vector2, radius: Vector2, segments: int = 48,
		start_angle: float = 0.0, end_angle: float = TAU) -> PackedVector2Array:
	var pts := PackedVector2Array()
	segments = maxi(3, segments)
	var span := end_angle - start_angle
	for i in range(segments + 1):
		var t := float(i) / float(segments)
		var a := start_angle + span * t
		pts.append(center + Vector2(cos(a) * radius.x, sin(a) * radius.y))
	return pts


## An ellipse deformed by a per-angle radius multiplier. Used for the skull, so
## cheeks can bulge without hand-authoring a polygon.
static func shaped_ellipse(center: Vector2, radius: Vector2, segments: int,
		shaper: Callable) -> PackedVector2Array:
	var pts := PackedVector2Array()
	segments = maxi(3, segments)
	for i in range(segments):
		var t := float(i) / float(segments)
		var a := t * TAU
		var dir := Vector2(cos(a), sin(a))
		var mul: Vector2 = shaper.call(t, dir)
		pts.append(center + Vector2(dir.x * radius.x * mul.x, dir.y * radius.y * mul.y))
	return pts


## The cap of a circle lying above a horizontal cut line (Y-down, so "above"
## means smaller Y). Drives the eyelids: sweep y_cut from -r to +r to close.
static func circle_above(center: Vector2, r: float, y_cut: float,
		segments: int = 28) -> PackedVector2Array:
	if y_cut <= -r:
		return PackedVector2Array()          # lid fully raised, nothing covered
	if y_cut >= r:
		return ellipse(center, Vector2(r, r), segments)   # fully closed

	# Points satisfy sin(a) <= y_cut / r, which is the arc running from
	# (PI - asin) around through 3*PI/2 (screen top) to (TAU + asin).
	var a := asin(clampf(y_cut / r, -1.0, 1.0))
	return ellipse(center, Vector2(r, r), segments, PI - a, TAU + a)


## Catmull-Rom smoothing through control points, with clamped endpoints.
static func smooth_path(control: PackedVector2Array, count: int) -> PackedVector2Array:
	var out := PackedVector2Array()
	var n := control.size()
	if n == 0 or count < 2:
		return out
	if n == 1:
		for i in range(count):
			out.append(control[0])
		return out

	for i in range(count):
		var t := float(i) / float(count - 1) * float(n - 1)
		var seg := clampi(int(floor(t)), 0, n - 2)
		var f := t - float(seg)

		var p0 := control[maxi(0, seg - 1)]
		var p1 := control[seg]
		var p2 := control[seg + 1]
		var p3 := control[mini(n - 1, seg + 2)]

		var f2 := f * f
		var f3 := f2 * f
		out.append(0.5 * (
			(2.0 * p1) +
			(-p0 + p2) * f +
			(2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * f2 +
			(-p0 + 3.0 * p1 - 3.0 * p2 + p3) * f3))
	return out


## Sweep a variable-width ribbon along a path and return it as one closed
## polygon: down the left edge, back up the right. Tail and whiskers use this.
static func ribbon(path: PackedVector2Array, widths: PackedFloat32Array) -> PackedVector2Array:
	var n := path.size()
	if n < 2 or widths.size() != n:
		return PackedVector2Array()

	var left := PackedVector2Array()
	var right := PackedVector2Array()

	for i in range(n):
		var prev := path[maxi(0, i - 1)]
		var next := path[mini(n - 1, i + 1)]
		var tangent := (next - prev)
		if tangent.length_squared() < 0.000001:
			tangent = Vector2.RIGHT
		var normal := tangent.normalized().orthogonal()
		var half := widths[i] * 0.5
		left.append(path[i] + normal * half)
		right.append(path[i] - normal * half)

	var poly := left
	for i in range(right.size() - 1, -1, -1):
		poly.append(right[i])
	return poly


## Rounded isoceles triangle - the ear shell. Apex points along -Y (up).
static func rounded_triangle(center: Vector2, half_width: float, height: float,
		round_amount: float = 0.34, segments: int = 26) -> PackedVector2Array:
	var pts := PackedVector2Array()
	for i in range(segments + 1):
		var t := float(i) / float(segments)
		# Blend a straight taper with an elliptical dome to round the tip off.
		var linear := 1.0 - t
		var dome := sqrt(maxf(0.0, 1.0 - t * t))
		var w := half_width * lerpf(linear, dome, round_amount)
		pts.append(center + Vector2(-w, -height * t))
	for i in range(segments, -1, -1):
		var t := float(i) / float(segments)
		var linear := 1.0 - t
		var dome := sqrt(maxf(0.0, 1.0 - t * t))
		var w := half_width * lerpf(linear, dome, round_amount)
		pts.append(center + Vector2(w, -height * t))
	return pts


## Superellipse / squircle. n=2 is an ellipse, n=4 reads as a rounded rectangle,
## n=8+ approaches a hard rect. The whole face's emotion rides on this shape.
static func squircle(center: Vector2, half: Vector2, n: float = 4.0,
		segments: int = 72, rotation: float = 0.0) -> PackedVector2Array:
	var pts := PackedVector2Array()
	var e := 2.0 / maxf(0.5, n)
	for i in range(segments):
		var t := float(i) / float(segments) * TAU
		var ct := cos(t)
		var st := sin(t)
		var p := Vector2(
			half.x * signf(ct) * pow(absf(ct), e),
			half.y * signf(st) * pow(absf(st), e))
		pts.append(center + (p.rotated(rotation) if rotation != 0.0 else p))
	return pts


## Angular eye aperture - two edges meeting at sharp points, which is what makes
## an eye read as feline rather than as a circle stuck on a face.
##
## The falloff is LINEAR in |x|, not quadratic. A quadratic profile is a
## parabola and can never be anything but curvy, however hard the exponent is
## pushed. With a linear base, up_power / low_power do what you would expect:
## 1.0 gives perfectly straight edges meeting at knife points, below 1.0 bows
## them outward into a softer almond, above 1.0 pinches them concave.
##
## peak_shift slides the crest of the upper lid toward one corner, since a cat's
## upper lid peaks inboard rather than dead centre.
##
## edge_n shapes the base profile 1 - |x/a|^edge_n. It has to stay above 1.0:
## at exactly 1.0 the profile is linear and |x| puts a kink at x=0, so the lid
## comes to a tent point at its crest and the whole eye looks faceted. Around
## 1.7 the edges stay near-straight while the crest is smooth.
## The two lids are shaped independently, because a cat's eye is not symmetric:
## the upper lid runs as a near-straight chord while the lower one bulges. A
## shared profile can only ever produce a leaf or a diamond.
##
## A high edge_n_up holds the upper edge flat across almost its whole width and
## drops it abruptly at the corners - a flat top with knife points. A low
## edge_n_low with a low low_power gives the underside a full round curve.
static func almond(center: Vector2, half_w: float, up_h: float, low_h: float,
		up_power: float = 0.92, low_power: float = 0.55, peak_shift: float = 0.0,
		segments: int = 48, rotation: float = 0.0,
		edge_n_up: float = 6.0, edge_n_low: float = 1.9) -> PackedVector2Array:
	var raw := PackedVector2Array()
	var n_up := maxf(1.05, edge_n_up)
	var n_low := maxf(1.05, edge_n_low)

	# Upper edge, inner corner across to outer corner.
	for i in range(segments + 1):
		var x: float = lerpf(-half_w, half_w, float(i) / float(segments))
		var u: float = clampf(1.0 - pow(absf(x / half_w), n_up), 0.0, 1.0)
		var skew: float = 1.0 + peak_shift * (x / half_w)
		raw.append(Vector2(x, -up_h * pow(u, up_power) * skew))

	# Lower edge back the other way. Endpoints are skipped so the two corner
	# vertices are not duplicated.
	for i in range(segments - 1, 0, -1):
		var x: float = lerpf(-half_w, half_w, float(i) / float(segments))
		var u: float = clampf(1.0 - pow(absf(x / half_w), n_low), 0.0, 1.0)
		raw.append(Vector2(x, low_h * pow(u, low_power)))

	var out := PackedVector2Array()
	for p in raw:
		out.append(center + (p.rotated(rotation) if rotation != 0.0 else p))
	return out


## Smoothstep with zero derivative at both ends.
static func smoothstep01(x: float) -> float:
	x = clampf(x, 0.0, 1.0)
	return x * x * (3.0 - 2.0 * x)
