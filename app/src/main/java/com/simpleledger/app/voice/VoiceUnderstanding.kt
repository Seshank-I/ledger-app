package com.simpleledger.app.voice

import com.simpleledger.app.data.Direction
import com.simpleledger.app.data.PersonEntity
import kotlin.math.roundToLong

/** The final reading of a sentence, and which method produced it (shown to the user). */
data class Understood(val command: VoiceCommand, val usedAi: Boolean)

/**
 * Combines the on-device model with the rules. The model's answer wins where it gave one;
 * the rules fill any gap. A name the model returns must match a real person, so it can
 * never invent someone. Every result still goes through the confirm screen.
 */
object VoiceUnderstanding {
    fun merge(heard: String, people: List<PersonEntity>, currentPersonId: String?, ai: AiAnswer?): Understood {
        val rules = VoiceParser.parse(heard, people, currentPersonId)
        if (ai == null || ai.intent !in setOf("record", "balance")) return Understood(rules, usedAi = false)

        val rulesPerson = when (rules) {
            is VoiceCommand.Record -> rules.personId
            is VoiceCommand.AskBalance -> rules.personId
            VoiceCommand.NotUnderstood -> null
        }
        val rulesCandidates = when (rules) {
            is VoiceCommand.Record -> rules.candidates
            is VoiceCommand.AskBalance -> rules.candidates
            VoiceCommand.NotUnderstood -> emptyList()
        }
        val aiPerson = people.firstOrNull { it.name.equals(ai.person, ignoreCase = true) }?.id
        val personId = aiPerson ?: rulesPerson
        val candidates = if (personId == null) rulesCandidates else emptyList()

        if (ai.intent == "balance") return Understood(VoiceCommand.AskBalance(personId, candidates), usedAi = true)

        val direction = when (ai.direction) {
            "gave" -> Direction.GAVE
            "got" -> Direction.GOT
            else -> (rules as? VoiceCommand.Record)?.direction
        }
        val aiAmount = if (ai.amountRupees > 0) (ai.amountRupees * 100).roundToLong() else null
        val amount = aiAmount ?: (rules as? VoiceCommand.Record)?.amountPaise
        return Understood(VoiceCommand.Record(personId, candidates, direction, amount), usedAi = true)
    }
}
