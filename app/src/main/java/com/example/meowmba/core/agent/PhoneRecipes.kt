package com.example.meowmba.core.agent

import java.io.File
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Learning is restricted to explicit searches. Query values are bound in memory,
 * never persisted in recipes or promotion records. No drafts/messages/credentials. */
data class PhoneSearchRequest(val app: String, val query: String) {
    companion object {
        fun parse(goal: String): PhoneSearchRequest? {
            val match = Regex("^(?:search|find) (?:for )?(.{1,160}?) (?:in|on) ([\\p{L}0-9][\\p{L}0-9 ._-]{0,60})$", RegexOption.IGNORE_CASE)
                .matchEntire(goal.trim()) ?: return null
            val query = match.groupValues[1].removeSurrounding("\"").trim()
            val app = match.groupValues[2].lowercase(Locale.ROOT).trim()
            if (query.isBlank() || query.any { it.isISOControl() } ||
                Regex("\\b(and|then|send|post|password|login|pay|delete)\\b", RegexOption.IGNORE_CASE).containsMatchIn(goal)) return null
            return PhoneSearchRequest(app, query)
        }
    }
}
@Serializable data class PhoneRecipeStep(val pkg: String, val version: Long, val action: PhoneAction, val layout: String)
@Serializable data class PhoneRecipe(val id: String, val app: String, val startPackage: String,
    val startVersion: Long, val steps: List<PhoneRecipeStep>, val greenRuns: Int = 1,
    val suspended: Boolean = false, val inFlight: Boolean = false) {
    val promoted get() = greenRuns >= 3 && !suspended && !inFlight
    fun bind(request: PhoneSearchRequest): List<PhoneRecipeStep> = steps.map { step ->
        val a = step.action
        step.copy(action = a.copy(value = a.value?.replace(QUERY, request.query),
            expect = a.expect?.copy(text = a.expect.text?.replace(QUERY, request.query))))
    }
    companion object { const val QUERY = "{{search_query}}" }
}
@Serializable private data class RecipeDocument(val version: Int = 2, val recipes: List<PhoneRecipe> = emptyList())

/** Corrupt/unknown files fail closed. Atomic replacement prevents half-written recipes.
 * Each candidate needs three matching, independently planned verified runs before replay. */
class PhoneRecipeStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = false }
    private var entries: List<PhoneRecipe> = runCatching {
        if (!file.isFile || file.length() > 256_000) emptyList() else
            json.decodeFromString<RecipeDocument>(file.readText()).let { doc ->
                require(doc.version == 2 && doc.recipes.size <= 24)
                doc.recipes.filter(::valid).map { if (it.inFlight) it.copy(inFlight = false, suspended = true, greenRuns = 0) else it }
            }
    }.getOrDefault(emptyList())
    @Synchronized fun match(request: PhoneSearchRequest, frame: PhoneObservation): PhoneRecipe? = entries.firstOrNull {
        !it.suspended && !it.inFlight && it.app == request.app && it.startPackage == frame.pkg && it.startVersion == frame.version && it.steps.first().layout == frame.recipeContext()
    }
    /** Persist BEFORE the first replayed action. Process death leaves a marker that
     * prevents a new process from silently repeating a partially executed recipe. */
    @Synchronized fun beginReplay(id: String): Boolean {
        if (entries.none { it.id == id && it.promoted }) return false
        save(entries.map { if (it.id == id) it.copy(inFlight = true) else it })
        return true
    }
    @Synchronized fun suspendRecipe(id: String) = save(entries.map {
        if (it.id == id) it.copy(suspended = true, inFlight = false, greenRuns = 0) else it
    })
    @Synchronized fun record(request: PhoneSearchRequest, trace: List<VerifiedPhoneStep>, finish: PhoneAction, completedReplay: Boolean = false) {
        val compiled = compile(request, trace, finish) ?: return
        val existing = entries.firstOrNull { it.id == compiled.id }
        val next = if (existing != null && existing.steps == compiled.steps && !existing.suspended && (!existing.inFlight || completedReplay))
            compiled.copy(greenRuns = (existing.greenRuns + 1).coerceAtMost(100)) else compiled
        save((entries.filterNot { it.id == next.id } + next).takeLast(24))
    }
    @Synchronized fun counts(): Pair<Int, Int> = entries.count { !it.suspended && !it.promoted } to entries.count { it.promoted }
    private fun save(next: List<PhoneRecipe>) {
        file.parentFile?.mkdirs()
        val temp = File.createTempFile(".recipe-", ".pending", file.parentFile)
        try {
            java.io.FileOutputStream(temp).use { it.write(json.encodeToString(RecipeDocument(recipes = next)).toByteArray()); it.fd.sync() }
            java.nio.file.Files.move(temp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
            entries = next
        } finally { temp.delete() }
    }
    private fun compile(request: PhoneSearchRequest, trace: List<VerifiedPhoneStep>, finish: PhoneAction): PhoneRecipe? {
        if (trace.size !in 1..10 || trace.any { it.before.version < 0 || it.after.version < 0 }) return null
        if (trace.zipWithNext().any { (a, b) -> !a.after.stableWith(b.before) }) return null
        val ending = finish.expect ?: return null
        val last = trace.last().after
        if (finish.type != "done" || ending.kind != "visible_text" || !PhoneVerification.holds(ending, last, last) ||
            !PhoneVerification.searchResult(request, last, trace)) return null
        val steps = mutableListOf<PhoneRecipeStep>()
        for (step in trace) {
            val original = step.action
            if (!PhoneVerification.effects(original, step.before, step.after)) return null
            val canonical = when (original.type) {
                "type", "tap", "tap_index" -> {
                    val node = original.selector?.let(step.before::find) ?: return null
                    if (original.type == "type" && (original.value != request.query || !PhoneVerification.isSearchField(node))) return null
                    if (original.type != "type" && (node.label.lowercase(Locale.ROOT) !in SAFE_NAV || node.labelFromValue)) return null
                    if (node.label.equals("go", true) && step.before.controls.none(PhoneVerification::isSearchField)) return null
                    val label = node.label.takeIf { !node.labelFromValue && it.lowercase(Locale.ROOT) in SAFE_NAV }
                    val id = node.viewId?.takeIf { it.matches(VIEW_ID) }
                    val selector = PhoneSelector(label, id, node.role)
                    if (!safeSelector(selector, original.type == "type")) return null
                    original.copy(type = if (original.type == "type") "type" else "tap_index", selector = selector,
                        index = null, text = null, field = null, direction = null, value = if (original.type == "type") PhoneRecipe.QUERY else null)
                }
                "launch_app" -> if (original.text.equals(request.app, true))
                    PhoneAction("launch_app", text = request.app, expect = original.expect) else return null
                else -> return null
            }
            val ex = original.expect ?: return null
            val sanitized = if (ex.kind == "editable_equals") {
                if (original.type != "type" || ex.text != request.query || ex.selector?.let(step.before::find) != original.selector?.let(step.before::find)) return null
                ex.copy(text = PhoneRecipe.QUERY, selector = canonical.selector)
            } else {
                if (ex.selector != null && !safeSelector(ex.selector, false)) return null
                ex.copy(text = ex.text?.let { templateText(it, request.query) ?: return null })
            }
            steps += PhoneRecipeStep(step.before.pkg, step.before.version, canonical.copy(expect = sanitized), step.before.recipeContext())
        }
        val finalText = templateText(ending.text ?: return null, request.query) ?: return null
        steps += PhoneRecipeStep(last.pkg, last.version, PhoneAction("done", expect = PhoneExpectation("visible_text", last.pkg, finalText)), last.recipeContext())
        val first = trace.first().before
        val id = java.security.MessageDigest.getInstance("SHA-256")
            .digest("${request.app}|${first.pkg}|${first.version}|${first.recipeContext()}".toByteArray()).take(12).joinToString("") { "%02x".format(it) }
        return PhoneRecipe(id, request.app, first.pkg, first.version, steps).takeIf(::valid)
    }
    private fun templateText(text: String, query: String): String? = when {
        text.equals(query, true) -> PhoneRecipe.QUERY
        text.equals("results for $query", true) -> "results for ${PhoneRecipe.QUERY}"
        text.lowercase(Locale.ROOT) in SAFE_RESULTS -> text
        else -> null
    }
    private fun safeSelector(selector: PhoneSelector?, typing: Boolean): Boolean {
        if (selector?.valid() != true || (selector.viewId != null && !selector.viewId.matches(VIEW_ID))) return false
        if (selector.label != null && selector.label.lowercase(Locale.ROOT) !in SAFE_NAV) return false
        if (typing) return selector.role == "edit" && (selector.label?.lowercase(Locale.ROOT)?.startsWith("search") == true ||
            selector.viewId?.substringAfter(":id/")?.let { Regex("(?:^|_)(search|query)(?:_|$)", RegexOption.IGNORE_CASE).containsMatchIn(it) } == true)
        return selector.label != null
    }
    private fun valid(recipe: PhoneRecipe): Boolean {
        if (!recipe.id.matches(Regex("[a-f0-9]{24}")) || recipe.app.length !in 1..61 || recipe.startVersion < 0 ||
            recipe.greenRuns !in 0..100 || recipe.steps.size !in 2..11 || recipe.steps.first().pkg != recipe.startPackage ||
            recipe.steps.first().version != recipe.startVersion || recipe.steps.count { it.action.type == "type" } != 1 ||
            recipe.steps.dropLast(1).any { it.action.type == "done" } || recipe.steps.last().action.type != "done") return false
        return recipe.steps.all { step ->
            val a = step.action; val ex = a.expect
            step.pkg.matches(Regex("[a-zA-Z0-9_.]{1,200}")) && step.version >= 0 && step.layout.matches(Regex("[a-f0-9]{64}")) && ex?.valid() == true &&
                a.index == null && a.field == null && a.direction == null &&
                (ex.text == null || ex.text == PhoneRecipe.QUERY || ex.text == "results for ${PhoneRecipe.QUERY}" || ex.text.lowercase(Locale.ROOT) in SAFE_RESULTS) &&
                (ex.selector == null || safeSelector(ex.selector, ex.kind == "editable_equals")) &&
                when (a.type) {
                    "type" -> a.value == PhoneRecipe.QUERY && a.text == null && safeSelector(a.selector, true)
                    "tap_index" -> a.value == null && a.text == null && safeSelector(a.selector, false)
                    "launch_app" -> a.value == null && a.selector == null && a.text == recipe.app
                    "done" -> a.value == null && a.text == null && a.selector == null && ex.kind == "visible_text" && ex.pkg == step.pkg
                    else -> false
                }
        }
    }
    companion object {
        private val VIEW_ID = Regex("[a-zA-Z0-9_.]+:id/[a-zA-Z0-9_]+")
        private val SAFE_NAV = setOf("search", "search here", "search query", "search field", "search box", "go", "find")
        private val SAFE_RESULTS = setOf("results", "search results")
    }
}
