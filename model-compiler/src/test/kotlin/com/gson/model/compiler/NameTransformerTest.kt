package com.gson.model.compiler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class NameTransformerTest {
    @Test
    fun base64UsesUrlAlphabetWithoutPadding() {
        val encoded = NameTransformer.wireName("phone", "base64", emptyMap(), "")
        assertEquals(
            Base64.getUrlEncoder().withoutPadding().encodeToString("phone".toByteArray()),
            encoded,
        )
        assertFalse(encoded.contains("="))
        assertFalse(encoded.contains("+"))
        assertFalse(encoded.contains("/"))
    }

    @Test
    fun reverseFlipsTheSemanticName() {
        assertEquals("enohp", NameTransformer.wireName("phone", "reverse", emptyMap(), ""))
    }

    @Test
    fun xorIsStableHex() {
        val once = NameTransformer.wireName("phone", "xor", emptyMap(), "bd")
        val twice = NameTransformer.wireName("phone", "xor", emptyMap(), "bd")
        assertEquals(once, twice)
        assertTrue(once.matches(Regex("[0-9a-f]+")))
    }

    @Test
    fun dictLooksUpTheSemanticName() {
        val dict = mapOf("phone" to "cowsunflower")
        assertEquals("cowsunflower", NameTransformer.wireName("phone", "dict", dict, ""))
    }

    @Test
    fun affixesBuildClassAndParameterNames() {
        assertEquals(
            "SparrowGetSmsRequest",
            NameTransformer.className("GetSms", "Sparrow", "Request"),
        )
        assertEquals("phoneBySparrow", NameTransformer.paramName("phone", "", "BySparrow"))
        assertEquals("mPhone", NameTransformer.paramName("phone", "m", ""))
    }

    @Test
    fun kotlinLiteralEscapesDollarAndQuote() {
        assertEquals(
            "\"a\\\"b\\\$c\"",
            NameTransformer.kotlinStringLiteral("a\"b\$c"),
        )
    }
}
