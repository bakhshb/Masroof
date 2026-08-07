package com.baraa.masroof.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Material 3 date picker field. Read-only text + calendar icon; tapping
 * opens [DatePickerDialog]. Values are [LocalDate] in the device zone.
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
    /** Inclusive upper bound (e.g. today for opening-balance dates). */
    maxDate: LocalDate? = null,
    /** Inclusive lower bound. */
    minDate: LocalDate? = null,
    modifier: Modifier = Modifier,
) {
    var dialogVisible by remember { mutableStateOf(false) }
    val locale = Locale("ar")
    val displayFormatter = remember(locale) { DateTimeFormatter.ofPattern("d MMMM yyyy", locale) }
    val displayValue = selected.format(displayFormatter)

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
        val constraints = buildConstraints(isStart, rangeEnd, rangeStart, minDate, maxDate, tz)
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
    minDate: LocalDate?,
    maxDate: LocalDate?,
    tz: ZoneId,
): SelectableDates = object : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
        val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
        if (minDate != null && date.isBefore(minDate)) return false
        if (maxDate != null && date.isAfter(maxDate)) return false
        val startBound = rangeStart?.atStartOfDay(tz)?.toInstant()?.toEpochMilli() ?: Long.MIN_VALUE
        val endBound = rangeEnd?.atStartOfDay(tz)?.toInstant()?.toEpochMilli() ?: Long.MAX_VALUE
        return when {
            isStart && rangeEnd != null -> utcTimeMillis <= endBound
            !isStart && rangeStart != null -> utcTimeMillis >= startBound
            else -> true
        }
    }

    override fun isSelectableYear(year: Int): Boolean = year in 2010..2100
}

/** Converts a local calendar day to epoch millis at start-of-day in [tz]. */
fun localDateToUtcMillis(date: LocalDate, tz: ZoneId = ZoneId.systemDefault()): Long =
    date.atStartOfDay(tz).toInstant().toEpochMilli()
