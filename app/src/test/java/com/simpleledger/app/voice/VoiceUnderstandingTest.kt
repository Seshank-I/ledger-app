package com.simpleledger.app.voice

import com.simpleledger.app.data.Direction.GAVE
import com.simpleledger.app.data.Direction.GOT
import com.simpleledger.app.data.PersonEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric only because parseAnswer uses Android's org.json.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceUnderstandingTest {
    private val people = listOf(PersonEntity("r", "Ramesh", null, false, 0), PersonEntity("s", "Sita", null, false, 0))

    @Test
    fun `no model answer uses the rules`() {
        val u = VoiceUnderstanding.merge("I gave Ramesh 500", people, null, null)
        assertFalse(u.usedAi)
        assertEquals(VoiceCommand.Record("r", emptyList(), GAVE, 50_000L), u.command)
    }

    @Test
    fun `model understands phrasing the rules miss`() {
        // "handed over" is not a rule phrase, so the rules alone cannot tell the direction.
        val ai = AiAnswer("record", "Sita", "gave", 1500.0)
        val u = VoiceUnderstanding.merge("handed over fifteen hundred to Sita", people, null, ai)
        assertTrue(u.usedAi)
        assertEquals(VoiceCommand.Record("s", emptyList(), GAVE, 150_000L), u.command)
    }

    @Test
    fun `a name the model invents is ignored`() {
        val ai = AiAnswer("record", "Mohan", "got", 200.0)
        val u = VoiceUnderstanding.merge("got 200 from Mohan", people, null, ai)
        assertNull((u.command as VoiceCommand.Record).personId)
        assertEquals(GOT, (u.command as VoiceCommand.Record).direction)
    }

    @Test
    fun `rules fill what the model left out`() {
        val ai = AiAnswer("record", "Ramesh", "", 0.0)
        val u = VoiceUnderstanding.merge("I gave Ramesh 500", people, null, ai)
        assertEquals(VoiceCommand.Record("r", emptyList(), GAVE, 50_000L), u.command)
    }

    @Test
    fun `balance question`() {
        val ai = AiAnswer("balance", "Ramesh", "", 0.0)
        assertEquals(VoiceCommand.AskBalance("r", emptyList()), VoiceUnderstanding.merge("what's Ramesh's due", people, null, ai).command)
    }

    @Test
    fun `unknown from the model falls back to the rules`() {
        val u = VoiceUnderstanding.merge("got 2000 from Sita", people, null, AiAnswer("unknown", "", "", 0.0))
        assertFalse(u.usedAi)
        assertEquals(VoiceCommand.Record("s", emptyList(), GOT, 200_000L), u.command)
    }

    @Test
    fun `model reply with extra words is still read`() {
        val a = OnDeviceAi.parseAnswer("Sure! ```json\n{\"intent\":\"record\",\"person\":\"Sita\",\"direction\":\"got\",\"amount\":2000}\n```")
        assertEquals(AiAnswer("record", "Sita", "got", 2000.0), a)
        assertNull(OnDeviceAi.parseAnswer("I could not understand"))
    }
}
