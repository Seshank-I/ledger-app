package com.simpleledger.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

object Money {
    /** ₹1,00,000 style, with paise only when there are any: ₹500, ₹500.50. Always positive. */
    fun format(paise: Long): String {
        val a = abs(paise)
        val rupees = groupIndian(a / 100)
        val p = a % 100
        return if (p == 0L) "₹$rupees" else "₹$rupees.${p.toString().padStart(2, '0')}"
    }

    fun groupIndian(n: Long): String {
        val s = n.toString()
        if (s.length <= 3) return s
        val last3 = s.takeLast(3)
        var rest = s.dropLast(3)
        val parts = ArrayList<String>()
        while (rest.length > 2) {
            parts.add(0, rest.takeLast(2))
            rest = rest.dropLast(2)
        }
        if (rest.isNotEmpty()) parts.add(0, rest)
        return parts.joinToString(",") + "," + last3
    }

    /** Whole rupees typed on the number pad, converted to paise. */
    fun rupeesToPaise(digits: String): Long = if (digits.isEmpty()) 0 else digits.toLong() * 100
}

object Rates {
    /** 200 → "2", 150 → "1.5" */
    fun plain(bp: Int): String = BigDecimal(bp).movePointLeft(2).stripTrailingZeros().toPlainString()

    /** 200 → "24" (percent a year) */
    fun yearlyPercent(bp: Int): String = BigDecimal(bp.toLong() * 12).movePointLeft(2).stripTrailingZeros().toPlainString()

    /** "2" → 200. Accepts 0.01 to 10 rupees per ₹100 per month. */
    fun parse(text: String): Int? {
        val v = text.trim().toBigDecimalOrNull() ?: return null
        val bp = v.movePointRight(2).setScale(0, RoundingMode.HALF_UP)
        if (bp < BigDecimal.ONE || bp > BigDecimal(1000)) return null
        return bp.toInt()
    }
}

object Dates {
    private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val DAY_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH)

    fun format(d: LocalDate): String = d.format(DAY)
    fun formatIso(iso: String): String = format(LocalDate.parse(iso))
    fun formatMillis(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DAY_TIME)
    fun dateOfMillis(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
}
