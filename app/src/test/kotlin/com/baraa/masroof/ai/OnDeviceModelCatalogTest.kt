package com.baraa.masroof.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OnDeviceModelCatalogTest {

    @Test
    fun catalogHasRecommendedGemma1b() {
        val recommended = OnDeviceModelCatalog.options.filter { it.recommended }
        assertEquals(1, recommended.size)
        assertEquals("gemma3-1b-it-int4.task", recommended.first().fileName)
        assertTrue(recommended.first().pageUrl.contains("huggingface.co"))
    }

    @Test
    fun listInstalledFindsTaskFilesOnly() {
        val root = createTempDir(prefix = "masroof-models-")
        try {
            val models = OnDeviceModelStore.modelsDirectory(root)
            File(models, "gemma3-1b-it-int4.task").writeText("fake-model-bytes")
            File(models, "notes.txt").writeText("ignore")
            val installed = OnDeviceModelStore.listInstalled(root)
            assertEquals(1, installed.size)
            assertEquals("gemma3-1b-it-int4.task", installed.first().fileName)
            assertTrue(OnDeviceModelStore.isPresent(installed.first().absolutePath))
            assertEquals(0, OnDeviceModelStore.listInstalled(root).count { it.fileName.endsWith(".txt") })
        } finally {
            root.deleteRecursively()
        }
    }
}
