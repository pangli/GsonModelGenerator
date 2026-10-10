package com.wiregen.compiler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WireMappingJsonTest {
    @Test
    fun renderOrdersClassesAndEscapesStrings() {
        val json = WireMappingJson.render(
            listOf(
                WireClassMapping(
                    source = "com.example.Zed",
                    generated = "com.example.SparrowZed",
                    fields = listOf(
                        WireFieldMapping("tag", "tagBySparrow", "client\"Tag"),
                    ),
                ),
                WireClassMapping(
                    source = "com.example.Demo",
                    generated = "com.example.SparrowDemo",
                    fields = listOf(
                        WireFieldMapping("phone", "phoneBySparrow", "cGhvbmU"),
                        WireFieldMapping("name", "nameBySparrow", "bmFtZQ"),
                    ),
                ),
            ),
        )
        val expected = """
            {
              "com.example.SparrowDemo": {
                "source": "com.example.Demo",
                "fields": [
                  { "semantic": "phone", "param": "phoneBySparrow", "wire": "cGhvbmU" },
                  { "semantic": "name", "param": "nameBySparrow", "wire": "bmFtZQ" }
                ]
              },
              "com.example.SparrowZed": {
                "source": "com.example.Zed",
                "fields": [
                  { "semantic": "tag", "param": "tagBySparrow", "wire": "client\"Tag" }
                ]
              }
            }
        """.trimIndent() + "\n"
        assertEquals(expected, json)
        assertTrue(json.contains("\\\"Tag\""))
    }

    @Test
    fun quoteEscapesControls() {
        assertEquals("\"a\\\\b\\\"c\\n\"", WireMappingJson.quote("a\\b\"c\n"))
    }
}
