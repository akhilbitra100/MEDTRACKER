package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.example.ui.components.ReminderTimesSelector
import com.example.data.model.TimeSlot
import com.example.notifications.AlarmScheduler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.MedicineEntity
import com.example.ui.theme.HealthTeal
import com.example.ui.theme.HealthTealDark
import com.example.ui.theme.HealthTealLight
import com.example.ui.theme.MedicalBlue
import com.example.ui.theme.StatusSkipOrange
import com.example.ui.theme.StatusTakenGreen
import com.example.ui.theme.StatusWarningAmber
import com.example.ui.theme.StatusWarningBg
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun MedicinesScreen(
    medicines: List<MedicineEntity>,
    onAddManual: (MedicineEntity) -> Unit,
    onDeleteMedicine: (Long) -> Unit,
    onNavigateToScan: () -> Unit,
    onUpdateStock: ((Long, Int, String, Int, Int, Boolean) -> Unit)? = null,
    onUpdateMedicine: ((MedicineEntity, MedicineEntity) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var medicineToDelete by remember { mutableStateOf<MedicineEntity?>(null) }
    var medicineToUpdateStock by remember { mutableStateOf<MedicineEntity?>(null) }
    var medicineToEdit by remember { mutableStateOf<MedicineEntity?>(null) }

    val filteredList = remember(medicines, searchQuery) {
        if (searchQuery.isBlank()) medicines
        else medicines.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.instructions.contains(searchQuery, ignoreCase = true)
        }
    }

    Box(modifier = modifier.fillMaxSize().testTag("medicines_screen")) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            item {
                Column {
                    Text(
                        text = stringResource(R.string.medicines_title),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    Text(
                        text = stringResource(R.string.medicines_subtitle, medicines.size),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Search field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            }

            if (filteredList.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Medication,
                                contentDescription = null,
                                tint = MedicalBlue.copy(alpha = 0.5f),
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.no_meds_found),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = "Scan a prescription or add a medication manually to get started.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = onNavigateToScan,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(stringResource(R.string.scan_hero_button))
                            }
                        }
                    }
                }
            } else {
                items(filteredList, key = { it.id }) { med ->
                    MedicineDetailCard(
                        medicine = med,
                        onDelete = { medicineToDelete = med },
                        onUpdateStock = { medicineToUpdateStock = med },
                        onEditSchedule = { medicineToEdit = med }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(60.dp))
            }
        }

        // Floating Action Button to Add Manually
        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = MedicalBlue,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_medicine_fab")
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.btn_add_manual))
        }
    }

    // Delete Confirmation Dialog
    medicineToDelete?.let { med ->
        AlertDialog(
            onDismissRequest = { medicineToDelete = null },
            title = { Text(stringResource(R.string.delete_dialog_title)) },
            text = { Text(stringResource(R.string.delete_dialog_msg)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteMedicine(med.id)
                        medicineToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.btn_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { medicineToDelete = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    // Update Stock Dialog
    medicineToUpdateStock?.let { med ->
        UpdateStockDialog(
            medicine = med,
            onDismiss = { medicineToUpdateStock = null },
            onConfirm = { newQty, newUnit, newDoseQty, newThreshold, newAlertEnabled ->
                onUpdateStock?.invoke(med.id, newQty, newUnit, newDoseQty, newThreshold, newAlertEnabled)
                medicineToUpdateStock = null
            }
        )
    }

    // Edit Medicine Schedule Dialog
    medicineToEdit?.let { med ->
        EditMedicineDialog(
            medicine = med,
            onDismiss = { medicineToEdit = null },
            onConfirm = { updated ->
                onUpdateMedicine?.invoke(med, updated)
                medicineToEdit = null
            }
        )
    }

    // Add Manual Dialog
    if (showAddDialog) {
        AddManualMedicineDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { newMed ->
                onAddManual(newMed)
                showAddDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedicineDetailCard(
    medicine: MedicineEntity,
    onDelete: () -> Unit,
    onUpdateStock: () -> Unit,
    onEditSchedule: () -> Unit
) {
    val isLowStock = medicine.refillAlertEnabled && medicine.currentQuantity <= medicine.lowStockThreshold

    // Calculate approximate run-out date based on active slots and dose quantity
    val dosesPerDay = (if (medicine.morningSlot) 1 else 0) +
            (if (medicine.afternoonSlot) 1 else 0) +
            (if (medicine.nightSlot) 1 else 0)
    val dailyConsumption = dosesPerDay * medicine.doseQuantity
    val runOutEstimate = if (dailyConsumption > 0 && medicine.currentQuantity > 0) {
        val daysRemaining = medicine.currentQuantity / dailyConsumption
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, daysRemaining) }
        val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(cal.time)
        "Approx. Run-Out Date: ~$dateStr (~$daysRemaining days remaining based on schedule)"
    } else if (medicine.currentQuantity == 0) {
        "Out of stock! Run-out date reached."
    } else {
        null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("medicine_card_${medicine.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Medication,
                            contentDescription = null,
                            tint = MedicalBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = medicine.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${medicine.strength} • ${medicine.form}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("delete_medicine_button_${medicine.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.btn_delete),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Preserved Original Prescription Instructions
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(10.dp)
            ) {
                Column {
                    Text(
                        text = "Original Prescription Instructions:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Text(
                        text = medicine.instructions,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        ),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Feature 2: Meal Instruction Badge
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val mealText = if (medicine.mealInstruction.isNotBlank() && !medicine.mealInstruction.contains("No meal", ignoreCase = true)) {
                    "🍽️ ${medicine.mealInstruction}"
                } else {
                    "🍽️ No meal instruction specified"
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(HealthTealLight.copy(alpha = 0.7f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = mealText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = HealthTealDark
                        )
                    )
                }

                if (medicine.mealInstructionRaw.isNotBlank()) {
                    Text(
                        text = "Rx: \"${medicine.mealInstructionRaw}\"",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }

            if (medicine.mealInstructionFlagged) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(StatusWarningBg)
                        .border(1.dp, StatusWarningAmber, RoundedCornerShape(6.dp))
                        .padding(6.dp)
                ) {
                    Text(
                        text = "⚠️ Unclear or conflicting meal instruction flagged on prescription. Please verify with physician.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF92400E),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            // Feature 3: Refill & Stock Tracking Section
            Spacer(modifier = Modifier.height(10.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isLowStock) Color(0xFFFFF7ED) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                border = if (isLowStock) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDBA74)) else null
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Refill Inventory",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${medicine.currentQuantity} ${medicine.quantityUnit} remaining • ${medicine.doseQuantity} / dose",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = if (isLowStock) Color(0xFFC2410C) else MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onEditSchedule,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .testTag("edit_medicine_button_${medicine.id}"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Edit Schedule",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }

                        OutlinedButton(
                            onClick = onUpdateStock,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .testTag("update_stock_button_${medicine.id}"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Update Stock",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }

                    if (isLowStock) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = Color(0xFFC2410C),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Low Stock Alert: Remaining quantity (≤ ${medicine.lowStockThreshold} ${medicine.quantityUnit}) is low. Contact pharmacy for refill.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF9A3412),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    runOutEstimate?.let { estimateText ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = estimateText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            // Scheduled Reminder Times Display
            val effectiveTimes = medicine.getEffectiveReminderTimes()
            if (effectiveTimes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp, top = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Scheduled Alerts:",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                    effectiveTimes.forEach { timeStr ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = timeStr,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Frequency and active dose slots
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Frequency: ${medicine.frequency}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier.padding(vertical = 2.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (medicine.morningSlot) SlotBadge(stringResource(R.string.slot_morning), Color(0xFFE0F2FE), MedicalBlue)
                    if (medicine.afternoonSlot) SlotBadge(stringResource(R.string.slot_afternoon), Color(0xFFFFEDD5), Color(0xFFC2410C))
                    if (medicine.nightSlot) SlotBadge(stringResource(R.string.slot_night), Color(0xFFE0E7FF), Color(0xFF4338CA))
                }
            }
        }
    }
}

@Composable
private fun SlotBadge(text: String, bg: Color, fg: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                color = fg,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp
            )
        )
    }
}

@Composable
private fun UpdateStockDialog(
    medicine: MedicineEntity,
    onDismiss: () -> Unit,
    onConfirm: (quantity: Int, unit: String, doseQty: Int, threshold: Int, alertEnabled: Boolean) -> Unit
) {
    var quantityStr by remember { mutableStateOf(medicine.currentQuantity.toString()) }
    var unit by remember { mutableStateOf(medicine.quantityUnit) }
    var doseQtyStr by remember { mutableStateOf(medicine.doseQuantity.toString()) }
    var thresholdStr by remember { mutableStateOf(medicine.lowStockThreshold.toString()) }
    var alertEnabled by remember { mutableStateOf(medicine.refillAlertEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Stock: ${medicine.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter current stock level and refill alert threshold. Inventory will decrease only when marked Taken.",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = quantityStr,
                        onValueChange = { quantityStr = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Remaining Qty") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = doseQtyStr,
                        onValueChange = { doseQtyStr = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Qty/Dose") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = thresholdStr,
                        onValueChange = { thresholdStr = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Alert Below") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Enable Refill Alerts", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = alertEnabled,
                        onCheckedChange = { alertEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MedicalBlue
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val q = quantityStr.toIntOrNull() ?: medicine.currentQuantity
                    val u = unit.ifBlank { "tablets" }
                    val dq = doseQtyStr.toIntOrNull() ?: 1
                    val th = thresholdStr.toIntOrNull() ?: 5
                    onConfirm(q, u, dq, th, alertEnabled)
                }
            ) {
                Text("Save Inventory")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddManualMedicineDialog(
    onDismiss: () -> Unit,
    onConfirm: (MedicineEntity) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var strength by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf("Once daily") }
    var reminderTimes by remember { mutableStateOf<List<String>>(emptyList()) }
    var showTimeValidationError by remember { mutableStateOf(false) }

    // Feature 2: Meal Instruction
    var mealInstruction by remember { mutableStateOf("No meal instruction specified") }

    // Feature 3: Refill Tracking
    var currentQuantityStr by remember { mutableStateOf("30") }
    var quantityUnit by remember { mutableStateOf("tablets") }
    var doseQuantityStr by remember { mutableStateOf("1") }
    var lowStockThresholdStr by remember { mutableStateOf("5") }
    var refillAlertEnabled by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.btn_add_manual), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_med_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_medicine_name_input")
                )
                OutlinedTextField(
                    value = strength,
                    onValueChange = { strength = it },
                    label = { Text(stringResource(R.string.field_strength)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_medicine_strength_input")
                )
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text(stringResource(R.string.field_dosage)) },
                    modifier = Modifier.fillMaxWidth().testTag("add_medicine_instructions_input")
                )
                OutlinedTextField(
                    value = frequency,
                    onValueChange = { frequency = it },
                    label = { Text(stringResource(R.string.field_frequency)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_medicine_frequency_input")
                )

                // Feature 2: Meal-Time Scheduling Option
                Text(
                    text = "Meal Instruction:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 2.dp)
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Before food", "After food", "With food", "No meal instruction specified").forEach { option ->
                        val isSelected = mealInstruction.equals(option, ignoreCase = true)
                        val label = when (option) {
                            "No meal instruction specified" -> "None"
                            else -> option
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { mealInstruction = option },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                // Feature 4: Per-Medicine Reminder Time Selection
                ReminderTimesSelector(
                    reminderTimes = reminderTimes,
                    onTimesChanged = { updatedList ->
                        reminderTimes = updatedList
                        if (updatedList.isNotEmpty()) showTimeValidationError = false
                    },
                    showErrorIfEmpty = showTimeValidationError,
                    frequencyHint = frequency
                )

                // Feature 3: Refill Details
                Text(
                    text = "Refill Inventory Tracking:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = currentQuantityStr,
                        onValueChange = { currentQuantityStr = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Starting Qty", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = quantityUnit,
                        onValueChange = { quantityUnit = it },
                        label = { Text("Unit", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = doseQuantityStr,
                        onValueChange = { doseQuantityStr = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Qty/Dose", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) return@Button
                    if (reminderTimes.isEmpty() || reminderTimes.any { !TimeSlot.isTimeInAnyValidSlot(it) }) {
                        showTimeValidationError = true
                        return@Button
                    }
                    val startingQty = currentQuantityStr.toIntOrNull() ?: 30
                    val doseQty = doseQuantityStr.toIntOrNull() ?: 1
                    val threshold = lowStockThresholdStr.toIntOrNull() ?: 5
                    val hasMorning = reminderTimes.any { TimeSlot.findSlotForTime(it) == TimeSlot.MORNING }
                    val hasAfternoon = reminderTimes.any { TimeSlot.findSlotForTime(it) == TimeSlot.AFTERNOON }
                    val hasNight = reminderTimes.any { 
                        val slot = TimeSlot.findSlotForTime(it)
                        slot == TimeSlot.NIGHT || slot == TimeSlot.EVENING
                    }

                    onConfirm(
                        MedicineEntity(
                            name = name.trim(),
                            strength = strength.trim(),
                            instructions = instructions.trim().ifEmpty { "Take as prescribed" },
                            frequency = frequency.trim(),
                            morningSlot = hasMorning,
                            afternoonSlot = hasAfternoon,
                            nightSlot = hasNight,
                            mealInstruction = mealInstruction,
                            currentQuantity = startingQty,
                            quantityUnit = quantityUnit.ifBlank { "tablets" },
                            doseQuantity = doseQty,
                            lowStockThreshold = threshold,
                            refillAlertEnabled = refillAlertEnabled,
                            reminderTimes = reminderTimes.joinToString(", ")
                        )
                    )
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("save_manual_medicine_button")
            ) {
                Text(stringResource(R.string.btn_add_manual))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditMedicineDialog(
    medicine: MedicineEntity,
    onDismiss: () -> Unit,
    onConfirm: (MedicineEntity) -> Unit
) {
    var name by remember { mutableStateOf(medicine.name) }
    var strength by remember { mutableStateOf(medicine.strength) }
    var instructions by remember { mutableStateOf(medicine.instructions) }
    var frequency by remember { mutableStateOf(medicine.frequency) }
    var reminderTimes by remember { mutableStateOf(medicine.getEffectiveReminderTimes().distinct()) }
    var showTimeValidationError by remember { mutableStateOf(false) }

    // Meal Instruction
    var mealInstruction by remember { mutableStateOf(medicine.mealInstruction.ifBlank { "No meal instruction specified" }) }

    // Refill Tracking
    var currentQuantityStr by remember { mutableStateOf(medicine.currentQuantity.toString()) }
    var quantityUnit by remember { mutableStateOf(medicine.quantityUnit) }
    var doseQuantityStr by remember { mutableStateOf(medicine.doseQuantity.toString()) }
    var lowStockThresholdStr by remember { mutableStateOf(medicine.lowStockThreshold.toString()) }
    var refillAlertEnabled by remember { mutableStateOf(medicine.refillAlertEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Medicine & Reminders", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Medicine Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_medicine_name_input")
                )
                OutlinedTextField(
                    value = strength,
                    onValueChange = { strength = it },
                    label = { Text("Strength") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_medicine_strength_input")
                )
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text("Dosage Instructions") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_medicine_instructions_input")
                )
                OutlinedTextField(
                    value = frequency,
                    onValueChange = { frequency = it },
                    label = { Text("Frequency") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_medicine_frequency_input")
                )

                // Meal Instruction
                Text(
                    text = "Meal Instruction:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 2.dp)
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Before food", "After food", "With food", "No meal instruction specified").forEach { option ->
                        val isSelected = mealInstruction.equals(option, ignoreCase = true)
                        val label = when (option) {
                            "No meal instruction specified" -> "None"
                            else -> option
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { mealInstruction = option },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                // Feature 4: Per-Medicine Reminder Time Selection
                ReminderTimesSelector(
                    reminderTimes = reminderTimes,
                    onTimesChanged = { updatedList ->
                        reminderTimes = updatedList
                        if (updatedList.isNotEmpty()) showTimeValidationError = false
                    },
                    showErrorIfEmpty = showTimeValidationError,
                    frequencyHint = frequency
                )

                // Refill Inventory Details
                Text(
                    text = "Refill Inventory Tracking:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = currentQuantityStr,
                        onValueChange = { currentQuantityStr = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Quantity", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = quantityUnit,
                        onValueChange = { quantityUnit = it },
                        label = { Text("Unit", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = doseQuantityStr,
                        onValueChange = { doseQuantityStr = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Qty/Dose", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) return@Button
                    if (reminderTimes.isEmpty() || reminderTimes.any { !TimeSlot.isTimeInAnyValidSlot(it) }) {
                        showTimeValidationError = true
                        return@Button
                    }
                    val qty = currentQuantityStr.toIntOrNull() ?: medicine.currentQuantity
                    val doseQty = doseQuantityStr.toIntOrNull() ?: medicine.doseQuantity
                    val threshold = lowStockThresholdStr.toIntOrNull() ?: medicine.lowStockThreshold
                    val hasMorning = reminderTimes.any { TimeSlot.findSlotForTime(it) == TimeSlot.MORNING }
                    val hasAfternoon = reminderTimes.any { TimeSlot.findSlotForTime(it) == TimeSlot.AFTERNOON }
                    val hasNight = reminderTimes.any { 
                        val slot = TimeSlot.findSlotForTime(it)
                        slot == TimeSlot.NIGHT || slot == TimeSlot.EVENING
                    }

                    onConfirm(
                        medicine.copy(
                            name = name.trim(),
                            strength = strength.trim(),
                            instructions = instructions.trim().ifEmpty { "Take as prescribed" },
                            frequency = frequency.trim(),
                            morningSlot = hasMorning,
                            afternoonSlot = hasAfternoon,
                            nightSlot = hasNight,
                            mealInstruction = mealInstruction,
                            currentQuantity = qty,
                            quantityUnit = quantityUnit.ifBlank { "tablets" },
                            doseQuantity = doseQty,
                            lowStockThreshold = threshold,
                            refillAlertEnabled = refillAlertEnabled,
                            reminderTimes = reminderTimes.joinToString(", ")
                        )
                    )
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("save_edit_medicine_button")
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}
