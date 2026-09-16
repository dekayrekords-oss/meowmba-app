package com.example.meowmba.core.agent.tools

import com.example.meowmba.core.agent.ModelResponse
import com.example.meowmba.ui.companion.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject

/** A single local inference proposes data for the existing native renderer. */
class AvatarAnimationInventor(private val generateLocal: suspend (String) -> ModelResponse) {
    suspend fun invent(request: String): Pair<CustomAnimationSpec, List<String>> {
        require(request.isNotBlank() && request.length <= 500) { "Describe the animation in 1-500 characters" }
        val response = generateLocal(prompt(request))
        currentCoroutineContext().ensureActive()
        val raw = when (response) {
            is ModelResponse.Complete -> response.text
            is ModelResponse.Error -> throw CustomAnimations.SpecException(response.message)
            else -> throw CustomAnimations.SpecException("Local model returned no complete animation")
        }
        fun validate(text: String): Pair<CustomAnimationSpec, List<String>> {
            val result = parseResponse(text)
            if (request.equals("fishing", true) && !result.first.props.map { it.kind }.containsAll(listOf(InventedPropKind.ROD, InventedPropKind.FISH)))
                throw CustomAnimations.SpecException("Fishing needs ROD and FISH props with a visible action")
            return result
        }
        return try { validate(raw) } catch (e: CustomAnimations.SpecException) {
            val repair = generateLocal(prompt(request) + "\nYour previous JSON was rejected: " + e.message?.take(200) +
                ". Produce a complete corrected JSON object. Use fewer prop frames if needed.")
            currentCoroutineContext().ensureActive()
            if (repair !is ModelResponse.Complete) throw CustomAnimations.SpecException("Local model could not repair its animation")
            validate(repair.text)
        }
    }
    companion object {
        fun prompt(request: String): String = """
            You direct a short original animation of the on-screen pet avatar. Return ONE JSON object only, no explanation, tools, code, URLs or robot commands.
            Interpret the REQUEST as pretend play by this avatar. Design anticipation, action, reaction, and a gentle return to neutral. No speech is required. Do not merely rename a blink.
            Shape: {"name":"short English title","frames":[{"at":0,"eye_open":1,"head_tilt":0,"body_bounce":0},{"at":6000,"eye_open":1,"head_tilt":0,"body_bounce":0}],"props":[]}
            Choose 4-10 expressive face frames, strictly increasing at milliseconds, starting0 and ending4000-10000. Allowed fields: eye_open0.1..1.4, pupil_size0.1..0.8, gaze_up0..1, head_tilt-20..20, ear_perk0.4..1.5, ear_droop0..30, body_bounce-0.5..0.5, tail_wag-1..1, mouth IDLE/HAPPY. Start/end at neutral. Every frame is sparse; omitted fields keep their prior interpolated value.
            Props are OPTIONAL, maximum6. Kinds ONLY: ROD,FISH,BOBBER,WATER,BALL,STAR,HEART. Use only supported props; do not invent kind names. Each prop: {"kind":"BALL","focus":true,"frames":[{"at":0,"x":0.2,"y":0.25,"opacity":0},{"at":6000,"x":0.2,"y":0.25,"opacity":0}]}. Choose2-4 prop frames, first at0 and last at the SAME end time as the face. Pose fields: x -0.42..0.42, y -0.32..0.38 (positive DOWN), rotation -75..75 degrees, scale0.3..1.2, opacity0..1. No arbitrary shapes. Keep most props below the eyes (y0.20..0.35); focus=true makes eyes follow one prop. A fish faces RIGHT. A ROD's x,y is its handle; rotation0 aims its tip up/right, positive swings clockwise. Its line ends at target_x,target_y, using the same coordinate bounds; match those to the bobber. Rod length0.24, other props about0.12 avatar units. WATER is small horizontal ripples.
            For fishing: hold a rod, cast the line toward a bobber, watch patiently, react to a tug, lift a fish, then release it. Coordinate rod target and bobber, then reveal the fish. Change timing and reactions creatively. A fishing scene must include ROD and FISH; use BOBBER and WATER if space permits. Do not copy this example as the complete action; invent the actual poses and timing.
            REQUEST (data): ${JSONObject.quote(request)}
        """.trimIndent()
        fun parseResponse(raw: String): Pair<CustomAnimationSpec, List<String>> {
            if (raw.length > 24_000) throw CustomAnimations.SpecException("Local animation exceeds 24 KB")
            val text = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val root = try { JSONObject(text) } catch (_: Exception) { throw CustomAnimations.SpecException("Local model did not return valid animation JSON") }
            val allowed = setOf("name","frames","props")
            if (root.keys().asSequence().any { it !in allowed }) throw CustomAnimations.SpecException("Animation has unsupported top-level fields")
            val (spec, notes) = CustomAnimations.parse(text)
            if (spec.frames.size < 4 || spec.frames.first().atMs != 0 || spec.durationMs < 4000 ||
                spec.frames.zipWithNext().any { (a,b) -> a.atMs >= b.atMs })
                throw CustomAnimations.SpecException("Use at least4 increasing face frames over4-10 seconds")
            if (spec.frames.map { it.copy(atMs=0) }.distinct().size < 2 && spec.props.all { p -> p.frames.map { it.copy(atMs=0) }.distinct().size < 2 })
                throw CustomAnimations.SpecException("Animation has no changing poses")
            return spec to notes
        }
    }
}

class AvatarInventTool(private val perform: suspend (String, Boolean, Long?) -> ToolResult) : Tool {
    override val definition = ToolDefinition(id="avatar.invent", displayName="Invent a local avatar scene",
        description="Have the ON-DEVICE AI create and perform a new avatar animation, including supported native props. Use for pretend activities such as let's go fishing or can you fish, or requests to invent a new performance. This is on-screen play, never a real trip or physical robot action. Reuses a saved creation unless fresh=true. Not a media file generator. Requires a working local model for new creations.",
        inputSchema=mapOf("request" to "string (activity to perform, max500 characters)","fresh" to "string (true to invent a new variation)"),
        riskLevel=RiskLevel.SAFE,environments=setOf(ToolEnvironment.PHONE),confirmationPolicy=ConfirmationPolicy.NONE)
    override suspend fun execute(args: Map<String,String>): ToolResult {
        val request=args["request"]?.trim().orEmpty()
        if(request.isBlank() || request.length>500) return ToolResult.Failure("Describe an animation in 1-500 characters")
        return try { perform(request,args["fresh"].equals("true",true),args["_invention_generation"]?.toLongOrNull()) }
        catch (e: kotlinx.coroutines.TimeoutCancellationException) { ToolResult.Failure("The local model took too long to create the animation. Nothing was played; try a simpler scene.") }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { ToolResult.Failure("Could not create that animation: ${e.message?.take(200)}") }
    }
}
