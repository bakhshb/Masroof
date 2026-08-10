package com.baraa.masroof.parsing.fixtures

import kotlinx.serialization.json.Json
import java.io.File

/**
 * Loads Bank AlJazira JSON fixtures from test resources.
 */
object AlJaziraFixtureLoader {
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
    }

    fun loadAllFromClasspath(): List<AlJaziraFixture> {
        val root = resolveTestdataRoot()
        val files = root.walkTopDown()
            .filter { it.isFile && it.extension == "json" && it.parentFile?.name != "reference" }
            .sortedBy { it.path }
            .toList()
        require(files.isNotEmpty()) { "No AlJazira fixtures found under $root" }
        return files.map { file ->
            json.decodeFromString(AlJaziraFixture.serializer(), file.readText())
        }
    }

    fun resolveTestdataRoot(): File {
        val candidates = listOf(
            File("src/test/resources/testdata/bank_aljazira"),
            File("app/src/test/resources/testdata/bank_aljazira"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Could not locate testdata/bank_aljazira")
    }
}
