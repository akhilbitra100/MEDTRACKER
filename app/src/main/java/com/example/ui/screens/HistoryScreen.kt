package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.example.data.model.DoseRecordEntity
import com.example.ui.components.StatusPill
import com.example.ui.theme.MedicalBlue
import com.example.ui.theme.StatusSkipOrange
import com.example.ui.theme.StatusTakenGreen
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class DayAdherence(
    val dateString: String,
    val displayLabel: String,
    val isToday: Boolean,
    val totalScheduled: Int,
    val takenDoses: Int,
    val skippedDoses: Int,
    val pendingDoses: Int,
    val adherencePercent: Int? // null when no doses scheduled
)

@Composable
fun HistoryScreen(
    doseHistory: List<DoseRecordEntity>,
    modifier: Modifier = Modifier
) {
    var filterStatus by remember { mutableStateOf("ALL") }

    // Feature 5: Compute 7-day adherence for current day and preceding 6 calendar days
    val sevenDaySummaries = remember(doseHistory) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val displayFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())

        (0..6).map { daysAgo ->
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -daysAgo)
            }
            val dateStr = dateFormat.format(cal.time)
            val dosesForDate = doseHistory.filter { it.scheduledDate == dateStr }
            val total = dosesForDate.size
            val taken = dosesForDate.count { it.status.equals("TAKEN", ignoreCase = true) }
            val skipped = dosesForDate.count { it.status.equals("SKIPPED", ignoreCase = true) }
            val pending = dosesForDate.count {
                !it.status.equals("TAKEN", ignoreCase = true) && !it.status.equals("SKIPPED", ignoreCase = true)
            }
            val percent = if (total > 0) ((taken.toDouble() / total.toDouble()) * 100.0).roundToInt() else null
            val label = when (daysAgo) {
                0 -> "Today (${displayFormat.format(cal.time)})"
                1 -> "Yesterday (${displayFormat.format(cal.time)})"
                else -> displayFormat.format(cal.time)
            }
            DayAdherence(
                dateString = dateStr,
                displayLabel = label,
                isToday = daysAgo == 0,
                totalScheduled = total,
                takenDoses = taken,
                skippedDoses = skipped,
                pendingDoses = pending,
                adherencePercent = percent
            )
        }
    }

    val totalSevenDayScheduled = sevenDaySummaries.sumOf { it.totalScheduled }
    val totalSevenDayTaken = sevenDaySummaries.sumOf { it.takenDoses }
    val totalSevenDaySkipped = sevenDaySummaries.sumOf { it.skippedDoses }
    val totalSevenDayPending = sevenDaySummaries.sumOf { it.pendingDoses }
    val overallSevenDayAdherence = if (totalSevenDayScheduled > 0) {
        ((totalSevenDayTaken.toDouble() / totalSevenDayScheduled.toDouble()) * 100.0).roundToInt()
    } else 0

    val takenCount = doseHistory.count { it.status.equals("TAKEN", ignoreCase = true) }
    val skippedCount = doseHistory.count { it.status.equals("SKIPPED", ignoreCase = true) }

    val filteredList = remember(doseHistory, filterStatus) {
        when (filterStatus) {
            "TAKEN" -> doseHistory.filter { it.status.equals("TAKEN", ignoreCase = true) }
            "SKIPPED" -> doseHistory.filter { it.status.equals("SKIPPED", ignoreCase = true) }
            "PENDING" -> doseHistory.filter { it.status.equals("PENDING", ignoreCase = true) }
            else -> doseHistory
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("history_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MedicalBlue,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "7-Day Adherence & History",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                }
                Text(
                    text = "Track medication adherence across the current day and past 6 calendar days.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Feature 5: 7-Day Adherence Visual Summary Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("seven_day_summary_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "7-Day Adherence Score",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            if (totalSevenDayScheduled > 0) {
                                Text(
                                    text = "$totalSevenDayTaken of $totalSevenDayScheduled doses taken ($totalSevenDaySkipped skipped, $totalSevenDayPending pending)",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            } else {
                                Text(
                                    text = "No scheduled doses recorded in the past 7 days",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }

                        if (totalSevenDayScheduled > 0) {
                            Text(
                                text = "$overallSevenDayAdherence%",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (overallSevenDayAdherence >= 80) StatusTakenGreen else StatusSkipOrange,
                                    fontSize = 32.sp
                                ),
                                modifier = Modifier.testTag("overall_adherence_percent")
                            )
                        } else {
                            Text(
                                text = "—",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 28.sp
                                )
                            )
                        }
                    }

                    if (totalSevenDayScheduled > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { (overallSevenDayAdherence / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp)),
                            color = if (overallSevenDayAdherence >= 80) StatusTakenGreen else StatusSkipOrange,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 7-day calendar bar visualization
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        sevenDaySummaries.reversed().forEach { day ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                val cal = Calendar.getInstance().apply {
                                    val parts = day.dateString.split("-")
                                    if (parts.size == 3) {
                                        set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                                    }
                                }
                                val dayLetter = SimpleDateFormat("EE", Locale.getDefault()).format(cal.time).take(1)

                                val barColor = when {
                                    day.totalScheduled == 0 -> Color(0xFFE2E8F0)
                                    (day.adherencePercent ?: 0) >= 80 -> StatusTakenGreen
                                    (day.adherencePercent ?: 0) > 0 -> StatusSkipOrange
                                    else -> Color(0xFFEF4444)
                                }

                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(barColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (day.totalScheduled > 0) "${day.takenDoses}/${day.totalScheduled}" else "0",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (day.totalScheduled == 0) Color(0xFF64748B) else Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (day.isToday) "Today" else dayLetter,
                                    fontSize = 11.sp,
                                    fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = if (day.isToday) MedicalBlue else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section Title: Day-by-Day Adherence (7 Days)
        item {
            Text(
                text = "Daily Adherence (Past 7 Days)",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
        }

        // Feature 5: Day-by-Day Detailed Cards for each of the 7 calendar days
        if (totalSevenDayScheduled == 0) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No dose records found in the past 7 days. Scheduled and completed doses will populate your adherence history.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        } else {
            items(sevenDaySummaries, key = { it.dateString }) { day ->
                DayAdherenceCard(day = day)
            }
        }

        // Section Title: Doses Log
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Medication Dose Audit Log",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
        }

        // Filter chips for audit log
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterStatus == "ALL",
                    onClick = { filterStatus = "ALL" },
                    label = { Text("${stringResource(R.string.filter_all)} (${doseHistory.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
                FilterChip(
                    selected = filterStatus == "TAKEN",
                    onClick = { filterStatus = "TAKEN" },
                    label = { Text("${stringResource(R.string.filter_taken)} ($takenCount)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
                FilterChip(
                    selected = filterStatus == "SKIPPED",
                    onClick = { filterStatus = "SKIPPED" },
                    label = { Text("${stringResource(R.string.filter_skipped)} ($skippedCount)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        // History Log List
        if (filteredList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_history_records),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        } else {
            items(filteredList, key = { it.id }) { item ->
                HistoryItemCard(item)
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HistoryItemCard(dose: DoseRecordEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                val icon = when (dose.status.uppercase()) {
                    "TAKEN" -> Icons.Default.CheckCircle
                    "SKIPPED" -> Icons.Default.WarningAmber
                    else -> Icons.Default.HourglassEmpty
                }
                val iconColor = when (dose.status.uppercase()) {
                    "TAKEN" -> StatusTakenGreen
                    "SKIPPED" -> StatusSkipOrange
                    else -> MedicalBlue
                }
                val iconBg = when (dose.status.uppercase()) {
                    "TAKEN" -> Color(0xFFDCFCE7)
                    "SKIPPED" -> Color(0xFFFFEDD5)
                    else -> Color(0xFFE0F2FE)
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = dose.medicineName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Text(
                        text = "${dose.medicineStrength} • ${dose.slot.lowercase().replaceFirstChar { it.uppercase() }} (${dose.scheduledTime})",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    dose.actionTimestamp?.let { ts ->
                        val sdf = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
                        Text(
                            text = sdf.format(Date(ts)),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            StatusPill(status = dose.status)
        }
    }
}

@Composable
private fun DayAdherenceCard(day: DayAdherence) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("day_adherence_card_${day.dateString}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (day.isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (day.isToday) 2.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = day.displayLabel,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (day.isToday) MedicalBlue else MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp
                        )
                    )
                    if (day.isToday) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MedicalBlue)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "CURRENT",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (day.totalScheduled > 0 && day.adherencePercent != null) {
                    val badgeBg = if (day.adherencePercent >= 80) Color(0xFFDCFCE7) else Color(0xFFFFEDD5)
                    val badgeColor = if (day.adherencePercent >= 80) StatusTakenGreen else StatusSkipOrange
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(badgeBg)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${day.adherencePercent}% Adherence",
                            color = badgeColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "No doses scheduled",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4 Count Pills: Total, Taken, Skipped, Pending
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Total
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Total", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${day.totalScheduled}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                // Taken
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFDCFCE7))
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Taken", fontSize = 10.sp, color = StatusTakenGreen)
                        Text("${day.takenDoses}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = StatusTakenGreen)
                    }
                }
                // Skipped
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFFEDD5))
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Skipped", fontSize = 10.sp, color = StatusSkipOrange)
                        Text("${day.skippedDoses}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = StatusSkipOrange)
                    }
                }
                // Pending
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE0F2FE))
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Pending", fontSize = 10.sp, color = MedicalBlue)
                        Text("${day.pendingDoses}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MedicalBlue)
                    }
                }
            }

            if (day.totalScheduled > 0 && day.adherencePercent != null) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (day.adherencePercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (day.adherencePercent >= 80) StatusTakenGreen else StatusSkipOrange,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}
