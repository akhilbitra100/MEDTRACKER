package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TimeSlot
import com.example.notifications.AlarmScheduler
import java.util.Calendar

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderTimesSelector(
    reminderTimes: List<String>,
    onTimesChanged: (List<String>) -> Unit,
    showErrorIfEmpty: Boolean = false,
    frequencyHint: String = "",
    specifiedPrescriptionTime: String = "",
    modifier: Modifier = Modifier
) {
    var showSlotPicker by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var timeToEdit by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Reminder Notification Times:",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }

        if (specifiedPrescriptionTime.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Time specified on prescription: $specifiedPrescriptionTime",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        } else if (frequencyHint.isNotBlank()) {
            Text(
                text = "Prescription frequency: \"$frequencyHint\". Please select suitable reminder time(s):",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                ),
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        // List of configured reminder times
        if (reminderTimes.isEmpty()) {
            val borderColor = if (showErrorIfEmpty) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
            val bgColor = if (showErrorIfEmpty) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(bgColor)
                    .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = if (showErrorIfEmpty) "⚠️ Please configure at least one reminder time." else "No reminder times configured yet.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (showErrorIfEmpty) FontWeight.Bold else FontWeight.Normal,
                            color = if (showErrorIfEmpty) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Text(
                        text = "Tap '+ Add Reminder Time' below to choose a time slot and pick a reminder time.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        ),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                reminderTimes.forEachIndexed { index, timeStr ->
                    val matchedSlot = TimeSlot.findSlotForTime(timeStr)
                    val isValidSlot = matchedSlot != null

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isValidSlot) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .testTag("reminder_time_chip_$index")
                            .clickable {
                                editingIndex = index
                                timeToEdit = timeStr
                                showSlotPicker = true
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (isValidSlot) Icons.Default.AccessTime else Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = if (isValidSlot) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = timeStr,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isValidSlot) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                        fontSize = 13.sp
                                    )
                                )
                                Text(
                                    text = matchedSlot?.displayName ?: "Outside Valid Slot",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isValidSlot) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f) else MaterialTheme.colorScheme.error,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            // Edit Button
                            IconButton(
                                onClick = {
                                    editingIndex = index
                                    timeToEdit = timeStr
                                    showSlotPicker = true
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit $timeStr",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            // Delete Button
                            IconButton(
                                onClick = {
                                    val newList = reminderTimes.toMutableList()
                                    newList.removeAt(index)
                                    onTimesChanged(newList)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove $timeStr",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Buttons: Add Reminder Time Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    editingIndex = null
                    timeToEdit = ""
                    showSlotPicker = true
                },
                shape = RoundedCornerShape(10.dp),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier.testTag("add_reminder_time_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (reminderTimes.isEmpty()) "Select Reminder Time" else "Add Another Time",
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (reminderTimes.isNotEmpty()) {
                Text(
                    text = "${reminderTimes.size} reminder(s) configured",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }

        // Permitted Slots Guide
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Slot ranges: Morning (5:00 AM–11:59 AM) • Afternoon (12:00 PM–4:59 PM) • Evening (5:00 PM–6:59 PM) • Night (7:00 PM–11:59 PM)",
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                fontSize = 11.sp
            )
        )
    }

    // Slot-Restricted Time Picker Dialog
    if (showSlotPicker) {
        SlotTimePickerDialog(
            initialTime = timeToEdit,
            onDismiss = {
                showSlotPicker = false
                editingIndex = null
                timeToEdit = ""
            },
            onConfirm = { confirmedTime ->
                val current = reminderTimes.toMutableList()
                val idx = editingIndex
                if (idx != null && idx in current.indices) {
                    current[idx] = confirmedTime
                } else {
                    if (!current.contains(confirmedTime)) {
                        current.add(confirmedTime)
                    }
                }
                onTimesChanged(current.distinct())
                showSlotPicker = false
                editingIndex = null
                timeToEdit = ""
            }
        )
    }
}

/**
 * Dialog enforcing strict time-slot restrictions:
 * - Morning: 5:00 AM to 11:59 AM
 * - Afternoon: 12:00 PM to 4:59 PM
 * - Evening: 5:00 PM to 6:59 PM
 * - Night: 7:00 PM to 11:59 PM
 *
 * Enforces the restriction in the actual picker UI by only presenting valid hours for the selected slot.
 * When the slot changes, the available range and hours update immediately.
 * If the selected time is invalid for the new slot, the user is required to select a valid hour.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SlotTimePickerDialog(
    initialTime: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val initialSlot = if (initialTime.isNotBlank()) {
        TimeSlot.findSlotForTime(initialTime) ?: TimeSlot.MORNING
    } else {
        TimeSlot.MORNING
    }

    val initialCal = if (initialTime.isNotBlank()) {
        AlarmScheduler.parseTimeToCalendar(initialTime)
    } else null

    var selectedSlot by remember { mutableStateOf(initialSlot) }

    // Selected hour in 24h format. Null if unselected or if previously selected hour is invalid for the current slot.
    var selectedHour24 by remember {
        val hour = initialCal?.get(Calendar.HOUR_OF_DAY)
        mutableStateOf<Int?>(if (hour != null && initialSlot.validHours24.contains(hour)) hour else null)
    }

    var selectedMinute by remember {
        val min = initialCal?.get(Calendar.MINUTE) ?: 0
        // Round to nearest 5 for easy picker
        mutableIntStateOf(min)
    }

    val isValidSelection = selectedHour24 != null && selectedSlot.isValid(selectedHour24!!, selectedMinute)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (initialTime.isBlank()) "Choose Reminder Time" else "Edit Reminder Time",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Time Slot Selector
                Text(
                    text = "1. Select Time Slot:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TimeSlot.values().forEach { slot ->
                        val isSelected = selectedSlot == slot
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (selectedSlot != slot) {
                                    selectedSlot = slot
                                    // If previous hour is not valid in new slot, reset it so user must pick a valid one
                                    if (selectedHour24 == null || !slot.validHours24.contains(selectedHour24)) {
                                        selectedHour24 = null
                                    }
                                }
                            },
                            label = { Text(slot.displayName) },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("slot_chip_${slot.id.lowercase()}")
                        )
                    }
                }

                // 2. Clear short explanation of permitted range beneath slot selector (Requirement 7)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Permitted range for ${selectedSlot.displayName}: ${selectedSlot.permittedRangeText}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                // 3. Hour Selector - STRICTLY RESTRICTED to selectedSlot.validHours24 (Requirements 1, 2, 3)
                Text(
                    text = "2. Select Hour (Restricted to ${selectedSlot.displayName}):",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    selectedSlot.validHours24.forEach { hour24 ->
                        val isSelected = selectedHour24 == hour24
                        val hour12 = when {
                            hour24 == 0 -> 12
                            hour24 > 12 -> hour24 - 12
                            else -> hour24
                        }
                        val amPm = if (hour24 >= 12) "PM" else "AM"
                        val label = "$hour12 $amPm"

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .clickable { selectedHour24 = hour24 }
                                .testTag("hour_chip_$hour24")
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                // 4. Minute Selector
                Text(
                    text = "3. Select Minute:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55).forEach { minute ->
                        val isSelected = selectedMinute == minute
                        val label = String.format(":%02d", minute)

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .clickable { selectedMinute = minute }
                                .testTag("minute_chip_$minute")
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // 5. Selected Time Preview and Validation Feedback
                if (selectedHour24 != null) {
                    val previewTime = AlarmScheduler.formatTimeToAmPm(selectedHour24!!, selectedMinute)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Selected Time: $previewTime (${selectedSlot.displayName})",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Please tap an hour above to schedule this ${selectedSlot.displayName} dose (${selectedSlot.permittedRangeText}).",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isValidSelection) {
                        val formatted = AlarmScheduler.formatTimeToAmPm(selectedHour24!!, selectedMinute)
                        onConfirm(formatted)
                    }
                },
                enabled = isValidSelection,
                modifier = Modifier.testTag("confirm_slot_time_button")
            ) {
                Text("Confirm Time")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_slot_time_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
