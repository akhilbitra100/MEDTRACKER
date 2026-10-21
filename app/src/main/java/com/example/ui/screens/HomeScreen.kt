package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Brightness5
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.runtime.remember
import com.example.data.model.MedicineEntity

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.ScheduleTimes
import com.example.data.model.DoseRecordEntity
import com.example.ui.components.DoseItemCard
import com.example.ui.components.SafetyDisclaimerBanner
import com.example.ui.components.SummaryStatsCard
import com.example.ui.theme.HealthTeal
import com.example.ui.theme.MedicalBlue
import java.util.Calendar

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.Schedule

@Composable
fun HomeScreen(
    todayFormattedDate: String,
    todayDoses: List<DoseRecordEntity>,
    scheduleTimes: ScheduleTimes,
    medicines: List<MedicineEntity> = emptyList(),
    onNavigateToScan: () -> Unit,
    onNavigateToMedicines: () -> Unit = {},
    onTaken: (DoseRecordEntity) -> Unit,
    onSkip: (DoseRecordEntity) -> Unit,
    onUndo: (DoseRecordEntity) -> Unit,
    onSpeakDose: ((DoseRecordEntity) -> Unit)? = null,
    onCancelSnooze: ((DoseRecordEntity) -> Unit)? = null,
    onTestReminder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when {
        hourOfDay < 12 -> stringResource(R.string.greeting_morning)
        hourOfDay < 17 -> stringResource(R.string.greeting_afternoon)
        else -> stringResource(R.string.greeting_evening)
    }

    val scheduledCount = todayDoses.size
    val takenCount = todayDoses.count { it.status.equals("TAKEN", ignoreCase = true) }
    val pendingCount = todayDoses.count { it.status.equals("PENDING", ignoreCase = true) }

    // Group doses strictly by configured reminder time from saved records
    val groupedDosesByTime = remember(todayDoses) {
        todayDoses.groupBy { it.scheduledTime }
    }

    val sortedConfiguredTimes = remember(todayDoses) {
        groupedDosesByTime.keys.sortedWith { t1, t2 ->
            val cal1 = com.example.notifications.AlarmScheduler.parseTimeToCalendar(t1)?.timeInMillis ?: 0L
            val cal2 = com.example.notifications.AlarmScheduler.parseTimeToCalendar(t2)?.timeInMillis ?: 0L
            cal1.compareTo(cal2)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .testTag("home_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Welcome Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                        )
                        Text(
                            text = greeting,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )
                        Text(
                            text = todayFormattedDate,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Test Reminder Quick Button (48x48dp accessible touch target)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                            .clickable(onClick = onTestReminder)
                            .testTag("quick_test_reminder_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = stringResource(R.string.btn_test_reminder),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

        // Feature 3: Medicine Refill Alert Card (if any medicine is at or below threshold)
        val lowStockMedicines = medicines.filter { it.refillAlertEnabled && it.currentQuantity <= it.lowStockThreshold }
        if (lowStockMedicines.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToMedicines() }
                        .testTag("home_refill_alert_card"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDBA74))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = "Refill warning",
                            tint = Color(0xFFC2410C),
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Refill Alert (${lowStockMedicines.size} medicine${if (lowStockMedicines.size > 1) "s" else ""} low on stock)",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF9A3412)
                                )
                            )
                            val summary = lowStockMedicines.joinToString("; ") {
                                "${it.name}: ${it.currentQuantity} ${it.quantityUnit} remaining"
                            }
                            Text(
                                text = "$summary. Tap to view or update inventory.",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFC2410C))
                            )
                        }
                    }
                }
            }
        }

        // 2. Summary Statistics Card
        item {
            SummaryStatsCard(
                scheduledCount = scheduledCount,
                takenCount = takenCount,
                pendingCount = pendingCount
            )
        }

        // 3. Prominent "Scan Prescription" Hero CTA Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("scan_prescription_hero_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(MedicalBlue, HealthTeal)
                            )
                        )
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.scan_hero_title),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.scan_hero_desc),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color(0xFFE0F2FE),
                                    fontSize = 14.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = onNavigateToScan,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = MedicalBlue
                                ),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("scan_prescription_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddAPhoto,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.scan_hero_button),
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Balanced decorative icon container on the right
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddAPhoto,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        }

        // 4. Configured Medicine Reminder Times Schedule
        item {
            Text(
                text = stringResource(R.string.section_today_schedule),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (sortedConfiguredTimes.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .testTag("empty_schedule_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No Medication Reminders Scheduled Today",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Prescriptions and doses will appear here at their configured reminder times.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        )
                    }
                }
            }
        } else {
            sortedConfiguredTimes.forEach { configuredTime ->
                val dosesForTime = groupedDosesByTime[configuredTime] ?: emptyList()
                val sampleDose = dosesForTime.firstOrNull()
                val slotName = sampleDose?.slot ?: com.example.notifications.AlarmScheduler.parseSlotFromTime(configuredTime)

                val (icon, iconTint, bgTint) = when (slotName.uppercase()) {
                    "MORNING" -> Triple(Icons.Default.WbSunny, Color(0xFFEAB308), Color(0xFFFEF9C3))
                    "AFTERNOON" -> Triple(Icons.Default.Brightness5, Color(0xFFF97316), Color(0xFFFFEDD5))
                    else -> Triple(Icons.Default.Brightness6, Color(0xFF6366F1), Color(0xFFE0E7FF))
                }

                item(key = "header_$configuredTime") {
                    val formattedSlotTitle = when (slotName.uppercase()) {
                        "MORNING" -> stringResource(R.string.section_morning)
                        "AFTERNOON" -> stringResource(R.string.section_afternoon)
                        "NIGHT" -> stringResource(R.string.section_night)
                        else -> "Scheduled Dose"
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    SlotSectionHeader(
                        title = "$formattedSlotTitle ($configuredTime)",
                        time = configuredTime,
                        icon = icon,
                        iconTint = iconTint,
                        bgTint = bgTint
                    )
                }

                items(dosesForTime, key = { it.id }) { dose ->
                    DoseItemCard(
                        dose = dose,
                        onTaken = { onTaken(dose) },
                        onSkip = { onSkip(dose) },
                        onUndo = { onUndo(dose) },
                        onSpeak = if (onSpeakDose != null) { { onSpeakDose(dose) } } else null,
                        onCancelSnooze = if (onCancelSnooze != null) { { onCancelSnooze(dose) } } else null
                    )
                }
            }
        }


        // 5. Safety Disclaimer at bottom of Home
        item {
            Spacer(modifier = Modifier.height(12.dp))
            SafetyDisclaimerBanner()
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
}

@Composable
private fun SlotSectionHeader(
    title: String,
    time: String,
    icon: ImageVector,
    iconTint: Color,
    bgTint: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(bgTint),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = time,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
private fun EmptySlotCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
