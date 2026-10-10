package com.wiregen.compiler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KotlinSourceTest {
    private val source = KotlinSource(
        """
        package sample

        import com.example.Parent
        import com.example.Names as AppNames

        abstract class DemoLogin(
            val phone: String,
            val children: List<DemoChild>? = null,
            val clientName: String = AppNames.appName,
            val installed: String = DateUtils.format(
                Names.value,
            ),
            val raw: String = "a,b)",
        ) : Parent() {
            val extra: Long = System.currentTimeMillis()
        }

        abstract class HeaderOnly(
            val phone: String = "x",
        ) : Parent()

        interface NextOne {
            val phone: String
        }

        interface Plain {
            val phone: String
        }
        """.trimIndent(),
    )

    @Test
    fun readsExplicitImports() {
        assertEquals(
            mapOf("Parent" to "com.example.Parent", "AppNames" to "com.example.Names"),
            source.explicitImports(),
        )
    }

    @Test
    fun readsSupertypeCall() {
        assertEquals("Parent()", source.supertypeClause("DemoLogin"))
        assertEquals("Parent()", source.supertypeClause("HeaderOnly"))
        assertEquals("\"x\"", source.memberDefault("HeaderOnly", "phone"))
        assertNull(source.supertypeClause("Plain"))
    }

    @Test
    fun readsDefaultsIncludingNestedCallsAndStrings() {
        assertNull(source.memberDefault("DemoLogin", "phone"))
        assertEquals("null", source.memberDefault("DemoLogin", "children"))
        assertEquals("AppNames.appName", source.memberDefault("DemoLogin", "clientName"))
        assertEquals("DateUtils.format(\n        Names.value,\n    )", source.memberDefault("DemoLogin", "installed"))
        assertEquals("\"a,b)\"", source.memberDefault("DemoLogin", "raw"))
        assertEquals("System.currentTimeMillis()", source.memberDefault("DemoLogin", "extra"))
        assertNull(source.memberDefault("Plain", "phone"))
    }

    @Test
    fun identifiersIgnoreMemberAccess() {
        assertEquals(
            setOf("AppNames"),
            source.simpleIdentifiers("AppNames.appName"),
        )
        assertEquals(
            setOf("DateUtils", "Names"),
            source.simpleIdentifiers("DateUtils.format(\n        Names.value,\n    )"),
        )
    }
}
