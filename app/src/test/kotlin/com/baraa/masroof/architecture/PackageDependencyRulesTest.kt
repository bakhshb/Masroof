package com.baraa.masroof.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Enforces the package-level dependency policy while Masroof remains a single Gradle module.
 *
 * Temporary exceptions represent existing dependency debt and must be removed by the roadmap
 * PR named alongside each exception. New exceptions are not permitted.
 */
class PackageDependencyRulesTest {

    @Test
    fun core_hasNoMasroofDependencies() {
        assertPackagesDoNotImport(
            packages = listOf("core"),
            forbiddenImports = listOf("import com.baraa.masroof."),
        )
    }

    @Test
    fun parsing_doesNotDependOnOuterLayers() {
        assertPackagesDoNotImport(
            packages = listOf("parsing"),
            forbiddenImports = outerLayerImports,
        )
    }

    @Test
    fun bank_doesNotDependOnApplicationOrInfrastructure() {
        assertPackagesDoNotImport(
            packages = listOf("bank"),
            forbiddenImports = listOf(
                "import com.baraa.masroof.application.",
                "import com.baraa.masroof.data.",
                "import com.baraa.masroof.presentation.",
                "import com.baraa.masroof.sms.",
            ),
        )
    }

    @Test
    fun data_doesNotDependOnUiOrSms() {
        assertPackagesDoNotImport(
            packages = listOf("data"),
            forbiddenImports = listOf(
                "import com.baraa.masroof.presentation.",
                "import com.baraa.masroof.sms.",
            ),
        )
    }

    @Test
    fun application_doesNotDependOnPresentationOutsideTemporaryExceptions() {
        assertPackagesDoNotImport(
            packages = listOf("application"),
            forbiddenImports = listOf("import com.baraa.masroof.presentation."),
            allowedFiles = setOf(
                // Bootstrap exception — composition root may wire all layers.
                "application/AppContainer.kt",
            ),
        )
    }

    @Test
    fun sms_doesNotOrchestrateApplicationOutsideTemporaryExceptions() {
        assertPackagesDoNotImport(
            packages = listOf("sms"),
            forbiddenImports = listOf("import com.baraa.masroof.application."),
            allowedFiles = setOf(
                // PR 4 — move ProcessRawSms orchestration to application.
                "sms/ingestion/SmsIngestionService.kt",
                // PR 4 — logging moves behind the application intake boundary.
                "sms/scanner/HistoricalSmsScanner.kt",
                "sms/receiver/IncomingSmsReceiver.kt",
            ),
        )
    }

    @Test
    fun presentation_doesNotDependOnDataOrSmsOutsideTemporaryExceptions() {
        assertPackagesDoNotImport(
            packages = listOf("presentation"),
            forbiddenImports = listOf(
                "import com.baraa.masroof.data.",
                "import com.baraa.masroof.sms.",
            ),
            allowedFiles = setOf(
                // PR 8 — move locale bootstrap behind an application boundary.
                "presentation/locale/AppLocaleContext.kt",
            ),
        )
    }

    @Test
    fun presentation_doesNotUseDomainServicesOrPortsOutsideTemporaryExceptions() {
        assertFilesDoNotImport(
            files = kotlinFilesIn("presentation").filter { it.name.endsWith("ViewModel.kt") },
            forbiddenImports = listOf(
                "import com.baraa.masroof.domain.ownership.",
                "import com.baraa.masroof.domain.period.",
                "import com.baraa.masroof.domain.repository.",
                "import com.baraa.masroof.domain.rules.",
            ),
            allowedFiles = setOf(
                // PR 7 — review/settings/registry workflow application facades.
                "presentation/review/ReviewViewModel.kt",
                "presentation/settings/SettingsViewModel.kt",
                // PR 8 — dashboard/onboarding/notification workflow facades.
                "presentation/dashboard/DashboardViewModel.kt",
                "presentation/notification/NotificationCenterViewModel.kt",
                "presentation/onboarding/OnboardingViewModel.kt",
            ),
        )
    }

    private fun assertPackagesDoNotImport(
        packages: List<String>,
        forbiddenImports: List<String>,
        allowedFiles: Set<String> = emptySet(),
    ) = assertFilesDoNotImport(
        files = packages.flatMap(::kotlinFilesIn),
        forbiddenImports = forbiddenImports,
        allowedFiles = allowedFiles,
    )

    private fun assertFilesDoNotImport(
        files: List<File>,
        forbiddenImports: List<String>,
        allowedFiles: Set<String> = emptySet(),
    ) {
        assertTrue("Expected Kotlin sources", files.isNotEmpty())
        files.forEach { file ->
            val relativePath = file.relativeTo(sourceRoot).invariantSeparatorsPath
            if (relativePath in allowedFiles) return@forEach
            val source = file.readText()
            forbiddenImports.forEach { forbiddenImport ->
                assertFalse(
                    "$relativePath must not contain '$forbiddenImport'. " +
                        "Add no exceptions; remove the documented temporary exception in its roadmap PR.",
                    source.contains(forbiddenImport),
                )
            }
        }
    }

    private fun kotlinFilesIn(packageName: String): List<File> {
        val root = File(sourceRoot, packageName)
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private companion object {
        val sourceRoot = File("src/main/kotlin/com/baraa/masroof")

        val outerLayerImports = listOf(
            "import com.baraa.masroof.application.",
            "import com.baraa.masroof.bank.",
            "import com.baraa.masroof.data.",
            "import com.baraa.masroof.presentation.",
            "import com.baraa.masroof.sms.",
        )
    }
}
