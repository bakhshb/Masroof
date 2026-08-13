package com.baraa.masroof.presentation.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionShareTextTest {
    @Test
    fun field_joinsLabelAndValue() {
        assertEquals("المبلغ: 85.00 ر.س", TransactionShareText.field("المبلغ", "85.00 ر.س"))
    }

    @Test
    fun listRow_joinsVisibleColumns() {
        assertEquals(
            "3 أغسطس • كارفور • 85.00 ر.س • مشتريات",
            TransactionShareText.listRow("3 أغسطس", "كارفور", "85.00 ر.س", "مشتريات"),
        )
    }

    @Test
    fun document_skipsBlankOptionalFields() {
        val text = TransactionShareText.document(
            "مصروف — تفاصيل عملية",
            TransactionShareText.field("المبلغ", "85.00 ر.س"),
            null,
            "   ",
            TransactionShareText.field("النوع", "مشتريات"),
        )
        assertEquals(
            """
            مصروف — تفاصيل عملية
            المبلغ: 85.00 ر.س
            النوع: مشتريات
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun listDocument_includesMetaThenRows() {
        val text = TransactionShareText.listDocument(
            title = "مصروف — عمليات الفترة يوليو",
            metaLines = listOf("البحث: كارفور", "العدد: 2", ""),
            rows = listOf(
                "3 أغسطس • كارفور • 85.00 ر.س • مشتريات",
                "1 أغسطس • كارفور • 40.00 ر.س • مشتريات",
            ),
        )
        assertTrue(text.startsWith("مصروف — عمليات الفترة يوليو"))
        assertTrue(text.contains("البحث: كارفور"))
        assertTrue(text.contains("العدد: 2"))
        assertTrue(text.contains("3 أغسطس • كارفور"))
        assertFalse(text.contains("\n\n\n"))
    }
}
