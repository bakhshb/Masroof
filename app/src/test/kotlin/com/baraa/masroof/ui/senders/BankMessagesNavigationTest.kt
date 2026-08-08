package com.baraa.masroof.ui.senders

import com.baraa.masroof.ui.settings.SettingsDestinations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BankMessagesNavigationTest {
    @Test
    fun senderRouteIncludesProfileId() {
        assertEquals(
            "settings/bank_messages/sender/42",
            SettingsDestinations.bankMessagesSender(42L),
        )
    }

    @Test
    fun templateRouteIncludesPatternId() {
        assertEquals(
            "settings/bank_messages/template/7",
            SettingsDestinations.bankMessagesTemplate(7L),
        )
    }

    @Test
    fun hubDoesNotExpandSenderInline() {
        val source = java.io.File(
            "app/src/main/kotlin/com/baraa/masroof/ui/senders/BankMessagesScreen.kt",
        ).takeIf { it.exists() }?.readText()
            ?: java.io.File(
                "/home/debian/projects/Masroof/app/src/main/kotlin/com/baraa/masroof/ui/senders/BankMessagesScreen.kt",
            ).readText()
        assertTrue(source.contains("onSenderClick"))
        // Hub must navigate, not set selectedProfile for inline expansion.
        assertTrue(!source.contains("selectedProfile ="))
    }

    @Test
    fun templateEditorIsFullScreenNotDialog() {
        val editor = java.io.File(
            "/home/debian/projects/Masroof/app/src/main/kotlin/com/baraa/masroof/ui/senders/TemplateEditorScreen.kt",
        ).readText()
        assertTrue(editor.contains("MasroofTopAppBar"))
        assertTrue(editor.contains("تعديل النمط"))
        assertTrue(!editor.contains("AlertDialog"))
    }
}
