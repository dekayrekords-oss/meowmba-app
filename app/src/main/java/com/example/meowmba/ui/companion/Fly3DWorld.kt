package com.example.meowmba.ui.companion

import com.example.meowmba.core.behavior.BehaviorBias
import com.example.meowmba.core.neural.FlyMotorIntent
import kotlin.math.*

/** Chest-root pose, world units, +Y up / +Z toward camera. Angles in degrees. */
internal data class Fly3DPose(
    val x: Float = 0f, val y: Float = 2f, val z: Float = 0f,
    val yaw: Float = 0f, val pitch: Float = 0f, val roll: Float = 0f,
    val vx: Float = 0f, val vy: Float = 0f, val vz: Float = 0f,
    val flight: Float = 1f, val wingPhase: Float = 0f, val walkPhase: Float = 0f,
    val reach: Float = 0f, val holding: String? = null,
    val handX: Float = .42f, val handY: Float = 1.8f, val handZ: Float = .2f,
    val gazeYaw: Float = 0f,
    val grooming: Float = 0f,
)

/** Prop XYZ denotes its base; contact at base + .10 along Y. */
data class Fly3DProp(val kind: String, val x: Float, val y: Float, val z: Float,
    val vx: Float = 0f, val vy: Float = 0f, val vz: Float = 0f,
    val held: Boolean = false, val angle: Float = 0f)
internal data class Fly3DWorldSnapshot(val pose: Fly3DPose = Fly3DPose(),
    val props: List<Fly3DProp> = listOf(Fly3DProp("book", -1f, .8f, .5f), Fly3DProp("glass", 1f, .8f, .5f), Fly3DProp("ball", 1.6f, .8f, 0f)),
    val elapsed: Float = 0f, val action: HabitatAction = HabitatAction.EXPLORE,
    val backgroundId: String = "kitchen")
internal data class Fly3DTarget(val x: Float, val y: Float, val z: Float)

/**
 * Deterministic bounded cartoon flight, not a biological simulation. The host supplies
 * active elapsed time; repeated timestamps freeze even queued commands. Large resume
 * gaps consume at most 50ms, split into <=10ms physics steps, never offline motion.
 * Contacts approach physically and grasp only when the world-space hand meets a prop.
 */
internal class Fly3DWorld {
    private var frame = Fly3DWorldSnapshot()
    private var lastTime: Float? = null
    private var lastCommand = Long.MIN_VALUE
    private var target = Fly3DTarget(0f, 2.5f, 0f)
    private var actionAge = 0f
    private var targetKind: String? = null
    private var approached = false
    private var tapRemaining = 0f
    private var holdOffset = Fly3DTarget(0f, -.1f, 0f)
    private var neuralHeading = 0f
    private var brainHadControl = false

    fun snapshot(): Fly3DWorldSnapshot = frame

    /** Normalized touch selects a point on a bounded interaction plane, not an invented depth. */
    fun tap(normalizedX: Float, normalizedY: Float, depth: Float = 0f) {
        target = normalizedTarget(normalizedX, normalizedY, depth)
        tapRemaining = 4f
    }

    fun step(timeSec: Float, command: HabitatCommand, bias: BehaviorBias,
        playPreference: Float = 0f, backgroundId: String = "kitchen", wingDrive: Float = 0f,
        motorIntent: FlyMotorIntent = FlyMotorIntent.NONE): Fly3DWorldSnapshot {
        if (!timeSec.isFinite()) return frame
        val prior = lastTime
        if (prior != null && timeSec <= prior) return frame
        lastTime = timeSec
        val dt = if (prior == null) 0f else (timeSec - prior).coerceIn(0f, .05f)
        if (command.id != lastCommand) {
            lastCommand = command.id
            begin(command.action)
        }
        frame = frame.copy(backgroundId = backgroundId)
        val steps = ceil(dt / .01f).toInt().coerceAtLeast(1)
        val motor = motorIntent.bounded()
        repeat(steps) { advance(dt / steps, bias.arousal.finite(.4f).coerceIn(0f, 1f), playPreference.finite(0f).coerceIn(-1f, 1f), wingDrive.finite(0f).coerceIn(0f, 1f), motor) }
        return frame
    }

    private fun begin(action: HabitatAction) {
        // A new request lets go in place; it never resets object positions.
        frame = frame.copy(action = action, props = frame.props.map { it.copy(held = false) }, pose = frame.pose.copy(holding = null))
        actionAge = 0f; approached = false
        targetKind = when (action) {
            HabitatAction.READ -> "book"
            HabitatAction.DRINK -> "glass"
            HabitatAction.FEED -> "fruit"
            HabitatAction.PLAY, HabitatAction.NUDGE -> "ball"
            else -> null
        }
        if (action == HabitatAction.FEED) {
            // One explicit serving, never an accumulating collection or an offline timer.
            frame = frame.copy(props = frame.props.filterNot { it.kind == "fruit" } +
                Fly3DProp("fruit", .25f, TABLE_Y, .55f))
        }
        if (action != HabitatAction.EXPLORE) tapRemaining = 0f
        if (action == HabitatAction.REST || action == HabitatAction.FEED || action == HabitatAction.RELEASE)
            target = Fly3DTarget(frame.pose.x.coerceIn(-1.8f, 1.8f), TABLE_CHEST, frame.pose.z.coerceIn(-.7f, .7f))
    }

    private fun advance(dt: Float, activity: Float, preference: Float, wingDrive: Float, motor: FlyMotorIntent) {
        if (dt <= 0f) return
        actionAge += dt
        tapRemaining = (tapRemaining - dt).coerceAtLeast(0f)
        val elapsed = frame.elapsed + dt
        var props = frame.props.map { integrateProp(it, dt) }
        val prop = props.firstOrNull { it.kind == targetKind }
        val old = frame.pose
        // Explicit touches, food/prop requests and rest retain control of their
        // destinations. Only free exploration accepts measured motor proposals.
        val neuralControl = motor.active && frame.action == HabitatAction.EXPLORE &&
            tapRemaining == 0f && targetKind == null && old.holding == null
        if (neuralControl && !brainHadControl) neuralHeading = old.yaw
        brainHadControl = neuralControl
        val contactGround = if (prop != null && prop.y < .6f) FLOOR_CHEST else TABLE_CHEST
        if (prop != null && !approached) {
            target = Fly3DTarget((prop.x - .42f).coerceIn(-2.35f, 2.35f), contactGround, (prop.z - .2f).coerceIn(-1.3f, 1.3f))
        } else if (neuralControl) {
            neuralHeading = (neuralHeading + motor.turn * 85f * dt) % 360f
            val lift = max(motor.takeoff, max(motor.jump, motor.escape))
            val settle = max(motor.landing, motor.grooming * (1f - lift))
            val travel = (motor.forward - motor.backward + motor.escape * .6f)
                .coerceIn(-1f, 1f) * (1f - motor.halt)
            val heading = neuralHeading / DEG
            target = Fly3DTarget(
                (old.x + sin(heading) * travel * .85f).coerceIn(-2.4f, 2.4f),
                when {
                    settle > .25f && settle > lift -> TABLE_CHEST
                    lift > .25f -> (old.y + lift * .8f).coerceAtMost(3.7f)
                    else -> old.y
                },
                (old.z + cos(heading) * travel * .85f).coerceIn(-1.4f, 1.4f))
        } else if (frame.action == HabitatAction.EXPLORE && tapRemaining == 0f) {
            val speed = .30f + activity * .11f + preference * .035f
            val phase = elapsed * speed
            target = Fly3DTarget(sin(phase) * 1.85f, 2.45f + sin(phase * .71f) * .75f, sin(phase * .83f + .6f) * 1.05f)
        }
        // A held object stays in an easy-to-read presentation pose near its pickup spot.
        val dx = target.x - old.x; val dy = target.y - old.y; val dz = target.z - old.z
        val maxSpeed = .85f + activity * .6f
        fun steer(v: Float, d: Float): Float = (v + (d * 7f - v * 5.5f) * dt).coerceIn(-maxSpeed, maxSpeed)
        var vx = steer(old.vx, dx); var vy = steer(old.vy, dy); var vz = steer(old.vz, dz)
        val speed = sqrt(vx * vx + vy * vy + vz * vz)
        if (speed > maxSpeed) { val ratio = maxSpeed / speed; vx *= ratio; vy *= ratio; vz *= ratio }
        // Changing the destination must not raise the collision plane through the actor.
        val landingY = if (target.y < TABLE_CHEST - .1f) FLOOR_CHEST else TABLE_CHEST
        val minY = if (old.y >= TABLE_CHEST && landingY == TABLE_CHEST) TABLE_CHEST else FLOOR_CHEST
        val x = (old.x + vx * dt).coerceIn(-2.5f, 2.5f)
        val y = (old.y + vy * dt).coerceIn(minY, 4f)
        val z = (old.z + vz * dt).coerceIn(-1.5f, 1.5f)
        if (x == -2.5f || x == 2.5f) vx = 0f
        if (y == minY || y == 4f) vy = 0f
        if (z == -1.5f || z == 1.5f) vz = 0f
        val grounded = abs(y - landingY) < .022f && target.y <= landingY + .01f && abs(vy) < .08f
        val flight = ease(old.flight, if (grounded) 0f else 1f, dt, 6f)
        val horizontal = hypot(vx, vz)
        // Return frontward during held/perched moments; cruising turns show real depth.
        val presenting = approached || grounded || sin(elapsed * .4f) > .88f
        val desiredYaw = if (neuralControl) neuralHeading
            else if (presenting || horizontal < .035f || targetKind != null) 0f else atan2(vx, vz) * DEG
        val yaw = old.yaw + angleDelta(old.yaw, desiredYaw).coerceIn(-95f * dt, 95f * dt)
        val bank = (angleDelta(old.yaw, desiredYaw) * -.22f).coerceIn(-24f, 24f) * flight
        val near = distance(x, y, z, target.x, target.y, target.z) < .18f
        val reaching = prop != null && !approached && near
        val reach = ease(old.reach, if (reaching || old.holding != null) 1f else 0f, dt, 6f)
        val feeding = old.holding == "fruit"
        val neutral = neutralHandOffset(yaw, feeding)
        val handTargetX = if (reaching) prop!!.x else x + neutral.x
        val handTargetY = if (reaching) prop!!.y + .10f else y + neutral.y
        val handTargetZ = if (reaching) prop!!.z else z + neutral.z
        // Exponential easing alone has unbounded speed when grasp changes to lifting.
        // Cap the complete 3D hand displacement (not each axis) so the carried prop
        // cannot jump on that transition, even at a low refresh rate or resume frame.
        val easedX = ease(old.handX, handTargetX, dt, 12f)
        val easedY = ease(old.handY, handTargetY, dt, 12f)
        val easedZ = ease(old.handZ, handTargetZ, dt, 12f)
        val handDistance = distance(old.handX, old.handY, old.handZ, easedX, easedY, easedZ)
        val handFraction = if (handDistance > .000001f) minOf(1f, 1.1f * dt / handDistance) else 1f
        val hx = old.handX + (easedX - old.handX) * handFraction
        val hy = old.handY + (easedY - old.handY) * handFraction
        val hz = old.handZ + (easedZ - old.handZ) * handFraction
        var holding = old.holding
        if (reaching && prop != null && distance(hx, hy, hz, prop.x, prop.y + .1f, prop.z) < .024f && horizontal < .08f) {
            approached = true; actionAge = 0f
            if (frame.action == HabitatAction.READ || frame.action == HabitatAction.DRINK || frame.action == HabitatAction.FEED) {
                holding = prop.kind
                holdOffset = Fly3DTarget(prop.x - hx, prop.y - hy, prop.z - hz)
                props = props.map { if (it.kind == prop.kind) it.copy(held = true, vx = 0f, vy = 0f, vz = 0f) else it }
            } else {
                props = props.map { if (it.kind == prop.kind) it.copy(vx = if (frame.action == HabitatAction.NUDGE) 1.8f else -.9f, vy = if (frame.action == HabitatAction.PLAY) 1.5f else .10f, vz = .45f) else it }
            }
        }
        props = props.map { if (it.held) it.copy(x = hx + holdOffset.x, y = hy + holdOffset.y, z = hz + holdOffset.z) else it }
        if (holding == "fruit" && actionAge > 2.5f &&
            distance(hx, hy, hz, handTargetX, handTargetY, handTargetZ) < .045f) {
            props = props.filterNot { it.kind == "fruit" }
            holding = null
        }
        // A stopped walker puts both feet down instead of retaining a lifted shoe.
        val walkPhase = if (horizontal < .035f) {
            val nearest = round(old.walkPhase / PI.toFloat()) * PI.toFloat()
            ease(old.walkPhase, nearest, dt, 9f)
        } else (old.walkPhase + dt * horizontal * 9f * (1f - flight)) % TAU
        val pose = Fly3DPose(x, y, z, yaw,
            ease(old.pitch, (vy * -12f).coerceIn(-18f, 18f), dt, 5f), ease(old.roll, bank, dt, 5f),
            vx, vy, vz, flight,
            // BANC DNg27 -> wing-power activity only changes the visual motor
            // rhythm. Kinematic flight remains bounded authored animation.
            (old.wingPhase + dt * (6f + flight * 34f + wingDrive * 16f)) % TAU,
            walkPhase,
            reach, holding, hx, hy, hz, (-yaw).coerceIn(-35f, 35f),
            ease(old.grooming, if (neuralControl && grounded) motor.grooming else 0f, dt, 5f))
        frame = frame.copy(pose = pose, props = props, elapsed = elapsed)
    }

    private fun integrateProp(prop: Fly3DProp, dt: Float): Fly3DProp {
        if (prop.held) return prop
        val x = (prop.x + prop.vx * dt).coerceIn(-2.65f, 2.65f)
        val z = (prop.z + prop.vz * dt).coerceIn(-1.55f, 1.55f)
        // Once an object has fallen under the tabletop, crossing underneath cannot teleport it up.
        val aboveTable = prop.y >= TABLE_Y - .005f && abs(x) <= 2.1f && abs(z) <= 1f
        val surface = if (aboveTable) TABLE_Y else 0f
        var vy = prop.vy - 4f * dt
        var y = prop.y + vy * dt
        var vx = prop.vx; var vz = prop.vz
        if (y <= surface) {
            y = surface
            vy = if (abs(vy) > .25f) -vy * if (prop.kind == "ball") .48f else .12f else 0f
            val friction = (1f - dt * if (prop.kind == "ball") 1.4f else 5f).coerceAtLeast(0f)
            vx *= friction; vz *= friction
        }
        if (abs(x) >= 2.65f) vx *= -.3f
        if (abs(z) >= 1.55f) vz *= -.3f
        return prop.copy(x = x, y = y, z = z, vx = vx, vy = vy, vz = vz,
            angle = (prop.angle + prop.vx * dt * 45f) % 360f)
    }

    companion object {
        const val TABLE_Y = .8f
        const val TABLE_CHEST = 1.35f
        const val FLOOR_CHEST = .55f
        /** Local grip turns with the chest; world-space contact still overrides this. */
        fun neutralHandOffset(yawDegrees: Float, feeding: Boolean = false): Fly3DTarget {
            val a = yawDegrees.finite(0f) / DEG
            val x = if (feeding) .04f else .42f
            val z = if (feeding) .35f else .20f
            return Fly3DTarget(x * cos(a) + z * sin(a), if (feeding) .04f else -.20f,
                -x * sin(a) + z * cos(a))
        }
        private const val TAU = 6.2831855f
        private const val DEG = 57.29578f
        fun normalizedTarget(x: Float, y: Float, depth: Float = 0f) = Fly3DTarget(
            (x.finite(.5f).coerceIn(0f, 1f) - .5f) * 4.6f,
            3.8f - y.finite(.5f).coerceIn(0f, 1f) * 2.3f,
            depth.finite(0f).coerceIn(-1.3f, 1.3f))
        private fun Float.finite(fallback: Float) = if (isFinite()) this else fallback
        private fun ease(a: Float, b: Float, dt: Float, rate: Float) = a + (b - a) * (1f - exp(-dt * rate))
        private fun angleDelta(a: Float, b: Float): Float = ((b - a + 540f) % 360f + 360f) % 360f - 180f
        private fun distance(x: Float, y: Float, z: Float, xx: Float, yy: Float, zz: Float) = sqrt((x-xx).pow(2) + (y-yy).pow(2) + (z-zz).pow(2))
    }
}
