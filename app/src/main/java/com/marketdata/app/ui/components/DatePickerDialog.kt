package com.marketdata.app.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.marketdata.app.ui.theme.AccentBlue
import com.marketdata.app.ui.theme.DarkSurface
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDatePickerDialog(
    initialDate: Date,
    onDismiss: () -> Unit,
    onDateSelected: (Date) -> Unit
) {
    val cal = Calendar.getInstance().apply { time = initialDate }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = cal.timeInMillis
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let {
                    onDateSelected(Date(it))
                }
                onDismiss()
            }) { Text("OK", color = AccentBlue) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        colors = DatePickerDefaults.colors(containerColor = DarkSurface)
    ) {
        DatePicker(
            state = datePickerState,
            colors = DatePickerDefaults.colors(containerColor = DarkSurface)
        )
    }
}
