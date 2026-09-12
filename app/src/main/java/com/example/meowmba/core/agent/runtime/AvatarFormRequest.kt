package com.example.meowmba.core.agent.runtime

import com.example.meowmba.ui.companion.AvatarAnimal
import java.util.Locale

/**
 * One explicit self-transformation request, never substring routing. This only
 * identifies the requested phrase: the tool's allowlist decides availability.
 * A bounded unknown species is retained so it can fail honestly, not map to NONE.
 */
object AvatarFormRequest {
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
            "a fruit fly|a fruit-fly|a fruitfly|a drosophila|a robot|an android|a panda(?: bear)?|a giant panda)|" +
            "(?:just |be )?eyes only)$",
    )
    private val command = Regex(
        "^(be|become|turn(?: yourself)? into|transform(?: yourself)? into|" +
            "morph(?: yourself)? into|look like|" +
            "(?:change|set) your (?:avatar|form|appearance)(?: form)? (?:to|into)) (.+)$",
    )
    private val article = Regex("^(?:a|an|the) +")
    private val animalPhrase = Regex("[a-z]+(?:-[a-z]+)*(?: [a-z]+(?:-[a-z]+)*){0,2}")
    // Unknown roles such as "be a CEO" are not avatar requests. These common
    // species can fail honestly; explicit "change your avatar to ..." remains
    // the unambiguous route for any other unsupported form.
    private val otherSpecies = setOf("dragon", "unicorn", "fox", "arctic fox", "red panda",
        "panda", "bear", "rabbit", "bunny", "wolf", "lion", "tiger", "owl", "bird",
        "parrot", "penguin", "raven", "horse", "pony", "deer", "mouse", "rat", "hamster",
        "badger", "skunk", "ferret", "otter", "frog", "lizard", "turtle", "elephant",
        "giraffe", "monkey", "lemur", "ring-tailed lemur", "koala", "kangaroo", "dinosaur")
    private val forbiddenWords = setOf(
        // Negation, alternatives, conditions and extra instructions are not
        // permission to execute the first positive fragment of an utterance.
        "no", "not", "never", "dont", "don't", "cannot", "cant", "can't", "stop", "avoid",
        "and", "or", "then", "also", "but", "except", "unless", "if", "when", "while",
        "instead", "rather", "without", "with", "for", "until", "before", "after", "because",
        "in", "on", "at", "to", "from", "my", "your", "our", "their", "his", "her",
        "me", "you", "please", "now", "today", "tomorrow",
        // File/media requests have a different output and authority boundary.
        "image", "images", "picture", "pictures", "photo", "photos", "photograph", "photographs",
        "video", "videos", "movie", "film", "gif", "drawing", "draw", "render", "portrait",
        "artwork", "animation", "clip", "file", "asset",
        // Common short extra imperatives and conversational roles are not species.
        "act", "pretend", "sing", "dance", "bark", "meow", "speak", "say", "tell", "open",
        "send", "delete", "move", "walk", "run", "jump", "play", "make", "write", "search",
        "friend", "buddy", "person", "owner", "lover", "fan", "boy", "girl", "gentleman",
    )

    /** A greeting/name prefix is the router's job; only polite wrappers live here. */
    fun parse(text: String): String? {
        if (text.length > 200 || text.any { it == '\n' || it == '\r' || it == ';' }) return null
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
        if (requested.length !in 1..48 || !animalPhrase.matches(requested)) return null
        if (requested.split(' ', '-').any { it in forbiddenWords }) return null
        val explicitAppearance = match.groupValues[1].startsWith("change your ") ||
            match.groupValues[1].startsWith("set your ")
        if (!explicitAppearance && AvatarAnimal.resolve(requested) == null && requested !in otherSpecies) return null
        // 'Be happy/sad/careful' belongs to emotion/conversation, not an unknown
        // animal error. Bare 'be cat' is unambiguous; new species use 'be a ...'
        // or the explicit transformation verbs above.
        if (match.groupValues[1] == "be" && rawTarget == requested && AvatarAnimal.resolve(requested) == null) {
            return null
        }
        return requested
    }
}
