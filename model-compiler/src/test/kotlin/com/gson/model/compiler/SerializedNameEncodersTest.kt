package com.gson.model.compiler

import com.gson.model.annotation.SerializedNameEncoder
import org.junit.Assert.assertEquals
import org.junit.Test

class PrefixEncoder : SerializedNameEncoder {
    override fun encode(semantic: String): String = "k_$semantic"
}

class SerializedNameEncodersTest {
    @Test
    fun loadsAnEncoderFromTheClasspath() {
        val encoder = SerializedNameEncoders.load(PrefixEncoder::class.java.name)
        assertEquals("k_phone", encoder.encode("phone"))
    }
}
