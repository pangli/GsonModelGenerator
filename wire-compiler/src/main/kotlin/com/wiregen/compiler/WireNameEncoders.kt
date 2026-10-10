package com.wiregen.compiler

import com.wiregen.annotation.WireNameEncoder

internal object WireNameEncoders {
    fun load(className: String): WireNameEncoder {
        val clazz = findClass(className)
        if (!WireNameEncoder::class.java.isAssignableFrom(clazz)) {
            throw IllegalArgumentException("$className must implement WireNameEncoder")
        }
        val constructor = try {
            clazz.getDeclaredConstructor()
        } catch (_: NoSuchMethodException) {
            throw IllegalArgumentException("$className needs a public no-arg constructor")
        }
        constructor.isAccessible = true
        return constructor.newInstance() as WireNameEncoder
    }

    private fun findClass(className: String): Class<*> {
        val loaders = listOfNotNull(
            WireNameEncoders::class.java.classLoader,
            Thread.currentThread().contextClassLoader,
        ).distinct()
        var last: ClassNotFoundException? = null
        loaders.forEach { loader ->
            try {
                return Class.forName(className, true, loader)
            } catch (error: ClassNotFoundException) {
                last = error
            }
        }
        throw IllegalArgumentException(
            "cannot load WireNameEncoder $className. Put it on the ksp classpath.",
            last,
        )
    }
}
