package com.baraa.masroof.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class OnDeviceModelDownloaderTest {
    @Test
    fun sanitizeFileNameKeepsTaskExtension() {
        assertEquals(
            "gemma3-1b-it-int4.task",
            OnDeviceModelDownloader.sanitizeFileName("../gemma3-1b-it-int4.task"),
        )
        assertEquals(
            "model_name.litertlm",
            OnDeviceModelDownloader.sanitizeFileName("model name.litertlm"),
        )
    }
}
