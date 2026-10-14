package com.englishtutor.data.tutor

/**
 * Minimal Hermes reply helpers for AET Telegram wait (tool cards vs answer).
 */
object AgentReplyParts {
    fun isOwnEcho(text: String): Boolean =
        text.matches(Regex("""Голосовое сообщение \(\d+ ms\)"""))

    fun containsAnswer(raw: String): Boolean {
        val text = raw.trim()
        if (text.isEmpty()) return false
        if (text.contains("```json", ignoreCase = true)) return true
        if (text.contains("\"titleRu\"") && text.contains("\"words\"")) return true
        return text.lines().any { line ->
            val t = line.trim()
            t.isNotEmpty() && !isToolLine(t) && !isSpeechHeading(t)
        }
    }

    fun isToolLine(line: String): Boolean {
        val text = line.trim()
        if (text.equals("shell", ignoreCase = true) ||
            text.equals("terminal", ignoreCase = true) ||
            text.startsWith("tool_") ||
            text.startsWith("Reading skill", ignoreCase = true)
        ) {
            return true
        }
        if (text.startsWith("Writing", ignoreCase = true) ||
            text.startsWith("Wrote", ignoreCase = true) ||
            text.startsWith("Appending", ignoreCase = true) ||
            text.startsWith("Creating", ignoreCase = true) ||
            text.startsWith("Reading ", ignoreCase = true) ||
            text.contains("/opt/data/cache/scratch", ignoreCase = true)
        ) {
            return true
        }
        if (text.startsWith("cd ") ||
            text.startsWith("which ") ||
            text.startsWith("grep ") ||
            text.startsWith("find ") ||
            text.startsWith("env ") ||
            text.startsWith("cat ") ||
            text.startsWith("pip ") ||
            text.startsWith("timeout ") ||
            text.startsWith("python3 ") ||
            text.startsWith("python ") ||
            text.startsWith("/opt/")
        ) {
            return true
        }
        return false
    }

    private fun isSpeechHeading(line: String): Boolean {
        val text = line.trim()
        if (text.equals("PC terminal", ignoreCase = true) ||
            text.equals("terminal", ignoreCase = true) ||
            text.equals("shell", ignoreCase = true) ||
            text.equals("PC", ignoreCase = true) ||
            isToolLine(text)
        ) {
            return true
        }
        val words = text.replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
        return words.isEmpty() ||
            words.equals("terminal", ignoreCase = true) ||
            words.equals("pc terminal", ignoreCase = true)
    }
}
