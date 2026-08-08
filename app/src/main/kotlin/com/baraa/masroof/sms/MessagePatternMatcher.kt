package com.baraa.masroof.sms

import com.baraa.masroof.data.db.MessagePatternStatus
import com.baraa.masroof.data.repository.MessagePattern

/** Checks whether a body matches an explicitly ignored canonical template. */
object MessagePatternMatcher {
    fun isIgnored(body: String?, patterns: List<MessagePattern>): Boolean =
        patterns
            .asSequence()
            .filter { it.definition.status == MessagePatternStatus.IGNORED }
            .any { pattern ->
                TemplateMatcher.matches(pattern.definition.templateText, body, pattern.anchors)
            }
}
