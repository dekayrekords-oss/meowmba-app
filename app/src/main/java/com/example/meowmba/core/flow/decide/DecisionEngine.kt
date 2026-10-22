package com.example.meowmba.core.flow.decide

import com.example.meowmba.core.flow.Judgment
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * A typed question in the System One style shared by Laya and Jev:
 * `noul` → P(true), `choice` → one of named criteria with probabilities.
 * Laya's `score` is its weakest primitive, so Meow Flow uses Choice for levels.
 */
sealed interface Question {
    val id: String
    val instructions: String

    data class Noul(override val id: String, override val instructions: String) : Question

    data class Choice(
        override val id: String,
        override val instructions: String,
        /** Insertion-ordered option key → concrete description. */
        val criteria: Map<String, String>,
    ) : Question {
        init { require(criteria.size >= 2) { "choice $id needs at least two options" } }
    }

    /** Wire form used by Laya's `predict(state, questions)` and TypeSafe `/v1/systemone`. */
    fun toWire(): JsonObject = when (this) {
        is Noul -> buildJsonObject { put("type", "noul"); put("instructions", instructions) }
        is Choice -> buildJsonObject {
            put("type", "choice"); put("instructions", instructions)
            putJsonObject("criteria") { criteria.forEach { (k, v) -> put(k, v) } }
        }
    }
}

/** One answer to one question, before it is tied to an intention. */
data class Answer(
    val questionId: String,
    /** "yes"/"no" for Noul, the option key for Choice. */
    val answer: String,
    /** P(yes) for Noul; P(chosen option) for Choice. */
    val probability: Double,
    /** Full distribution for Choice; empty for Noul. */
    val distribution: Map<String, Double> = emptyMap(),
)

/**
 * A model that answers typed questions over a compact state. Implementations:
 * LayaOnDeviceEngine (phase 2), JevEngine (phase 8, opt-in). No server engine (owner D8).
 * Implementations must never throw for "unavailable"; return null from [ask] instead.
 */
interface DecisionEngine {
    val id: String
    suspend fun available(): Boolean
    /** Null = abstain (model missing, too hot, input too long). */
    suspend fun ask(stateJson: String, questions: List<Question>): List<Answer>?
}

fun Answer.toJudgment(intentionId: String?, engine: String) =
    Judgment(intentionId, questionId, answer, probability, engine)
