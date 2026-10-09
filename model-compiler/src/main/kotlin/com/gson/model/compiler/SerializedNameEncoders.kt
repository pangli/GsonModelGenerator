package com.gson.model.compiler

import com.gson.model.annotation.SerializedNameEncoder

internal object SerializedNameEncoders {
    fun load(className: String): SerializedNameEncoder {
        val clazz = findClass(className)
        if (!SerializedNameEncoder::class.java.isAssignableFrom(clazz)) {
            throw IllegalArgumentException("$className must implement SerializedNameEncoder")
        }
        val constructor = try {
            clazz.getDeclaredConstructor()
        } catch (_: NoSuchMethodException) {
            throw IllegalArgumentException("$className needs a public no-arg constructor")
        }
        constructor.isAccessible = true
        return constructor.newInstance() as SerializedNameEncoder
    }

    private fun findClass(className: String): Class<*> {
        val loaders = listOfNotNull(
            SerializedNameEncoders::class.java.classLoader,
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
            "cannot load SerializedNameEncoder $className. Put it on the ksp classpath.",
            last,
        )
    }
}
