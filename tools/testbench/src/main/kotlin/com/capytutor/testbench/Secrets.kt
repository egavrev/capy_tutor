package com.capytutor.testbench

import java.io.File
import java.util.Properties

/** Finds the Groq key: GROQ_API_KEY, else secrets.properties in this folder or a parent. Never printed. */
object Secrets {
    fun groqKey(): String? {
        System.getenv("GROQ_API_KEY")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        var dir: File? = File("").absoluteFile
        repeat(5) {
            val f = File(dir, "secrets.properties")
            if (f.isFile) {
                val p = Properties().apply { f.reader(Charsets.UTF_8).use { load(it) } }
                p.getProperty("GROQ_API_KEY")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            }
            dir = dir?.parentFile
        }
        return null
    }
}
