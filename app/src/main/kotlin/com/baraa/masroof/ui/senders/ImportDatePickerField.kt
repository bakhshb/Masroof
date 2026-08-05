package com.baraa.masroof.ui.senders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Material 3-backed date picker field. The field is **read-only** — the
 * user opens the calendar dialog by tapping on it. Internally stores the
 * picked value as [LocalDate]; the selected widget millis is mapped back
 * via the device timezone so day boundaries are stable across RTL & locales.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarDateField(
    label: String,
    selected: LocalDate,
    onSelected: (LocalDate) -> Unit,
    enabled: Boolean = true,
    isStart: Boolean = false,
    rangeEnd: LocalDate? = null,
    rangeStart: LocalDate? = null,
    modifier: Modifier = Modifier,
) {
    var dialogVisible by remember { mutableStateOf(false) }
    val locale = Locale("ar")
    val displayFormatter = remember(locale) { DateTimeFormatter.ofPattern("d MMMM yyyy", locale) }
    val displayValue = selected.format(displayFormatter)

    // OutlinedTextField is read-only; the underlying clickable area is the
    // whole field. We rely on OutlinedTextField's enabled=false to disable
    // typing, then enable the field via the icon click to open the dialog.
    Box(modifier = modifier.fillMaxWidth().clickable(enabled = enabled) { dialogVisible = true }) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = { /* no-op: read-only */ },
            enabled = enabled,
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Filled.CalendarMonth,
                    contentDescription = "افتح التقويم",
                    modifier = Modifier.size(28.dp).clickable(enabled = enabled) { dialogVisible = true },
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (dialogVisible) {
        val tz = remember { ZoneId.systemDefault() }
        val selectedMillis = remember(selected, tz) {
            selected.atStartOfDay(tz).toInstant().toEpochMilli()
        }
        val constraints = buildConstraints(isStart, rangeEnd, rangeStart, selectedMillis, tz)
        val state = rememberDatePickerState(
            initialSelectedDateMillis = selectedMillis,
            yearRange = 2010..2100,
            selectableDates = constraints,
        )
        DatePickerDialog(
            onDismissRequest = { dialogVisible = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        // Map back via the device timezone so the user sees the
                        // calendar day they actually picked.
                        val picked = Instant.ofEpochMilli(millis).atZone(tz).toLocalDate()
                        onSelected(picked)
                    }
                    dialogVisible = false
                }) { Text("موافق") }
            },
            dismissButton = {
                TextButton(onClick = { dialogVisible = false }) { Text("إلغاء") }
            },
        ) {
            DatePicker(state = state, showModeToggle = false)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private fun buildConstraints(
    isStart: Boolean,
    rangeEnd: LocalDate?,
    rangeStart: LocalDate?,
    selectedMillis: Long,
    tz: ZoneId,
): androidx.compose.material3.SelectableDates = object : androidx.compose.material3.SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
        val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneId.of("UTC")).toLocalDate()
        val start = rangeStart?.atStartOfDay(tz)?.toInstant()?.toEpochMilli() ?: Long.MIN_VALUE
        val end = rangeEnd?.atStartOfDay(tz)?.toInstant()?.toEpochMilli() ?: Long.MAX_VALUE
        return when {
            isStart && rangeEnd != null -> utcTimeMillis <= end
            !isStart && rangeStart != null -> utcTimeMillis >= start
            else -> true
        }
    }
    override fun isSelectableYear(year: Int): Boolean = year in 2010..2100
}

/**
 * Compose DatePicker (Material 3) uses UTC for date math, but we want the
 * calendar to show days in the device timezone. This shim converts a
 * user-local LocalDate to the equivalent UTC midnight millis.
 */
internal fun localDateToUtcMillis(date: LocalDate, tz: ZoneId): Long {
    return date.atStartOfDay(tz).toInstant().toEpochMilli()
}
