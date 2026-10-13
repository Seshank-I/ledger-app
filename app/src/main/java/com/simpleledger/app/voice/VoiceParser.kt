package com.simpleledger.app.voice

import com.simpleledger.app.data.Direction
import com.simpleledger.app.data.PersonEntity
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.max
import kotlin.math.min

/** What a spoken sentence asked for. Missing pieces are null; the voice screen asks for them. */
sealed interface VoiceCommand {
    data class Record(
        val personId: String?,
        /** Several people sounded alike; the user picks one. */
        val candidates: List<String>,
        val direction: Direction?,
        val amountPaise: Long?,
    ) : VoiceCommand

    data class AskBalance(val personId: String?, val candidates: List<String>) : VoiceCommand

    data object NotUnderstood : VoiceCommand
}

/**
 * Understands short money sentences in English and Telugu, for example
 * "I gave Ramesh 500", "got 2000 from Sita", "How much does Ramesh owe",
 * "రమేష్ కి 500 ఇచ్చాను", "సీత నుండి రెండు వేలు తీసుకున్నాను".
 * It never saves anything itself: the result always goes to the confirm screen.
 */
object VoiceParser {
    fun parse(heard: String, people: List<PersonEntity>, currentPersonId: String? = null): VoiceCommand {
        val text = heard.replace('‌', ' ').replace('‍', ' ').trim()
        if (text.isEmpty()) return VoiceCommand.NotUnderstood
        val lower = text.lowercase()
        val tokens = tokenize(lower)

        val amount = Numbers.amountPaise(tokens)
        val direction = direction(lower)
        val match = NameMatcher.match(tokens, people)
        val personId = match.best ?: currentPersonId.takeIf { match.candidates.isEmpty() }

        if (amount == null && isQuestion(lower)) {
            return VoiceCommand.AskBalance(personId, match.candidates)
        }
        if (amount == null && direction == null && match.best == null && match.candidates.isEmpty()) {
            return VoiceCommand.NotUnderstood
        }
        return VoiceCommand.Record(personId, match.candidates, direction, amount)
    }

    /** Splits into words, keeping "1,500" and "1.5" whole. */
    internal fun tokenize(s: String): List<String> =
        s.replace(Regex("(?<=\\d),(?=\\d)"), "")
            .split(Regex("[\\s,!?।]+|(?<!\\d)\\.|\\.(?!\\d)"))
            .map { it.trim('₹', '"', '\'', '(', ')') }
            .filter { it.isNotEmpty() }

    // The user paying money back is still money going out. Checked before everything else.
    private val FIRST_PERSON_GAVE = listOf(
        "i returned", "i repaid", "i paid back", "i gave back", "i gave", "i paid", "i lent", "i sent",
        // Telugu: I gave, I sent (first-person endings)
        "ఇచ్చాను", "పంపాను",
    )

    // Phrases where money came TO the user. Checked next: "gave me" contains "gave".
    private val GOT_PHRASES = listOf(
        "gave me", "paid me", "sent me", "returned", "repaid", "gave back", "paid back",
        "got", "received", "receive", "took", "borrowed", "from",
        // Telugu: from, took, he/she/they gave, came, returned, received
        "నుండి", "నుంచి", "దగ్గర", "తీసుకున్న", "ఇచ్చాడు", "ఇచ్చింది", "ఇచ్చారు", "వచ్చింది", "వచ్చాయి", "తిరిగి", "అందింది",
    )

    // Phrases where money went FROM the user.
    private val GAVE_PHRASES = listOf(
        "gave", "give", "given", "lent", "lend", "paid", "pay", "sent", "loan to",
        // Telugu: I gave, gave (short), lent, sent
        "ఇచ్చాను", "ఇచ్చా", "అప్పు ఇచ్చ", "పంపాను", "పంపా",
    )

    private val QUESTION_WORDS = listOf(
        "how much", "balance", "owe", "owes", "due", "pending", "outstanding",
        "ఎంత", "బాకీ", "బ్యాలెన్స్", "రావాలి", "ఇవ్వాలి",
    )

    internal fun direction(lower: String): Direction? = when {
        FIRST_PERSON_GAVE.any { containsPhrase(lower, it) } && !containsPhrase(lower, "gave me") -> Direction.GAVE
        GOT_PHRASES.any { containsPhrase(lower, it) } -> Direction.GOT
        GAVE_PHRASES.any { containsPhrase(lower, it) } -> Direction.GAVE
        else -> null
    }

    private fun isQuestion(lower: String) = QUESTION_WORDS.any { containsPhrase(lower, it) }

    /** Whole-word match for English; plain substring for Telugu, whose words carry attached suffixes. */
    private fun containsPhrase(text: String, phrase: String): Boolean =
        if (phrase.first().code < 0x0C00) Regex("\\b${Regex.escape(phrase)}\\b").containsMatchIn(text) else text.contains(phrase)
}

/** Spoken amounts: digits ("2,000", "2.5"), English words, and Telugu words, with lakh and crore. */
internal object Numbers {
    private val UNITS = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7,
        "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14,
        "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19, "twenty" to 20,
        "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90,
        "a" to 1, "an" to 1,
        "ఒకటి" to 1, "ఒక" to 1, "రెండు" to 2, "మూడు" to 3, "నాలుగు" to 4, "ఐదు" to 5, "అయిదు" to 5, "ఆరు" to 6,
        "ఏడు" to 7, "ఎనిమిది" to 8, "తొమ్మిది" to 9, "పది" to 10, "ఇరవై" to 20, "ముప్పై" to 30, "నలభై" to 40,
        "యాభై" to 50, "అరవై" to 60, "డెబ్బై" to 70, "ఎనభై" to 80, "తొంభై" to 90,
        // Colloquial hundreds: "ఐదొందలు" = 500
        "రెండొందలు" to 200, "మూడొందలు" to 300, "నాలుగొందలు" to 400, "ఐదొందలు" to 500,
        "ఆరొందలు" to 600, "ఏడొందలు" to 700, "ఎనిమిదొందలు" to 800, "తొమ్మిదొందలు" to 900,
    )
    // "ఐదు వందలు" = 5 × 100; "వంద" alone = 100.
    private val HUNDRED = setOf("hundred", "hundreds", "వంద", "వందలు", "వందల")
    private val SCALES = mapOf(
        "thousand" to 1_000L, "thousands" to 1_000L, "k" to 1_000L,
        "lakh" to 100_000L, "lakhs" to 100_000L, "lac" to 100_000L, "lacs" to 100_000L,
        "crore" to 10_000_000L, "crores" to 10_000_000L,
        "వెయ్యి" to 1_000L, "వేలు" to 1_000L, "వేల" to 1_000L, "లక్ష" to 100_000L, "లక్షలు" to 100_000L, "లక్షల" to 100_000L,
        "కోటి" to 10_000_000L, "కోట్లు" to 10_000_000L,
    )
    private val SKIP_INSIDE = setOf("and", "rupees", "rupee", "rs", "రూపాయలు", "రూపాయల", "రూపాయి")

    private fun digitValue(token: String): BigDecimal? {
        // Telugu digits ౦-౯ become 0-9; "rs500", "500rs", "500/-" and "2k" are understood.
        var cleaned = token.map { if (it in '౦'..'౯') '0' + (it - '౦') else it }.joinToString("")
        cleaned = cleaned.removePrefix("rs").removeSuffix("/-").removeSuffix("rs").replace(",", "")
        val thousands = cleaned.endsWith("k") && cleaned.length > 1
        if (thousands) cleaned = cleaned.dropLast(1)
        val value = cleaned.toBigDecimalOrNull()?.takeIf { it.signum() >= 0 } ?: return null
        return if (thousands) value * BigDecimal(1000) else value
    }

    private fun isNumberWord(t: String) = t in UNITS || t in HUNDRED || t in SCALES || digitValue(t) != null

    /** The first run of number words in the sentence, as paise; null when there is none. */
    fun amountPaise(tokens: List<String>): Long? {
        val start = tokens.indexOfFirst { isNumberWord(it) && it !in setOf("a", "an", "ఒక") || digitValue(it) != null }
        if (start < 0) return null
        var total = BigDecimal.ZERO
        var current = BigDecimal.ZERO
        var i = start
        var used = false
        while (i < tokens.size) {
            val t = tokens[i]
            val digits = digitValue(t)
            when {
                digits != null -> { current += digits; used = true }
                t in UNITS -> { current += BigDecimal(UNITS.getValue(t)); used = true }
                t in HUNDRED -> { current = current.max(BigDecimal.ONE) * BigDecimal(100); used = true }
                t in SCALES -> {
                    total += current.max(BigDecimal.ONE) * BigDecimal(SCALES.getValue(t))
                    current = BigDecimal.ZERO
                    used = true
                }
                t in SKIP_INSIDE && used -> Unit
                else -> break
            }
            i++
        }
        val rupees = total + current
        if (!used || rupees.signum() <= 0) return null
        return rupees.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValueExact()
    }
}

/** Finds which person was named, across English and Telugu script, tolerating small mishearings. */
internal object NameMatcher {
    data class Result(val best: String?, val candidates: List<String>)

    fun match(tokens: List<String>, people: List<PersonEntity>): Result {
        if (people.isEmpty()) return Result(null, emptyList())
        val words = tokens.map { Phonetic.key(Transliterate.toLatin(it)) }.filter { it.length >= 2 }
        val scored = people.map { p -> p.id to score(p.name, words) }.filter { it.second >= 0.75 }.sortedByDescending { it.second }
        if (scored.isEmpty()) return Result(null, emptyList())
        val top = scored.first().second
        val tied = scored.filter { top - it.second < 0.01 }.map { it.first }
        return if (tied.size == 1) Result(tied.first(), emptyList()) else Result(null, tied)
    }

    /** Best similarity of the name (or its first word) against any spoken word or pair of words. */
    private fun score(name: String, words: List<String>): Double {
        val full = Phonetic.key(Transliterate.toLatin(name.lowercase()))
        val first = Phonetic.key(Transliterate.toLatin(name.lowercase().split(Regex("\\s+")).first()))
        val spoken = words + words.zipWithNext { a, b -> a + b }
        return listOf(full, first).filter { it.length >= 2 }.maxOfOrNull { key ->
            spoken.maxOfOrNull { w -> similarity(key, w) } ?: 0.0
        } ?: 0.0
    }

    /**
     * Spoken Telugu words carry suffixes ("రమేష్కి" = to Ramesh), so a word may start with the name.
     * A prefix match scores a little lower than a whole-word match, and longer names win ties,
     * so "Ramesh" beats "Ram" when "రమేష్కి" is heard.
     */
    private fun similarity(key: String, word: String): Double {
        val whole = 1.0 - Edit.distance(key, word).toDouble() / max(key.length, word.length)
        val prefix = if (word.length > key.length) {
            1.0 - Edit.distance(key, word.take(key.length)).toDouble() / key.length - 0.1
        } else 0.0
        return max(whole, prefix) + 0.01 * key.length
    }
}

internal object Edit {
    fun distance(a: String, b: String): Int {
        val row = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = row[0]
            row[0] = i
            for (j in 1..b.length) {
                val tmp = row[j]
                row[j] = min(min(row[j] + 1, row[j - 1] + 1), prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return row[b.length]
    }
}

/** Collapses spelling differences that sound the same: "Seetha" and "Sita" both become "sita". */
internal object Phonetic {
    private val RULES = listOf(
        "aa" to "a", "ee" to "i", "ii" to "i", "oo" to "u", "uu" to "u",
        "th" to "t", "dh" to "d", "sh" to "s", "ph" to "f", "bh" to "b", "kh" to "k", "gh" to "g", "ch" to "c",
        "w" to "v", "z" to "j", "q" to "k", "y" to "i",
    )

    fun key(s: String): String {
        var k = s.lowercase().filter { it in 'a'..'z' }
        RULES.forEach { (from, to) -> k = k.replace(from, to) }
        return k.replace(Regex("(.)\\1+"), "$1")
    }
}

/** Telugu script to Latin letters, enough to compare names: "రమేష్" → "ramesh". */
internal object Transliterate {
    private val VOWELS = mapOf(
        'అ' to "a", 'ఆ' to "aa", 'ఇ' to "i", 'ఈ' to "ii", 'ఉ' to "u", 'ఊ' to "uu", 'ఋ' to "ru",
        'ఎ' to "e", 'ఏ' to "e", 'ఐ' to "ai", 'ఒ' to "o", 'ఓ' to "o", 'ఔ' to "au",
    )
    private val SIGNS = mapOf(
        'ా' to "aa", 'ి' to "i", 'ీ' to "i", 'ు' to "u", 'ూ' to "u", 'ృ' to "ru",
        'ె' to "e", 'ే' to "e", 'ై' to "ai", 'ొ' to "o", 'ో' to "o", 'ౌ' to "au",
    )
    private val CONSONANTS = mapOf(
        'క' to "k", 'ఖ' to "kh", 'గ' to "g", 'ఘ' to "gh", 'ఙ' to "n",
        'చ' to "ch", 'ఛ' to "chh", 'జ' to "j", 'ఝ' to "jh", 'ఞ' to "n",
        'ట' to "t", 'ఠ' to "th", 'డ' to "d", 'ఢ' to "dh", 'ణ' to "n",
        'త' to "t", 'థ' to "th", 'ద' to "d", 'ధ' to "dh", 'న' to "n",
        'ప' to "p", 'ఫ' to "ph", 'బ' to "b", 'భ' to "bh", 'మ' to "m",
        'య' to "y", 'ర' to "r", 'ఱ' to "r", 'ల' to "l", 'ళ' to "l", 'వ' to "v",
        'శ' to "sh", 'ష' to "sh", 'స' to "s", 'హ' to "h",
    )
    private const val VIRAMA = '్'
    private const val ANUSVARA = 'ం'
    private const val VISARGA = 'ః'

    fun toLatin(s: String): String {
        if (s.none { it.code in 0x0C00..0x0C7F }) return s
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            val next = s.getOrNull(i + 1)
            when {
                ch in CONSONANTS -> {
                    out.append(CONSONANTS.getValue(ch))
                    when {
                        next == VIRAMA -> i++
                        next != null && next in SIGNS -> { out.append(SIGNS.getValue(next)); i++ }
                        else -> out.append('a')
                    }
                }
                ch in VOWELS -> out.append(VOWELS.getValue(ch))
                ch == ANUSVARA -> out.append(if (next != null && CONSONANTS[next]?.first() in setOf('p', 'b', 'm')) 'm' else 'n')
                ch == VISARGA -> out.append('h')
                else -> out.append(ch)
            }
            i++
        }
        return out.toString()
    }
}
