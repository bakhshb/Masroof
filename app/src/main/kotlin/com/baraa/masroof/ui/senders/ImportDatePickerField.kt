package com.baraa.masroof.ui.senders

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.baraa.masroof.ui.theme.CalendarDateField as SharedCalendarDateField
import java.time.LocalDate

/**
 * Compatibility alias — prefer [com.baraa.masroof.ui.theme.CalendarDateField].
 */
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
) = SharedCalendarDateField(
    label = label,
    selected = selected,
    onSelected = onSelected,
    enabled = enabled,
    isStart = isStart,
    rangeEnd = rangeEnd,
    rangeStart = rangeStart,
    modifier = modifier,
)
