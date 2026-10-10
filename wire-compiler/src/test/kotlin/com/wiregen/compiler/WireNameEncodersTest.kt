package com.wiregen.compiler

import com.wiregen.annotation.WireNameEncoder
import org.junit.Assert.assertEquals
import org.junit.Test

class PrefixEncoder : WireNameEncoder {
    override fun encode(semantic: String): String = "k_$semantic"
}

class WireNameEncodersTest {
    @Test
    fun loadsAnEncoderFromTheClasspath() {
        val encoder = WireNameEncoders.load(PrefixEncoder::class.java.name)
        assertEquals("k_phone", encoder.encode("phone"))
    }
}
