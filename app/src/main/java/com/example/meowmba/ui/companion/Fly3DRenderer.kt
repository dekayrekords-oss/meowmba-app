package com.example.meowmba.ui.companion

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Immutable snapshot from the existing pause-aware avatar clock. Angles are radians.
 * Position is world-space, in x[-2.5,2.5], y[.7,4], z[-1.5,1.5].
 * blink is closure (0=open, 1=closed); flight/reach/mouth are 0..1.
 */
data class Fly3DFrame(
    val timeSeconds: Float = 0f,
    val x: Float = 0f, val y: Float = 2.1f, val z: Float = 0f,
    val yaw: Float = 0f, val pitch: Float = 0f, val roll: Float = 0f,
    val flight: Float = 0f, val walkPhase: Float = 0f,
    val gazeX: Float = 0f, val gazeY: Float = 0f,
    val blink: Float = 0f, val mouth: Float = 0f, val reach: Float = 0f,
    val holding: String? = null, val backgroundId: String = "kitchen", val active: Boolean = true,
    val props: List<Fly3DProp>? = null,
    val handX: Float? = null, val handY: Float? = null, val handZ: Float? = null,
)

/** GLES2 mesh renderer. Host uses RENDERMODE_WHEN_DIRTY and publishes [frame] before
 * requestRender; there is no timer, thread, sensor subscription or inference here.
 * Every primitive is volumetric; depth and yaw reveal genuinely different side/back views.
 */
class Fly3DRenderer : GLSurfaceView.Renderer {
    @Volatile var frame = Fly3DFrame()
    @Volatile var lastError: String? = null
        private set
    @Volatile var renderedFrames: Long = 0
        private set

    private val sphere = Fly3DGeometry.sphere()
    private val smallSphere = Fly3DGeometry.sphere(12, 8)
    private val cube = Fly3DGeometry.cube()
    private val wing = Fly3DGeometry.wing()
    private val model = FloatArray(16)
    private val view = FloatArray(16)
    private val projection = FloatArray(16)
    private val vp = FloatArray(16)
    private val mvp = FloatArray(16)
    private val inverse = FloatArray(16)
    private val normal = FloatArray(16)
    private val stack = Array(20) { FloatArray(16) }
    private var stackAt = 0
    private var program = 0
    private var positionHandle = 0
    private var normalHandle = 0
    private var mvpHandle = 0
    private var modelHandle = 0
    private var viewProjectionHandle = 0
    private var normalMatrixHandle = 0
    private var colorHandle = 0
    private var outlineHandle = 0
    private var outlineWidthHandle = 0
    private var unlitHandle = 0
    private var ready = false
    private var lastActiveFrame = Fly3DFrame()
    private val rootInverse = FloatArray(16)
    private val worldHand = FloatArray(4)
    private val localHand = FloatArray(4)
    private var hasHandTarget = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        ready = false
        try {
            // Old object names belong to the lost context; never try to reuse them.
            program = link(VERTEX_SHADER, FRAGMENT_SHADER)
            positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
            normalHandle = GLES20.glGetAttribLocation(program, "aNormal")
            mvpHandle = GLES20.glGetUniformLocation(program, "uMvp")
            modelHandle = GLES20.glGetUniformLocation(program, "uModel")
            viewProjectionHandle = GLES20.glGetUniformLocation(program, "uViewProjection")
            normalMatrixHandle = GLES20.glGetUniformLocation(program, "uNormalMatrix")
            colorHandle = GLES20.glGetUniformLocation(program, "uColor")
            outlineHandle = GLES20.glGetUniformLocation(program, "uOutline")
            outlineWidthHandle = GLES20.glGetUniformLocation(program, "uOutlineWidth")
            unlitHandle = GLES20.glGetUniformLocation(program, "uUnlit")
            sphere.upload(); smallSphere.upload(); cube.upload(); wing.upload()
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            GLES20.glDepthFunc(GLES20.GL_LEQUAL)
            GLES20.glEnable(GLES20.GL_CULL_FACE)
            GLES20.glFrontFace(GLES20.GL_CCW)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            Matrix.setLookAtM(view, 0, 0f, 2.5f, 8f, 0f, 1.5f, 0f, 0f, 1f, 0f)
            ready = true; lastError = null
        } catch (error: RuntimeException) {
            lastError = "3D renderer initialization failed: ${error.message}"
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / max(1, height)
        // Keep the entire play width visible on folded portrait displays without moving the camera.
        val halfWidth = max(3.8f, 3.2f * aspect)
        val near = .1f
        Matrix.frustumM(projection, 0, -halfWidth * near / 8f, halfWidth * near / 8f,
            -halfWidth / aspect * near / 8f, halfWidth / aspect * near / 8f, near, 40f)
        Matrix.multiplyMM(vp, 0, projection, 0, view, 0)
    }

    override fun onDrawFrame(gl: GL10?) {
        val incoming = frame
        val f = if (incoming.active) incoming.also { lastActiveFrame = it } else lastActiveFrame
        // One coherent snapshot for opaque geometry, transparent wings and articulated hand.
        GLES20.glClearColor(.88f, .90f, .84f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (!ready) return
        GLES20.glUseProgram(program)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDepthMask(true)
        Matrix.setIdentityM(model, 0); stackAt = 0
        room(f)
        push()
        Matrix.translateM(model, 0, f.x.safe().coerceIn(-2.5f,2.5f), f.y.safe(2.1f).coerceIn(.55f,4f), f.z.safe().coerceIn(-1.5f,1.5f))
        Matrix.rotateM(model, 0, f.yaw.safe() * DEGREES, 0f,1f,0f)
        Matrix.rotateM(model, 0, f.pitch.safe() * DEGREES, 1f,0f,0f)
        Matrix.rotateM(model, 0, f.roll.safe() * DEGREES, 0f,0f,1f)
        Matrix.scaleM(model, 0, .50f,.50f,.50f)
        hasHandTarget = f.handX?.isFinite() == true && f.handY?.isFinite() == true && f.handZ?.isFinite() == true
        if (hasHandTarget && Matrix.invertM(rootInverse,0,model,0)) {
            worldHand[0]=f.handX!!; worldHand[1]=f.handY!!; worldHand[2]=f.handZ!!; worldHand[3]=1f
            Matrix.multiplyMV(localHand,0,rootInverse,0,worldHand,0)
        } else hasHandTarget=false
        fly(f)
        pop()
        renderedFrames++
    }

    private fun room(f: Fly3DFrame) {
        val night = f.backgroundId in NIGHT_BACKGROUNDS
        val garden = f.backgroundId in GARDEN_BACKGROUNDS
        val wallColor = when(f.backgroundId) {
            "library" -> 0x9AAB9F; "workshop" -> 0xB9ADA0; "cafe" -> 0xD8B5A4
            "pond" -> 0x91BDBA; "beach" -> 0xB7DDD7; "orchard" -> 0xB8CD9E
            else -> if (night) 0x37465D else if (garden) 0xABC7AC else 0xDADAC4
        }
        val floorColor = if (f.backgroundId == "beach") 0xD9C9A0 else if (night) 0x606870 else if (garden) 0x749770 else 0xB9B69A
        box(0f,-.10f,0f, 4.5f,.10f,3.7f, floorColor, outline = false)
        box(0f,3f,-2.6f, 4.5f,3f,.10f, wallColor, outline = false)
        box(-4.3f,3f,0f, .10f,3f,2.6f, wallColor, outline = false)
        // Floorboards and wall paneling establish parallax and depth cues without textures.
        for (i in -3..3) {
            box(i * 1.1f,.004f,0f, .009f,.005f,2.6f, if (night) 0x505A64 else 0x939884, outline = false)
            box(i * 1.1f,1.45f,-2.47f, .018f,1.4f,.022f, if (night) 0x526079 else 0xBAC7B2, outline = false)
        }
        // Recessed window, sky, mullions and a slow passing cloud live at different depths.
        box(-1.65f,3.10f,-2.42f, 1.0f,.95f,.06f, 0x657D77)
        box(-1.65f,3.10f,-2.33f, .88f,.83f,.035f, if (night) 0x233955 else 0xC0DFD8, outline = false)
        val cloudX = -1.65f + sin(f.timeSeconds.safe() * .12f) * .40f
        ellipsoid(cloudX,3.38f,-2.25f, .37f,.16f,.06f, if (night) 0xD6D9B2 else 0xF1F0DF, outline = false)
        box(-1.65f,3.10f,-2.18f, .027f,.86f,.035f, 0x708781, outline = false)
        box(-1.65f,3.10f,-2.17f, .90f,.025f,.035f, 0x708781, outline = false)
        roomDetails(f)
        // Main interaction table top at y=.80. Grounded root≈1.35 (feet−1.10*.50).
        box(0f,.72f,0f, 2.10f,.08f,1.0f, if (garden) 0x9A8862 else 0xA88662)
        for (side in -1..1 step 2) {
            box(side*1.90f,.35f,-.80f, .075f,.35f,.075f, 0x735F49)
            box(side*1.90f,.35f,.80f, .075f,.35f,.075f, 0x735F49)
        }
        // The simulation owns every base position, including held/falling props; no duplicate inventory.
        val sceneProps=f.props
        if (sceneProps != null) {
            for (prop in sceneProps) {
                push(); Matrix.translateM(model,0,prop.x,prop.y,prop.z)
                Matrix.rotateM(model,0,prop.angle,0f,0f,1f)
                when(prop.kind) { "book" -> book(); "glass" -> glass(); else -> ball() }
                pop()
            }
        } else {
            if (f.holding != "book") { push(); Matrix.translateM(model,0,-1f,.8f,.5f); book(); pop() }
            if (f.holding != "glass") { push(); Matrix.translateM(model,0,1f,.8f,.5f); glass(); pop() }
            push(); Matrix.translateM(model,0,1.6f,.8f,0f); ball(); pop()
        }
        // A succulent offers a recognizable volumetric silhouette and gentle background movement.
        ellipsoid(-2.65f,.24f,-1.55f,.24f,.24f,.24f,0xB17A5B)
        for (i in 0..4) {
            push(); Matrix.translateM(model,0,-2.65f,.46f,-1.55f)
            Matrix.rotateM(model,0,i*72f,0f,1f,0f)
            Matrix.rotateM(model,0,25f + sin(f.timeSeconds.safe()*.65f+i)*3f,0f,0f,1f)
            ellipsoid(0f,.22f,0f,.065f,.28f,.11f,0x567D59); pop()
        }
    }

    /** Ten authored scenery motifs use the same bounded geometry and active host time. */
    private fun roomDetails(f: Fly3DFrame) {
        val t=f.timeSeconds.safe()
        when(f.backgroundId) {
            "library" -> {
                for (shelf in 0..2) {
                    box(1.8f,1.7f+shelf*.57f,-2.23f,1.15f,.045f,.25f,0x806A55)
                    for (i in 0..5) {
                        val height=.16f+(i%3)*.035f
                        box(.87f+i*.34f,1.75f+shelf*.57f+height,-2.22f,.11f,height,.15f,
                            if(i%2==0) 0x547F77 else 0xBF8869)
                    }
                }
            }
            "greenhouse" -> {
                for(i in 0..3) {
                    box(.60f+i*.78f,3.0f,-2.35f,.035f,1.48f,.04f,0x66897B,outline=false)
                    val sway=sin(t*.55f+i)*.08f
                    segment(.75f+i*.72f,1.5f,-2.10f,.75f+i*.72f+sway,2.75f,-2.10f,.025f,0x527950)
                    ellipsoid(.86f+i*.72f+sway,2.45f,-2.10f,.20f,.34f,.07f,0x77A36B)
                }
            }
            "workshop" -> {
                box(1.9f,2.75f,-2.31f,1.15f,.76f,.05f,0xA38366)
                for(i in 0..4) {
                    box(1.05f+i*.42f,2.70f,-2.18f,.035f,.32f,.04f,0x637C79)
                    box(1.05f+i*.42f,2.99f,-2.17f,.12f,.065f,.055f,0xBCC6B5)
                }
                ellipsoid(2.6f,1.8f,-2.08f,.25f,.25f,.035f,0xB99363)
                segment(2.6f,1.8f,-2.02f,2.6f+sin(t*.22f)*.19f,1.8f+cos(t*.22f)*.19f,-2.02f,.017f,0x586867)
            }
            "pond" -> {
                box(1.8f,2.5f,-2.31f,1.18f,.90f,.06f,0x6FA7AA)
                for(i in 0..3) {
                    ellipsoid(1.15f+i*.49f,2.1f+sin(t*.6f+i)*.06f,-2.20f,.23f,.08f,.04f,0x91BC8B)
                    segment(1f+i*.51f,1.66f,-2.18f,1f+i*.51f+sin(t*.5f+i)*.05f,2.65f,-2.18f,.018f,0x567E68)
                }
            }
            "orchard" -> {
                segment(1.8f,1.35f,-2.1f,1.9f,3.2f,-2.1f,.075f,0x8C7859)
                for(i in 0..4) {
                    val a=i*1.4f
                    ellipsoid(1.85f+sin(a)*.64f,2.9f+cos(a)*.4f,-2.12f,.49f,.47f,.19f,0x87A768)
                    ellipsoid(1.85f+sin(a)*.68f,2.74f+cos(a)*.36f+sin(t*.7f+i)*.018f,-1.90f,.095f,.10f,.085f,0xD38361)
                }
            }
            "cafe" -> {
                box(1.9f,2.6f,-2.31f,1.1f,.62f,.07f,0x54756B)
                for(i in 0..4) box(1.15f+i*.37f,3.43f+sin(t*.4f+i)*.014f,-2.02f,.18f,.12f,.34f,
                    if(i%2==0) 0xE4C9A7 else 0xAA7764)
                for(i in 0..2) box(1.32f,2.85f-i*.22f,-2.20f,.38f,.012f,.009f,0xD8D7B4,outline=false)
            }
            "observatory" -> {
                for(i in 0..8) {
                    val a=i*2.39f
                    ellipsoid(1.8f+sin(a)*1.1f,2.85f+cos(a*.71f)*1.1f,-2.28f,
                        .024f+sin(t*.8f+i)*.006f,.035f,.015f,0xD9DFBD,outline=false)
                }
                segment(2.65f,0f,-1.7f,2.65f,1.4f,-1.7f,.045f,0x74828A)
                segment(2.25f,1.2f,-1.7f,2.92f,1.9f,-1.7f,.18f,0xA8AE9F)
            }
            "beach" -> {
                for(i in 0..4) ellipsoid(.9f+i*.52f,1.78f+sin(t*.6f+i*.5f)*.06f,-2.24f,.4f,.14f,.08f,
                    if(i%2==0) 0x96C6BC else 0xE5E7CC,outline=false)
                segment(2.7f,0f,-1.7f,2.5f,2.0f,-1.7f,.055f,0xB5996E)
                ellipsoid(2.5f,2.08f,-1.7f,.8f,.21f,.55f,0xD4A074)
            }
            "bedroom" -> {
                box(2.85f,.34f,-1.62f,.65f,.30f,.77f,0xAA8F88)
                box(2.85f,.66f,-1.6f,.66f,.055f,.78f,0x9BAFA9)
                ellipsoid(2.85f,.78f,-2.0f,.45f,.12f,.24f,0xD4D6B9)
                ellipsoid(2.4f,3.5f+sin(t*.3f)*.02f,-2.28f,.32f,.32f,.04f,0xD5D5A8,outline=false)
            }
            else -> { // Kitchen hanging copper pans and a slow pendulum clock.
                for(i in 0..2) {
                    segment(1.1f+i*.67f,3.3f,-2.17f,1.1f+i*.67f,2.89f,-2.17f,.027f,0x6B7771)
                    ellipsoid(1.1f+i*.67f,2.67f,-2.13f,.23f,.25f,.06f,0xB89169)
                }
                ellipsoid(2.0f,1.85f,-2.20f,.25f,.28f,.04f,0xDDD8BA)
                segment(2f,1.85f,-2.14f,2f+sin(t*.12f)*.17f,1.85f+cos(t*.12f)*.17f,-2.14f,.013f,0x5A7068)
            }
        }
    }

    private fun fly(f: Fly3DFrame) {
        val flight = f.flight.safe().coerceIn(0f,1f)
        val phase = f.walkPhase.safe()
        val stride = sin(phase) * (1f-flight)
        val reach = f.reach.safe().coerceIn(0f,1f)
        val t = f.timeSeconds.safe()
        // Full abdomen visible from behind; head projects forward instead of replacing the body.
        ellipsoid(0f,-.27f,-.04f,.43f,.57f,.39f,GREEN)
        ellipsoid(0f,-.38f,.01f,.435f,.12f,.388f,DARK_GREEN)
        ellipsoid(0f,-.63f,-.015f,.31f,.10f,.285f,DARK_GREEN)
        ellipsoid(0f,.12f,-.02f,.42f,.36f,.35f,SHELL)
        ellipsoid(0f,.49f,.13f,.62f,.52f,.47f,SHELL)
        ellipsoid(-.22f,.71f,-.14f,.24f,.17f,.12f,SHELL_LIGHT,outline=false)
        // Two humanoid legs: hip → knee → ankle; feet have depth and a forward toe.
        for (side in -1..1 step 2) {
            val sx = side.toFloat()
            val kneeY = -.76f + flight*.17f
            val kneeZ = stride*sx*.13f + flight*.18f
            val ankleX = sx*(.28f + flight*.08f)
            val ankleY = -1.00f + flight*.40f + max(0f,stride*sx)*.12f
            val ankleZ = stride*sx*.21f - flight*.12f
            segment(sx*.20f,-.57f,0f,sx*.29f,kneeY,kneeZ,.056f,SHELL)
            segment(sx*.29f,kneeY,kneeZ,ankleX,ankleY,ankleZ,.055f,SHELL)
            ellipsoid(ankleX,ankleY-.015f,ankleZ+.085f,.14f,.075f,.22f,SHELL)
            // Rear pair of arms sit behind the shoulders: insect identity, readable six-limb count.
            val backHandX = sx*(.69f+flight*.12f)
            val backHandY = -.35f+flight*.26f+stride*sx*.06f
            segment(sx*.34f,-.08f,-.15f,sx*.56f,-.25f,-.18f,.050f,SHELL)
            segment(sx*.56f,-.25f,-.18f,backHandX,backHandY,-.09f,.047f,SHELL)
            hand(backHandX,backHandY,-.09f,.09f)
            // Front hands lift/gesture with reach, preserving elbow articulation.
            val held = side > 0 && f.holding != null
            val ik = side > 0 && hasHandTarget
            val handX = if (ik) localHand[0] else sx*(if (held) .78f else .82f + reach*.22f)
            val handY = if (ik) localHand[1] else if (held) .09f else .04f+reach*.47f+flight*.13f+sin(phase+side)*.045f
            val handZ = if (ik) localHand[2] else if (held) .43f else .19f+reach*.15f
            val elbowX = if (ik) (.43f+handX)*.5f+.12f else sx*.67f
            val elbowY = if (ik) (.21f+handY)*.5f+.10f else .08f+reach*.23f
            val elbowZ = if (ik) (.07f+handZ)*.5f else .16f
            segment(sx*.43f,.21f,.07f,elbowX,elbowY,elbowZ,.063f,SHELL)
            segment(elbowX,elbowY,elbowZ,handX,handY,handZ,.055f,SHELL)
            if (held && f.props == null) {
                push(); Matrix.translateM(model,0,handX+.03f,handY-.08f,handZ)
                Matrix.scaleM(model,0,.70f,.70f,.70f)
                if (f.holding == "book") { Matrix.rotateM(model,0,-74f,1f,0f,0f); book() } else glass()
                pop()
            }
            hand(handX,handY,handZ,.108f)
        }
        val closure = f.blink.safe().coerceIn(0f,1f)
        val eyeHeight = .31f*(1f-closure)+.025f
        // Red compound lenses form the face's silhouette; pupils remain small, friendly and dimensional.
        for (side in -1..1 step 2) {
            val eyeX = side*.30f
            ellipsoid(eyeX,.53f,.48f,.30f,eyeHeight,.23f,RED)
            if (closure < .92f) {
                val gx = f.gazeX.safe().coerceIn(-1f,1f)*.065f
                val gy = f.gazeY.safe().coerceIn(-1f,1f)*.065f
                ellipsoid(eyeX+gx,.52f+gy,.689f,.102f,eyeHeight*.47f,.045f,INK,outline=false)
                ellipsoid(eyeX+gx-.026f,.56f+gy,.729f,.031f,.039f,.012f,CREAM,outline=false)
                ellipsoid(eyeX+gx+.025f,.49f+gy,.727f,.012f,.015f,.009f,CREAM,outline=false)
                // Stable faceted eye dots on the outside edge; no texture or random allocation.
                for (i in 0..3) {
                    val a = -.8f + i*.49f
                    ellipsoid(eyeX+side*.20f,.53f+sin(a)*eyeHeight*.68f,.60f+cos(a)*.04f,
                        .016f,.020f,.010f,0xA83F4A,outline=false)
                }
            }
        }
        // A curved smile sits on the face: never a proboscis, muzzle or ant mandible.
        val speech = f.mouth.safe().coerceIn(0f,1f)
        if (speech > .08f) {
            ellipsoid(0f,.185f,.566f,.12f,.026f+speech*.086f,.021f,INK,outline=false)
            ellipsoid(0f,.15f-speech*.015f,.586f,.063f,.017f+speech*.018f,.007f,0xD58E86,outline=false)
        } else {
            segment(-.13f,.205f,.554f,-.066f,.166f,.573f,.014f,INK,outline=false)
            segment(-.066f,.166f,.573f,.066f,.166f,.573f,.014f,INK,outline=false)
            segment(.066f,.166f,.573f,.13f,.205f,.554f,.014f,INK,outline=false)
        }
        // Short arista antennae, with three fine bristles each; response shares gaze/time snapshot.
        for (side in -1..1 step 2) {
            val sx = side.toFloat()
            val bend = sin(t*1.4f+side)*.035f + f.gazeX.safe().coerceIn(-1f,1f)*.025f
            segment(sx*.19f,.89f,.12f,sx*.23f+bend,1.10f,.09f,.026f,SHELL)
            ellipsoid(sx*.23f+bend,1.10f,.09f,.057f,.080f,.055f,SHELL_LIGHT)
            for (i in 0..2) segment(sx*.24f+bend,1.10f+i*.025f,.09f,
                sx*(.34f+i*.018f)+bend,1.13f+i*.046f,.10f,.009f,SHELL,outline=false)
        }
        // Transparent geometry is last, after all opaque surfaces have written depth.
        // Their roots are behind the thorax, so the head correctly occludes them.
        val backFirst = if (sin(f.yaw.safe()) > 0f) -1 else 1
        drawWing(backFirst,flight,t)
        drawWing(-backFirst,flight,t)
    }

    private fun drawWing(side: Int, flight: Float, time: Float) {
        push()
        Matrix.translateM(model,0,side*.24f,.32f,-.25f)
        Matrix.rotateM(model,0,side*(116f+sin(time*(5f+flight*19f))*(8f+flight*23f)),0f,1f,0f)
        Matrix.rotateM(model,0,-42f+flight*12f,1f,0f,0f)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glDepthMask(false)
        draw(wing,0xB7DFDF,.68f,outline=false)
        // The central vein is a real thin strut and also helps wings read against pale rooms.
        segment(0f,.02f,.05f,0f,.095f,1.25f,.009f,0x71989E,outline=false)
        for (i in 1..3) segment(0f,.07f,.27f*i,side*.17f,.09f,.27f*i+.17f,.006f,0x86A9AB,outline=false)
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
        pop()
    }

    private fun hand(x: Float,y: Float,z: Float,r: Float) {
        ellipsoid(x,y,z,r,r*.90f,r*.82f,SHELL)
        ellipsoid(x-.024f,y+.034f,z+.064f,r*.28f,r*.22f,r*.17f,SHELL_LIGHT,outline=false)
        // Two rounded knuckles and thumb, rather than featureless stick ends.
        ellipsoid(x+r*.58f,y+r*.22f,z+.018f,r*.40f,r*.42f,r*.46f,SHELL)
    }

    private fun ball() {
        ellipsoid(0f,.18f,0f,.18f,.18f,.18f,0xDF9965)
        ellipsoid(-.04f,.24f,.15f,.08f,.08f,.013f,0xF5D6A7,outline=false)
    }

    private fun book() {
        box(0f,.055f,0f,.28f,.055f,.20f,0x477C77)
        box(.015f,.058f,.0f,.248f,.030f,.181f,0xE6D6B5,outline=false)
        box(0f,.111f,0f,.28f,.012f,.20f,0x477C77)
        box(0f,.125f,0f,.13f,.003f,.014f,0xD6BD7D,outline=false)
    }

    private fun glass() {
        // Opaque cel-shaded rim/base and water keep depth correct; lightly colored wall volume.
        ellipsoid(0f,.20f,0f,.125f,.20f,.125f,0xB9D7D4,outline=true)
        ellipsoid(0f,.365f,0f,.113f,.022f,.113f,0xE4EEDE,outline=false)
        ellipsoid(0f,.367f,0f,.089f,.014f,.089f,0x72A9B1,outline=false)
        ellipsoid(-.056f,.24f,.095f,.018f,.095f,.012f,0xE7F4E8,outline=false)
    }

    private fun ellipsoid(x:Float,y:Float,z:Float,sx:Float,sy:Float,sz:Float,color:Int,outline:Boolean=true) {
        push(); Matrix.translateM(model,0,x,y,z); Matrix.scaleM(model,0,sx,sy,sz)
        draw(if (max(sx,max(sy,sz)) < .16f) smallSphere else sphere,color,outline=outline); pop()
    }
    private fun box(x:Float,y:Float,z:Float,sx:Float,sy:Float,sz:Float,color:Int,outline:Boolean=true) {
        push(); Matrix.translateM(model,0,x,y,z); Matrix.scaleM(model,0,sx,sy,sz)
        draw(cube,color,outline=outline); pop()
    }
    private fun segment(ax:Float,ay:Float,az:Float,bx:Float,by:Float,bz:Float,r:Float,color:Int,outline:Boolean=true) {
        val dx=bx-ax; val dy=by-ay; val dz=bz-az
        val length=sqrt(dx*dx+dy*dy+dz*dz)
        if (length < .0001f) return
        push(); Matrix.translateM(model,0,(ax+bx)*.5f,(ay+by)*.5f,(az+bz)*.5f)
        val horizontal=sqrt(dx*dx+dz*dz)
        if (horizontal > .0001f) Matrix.rotateM(model,0,acos((dy/length).coerceIn(-1f,1f))*DEGREES,dz/horizontal,0f,-dx/horizontal)
        else if (dy < 0f) Matrix.rotateM(model,0,180f,1f,0f,0f)
        Matrix.scaleM(model,0,r,length*.5f+r*.30f,r)
        draw(smallSphere,color,outline=outline); pop()
    }

    private fun push() { System.arraycopy(model,0,stack[stackAt++],0,16) }
    private fun pop() { System.arraycopy(stack[--stackAt],0,model,0,16) }
    private fun draw(mesh:Fly3DMesh,color:Int,alpha:Float=1f,outline:Boolean=true) {
        Matrix.multiplyMM(mvp,0,vp,0,model,0)
        if (!Matrix.invertM(inverse,0,model,0)) return
        Matrix.transposeM(normal,0,inverse,0)
        GLES20.glUniformMatrix4fv(mvpHandle,1,false,mvp,0)
        GLES20.glUniformMatrix4fv(modelHandle,1,false,model,0)
        GLES20.glUniformMatrix4fv(viewProjectionHandle,1,false,vp,0)
        GLES20.glUniformMatrix4fv(normalMatrixHandle,1,false,normal,0)
        GLES20.glUniform4f(colorHandle,((color shr 16) and 255)/255f,((color shr 8) and 255)/255f,(color and 255)/255f,alpha)
        GLES20.glUniform1f(unlitHandle,if (color == INK) 1f else 0f)
        mesh.bind(positionHandle,normalHandle)
        if (outline) {
            // Black inverted hull. Extrusion is in world units, independent of ellipsoid scale.
            GLES20.glCullFace(GLES20.GL_FRONT)
            GLES20.glUniform1f(outlineHandle,1f)
            GLES20.glUniform1f(outlineWidthHandle,.014f)
            mesh.draw()
        }
        GLES20.glCullFace(GLES20.GL_BACK)
        GLES20.glUniform1f(outlineHandle,0f)
        GLES20.glUniform1f(outlineWidthHandle,0f)
        mesh.draw()
    }

    private fun compile(type:Int,source:String):Int {
        val shader=GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader,source); GLES20.glCompileShader(shader)
        val status=IntArray(1); GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,status,0)
        if (status[0]==0) { val detail=GLES20.glGetShaderInfoLog(shader); GLES20.glDeleteShader(shader); error(detail) }
        return shader
    }
    private fun link(vertex:String,fragment:String):Int {
        val vs=compile(GLES20.GL_VERTEX_SHADER,vertex); val fs=compile(GLES20.GL_FRAGMENT_SHADER,fragment)
        val p=GLES20.glCreateProgram(); GLES20.glAttachShader(p,vs); GLES20.glAttachShader(p,fs); GLES20.glLinkProgram(p)
        GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs)
        val status=IntArray(1); GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,status,0)
        if(status[0]==0) { val detail=GLES20.glGetProgramInfoLog(p); GLES20.glDeleteProgram(p); error(detail) }
        return p
    }
    private fun Float.safe(fallback:Float=0f)=if(isFinite()) this else fallback

    private companion object {
        const val DEGREES=57.29578f
        const val INK=0x192D31
        const val SHELL=0x3B5154
        const val SHELL_LIGHT=0x708B85
        const val GREEN=0x78A758
        const val DARK_GREEN=0x416C48
        const val RED=0xEC6856
        const val CREAM=0xFFF2D0
        val NIGHT_BACKGROUNDS=setOf("observatory","bedroom")
        val GARDEN_BACKGROUNDS=setOf("greenhouse","pond","orchard","beach")
        const val VERTEX_SHADER="""
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            uniform mat4 uMvp;
            uniform mat4 uModel;
            uniform mat4 uViewProjection;
            uniform mat4 uNormalMatrix;
            uniform float uOutlineWidth;
            varying vec3 vNormal;
            void main() {
                vec3 n=normalize((uNormalMatrix*vec4(aNormal,0.0)).xyz);
                vNormal=n;
                vec4 base=uMvp*vec4(aPosition,1.0);
                gl_Position=base+uViewProjection*vec4(n*uOutlineWidth,0.0);
            }
        """
        const val FRAGMENT_SHADER="""
            precision mediump float;
            uniform vec4 uColor;
            uniform float uOutline;
            uniform float uUnlit;
            varying vec3 vNormal;
            void main() {
                if(uOutline>0.5) { gl_FragColor=vec4(0.075,0.12,0.13,uColor.a); return; }
                vec3 n=normalize(vNormal);
                float light=max(dot(n,normalize(vec3(-0.45,0.8,0.65))),0.0);
                float band=light>0.76?1.08:(light>0.35?0.91:0.70);
                band=mix(band,1.0,uUnlit);
                gl_FragColor=vec4(uColor.rgb*band,uColor.a);
            }
        """
    }
}
