package com.simpleledger.app.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatementSharerTest {
    @Test
    fun `indian mobile numbers become a WhatsApp chat id`() {
        assertEquals("919876543210@s.whatsapp.net", StatementSharer.whatsAppId("9876543210"))
        assertEquals("919876543210@s.whatsapp.net", StatementSharer.whatsAppId("+91 98765 43210"))
        assertEquals("919876543210@s.whatsapp.net", StatementSharer.whatsAppId("098765-43210"))
        assertEquals("919876543210@s.whatsapp.net", StatementSharer.whatsAppId("91-9876543210"))
    }

    @Test
    fun `missing or odd numbers give no chat id`() {
        assertNull(StatementSharer.whatsAppId(null))
        assertNull(StatementSharer.whatsAppId(""))
        assertNull(StatementSharer.whatsAppId("12345"))
    }
}
