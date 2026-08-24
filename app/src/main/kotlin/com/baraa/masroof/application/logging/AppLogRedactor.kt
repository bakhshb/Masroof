package com.baraa.masroof.application.logging

object AppLogRedactor {
    private val tokenPatterns = listOf(
        Regex("""ghp_[A-Za-z0-9]{20,}"""),
        Regex("""github_pat_[A-Za-z0-9_]{20,}"""),
        Regex("""gho_[A-Za-z0-9]{20,}"""),
        Regex("""(?i)(authorization:\s*)(token|bearer)\s+\S+"""),
    )

    fun redact(message: String): String {
        var sanitized = message
        tokenPatterns.forEach { pattern ->
            sanitized = sanitized.replace(pattern) { match ->
                when {
                    match.groups.size >= 2 && match.groups[1] != null ->
                        "${match.groups[1]!!.value}${match.groups[2]!!.value} [REDACTED]"
                    else -> "[REDACTED]"
                }
            }
        }
        return sanitized
    }
}
