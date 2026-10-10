package com.gson.model.compiler

import org.junit.Assert.assertEquals
import org.junit.Test

class PathMappingJsonTest {
    @Test
    fun renderOrdersObjects() {
        val json = PathMappingJson.render(
            listOf(
                PathObjectMapping(
                    source = "com.example.Zed",
                    generated = "com.example.SparrowZed",
                    paths = listOf(
                        PathConstantMapping("Z_PATH", "z/path", "1/2"),
                    ),
                ),
                PathObjectMapping(
                    source = "com.example.ApiConstants",
                    generated = "com.example.SparrowApiConstants",
                    paths = listOf(
                        PathConstantMapping(
                            "GET_APP_CONFIG_PATH",
                            "api/app/ext/config/getApp",
                            "e/squill/nance/yew/lentil",
                        ),
                    ),
                ),
            ),
        )
        val expected = """
            {
              "com.example.SparrowApiConstants": {
                "source": "com.example.ApiConstants",
                "paths": [
                  { "name": "GET_APP_CONFIG_PATH", "semantic": "api/app/ext/config/getApp", "wire": "e/squill/nance/yew/lentil" }
                ]
              },
              "com.example.SparrowZed": {
                "source": "com.example.Zed",
                "paths": [
                  { "name": "Z_PATH", "semantic": "z/path", "wire": "1/2" }
                ]
              }
            }
        """.trimIndent() + "\n"
        assertEquals(expected, json)
    }
}
