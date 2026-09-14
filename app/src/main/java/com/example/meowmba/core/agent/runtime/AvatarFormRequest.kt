package com.example.meowmba.core.agent.runtime

import com.example.meowmba.ui.companion.AvatarAnimal
import java.util.Locale

/**
 * An anchored request to change this avatar's appearance. Explicit transform
 * verbs accept descriptions, not a species whitelist; the renderer validates
 * generated shape data separately. Quoted stories and extra actions are never
 * permission to execute just their positive transformation fragment.
 */
object AvatarFormRequest {
    const val MAX_DESCRIPTION_LENGTH = 500
    private val spaces = Regex("\\s+")
    private val politeQuestion = Regex("^(?:can|could|would|will) you (?:please )?")
    private val pleaseStart = Regex("^please(?:,)? +")
    private val pleaseEnd = Regex("(?:,)? +please$")
    private val desire = Regex("^(?:i want you to|i would like you to|i'd like you to|id like you to) +")
    private val reset = Regex(
        "^(?:(?:go|turn|change) back to (?:normal|(?:the |your )?original(?: form| animation| avatar)?)|" +
            "return to (?:normal|(?:the |your )?original(?: form| animation| avatar)?)|" +
            "stop (?:being|acting like) (?:an animal|a cat|a dog|a squirrel|a raccoon|a racoon|" +
            "an owl|a duck|an australian shepherd(?: dog)?|an aussie|a shepherd|" +
            "a rabbit|a bunny(?: rabbit)?|a pig|a piglet|a piggy|an alien|an extraterrestrial|" +
            "a fruit fly|a fruit-fly|a fruitfly|a drosophila|a robot|an android|a panda(?: bear)?|a giant panda|" +
            "clippy|clippit|merlin|(?:a |the )?wizard|(?:a |the )?paperclip)|" +
            "(?:just |be )?eyes only)$",
    )
    private val command = Regex(
        "^(be|become|turn(?: yourself)? into|transform(?: yourself)? into|" +
            "morph(?: yourself)? into|look like|" +
            "(?:change|set) (?:your |the )?(?:avatar|form|appearance)(?: form)? (?:to|into)|" +
            "make yourself (?:into|look like)) (.+)$",
    )
    private val article = Regex("^(?:a|an|the) +")
    private val descriptionCharacters = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N} ,'-]*")
    private val words = Regex("[\\p{L}]+(?:'[\\p{L}]+)?")
    // Bare 'be a ...' is also used for jobs/relationships/moods. Keep this one
    // conversational verb conservative while 'turn into a coffee cup' is clear.
    private val otherSpecies = setOf("dragon", "unicorn", "fox", "arctic fox", "red panda",
        "panda", "bear", "rabbit", "bunny", "wolf", "lion", "tiger", "owl", "bird",
        "parrot", "penguin", "raven", "horse", "pony", "deer", "mouse", "rat", "hamster",
        "badger", "skunk", "ferret", "otter", "frog", "lizard", "turtle", "elephant",
        "giraffe", "monkey", "lemur", "ring-tailed lemur", "koala", "kangaroo", "dinosaur")
    private val forbiddenWords = setOf(
        "no", "not", "never", "dont", "don't", "cannot", "cant", "can't", "stop", "avoid",
        "or", "then", "also", "but", "except", "unless", "if", "when", "while",
        "instead", "rather", "without", "until", "before", "after", "because",
        // These are extra instructions, not visual attributes. 'and' and 'with'
        // are deliberately allowed: a green dragon with red and gold wings.
        "act", "pretend", "sing", "dance", "bark", "meow", "speak", "say", "tell", "open",
        "send", "delete", "walk", "run", "jump", "play", "make", "write", "search", "download",
        "upload", "install", "execute", "browse", "ignore", "instructions", "prompt", "tool",
    )
    private val additionalAction = Regex(
        "(?:^| |,)and +(?:please +)?(?:compose|email|text|call|message|launch|remind|schedule|" +
            "purchase|buy|pay|transfer|navigate|order|cancel|book|turn|change|set|move|close|save|read|find|reveal)(?: |$)|" +
            ", *(?:please +)?(?:compose|email|text|call|message|launch|remind|schedule|purchase|buy|pay|transfer|" +
            "navigate|order|cancel|book|turn|change|set|move|close|save|read|find|reveal)(?: |$)",
    )
    private val mediaSuffix = Regex("(?:^| )(?:image|images|picture|pictures|photo|photos|photograph|photographs|video|videos|movie|film|gif|drawing|portrait|artwork|animation|clip|file|asset)$")
    private val mediaContext = Regex("(?:^| )(?:in|for|as) (?:an? |the )?(?:image|picture|photo|video|movie|film|gif|drawing|portrait|animation|clip|file)(?: |$)")
    private val nonAppearanceSuffix = Regex("(?:^| )(?:for me|for you|owner|person|friend|buddy|lover|fan|boy|girl|gentleman)$")

    /** A greeting/name prefix is the router's job; only polite wrappers live here. */
    fun parse(text: String): String? {
        if (text.length > MAX_DESCRIPTION_LENGTH + 150 || text.any { it == '\n' || it == '\r' || it == ';' }) return null
        var normalized = text.trim().lowercase(Locale.ROOT).replace(spaces, " ")
            .trimEnd('.', '!', '?').trimEnd()
        normalized = normalized.replaceFirst(pleaseStart, "")
        normalized = normalized.replaceFirst(politeQuestion, "")
        normalized = normalized.replaceFirst(desire, "")
        normalized = normalized.replaceFirst(pleaseStart, "")
        normalized = normalized.replace(pleaseEnd, "")
        if (reset.matches(normalized)) return AvatarAnimal.NONE.key
        val match = command.matchEntire(normalized) ?: return null
        val rawTarget = match.groupValues[2]
        val requested = rawTarget.replaceFirst(article, "")
        if (!isDescription(requested)) return null
        if (match.groupValues[1] == "be" && AvatarAnimal.resolve(requested) == null) {
            if (rawTarget == requested || requested !in otherSpecies) return null
        }
        return requested
    }

    /** Same boundary for deterministic routing and model-proposed tool arguments. */
    fun isDescription(text: String): Boolean {
        if (text.length !in 1..MAX_DESCRIPTION_LENGTH || text.any { it.isISOControl() }) return false
        val normalized = text.trim().lowercase(Locale.ROOT)
        if (AvatarAnimal.resolve(normalized) != null) return true
        if (!descriptionCharacters.matches(normalized) || normalized.endsWith('\'') || normalized.endsWith(',')) return false
        if (normalized.startsWith("my ") || normalized.startsWith("your ") || normalized.startsWith("our ")) return false
        if (words.findAll(normalized).any { it.value in forbiddenWords }) return false
        if (additionalAction.containsMatchIn(normalized)) return false
        if (mediaSuffix.containsMatchIn(normalized) || mediaContext.containsMatchIn(normalized)) return false
        return !nonAppearanceSuffix.containsMatchIn(normalized)
    }
}
