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
    val avatarScale: Float = 2f, val stageMode: Boolean = false,
    val wingPhase: Float? = null,
    val leftBlink: Float? = null, val rightBlink: Float? = null,
    val smile: Float = 0f, val concern: Float = 0f,
    val antennaLift: Float = 0f, val antennaLag: Float = 0f,
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

    private val sphere = Fly3DGeometry.sphere(28, 18)
    private val edgePass = Fly3DEdgePass()
    private val smallSphere = Fly3DGeometry.sphere(12, 8)
    private val cube = Fly3DGeometry.cube()
    private val wing = Fly3DGeometry.wing()
    private val ringMesh = Fly3DGeometry.ring()
    private val shadeMesh = Fly3DGeometry.shade()
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
    private var viewportWidth = 1
    private var viewportHeight = 1
    private var materialHandle = 0
    private var characterHandle = 0
    private var environmentHandle = 0
    private var shadowHandle = 0
    private var shadowPlaneHandle = 0
    private val shadowData = FloatArray(16)
    private val shadowPlanes = FloatArray(4)
    @Volatile var lastDrawCalls:Int=0
        private set
    val lastRenderPixels:Int get() = edgePass.renderPixels
    @Volatile var lastTriangles:Int=0
        private set
    private var drawCalls=0
    private var triangles=0
    private var drawingCharacter = false
    private var cartoonScenery = false
    private var nightScene = false
    private var sceneTime = 0f

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
            materialHandle = GLES20.glGetUniformLocation(program,"uMaterial")
            characterHandle = GLES20.glGetUniformLocation(program,"uCharacter")
            environmentHandle = GLES20.glGetUniformLocation(program,"uEnvironment")
            shadowHandle = GLES20.glGetUniformLocation(program,"uShadows[0]")
            shadowPlaneHandle = GLES20.glGetUniformLocation(program,"uShadowPlanes[0]")
            edgePass.contextCreated()
            sphere.upload(); smallSphere.upload(); cube.upload(); wing.upload(); ringMesh.upload(); shadeMesh.upload()
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            GLES20.glDepthFunc(GLES20.GL_LEQUAL)
            GLES20.glEnable(GLES20.GL_CULL_FACE)
            GLES20.glFrontFace(GLES20.GL_CCW)
            GLES20.glBlendFuncSeparate(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA, GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            Matrix.setLookAtM(view, 0, 0f, 2.5f, 8f, 0f, 1.5f, 0f, 0f, 1f, 0f)
            ready = true; lastError = null
        } catch (error: RuntimeException) {
            lastError = "3D renderer initialization failed: ${error.message}"
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth=max(1,width); viewportHeight=max(1,height)
        edgePass.resize(viewportWidth,viewportHeight)
        configureCamera(false)
    }

    private fun configureCamera(stage:Boolean) {
        val aspect=viewportWidth.toFloat()/viewportHeight
        val halfWidth=if(stage) max(1.6f,1.6f*aspect) else max(3.25f,3.0f*aspect)
        val near=.1f
        Matrix.frustumM(projection,0,-halfWidth*near/8f,halfWidth*near/8f,
            -halfWidth/aspect*near/8f,halfWidth/aspect*near/8f,near,40f)
        if(stage) Matrix.setLookAtM(view,0,0f,0f,8f,0f,0f,0f,0f,1f,0f)
        else Matrix.setLookAtM(view,0,0f,2.5f,8f,0f,1.5f,0f,0f,1f,0f)
        Matrix.multiplyMM(vp,0,projection,0,view,0)
    }

    override fun onDrawFrame(gl: GL10?) {
        val incoming = frame
        val f = if(incoming.active) incoming.also {lastActiveFrame=it} else {
            if(incoming.avatarScale!=lastActiveFrame.avatarScale || incoming.stageMode!=lastActiveFrame.stageMode ||
                incoming.backgroundId!=lastActiveFrame.backgroundId) {
                lastActiveFrame=lastActiveFrame.copy(avatarScale=incoming.avatarScale,stageMode=incoming.stageMode,
                    backgroundId=incoming.backgroundId)
            }
            lastActiveFrame
        }
        // One coherent snapshot for opaque geometry, transparent wings and articulated hand.
        if (!ready) return
        edgePass.begin()
        if(f.stageMode) GLES20.glClearColor(0f,0f,0f,0f) else GLES20.glClearColor(.88f,.90f,.84f,1f)
        GLES20.glDepthMask(true)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        configureCamera(f.stageMode)
        GLES20.glUseProgram(program)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDepthMask(true)
        Matrix.setIdentityM(model, 0); stackAt = 0; drawCalls=0; triangles=0
        nightScene=!f.stageMode && f.backgroundId in NIGHT_BACKGROUNDS
        sceneTime=f.timeSeconds.safe()
        GLES20.glUniform2f(environmentHandle,if(nightScene)1f else 0f,sceneTime)
        prepareShadows(f)
        drawingCharacter=false
        cartoonScenery=!f.stageMode && f.backgroundId in CARTOON_ROOM_BACKGROUNDS
        if(!f.stageMode) room(f)
        push()
        val requestedScale=f.avatarScale.safe(2f).coerceIn(.5f,3f)
        val unit:Float
        if(f.stageMode) {
            val aspect=viewportWidth.toFloat()/viewportHeight
            val visibleWidth=2f*max(1.6f,1.6f*aspect)
            val visibleHeight=visibleWidth/aspect
            // Side/back wing bounds and a small orbit reserve remain inside the transparent layer.
            val safeUnit=minOf(visibleWidth*.86f/2.7f,visibleHeight*.80f/2.7f)
            // Fit the nominal size in both axes BEFORE applying the user's multiplier.
            // Otherwise landscape's height cap can swallow even the 50% setting.
            val baseUnit=minOf(visibleWidth*.65f/2.38f,visibleHeight*.65f/2.7f)
            unit=minOf(baseUnit*requestedScale,safeUnit)
            Matrix.translateM(model,0,sin(sceneTime*.23f)*unit*.06f,
                -unit*.22f+sin(sceneTime*.41f)*unit*.035f,0f)
            Matrix.rotateM(model,0,f.yaw.safe()*DEGREES+sin(sceneTime*.19f)*8f,0f,1f,0f)
            Matrix.rotateM(model,0,f.pitch.safe().coerceIn(-.25f,.25f)*DEGREES,1f,0f,0f)
            Matrix.rotateM(model,0,f.roll.safe().coerceIn(-.18f,.18f)*DEGREES,0f,0f,1f)
        } else {
            val gameScale=requestedScale*1.22f
            unit=.50f*gameScale
            // Root height compensation preserves grounded feet; world-space grip stays authoritative.
            val groundCompensation=.545f*(gameScale-1f)*(1f-f.flight.safe().coerceIn(0f,1f))
            Matrix.translateM(model,0,f.x.safe().coerceIn(-2.5f,2.5f),
                f.y.safe(2.1f).coerceIn(.55f,4f)+groundCompensation,f.z.safe().coerceIn(-1.5f,1.5f))
            Matrix.rotateM(model,0,f.yaw.safe()*DEGREES,0f,1f,0f)
            Matrix.rotateM(model,0,f.pitch.safe()*DEGREES,1f,0f,0f)
            Matrix.rotateM(model,0,f.roll.safe()*DEGREES,0f,0f,1f)
        }
        Matrix.scaleM(model,0,unit,unit,unit)
        drawingCharacter=true
        hasHandTarget = !f.stageMode && f.handX?.isFinite() == true && f.handY?.isFinite() == true && f.handZ?.isFinite() == true
        if (hasHandTarget && Matrix.invertM(rootInverse,0,model,0)) {
            worldHand[0]=f.handX!!; worldHand[1]=f.handY!!; worldHand[2]=f.handZ!!; worldHand[3]=1f
            Matrix.multiplyMV(localHand,0,rootInverse,0,worldHand,0)
        } else hasHandTarget=false
        fly(f)
        pop()
        edgePass.present()
        lastDrawCalls=drawCalls+if(edgePass.available)1 else 0
        lastTriangles=triangles+if(edgePass.available)2 else 0
        renderedFrames++
    }

    private fun prepareShadows(f:Fly3DFrame) {
        shadowData.fill(0f); shadowPlanes.fill(-10f)
        if(!f.stageMode) {
            val ground=if(abs(f.x)<2.1f && abs(f.z)<1f && f.y>1.2f).8f else 0f
            val height=max(.0f,f.y.safe(2f)-.545f-ground)
            shadowData[0]=f.x.safe(); shadowData[1]=f.z.safe(); shadowData[2]=.35f+height*.12f
            shadowData[3]=.27f/(1f+height*.9f); shadowPlanes[0]=ground
            val props=f.props
            if(props!=null) {
                for(i in 0 until minOf(3,props.size)) {
                    val p=props[i]; val at=(i+1)*4
                    val plane=if(abs(p.x)<2.1f&&abs(p.z)<1f&&p.y>.7f).8f else 0f
                    shadowData[at]=p.x; shadowData[at+1]=p.z; shadowData[at+2]=if(p.kind=="book").30f else .17f
                    shadowData[at+3]=.25f/(1f+max(0f,p.y-plane)*2f); shadowPlanes[i+1]=plane
                }
            } else {
                shadowData[4]=-1f;shadowData[5]=.5f;shadowData[6]=.30f;shadowData[7]=.22f;shadowPlanes[1]=.8f
                shadowData[8]=1f;shadowData[9]=.5f;shadowData[10]=.16f;shadowData[11]=.22f;shadowPlanes[2]=.8f
                shadowData[12]=1.6f;shadowData[13]=0f;shadowData[14]=.17f;shadowData[15]=.23f;shadowPlanes[3]=.8f
            }
        }
        GLES20.glUniform4fv(shadowHandle,4,shadowData,0)
        GLES20.glUniform1fv(shadowPlaneHandle,4,shadowPlanes,0)
    }

    private fun room(f: Fly3DFrame) {
        if (cartoonScenery) {
            cartoonRoom(f)
            interactionTable(f, if (f.backgroundId == "cartoon_diner") 0xDA8A77 else 0xE4B386)
            return
        }
        val night = f.backgroundId in NIGHT_BACKGROUNDS
        val garden = f.backgroundId in GARDEN_BACKGROUNDS
        val outdoor=f.backgroundId in OUTDOOR_BACKGROUNDS
        val wallColor = when(f.backgroundId) {
            "library" -> 0x667F7C; "workshop" -> 0xB4A58D; "cafe" -> 0xDEB69A
            "pond" -> 0x91BDBA; "beach" -> 0xB7DDD7; "orchard" -> 0xB8CD9E
            else -> if (night) 0x37465D else if (garden) 0xABC7AC else 0xD9C9AF
        }
        val floorColor = if (f.backgroundId == "beach") 0xD9C9A0 else if (night) 0x606870 else if (garden) 0x749770 else 0xBCA181
        box(0f,-.10f,0f, 18f,.10f,18f, floorColor, outline = false)
        box(0f,6f,-2.6f,18f,6f,.10f,if(outdoor)0xBCDCD5 else wallColor,outline=false)
        box(-4.3f,6f,0f,.10f,6f,18f,wallColor,outline=false)
        box(4.3f,6f,0f,.10f,6f,18f,wallColor,outline=false)
        if(!outdoor) {
            box(0f,.58f,-2.46f,4.3f,.58f,.045f,if(night)0x3D5368 else 0x577975,outline=false)
            box(0f,1.20f,-2.39f,4.3f,.045f,.065f,if(night)0x8695A0 else 0xAEC2AF,outline=false)
        }
        // Floorboards and wall paneling establish parallax and depth cues without textures.
        for (i in -3..3) {
            box(i * 1.1f,.004f,0f, .007f,.005f,18f, if (night) 0x505A64 else 0x939884, outline = false)
            if(!outdoor) box(i*1.1f,.60f,-2.395f,.016f,.48f,.018f,if(night)0x728498 else 0x8BAA9A,outline=false)
        }
        if(!outdoor) {
        // Recessed window, sky, mullions and a slow passing cloud live at different depths.
        box(-1.65f,3.18f,-2.42f,1.08f,1.13f,.07f,0x496D71)
        box(-1.65f,3.18f,-2.32f,.975f,1.025f,.045f,if(night)0x203B62 else 0xA9D5E4,outline=false)
        ellipsoid(-2.05f,2.39f,-2.24f,.54f,.24f,.025f,if(night)0x406675 else 0x739D92,outline=false)
        ellipsoid(-1.27f,2.35f,-2.23f,.62f,.20f,.025f,if(night)0x375869 else 0x95B49B,outline=false)
        val cloudX=-1.65f+sin(sceneTime*.05f)*.20f
        ellipsoid(cloudX,3.58f,-2.23f,.35f,.13f,.025f,if(night)0xD6D9B2 else 0xFAEACC,outline=false)
        ellipsoid(cloudX-.17f,3.56f,-2.22f,.18f,.10f,.025f,if(night)0xD6D9B2 else 0xFAEACC,outline=false)
        ellipsoid(cloudX+.15f,3.53f,-2.22f,.20f,.09f,.025f,if(night)0xD6D9B2 else 0xFAEACC,outline=false)
        box(-1.65f,3.18f,-2.15f,.035f,1.03f,.045f,0xD5DCC4,outline=false)
        box(-1.65f,3.18f,-2.14f,.97f,.035f,.045f,0xD5DCC4,outline=false)
        box(-1.65f,2.03f,-2.09f,1.17f,.065f,.21f,0xCBD3BA)
        // Small greenery at the light source establishes scale and a natural focal point.
        ellipsoid(-2.36f,2.20f,-1.98f,.11f,.15f,.10f,0xB4785B)
        ellipsoid(-2.36f,2.44f,-1.98f,.075f,.20f,.06f,0x547C65,outline=false)
        } else {
            ellipsoid(-2.6f,3.7f,-2.35f,.32f,.32f,.06f,0xF2DEAE,outline=false)
            for(i in 0..3) ellipsoid(-2.4f+i*1.45f+sin(sceneTime*.035f+i)*.16f,
                3.3f+(i%2)*.42f,-2.33f,.55f,.14f,.08f,0xEAF0D8,outline=false)
        }
        roomDetails(f)
        roomFinishing(f)
        interactionTable(f, if (garden) 0x9A8862 else 0xA88662)
        // A succulent offers a recognizable volumetric silhouette and gentle background movement.
        ellipsoid(-2.65f,.24f,-1.55f,.24f,.24f,.24f,0xB17A5B)
        for (i in 0..4) {
            push(); Matrix.translateM(model,0,-2.65f,.46f,-1.55f)
            Matrix.rotateM(model,0,i*72f,0f,1f,0f)
            Matrix.rotateM(model,0,25f + sin(f.timeSeconds.safe()*.65f+i)*3f,0f,0f,1f)
            ellipsoid(0f,.22f,0f,.065f,.28f,.11f,0x567D59); pop()
        }
    }

    /** Both room styles share one authoritative contact plane and simulation-owned inventory. */
    private fun interactionTable(f: Fly3DFrame, color: Int) {
        // Main interaction table top at y=.80. Grounded root≈1.35 (feet−1.10*.50).
        box(0f,.72f,0f, 2.10f,.08f,1.0f, color)
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
                when(prop.kind) { "book" -> book(); "glass" -> glass(); "fruit" -> fruit(); else -> ball() }
                pop()
            }
        } else {
            if (f.holding != "book") { push(); Matrix.translateM(model,0,-1f,.8f,.5f); book(); pop() }
            if (f.holding != "glass") { push(); Matrix.translateM(model,0,1f,.8f,.5f); glass(); pop() }
            push(); Matrix.translateM(model,0,1.6f,.8f,0f); ball(); pop()
        }
    }

    /**
     * Full-fly habitats are opaque 3D rooms, so a new bitmap alone cannot change them.
     * These native counterparts keep the center clear and put two comic motifs at the
     * rear edges. All motion uses the existing paused frame snapshot; no extra GL assets.
     */
    private fun cartoonRoom(f: Fly3DFrame) {
        val wall = when (f.backgroundId) {
            "cartoon_snack_lab" -> 0xB6DDD1
            "cartoon_laundromat" -> 0xD2C5E2
            else -> 0xF3E0A7
        }
        val floor = when (f.backgroundId) {
            "cartoon_snack_lab" -> 0xE9BC97
            "cartoon_laundromat" -> 0xA8CABE
            else -> 0xD69A88
        }
        box(0f,-.10f,0f,18f,.10f,18f,floor,outline=false)
        box(0f,6f,-2.6f,18f,6f,.10f,wall,outline=false)
        box(-4.3f,6f,0f,.10f,6f,18f,wall,outline=false)
        box(4.3f,6f,0f,.10f,6f,18f,wall,outline=false)
        box(0f,.12f,-2.45f,4.3f,.12f,.05f,0x729891)
        // One broad wall stripe gives perspective without the older panels/window clutter.
        box(0f,1.24f,-2.46f,4.3f,.035f,.045f,0xF1E7C9,outline=false)
        when (f.backgroundId) {
            "cartoon_snack_lab" -> snackLabScenery()
            "cartoon_laundromat" -> laundryScenery()
            else -> dinerScenery()
        }
    }

    private fun snackLabScenery() {
        // The glass is a pale rear shell and open ink silhouette, so it never occludes the banana.
        box(-2.68f,.65f,-1.93f,.64f,.65f,.40f,0xE6AC7C)
        ellipsoid(-2.68f,1.34f,-1.93f,.66f,.065f,.43f,0x729891)
        ellipsoid(-2.68f,2.02f,-2.13f,.56f,.66f,.12f,0xD7F0DC)
        ellipsoid(-2.68f,2.72f,-2.02f,.085f,.075f,.085f,0x8BB9AC)
        val lean = sin(sceneTime * .45f) * 3f
        push(); Matrix.translateM(model,0,-2.68f,1.97f,-1.83f)
        Matrix.rotateM(model,0,lean,0f,0f,1f)
        // A six-piece curved fruit silhouette; small meshes keep this rear detail inexpensive.
        for (i in 0..5) {
            val a=i/6f; val b=(i+1)/6f
            segment(-.32f+a*.64f,.05f-sin(a*3.141593f)*.31f,0f,
                -.32f+b*.64f,.05f-sin(b*3.141593f)*.31f,0f,.115f,0xF0CB5C,outline=false)
        }
        ellipsoid(-.31f,.06f,0f,.060f,.050f,.065f,0x6F6546,outline=false)
        ellipsoid(.31f,.06f,0f,.060f,.050f,.065f,0x6F6546,outline=false)
        ellipsoid(-.07f,-.18f,.107f,.026f,.034f,.022f,INK,outline=false)
        ellipsoid(.07f,-.18f,.107f,.026f,.034f,.022f,INK,outline=false)
        box(0f,.23f,0f,.23f,.045f,.13f,0xDEAA48)
        for(i in -1..1) {
            segment(i*.17f,.24f,0f,i*.19f,.39f+(if(i==0).05f else 0f),0f,.042f,0xDEAA48)
        }
        pop()
        // Specimen jar gleam is fixed to the glass, separate from the gently tilting fruit.
        segment(-3.08f,1.70f,-1.94f,-3.04f,2.35f,-1.98f,.016f,0xFAF4D9,outline=false)
        // A compact absurdly oversized snack button is the only other piece of equipment.
        box(2.64f,1.11f,-1.93f,.52f,1.11f,.40f,0x78ADA6)
        box(2.64f,1.53f,-1.49f,.34f,.38f,.025f,0xD7F0DC)
        ellipsoid(2.64f,1.53f,-1.425f,.19f,.19f,.055f,0xD98369)
        box(2.64f,.52f,-1.49f,.26f,.065f,.06f,INK,outline=false)
    }

    private fun laundryScenery() {
        box(-2.60f,.89f,-1.92f,.61f,.89f,.43f,0x93C4B6)
        box(-2.60f,1.55f,-1.455f,.51f,.115f,.025f,0xEFE1BB)
        ellipsoid(-2.89f,1.55f,-1.405f,.061f,.061f,.032f,0xC58473)
        box(-2.40f,1.55f,-1.407f,.13f,.025f,.016f,INK,outline=false)
        ellipsoid(-2.60f,.83f,-1.435f,.47f,.47f,.050f,0xE5D8B7)
        ellipsoid(-2.60f,.83f,-1.37f,.37f,.37f,.035f,0x5D8999)
        // One lost sock slowly tumbles behind the portal rim using the host's active time.
        push(); Matrix.translateM(model,0,-2.60f,.83f,-1.32f)
        Matrix.rotateM(model,0,sceneTime*16f,0f,0f,1f)
        box(-.05f,.05f,0f,.063f,.13f,.018f,0xF1D49C,outline=false)
        ellipsoid(.01f,-.07f,.0f,.12f,.060f,.022f,0xF1D49C,outline=false)
        pop()
        // Opposite wall: the recovered sock receives a trophy instead of a second washer.
        box(2.63f,1.75f,-2.01f,.48f,.055f,.30f,0xAC92B9)
        box(2.63f,1.86f,-1.93f,.28f,.070f,.19f,0xB88A51)
        segment(2.63f,1.92f,-1.93f,2.63f,2.13f,-1.93f,.048f,0xDDB359)
        push(); Matrix.translateM(model,0,2.62f,2.47f,-1.93f)
        Matrix.rotateM(model,0,-14f,0f,0f,1f)
        box(-.055f,.045f,0f,.14f,.24f,.105f,0xF0D285)
        ellipsoid(.07f,-.20f,.01f,.26f,.12f,.14f,0xF0D285)
        box(-.055f,.23f,.112f,.14f,.030f,.012f,0x729891,outline=false)
        pop()
    }

    private fun dinerScenery() {
        // The flying saucer is interested only in this stack of pancakes at the room edge.
        box(-2.62f,1.22f,-1.92f,.60f,.06f,.43f,0xB86F63)
        box(-2.62f,.60f,-1.92f,.11f,.60f,.11f,0xD6AA86)
        ellipsoid(-2.62f,1.33f,-1.92f,.41f,.046f,.32f,0xEDE4C5)
        for(i in 0..2) ellipsoid(-2.62f,1.43f+i*.105f,-1.92f,.31f,.052f,.25f,0xDCAF69)
        box(-2.60f,1.715f,-1.90f,.08f,.040f,.07f,0xF4D977)
        val hover = sin(sceneTime * .8f) * .075f
        ellipsoid(-2.62f,2.55f+hover,-1.92f,.57f,.13f,.44f,0x95BDB1)
        ellipsoid(-2.62f,2.72f+hover,-1.92f,.29f,.18f,.25f,0xD5E7D8)
        ellipsoid(-2.62f,2.435f+hover,-1.92f,.28f,.035f,.24f,0xF3D7A4,outline=false)
        for(i in -1..1) ellipsoid(-2.62f+i*.24f,2.53f+hover,-1.49f,.041f,.041f,.025f,0xE9B46B,outline=false)
        // A boot cactus fills the opposite corner without introducing more tabletop inventory.
        box(2.79f,.065f,-1.80f,.44f,.065f,.29f,0x805B4D)
        ellipsoid(2.79f,.28f,-1.80f,.44f,.16f,.29f,0xB8725E)
        box(2.58f,.54f,-1.93f,.22f,.29f,.235f,0xB8725E)
        ellipsoid(2.60f,.845f,-1.93f,.245f,.050f,.25f,0xE1AD79)
        segment(2.60f,.85f,-1.93f,2.60f,1.69f,-1.93f,.14f,0x7BA36A)
        segment(2.58f,1.15f,-1.93f,2.25f,1.22f,-1.93f,.085f,0x7BA36A)
        segment(2.25f,1.22f,-1.93f,2.25f,1.48f,-1.93f,.080f,0x7BA36A)
        segment(2.63f,1.38f,-1.93f,2.88f,1.40f,-1.93f,.075f,0x7BA36A)
        segment(2.88f,1.40f,-1.93f,2.88f,1.62f,-1.93f,.070f,0x7BA36A)
        ellipsoid(2.60f,1.83f,-1.93f,.12f,.095f,.10f,0xD98691)
        box(2.61f,.55f,-1.681f,.13f,.018f,.008f,INK,outline=false)
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
                ellipsoid(.25f,3.82f,-2.20f,.25f,.28f,.04f,0xE2D3AC)
                segment(.25f,3.82f,-2.14f,.25f+sin(t*.12f)*.17f,3.82f+cos(t*.12f)*.17f,-2.14f,.013f,0x365656)
            }
        }
    }

    /** Layered room-specific furnishings; every loop has a small fixed upper bound. */
    private fun roomFinishing(f:Fly3DFrame) {
        val t=sceneTime
        // Beveled-looking tiered trim, table apron and warm/cool floor contact zones.
        box(0f,.09f,-2.42f,4.25f,.09f,.04f,if(nightScene)0x626B79 else 0xA7B49E,outline=false)
        box(0f,.63f,.88f,1.94f,.055f,.036f,0x735F49,outline=false)
        for(side in -1..1 step 2)box(side*1.94f,.63f,0f,.036f,.055f,.86f,0x735F49,outline=false)
        when(f.backgroundId) {
            "library" -> {
                box(0f,.015f,.15f,2.72f,.012f,1.65f,0x7C9290,outline=false)
                for(i in 0..4)box(-2.45f+i*.10f,.03f,.15f,.018f,.004f,1.48f,0xC2BB98,outline=false)
                segment(2.95f,0f,-.9f,2.95f,2.30f,-.9f,.035f,0xA78E60)
                ellipsoid(2.95f,2.34f,-.9f,.36f,.16f,.29f,0xCDB989)
                ellipsoid(2.95f,2.23f,-.9f,.24f,.035f,.20f,0xF3D7A4,outline=false)
                for(i in 0..4)box(.90f+i*.34f,2.35f,-2.055f,.055f,.009f,.003f,0xD5C194,outline=false)
            }
            "greenhouse" -> {
                for(i in 0..2){
                    box(-2.85f,.23f,-1.35f+i*.85f,.35f,.23f,.31f,0xAC8669)
                    for(j in 0..2) {
                        val x=-2.97f+j*.13f
                        segment(x,.43f,-1.35f+i*.85f,x+sin(t*.4f+i+j)*.04f,.9f,-1.35f+i*.85f,.012f,0x527950,outline=false)
                        ellipsoid(x,.74f,-1.35f+i*.85f,.09f,.18f,.07f,0x77A36B,outline=false)
                    }
                }
                for(i in 0..3) box(2.80f,.018f,-1.3f+i*.72f,.28f,.018f,.27f,0xA7B9A1,outline=false)
            }
            "workshop" -> {
                for(i in 0..2) {
                    box(-2.9f,.22f,-1.5f+i*.62f,.36f,.22f,.27f,0xA38366)
                    box(-2.9f,.40f,-1.5f+i*.62f,.32f,.016f,.245f,0xCAB28E,outline=false)
                }
                for(i in 0..4)for(j in 0..2)ellipsoid(1.05f+i*.42f,2.28f+j*.36f,-2.245f,.018f,.018f,.008f,0x795F4B,outline=false)
                box(2.75f,1.04f,-1.8f,.46f,.04f,.28f,0x806A55)
                box(2.75f,1.18f,-1.8f,.20f,.12f,.18f,0x637C79)
            }
            "pond" -> {
                ellipsoid(-2.85f,.016f,-.60f,.85f,.018f,1.30f,0x6FA7AA,outline=false)
                for(i in 0..4) {
                    val a=i*1.3f
                    ellipsoid(-2.85f+cos(a)*.77f,.07f,-.60f+sin(a)*1.17f,.18f,.10f,.23f,0x8CA198,outline=false)
                }
                val pulse=(t*.12f)%1f
                ring(-2.75f,.047f,-.55f,.12f+pulse*.35f,.12f+pulse*.35f,0xB4D8CB)
                ellipsoid(-3.0f,.07f,-.24f,.20f,.035f,.16f,0x91BC8B,outline=false)
                for(i in 0..2)segment(-3.4f,.10f,-1.3f+i*.18f,-3.4f+sin(t*.5f+i)*.035f,.64f,-1.3f+i*.18f,.014f,0x567E68,outline=false)
            }
            "orchard" -> {
                box(-2.85f,.20f,-.7f,.4f,.20f,.34f,0xA38366)
                for(i in 0..3)ellipsoid(-3.05f+(i%2)*.3f,.43f+(i/2)*.12f,-.77f+(i/2)*.19f,.13f,.14f,.13f,0xD38361)
                for(i in 0..4)ellipsoid(2.95f+sin(i*1.8f)*.45f,.06f,-.6f+cos(i*1.8f)*.6f,.18f,.038f,.10f,0x87A768,outline=false)
                segment(-2.8f,.43f,-1.7f,-2.8f,1.75f,-1.7f,.05f,0x8C7859)
                ellipsoid(-2.8f,1.95f,-1.7f,.57f,.65f,.38f,0x87A768)
            }
            "cafe" -> {
                for(side in -1..1 step 2){
                    box(side*2.75f,.49f,.5f,.32f,.055f,.31f,0x806A55)
                    box(side*2.75f,.96f,.76f,.31f,.30f,.04f,0xB48D78)
                    box(side*2.96f,.23f,.66f,.035f,.23f,.035f,0x735F49)
                    box(side*2.54f,.23f,.32f,.035f,.23f,.035f,0x735F49)
                }
                segment(-.3f,3.8f,-1.8f,3.3f,3.8f,-1.8f,.012f,0x667870,outline=false)
                for(i in 0..5)ellipsoid(-.2f+i*.65f,3.66f+sin(t*.32f+i)*.012f,-1.8f,.06f,.075f,.06f,0xF0CEA0,outline=false)
            }
            "observatory" -> {
                ring(0f,.015f,0f,2.90f,2.15f,0x9298A3)
                for(i in 0..7){
                    val a=i*.785f
                    box(cos(a)*2.85f,.02f,sin(a)*2.10f,.035f,.015f,.06f,0xC1BEA0,outline=false)
                }
                for(i in 0..2)segment(2.65f,1.1f,-1.7f,2.65f+sin(i*2.094f)*.35f,.03f,-1.7f+cos(i*2.094f)*.35f,.025f,0x74828A)
            }
            "beach" -> {
                for(i in 0..3) {
                    val x=-2.8f+(i%2)*.30f;val z=-1f+(i/2)*.80f
                    ellipsoid(x,.06f,z,.11f,.06f,.08f,0xEBD8BA,outline=false)
                    ring(x,.012f,z,.17f,.12f,0xBEAA83)
                }
                box(2.8f,.025f,.65f,.42f,.022f,.75f,0xA4C1B8,outline=false)
                for(i in 0..3)box(2.8f,.05f,.08f+i*.34f,.41f,.004f,.035f,0xECE2C5,outline=false)
                ellipsoid(-3.2f,.22f,1f,.21f,.21f,.21f,0xC48F72)
            }
            "bedroom" -> {
                box(0f,.015f,.2f,2.75f,.013f,1.6f,0x848E9F,outline=false)
                box(2.05f,.31f,-1.9f,.24f,.31f,.29f,0x8E8078)
                segment(2.05f,.62f,-1.9f,2.05f,.98f,-1.9f,.018f,0xB3A891)
                ellipsoid(2.05f,1.03f,-1.9f,.20f,.13f,.17f,0xDAC29E)
                for(i in 0..3)box(2.85f,.721f,-1.95f+i*.25f,.63f,.007f,.025f,0xCBD0B7,outline=false)
                for(i in 0..2)box(-2.68f+i*.11f,3.1f,-2.13f,.045f,.94f,.065f,0x909DA9,outline=false)
            }
            else -> {
                // Deep teal joinery, cream stone worktop and warm brass handles frame the pet table.
                box(2.08f,.55f,-1.87f,1.10f,.55f,.40f,0x365F60)
                box(2.08f,1.14f,-1.87f,1.16f,.055f,.45f,0xDFD4B8)
                for(i in 0..2){
                    val x=1.34f+i*.74f
                    box(x,.59f,-1.445f,.32f,.40f,.025f,0x547E78)
                    box(x,.61f,-1.412f,.245f,.30f,.008f,0x476F6A,outline=false)
                    segment(x-.11f,.89f,-1.387f,x+.11f,.89f,-1.387f,.014f,0xC4A36B,outline=false)
                }
                box(1.75f,2.04f,-2.12f,1.45f,.045f,.24f,0x967C59)
                for(i in 0..2) {
                    val x=.70f+i*.30f
                    ellipsoid(x,2.25f,-2.09f,.10f,.17f,.10f,if(i==1)0xAFB99D else 0xE0D4B7)
                    ellipsoid(x,2.43f,-2.09f,.104f,.025f,.104f,0x9E8058,outline=false)
                }
                ellipsoid(2.66f,1.32f,-1.88f,.19f,.13f,.15f,0xC69761)
                ring(2.66f,1.45f,-1.88f,.14f,.14f,0xE0C895)
                ellipsoid(1.30f,1.28f,-1.88f,.15f,.11f,.15f,0xBE8261)
                for(i in 0..2)ellipsoid(1.23f+i*.065f,1.49f+sin(t*.4f+i)*.013f,-1.88f,.065f,.18f,.06f,0x4F7861,outline=false)
                // A pendant gives the tall viewport an intentional upper focal point.
                segment(.10f,7f,-.10f,.10f,3.95f,-.10f,.013f,0x364F50,outline=false)
                push();Matrix.translateM(model,0,.10f,3.79f,-.10f);Matrix.scaleM(model,0,.34f,.18f,.34f)
                draw(shadeMesh,0x416B68,outline=false);pop()
                ring(.10f,3.61f,-.10f,.34f,.34f,0xC7AC79)
                ellipsoid(.10f,3.64f,-.10f,.18f,.045f,.18f,0xF3D7A4,outline=false)
            }
        }
    }

    private fun ring(x:Float,y:Float,z:Float,rx:Float,rz:Float,color:Int) {
        push(); Matrix.translateM(model,0,x,y,z); Matrix.scaleM(model,0,rx,.10f,rz)
        draw(ringMesh,color,outline=false); pop()
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
            if (held && f.props == null && !f.stageMode) {
                push(); Matrix.translateM(model,0,handX+.03f,handY-.08f,handZ)
                Matrix.scaleM(model,0,.70f,.70f,.70f)
                if (f.holding == "book") { Matrix.rotateM(model,0,-74f,1f,0f,0f); book() } else glass()
                pop()
            }
            hand(handX,handY,handZ,.108f)
        }
        // Red compound lenses form the face's silhouette; pupils remain small, friendly and dimensional.
        for (side in -1..1 step 2) {
            val closure = ((if (side < 0) f.leftBlink else f.rightBlink) ?: f.blink).safe().coerceIn(0f,1f)
            val eyeHeight = .31f*(1f-closure)+.025f
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
        val expression = (f.smile.safe() - f.concern.safe()).coerceIn(-1f, 1f)
        if (speech > .08f) {
            ellipsoid(0f,.185f,.566f,.12f,.026f+speech*.086f,.021f,INK,outline=false)
            ellipsoid(0f,.15f-speech*.015f,.586f,.063f,.017f+speech*.018f,.007f,0xD58E86,outline=false)
        } else {
            segment(-.135f,.203f+expression*.037f,.554f,-.066f,.166f-expression*.012f,.573f,.014f,INK,outline=false)
            segment(-.066f,.166f-expression*.012f,.573f,.066f,.166f-expression*.012f,.573f,.014f,INK,outline=false)
            segment(.066f,.166f-expression*.012f,.573f,.135f,.203f+expression*.037f,.554f,.014f,INK,outline=false)
        }
        // Short arista antennae, with three fine bristles each; response shares gaze/time snapshot.
        for (side in -1..1 step 2) {
            val sx = side.toFloat()
            val lift = f.antennaLift.safe().coerceIn(-1f,1f)*.08f
            val bend = sin(t*1.4f+side)*.035f + f.gazeX.safe().coerceIn(-1f,1f)*.025f +
                f.antennaLag.safe().coerceIn(-1f,1f)*.05f
            segment(sx*.19f,.89f,.12f,sx*.23f+bend,1.10f+lift,.09f,.026f,SHELL)
            ellipsoid(sx*.23f+bend,1.10f+lift,.09f,.057f,.080f,.055f,SHELL_LIGHT)
            for (i in 0..2) segment(sx*.24f+bend,1.10f+lift+i*.025f,.09f,
                sx*(.34f+i*.018f)+bend,1.13f+lift+i*.046f,.10f,.009f,SHELL,outline=false)
        }
        // Transparent geometry is last, after all opaque surfaces have written depth.
        // Their roots are behind the thorax, so the head correctly occludes them.
        val backFirst = if (sin(f.yaw.safe()) > 0f) -1 else 1
        drawWing(backFirst,flight,f.wingPhase?.takeIf { it.isFinite() } ?: t*24f)
        drawWing(-backFirst,flight,f.wingPhase?.takeIf { it.isFinite() } ?: t*24f)
    }

    private fun drawWing(side: Int, flight: Float, phase: Float) {
        push()
        Matrix.translateM(model,0,side*.24f,.32f,-.25f)
        Matrix.rotateM(model,0,side*(116f+sin(phase)*(8f+flight*23f)),0f,1f,0f)
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

    private fun fruit() {
        ellipsoid(0f,.105f,0f,.125f,.105f,.115f,0xE9A247)
        ellipsoid(-.035f,.15f,.085f,.035f,.035f,.013f,0xFFE0A1,outline=false)
        segment(0f,.19f,0f,.02f,.24f,0f,.012f,0x665237,outline=false)
        ellipsoid(.055f,.222f,0f,.05f,.018f,.025f,0x78A354,outline=false)
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
        drawCalls+=if(outline)2 else 1
        triangles+=mesh.vertexCount/3*(if(outline)2 else 1)
        Matrix.multiplyMM(mvp,0,vp,0,model,0)
        if (!Matrix.invertM(inverse,0,model,0)) return
        Matrix.transposeM(normal,0,inverse,0)
        GLES20.glUniformMatrix4fv(mvpHandle,1,false,mvp,0)
        GLES20.glUniformMatrix4fv(modelHandle,1,false,model,0)
        GLES20.glUniformMatrix4fv(viewProjectionHandle,1,false,vp,0)
        GLES20.glUniformMatrix4fv(normalMatrixHandle,1,false,normal,0)
        GLES20.glUniform4f(colorHandle,((color shr 16) and 255)/255f,((color shr 8) and 255)/255f,(color and 255)/255f,alpha)
        GLES20.glUniform1f(unlitHandle,if(color==INK)1f else if(color in EMISSIVE_COLORS).55f
            else if(cartoonScenery && !drawingCharacter).35f else 0f)
        GLES20.glUniform1f(characterHandle,if(drawingCharacter)1f else 0f)
        val material=when {
            alpha<1f -> 2f
            drawingCharacter -> if(color==RED)3f else 0f
            cartoonScenery -> 0f
            color in WOOD_COLORS -> 1f
            color in GLASS_COLORS -> 2f
            color in METAL_COLORS -> 3f
            color in LEAF_COLORS -> 4f
            else -> 0f
        }
        GLES20.glUniform1f(materialHandle,material)
        mesh.bind(positionHandle,normalHandle)
        if (outline) {
            // Black inverted hull. Extrusion is in world units, independent of ellipsoid scale.
            GLES20.glCullFace(GLES20.GL_FRONT)
            GLES20.glUniform1f(outlineHandle,1f)
            GLES20.glUniform1f(outlineWidthHandle,if(drawingCharacter).014f else if(cartoonScenery).013f else .008f)
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
        val EMISSIVE_COLORS=setOf(0xA9D5E4,0xFAEACC,0xF2DEAE,0xF0CEA0,0xF3D7A4)
        val WOOD_COLORS=setOf(0xBCA181,0xA88662,0x9A8862,0x735F49,0x806A55,0xB9B69A,0x8C7859,0xB5996E,0xA38366)
        val GLASS_COLORS=setOf(0xB9D7D4,0x72A9B1,0xE4EEDE,0x96C6BC,0x6FA7AA)
        val METAL_COLORS=setOf(0xC4A36B,0xC7AC79,0xC69761,0xB89169,0xB99363,0xBCC6B5,0xA8AE9F,0x637C79)
        val LEAF_COLORS=setOf(0x4F7861,0x547C65,0x77A36B,0x87A768,0x567D59,0x91BC8B,0x567E68)
        val OUTDOOR_BACKGROUNDS=setOf("pond","orchard","beach")
        val NIGHT_BACKGROUNDS=setOf("observatory","bedroom")
        val GARDEN_BACKGROUNDS=setOf("greenhouse","pond","orchard","beach")
        val CARTOON_ROOM_BACKGROUNDS=setOf("cartoon_snack_lab","cartoon_laundromat","cartoon_diner")
        const val VERTEX_SHADER="""
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            uniform mat4 uMvp;
            uniform mat4 uModel;
            uniform mat4 uViewProjection;
            uniform mat4 uNormalMatrix;
            uniform float uOutlineWidth;
            varying vec3 vNormal;
            varying vec3 vWorld;
            void main() {
                vWorld=(uModel*vec4(aPosition,1.0)).xyz;
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
            uniform float uMaterial;
            uniform float uCharacter;
            uniform vec2 uEnvironment;
            uniform vec4 uShadows[4];
            uniform float uShadowPlanes[4];
            varying vec3 vNormal;
            varying vec3 vWorld;
            float contact(vec4 s,float plane){
                vec2 d=(vWorld.xz-s.xy)/max(s.z,.01);
                float softness=1.0-smoothstep(.05,1.9,dot(d,d));
                return softness*s.w*(1.0-smoothstep(.015,.05,abs(vWorld.y-plane)));
            }
            void main() {
                if(uOutline>0.5) {gl_FragColor=vec4(.075,.12,.13,uColor.a);return;}
                vec3 n=normalize(vNormal);
                vec3 eye=normalize(vec3(0.,2.5,8.)-vWorld);
                vec3 lightDirection=normalize(vec3(-.45,.8,.65));
                float diffuse=max(dot(n,lightDirection),0.0);
                float steps=.61+smoothstep(.22,.39,diffuse)*.24+smoothstep(.65,.83,diffuse)*.19;
                float hemisphere=.08*(n.y*.5+.5);
                float smoothLight=mix(.48+.56*diffuse+hemisphere,.61+.40*diffuse,uCharacter);
                float illumination=mix(smoothLight,steps,.35*uCharacter);
                vec3 color=uColor.rgb;
                if(uMaterial>.5&&uMaterial<1.5){
                    // Broad quiet wood tone: previous subpixel grain crawled on phone surfaces.
                    float grain=sin(vWorld.x*8.+vWorld.z*.7);
                    color*=.99+.008*grain;
                }
                float glossy=step(1.5,uMaterial)*(1.-step(3.5,uMaterial));
                float spec=pow(max(dot(n,normalize(lightDirection+eye)),0.),28.)*glossy*.17;
                float rim=pow(1.-max(dot(n,eye),0.),3.)*mix(.04,.085,uCharacter);
                vec3 tint=mix(vec3(1.,.995,.98),vec3(.79,.88,1.05),uEnvironment.x*.45);
                color=color*illumination*tint+vec3(spec)+vec3(.68,.85,.85)*rim;
                // Broad warm window spill against cool cabinetry, with soft mullion shadows.
                vec2 pool=vec2(vWorld.x+1.15+vWorld.z*.25,vWorld.z-.25);
                float spill=(1.-smoothstep(.65,1.65,abs(pool.x)))*(1.-smoothstep(.45,2.7,abs(pool.y)));
                float pane=1.-.35*(1.-smoothstep(.025,.08,abs(pool.x)))
                    -.20*(1.-smoothstep(.025,.08,abs(pool.y+.35)));
                float up=smoothstep(.65,.95,n.y);
                color+=vec3(.17,.12,.05)*spill*pane*up*(1.-uEnvironment.x*.75)*(1.-uCharacter);
                float glow=1.-smoothstep(.0,3.2,length((vWorld-vec3(-1.7,3.1,-2.0))*vec3(.75,.75,1.)));
                color+=vec3(.075,.045,.012)*glow*(1.-uCharacter)*(1.-uEnvironment.x);
                // No high-frequency procedural plaster: it aliases into a checkerboard at mediump precision.
                float shade=contact(uShadows[0],uShadowPlanes[0])+contact(uShadows[1],uShadowPlanes[1])
                    +contact(uShadows[2],uShadowPlanes[2])+contact(uShadows[3],uShadowPlanes[3]);
                color*=1.-min(.48,shade)*smoothstep(.55,.9,n.y);
                float corner=1.-smoothstep(0.,.32,vWorld.y);
                color*=1.-corner*.065;
                // Bounded aerial perspective separates rear scenery from the interactive table.
                float haze=(1.-smoothstep(-2.7,-.4,vWorld.z))*.06;
                color=mix(color,mix(vec3(.79,.85,.76),vec3(.22,.29,.4),uEnvironment.x),haze);
                color=mix(color,uColor.rgb,uUnlit);
                gl_FragColor=vec4(color,uColor.a);
            }
        """
    }
}
