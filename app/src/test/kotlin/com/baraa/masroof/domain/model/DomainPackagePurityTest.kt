package com.baraa.masroof.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the rule that domain, core, parsing, and bank packages stay free of
 * Android / Room / Compose dependencies.
 */
class DomainPackagePurityTest {

    @Test
    fun domainCoreParsingBankSources_doNotImportAndroidFrameworks() {
        val roots = listOf(
            File("src/main/kotlin/com/baraa/masroof/domain"),
            File("src/main/kotlin/com/baraa/masroof/core"),
            File("src/main/kotlin/com/baraa/masroof/parsing"),
            File("src/main/kotlin/com/baraa/masroof/bank"),
        )
        val forbidden = listOf(
            "import android.",
            "import androidx.",
            "import androidx.room",
            "import androidx.compose",
        )

        val kotlinFiles = roots.flatMap { root ->
            if (!root.exists()) emptyList()
            else root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }
        assertTrue("Expected domain/core/parsing/bank Kotlin sources", kotlinFiles.isNotEmpty())

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

    @Test
    fun domainSources_doNotImportHigherLayers() {
        val domainRoot = File("src/main/kotlin/com/baraa/masroof/domain")
        assertTrue(domainRoot.isDirectory)
        val forbidden = listOf(
            "import com.baraa.masroof.parsing",
            "import com.baraa.masroof.bank",
            "import com.baraa.masroof.data",
            "import com.baraa.masroof.presentation",
            "import androidx.room",
            "import android.",
        )
        domainRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val text = file.readText()
                forbidden.forEach { needle ->
                    assertFalse(
                        "${file.path} must not contain '$needle' (domain must not depend on higher layers)",
                        text.contains(needle),
                    )
                }
            }
    }
}
