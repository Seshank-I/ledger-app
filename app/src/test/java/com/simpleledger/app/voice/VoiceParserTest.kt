package com.simpleledger.app.voice

import com.simpleledger.app.data.Direction.GAVE
import com.simpleledger.app.data.Direction.GOT
import com.simpleledger.app.data.PersonEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceParserTest {
    private fun person(id: String, name: String) = PersonEntity(id, name, null, false, 0)

    private val people = listOf(
        person("ramesh", "Ramesh"),
        person("ram", "Ram"),
        person("sita", "Seetha"),
        person("venkat", "Venkata Subrahmanya Rao"),
        person("lakshmi", "లక్ష్మి"),
    )

    private fun record(text: String, current: String? = null) = VoiceParser.parse(text, people, current) as VoiceCommand.Record

    @Test
    fun `english - gave`() {
        val r = record("I gave Ramesh 500")
        assertEquals("ramesh", r.personId); assertEquals(GAVE, r.direction); assertEquals(50_000L, r.amountPaise)
    }

    @Test
    fun `english - got from`() {
        val r = record("got 2000 from Sita")
        assertEquals("sita", r.personId); assertEquals(GOT, r.direction); assertEquals(200_000L, r.amountPaise)
    }

    @Test
    fun `english - person gave me means got`() {
        val r = record("Ramesh gave me 1,500 rupees")
        assertEquals("ramesh", r.personId); assertEquals(GOT, r.direction); assertEquals(150_000L, r.amountPaise)
    }

    @Test
    fun `english - I returned money means gave`() {
        assertEquals(GAVE, record("I returned 300 to Ram").direction)
        assertEquals(GOT, record("Ram returned 300").direction)
    }

    @Test
    fun `english - borrowed and lent`() {
        assertEquals(GOT, record("borrowed 5000 from Venkata").direction)
        assertEquals(GAVE, record("lent Ramesh 5000").direction)
    }

    @Test
    fun `english number words`() {
        assertEquals(250_000L, record("I gave Ramesh two thousand five hundred").amountPaise)
        assertEquals(10_000_000L, record("gave Ramesh one lakh").amountPaise)
        assertEquals(15_000_000L, record("gave Ramesh 1.5 lakh").amountPaise)
        assertEquals(100_000L, record("gave Ramesh a thousand").amountPaise)
        assertEquals(200_000L, record("gave Ramesh 2k").amountPaise)
        assertEquals(50_000L, record("gave Ramesh ₹500").amountPaise)
        assertEquals(50_000L, record("gave Ramesh 500rs").amountPaise)
        assertEquals(50_000_000_00L, record("gave Ramesh 5 crore").amountPaise)
    }

    @Test
    fun `spelling differences still match`() {
        assertEquals("sita", record("I gave Sita 100").personId)
        assertEquals("sita", record("I gave Seeta 100").personId)
        assertEquals("venkat", record("I gave Venkata 100").personId)
    }

    @Test
    fun `longer name wins over a shorter one it starts with`() {
        assertEquals("ramesh", record("I gave Ramesh 100").personId)
        assertEquals("ram", record("I gave Ram 100").personId)
    }

    @Test
    fun `no name uses the person whose screen is open`() {
        val r = record("I gave 500", current = "sita")
        assertEquals("sita", r.personId); assertEquals(GAVE, r.direction); assertEquals(50_000L, r.amountPaise)
    }

    @Test
    fun `missing pieces stay null`() {
        val r = record("Ramesh 500")
        assertEquals("ramesh", r.personId); assertNull(r.direction); assertEquals(50_000L, r.amountPaise)
        val noAmount = record("I gave Ramesh")
        assertNull(noAmount.amountPaise)
    }

    @Test
    fun `unknown name is not guessed`() {
        val r = record("I gave Mohan 500")
        assertNull(r.personId); assertTrue(r.candidates.isEmpty())
    }

    @Test
    fun `balance questions`() {
        val q = VoiceParser.parse("How much does Ramesh owe me", people)
        assertEquals(VoiceCommand.AskBalance("ramesh", emptyList()), q)
        val te = VoiceParser.parse("రమేష్ ఎంత బాకీ", people)
        assertEquals(VoiceCommand.AskBalance("ramesh", emptyList()), te)
    }

    @Test
    fun `nonsense is not understood`() {
        assertEquals(VoiceCommand.NotUnderstood, VoiceParser.parse("what is the weather", people))
        assertEquals(VoiceCommand.NotUnderstood, VoiceParser.parse("   ", people))
    }

    @Test
    fun `telugu - I gave`() {
        val r = record("రమేష్ కి 500 ఇచ్చాను")
        assertEquals("ramesh", r.personId); assertEquals(GAVE, r.direction); assertEquals(50_000L, r.amountPaise)
    }

    @Test
    fun `telugu - name with attached suffix`() {
        val r = record("రమేష్కి ఐదు వందలు ఇచ్చాను")
        assertEquals("ramesh", r.personId); assertEquals(GAVE, r.direction); assertEquals(50_000L, r.amountPaise)
    }

    @Test
    fun `telugu - took from means got`() {
        val r = record("సీత నుండి రెండు వేలు తీసుకున్నాను")
        assertEquals("sita", r.personId); assertEquals(GOT, r.direction); assertEquals(200_000L, r.amountPaise)
    }

    @Test
    fun `telugu - he gave me means got`() {
        val r = record("రమేష్ నాకు 1000 ఇచ్చాడు")
        assertEquals(GOT, r.direction); assertEquals(100_000L, r.amountPaise)
    }

    @Test
    fun `telugu - I gave back means gave`() {
        assertEquals(GAVE, record("రాము కి తిరిగి 300 ఇచ్చాను").direction)
    }

    @Test
    fun `telugu number words`() {
        assertEquals(250_000L, record("రమేష్ కి రెండు వేల ఐదు వందలు ఇచ్చాను").amountPaise)
        assertEquals(50_000L, record("రమేష్ కి ఐదొందలు ఇచ్చాను").amountPaise)
        assertEquals(10_000_000L, record("రమేష్ కి ఒక లక్ష ఇచ్చాను").amountPaise)
        assertEquals(50_000L, record("రమేష్ కి ౫౦౦ ఇచ్చాను").amountPaise)
        assertEquals(50_000L, record("రమేష్ కి 500 రూపాయలు ఇచ్చాను").amountPaise)
    }

    @Test
    fun `name saved in Telugu matches English speech and back`() {
        assertEquals("lakshmi", record("I gave Lakshmi 100").personId)
        assertEquals("ramesh", record("రమేష్ కి 100 ఇచ్చాను").personId)
    }

    @Test
    fun `transliteration basics`() {
        assertEquals("ramesh", Transliterate.toLatin("రమేష్"))
        assertEquals("sita", Transliterate.toLatin("సీత"))
        assertEquals(Phonetic.key("Seetha"), Phonetic.key("Sita"))
    }
}
