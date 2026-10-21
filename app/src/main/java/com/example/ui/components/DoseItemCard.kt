package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.example.ui.theme.HealthTeal
import com.example.ui.theme.MedicalBlue
import com.example.ui.theme.StatusPendingBg
import com.example.ui.theme.StatusPendingBlue
import com.example.ui.theme.StatusSkipBg
import com.example.ui.theme.StatusSkipOrange
import com.example.ui.theme.StatusTakenBg
import com.example.ui.theme.StatusTakenGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.material.icons.filled.VolumeUp
import com.example.ui.theme.HealthTealLight
import com.example.ui.theme.HealthTealDark

@Composable
fun DoseItemCard(
    dose: DoseRecordEntity,
    onTaken: () -> Unit,
    onSkip: () -> Unit,
    onUndo: () -> Unit,
    onSpeak: (() -> Unit)? = null,
    onCancelSnooze: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {

    val isTaken = dose.status.equals("TAKEN", ignoreCase = true)
    val isSkipped = dose.status.equals("SKIPPED", ignoreCase = true)
    val isPending = !isTaken && !isSkipped

    val borderStroke = when {
        isTaken -> StatusTakenGreen.copy(alpha = 0.4f)
        isSkipped -> StatusSkipOrange.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dose_card_${dose.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isTaken -> StatusTakenBg.copy(alpha = 0.25f)
                isSkipped -> StatusSkipBg.copy(alpha = 0.2f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPending) 2.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, borderStroke, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            // Header Row: Medication Name, Strength, and Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isTaken -> StatusTakenBg
                                    isSkipped -> StatusSkipBg
                                    else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Medication,
                            contentDescription = "Pill icon",
                            tint = when {
                                isTaken -> StatusTakenGreen
                                isSkipped -> StatusSkipOrange
                                else -> MedicalBlue
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = dose.medicineName,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Strength pill
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = dose.medicineStrength,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                            Text(
                                text = "•  ${dose.scheduledTime}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                        // Meal Instruction Badge (Feature 2)
                        if (dose.mealInstruction.isNotBlank() && !dose.mealInstruction.contains("No meal", ignoreCase = true)) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(HealthTealLight.copy(alpha = 0.7f))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "🍽️ ${dose.mealInstruction}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = HealthTealDark
                                    )
                                )
                            }
                        }

                        // Pending Snooze Indicator Badge (Feature 3)
                        val isSnoozed = isPending && dose.snoozedUntil > System.currentTimeMillis()
                        if (isSnoozed) {
                            Spacer(modifier = Modifier.height(4.dp))
                            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                            val snoozedTimeStr = sdf.format(Date(dose.snoozedUntil))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Alarm,
                                        contentDescription = null,
                                        tint = MedicalBlue,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "⏰ Snoozed until $snoozedTimeStr",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                    if (onCancelSnooze != null) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "(Cancel)",
                                            modifier = Modifier.clickable { onCancelSnooze() },
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 11.sp,
                                                color = MedicalBlue
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onSpeak != null) {
                        androidx.compose.material3.IconButton(
                            onClick = onSpeak,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Listen to spoken reminder",
                                tint = MedicalBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    // Status Badge
                    StatusPill(status = dose.status)
                }

            }

            // Prescribed Instructions (if available)
            if (dose.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Instructions: ${dose.notes}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons
            if (isPending) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Skip Button (Min 48dp touch target)
                    OutlinedButton(
                        onClick = onSkip,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("skip_button_${dose.id}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = StatusSkipOrange
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusSkipOrange)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.btn_skip),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.btn_skip),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        )
                    }

                    // Taken Button (Min 48dp touch target, equal weight for balanced alignment)
                    Button(
                        onClick = onTaken,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("taken_button_${dose.id}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StatusTakenGreen,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.btn_take_dose),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.btn_take_dose),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        )
                    }
                }
            } else {
                // Completed State with Undo option
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val timestampText = dose.actionTimestamp?.let {
                        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                        "Recorded at ${sdf.format(Date(it))}"
                    } ?: "Recorded"

                    Text(
                        text = timestampText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    )

                    TextButton(
                        onClick = onUndo,
                        modifier = Modifier.testTag("undo_button_${dose.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.btn_undo),
                            modifier = Modifier.size(16.dp),
                            tint = HealthTeal
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.btn_undo),
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = HealthTeal,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusPill(status: String) {
    val isTaken = status.equals("TAKEN", ignoreCase = true)
    val isSkipped = status.equals("SKIPPED", ignoreCase = true)

    val label = when {
        isTaken -> stringResource(R.string.stat_taken)
        isSkipped -> stringResource(R.string.stat_skipped)
        else -> stringResource(R.string.stat_pending)
    }
    val bg = when {
        isTaken -> StatusTakenBg
        isSkipped -> StatusSkipBg
        else -> StatusPendingBg
    }
    val text = when {
        isTaken -> StatusTakenGreen
        isSkipped -> StatusSkipOrange
        else -> StatusPendingBlue
    }
    val icon = when {
        isTaken -> Icons.Default.Check
        isSkipped -> Icons.Default.Close
        else -> null
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = text,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            )
        }
    }
}
