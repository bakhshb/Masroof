package com.baraa.masroof.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the P1 rule that domain and core money packages stay free of Android /
 * Room / Compose dependencies.
 */
class DomainPackagePurityTest {

    @Test
    fun domainAndCoreMoneySources_doNotImportAndroidFrameworks() {
        val roots = listOf(
            File("src/main/kotlin/com/baraa/masroof/domain"),
            File("src/main/kotlin/com/baraa/masroof/core"),
            File("src/main/kotlin/com/baraa/masroof/parsing"),
        )
        val forbidden = listOf(
            "import android.",
            "import androidx.",
            "import androidx.room",
            "import androidx.compose",
        )

        val kotlinFiles = roots.flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }
        assertTrue("Expected domain/core Kotlin sources", kotlinFiles.isNotEmpty())

        kotlinFiles.forEach { file ->
            val text = file.readText()
            forbidden.forEach { needle ->
                assertFalse(
                    "${file.path} must not contain '$needle'",
                    text.contains(needle),
                )
            }
        }
    }
}
