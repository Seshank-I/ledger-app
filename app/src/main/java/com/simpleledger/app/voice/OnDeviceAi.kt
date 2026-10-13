package com.simpleledger.app.voice

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.simpleledger.app.data.PersonEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** What the on-device model understood. Empty strings and zero mean "not said". */
data class AiAnswer(val intent: String, val person: String, val direction: String, val amountRupees: Double)

/**
 * Gemini Nano, running entirely on the phone through Android's AICore: nothing is sent anywhere.
 * Only some phones have it (e.g. Galaxy S26 Ultra, not iQOO Z10x); elsewhere [understand] returns null
 * and the rule-based [VoiceParser] is used alone.
 */
class OnDeviceAi(private val scope: CoroutineScope) {
    enum class Status { READY, DOWNLOADING, NOT_SUPPORTED }

    private val model by lazy { Generation.getClient() }
    private var downloadStarted = false

    suspend fun status(): Status = try {
        when (model.checkStatus()) {
            FeatureStatus.AVAILABLE -> Status.READY
            FeatureStatus.DOWNLOADING -> Status.DOWNLOADING
            FeatureStatus.DOWNLOADABLE -> {
                startDownload()
                Status.DOWNLOADING
            }
            else -> Status.NOT_SUPPORTED
        }
    } catch (e: Exception) {
        Status.NOT_SUPPORTED
    }

    /** The phone downloads the model once, in the background; until then the rules are used. */
    private fun startDownload() {
        if (downloadStarted) return
        downloadStarted = true
        scope.launch {
            try {
                model.download().collect { }
            } catch (e: Exception) {
                downloadStarted = false
            }
        }
    }

    suspend fun understand(heard: String, people: List<PersonEntity>): AiAnswer? {
        if (status() != Status.READY) return null
        return try {
            withTimeoutOrNull(TIMEOUT_MS) {
                val text = model.generateContent(prompt(heard, people)).candidates.firstOrNull()?.text
                text?.let(::parseAnswer)
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val TIMEOUT_MS = 8_000L

        fun prompt(heard: String, people: List<PersonEntity>): String = """
            You read one sentence about money between the user and another person. Reply with JSON only.
            People: ${people.joinToString("; ") { it.name }}
            intent: "record" if money was given or received, "balance" if asking how much is owed, otherwise "unknown".
            person: one name copied exactly from People, or "" if none is mentioned.
            direction: "gave" if the user gave, lent or paid money to the person; "got" if the user received or borrowed money, or the person paid or returned money; otherwise "".
            amount: rupees as a number, 0 if not said. lakh = 100000, crore = 10000000.
            The sentence may be in English or Telugu.
            "I gave Ramesh 500" -> {"intent":"record","person":"Ramesh","direction":"gave","amount":500}
            "Sita paid me back two thousand" -> {"intent":"record","person":"Sita","direction":"got","amount":2000}
            "how much does Ramesh owe" -> {"intent":"balance","person":"Ramesh","direction":"","amount":0}
            Sentence: "${heard.replace("\"", "'")}"
            JSON:
        """.trimIndent()

        /** Reads the first {...} block, tolerating extra words or code fences around it. */
        fun parseAnswer(text: String): AiAnswer? {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            return try {
                val o = JSONObject(text.substring(start, end + 1))
                AiAnswer(
                    intent = o.optString("intent").lowercase(),
                    person = o.optString("person").trim(),
                    direction = o.optString("direction").lowercase(),
                    amountRupees = o.optDouble("amount", 0.0).takeIf { !it.isNaN() } ?: 0.0,
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
