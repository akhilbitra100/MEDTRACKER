package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.R
import com.example.ai.ExtractedMedicine
import com.example.ui.components.SafetyDisclaimerBanner
import com.example.ui.components.ReminderTimesSelector
import com.example.notifications.AlarmScheduler
import com.example.ui.theme.DarkStatusTakenBg
import com.example.ui.theme.DarkStatusTakenGreen
import com.example.ui.theme.DarkStatusWarningAmber
import com.example.ui.theme.DarkStatusWarningBg
import com.example.ui.theme.HealthTeal
import com.example.ui.theme.MedicalBlue
import com.example.ui.theme.StatusTakenBg
import com.example.ui.theme.StatusTakenGreen
import com.example.ui.theme.StatusWarningAmber
import com.example.ui.theme.StatusWarningBg
import com.example.ui.viewmodel.ScannerUiState
import java.io.File

@Composable
fun ScannerScreen(
    scannerState: ScannerUiState,
    onSelectPreset: (Int) -> Unit,
    onSelectCustomUri: (Uri, Boolean) -> Unit,
    onRunScan: () -> Unit,
    onSetConfirmedDisclaimer: (Boolean) -> Unit,
    onUpdateExtractedMedicine: (ExtractedMedicine) -> Unit,
    onRemoveExtractedMedicine: (String) -> Unit,
    onConfirmAndSchedule: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    var showFullImageDialog by remember { mutableStateOf(false) }
    var tempCameraUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }

    // Camera capture launcher via FileProvider
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val uriStr = tempCameraUriString
        if (success && uriStr != null) {
            try {
                onSelectCustomUri(Uri.parse(uriStr), true)
            } catch (e: Exception) {
                localError = "Failed to process captured photo: ${e.message}"
            }
        } else {
            // User cancelled camera capture or capture failed - safely reset
            tempCameraUriString = null
        }
    }

    // Photo picker launcher (Gallery)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                onSelectCustomUri(uri, false)
            } catch (e: Exception) {
                localError = "Failed to open selected image: ${e.message}"
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("scanner_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DocumentScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.scanner_title),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                }
                Text(
                    text = stringResource(R.string.scanner_desc),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // 2. Safety Disclaimer Banner
        item {
            SafetyDisclaimerBanner()
        }

        // 3. Image Input Methods Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.scanner_step_1),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Buttons: Camera Capture and Gallery Picker
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                localError = null
                                try {
                                    val uri = createTempImageUri(context)
                                    tempCameraUriString = uri.toString()
                                    takePictureLauncher.launch(uri)
                                } catch (e: Exception) {
                                    localError = "Could not initialize camera capture: ${e.message ?: "Camera intent unavailable"}"
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("camera_capture_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.btn_take_photo),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                localError = null
                                try {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                } catch (e: Exception) {
                                    localError = "Could not open photo picker: ${e.message}"
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("gallery_photo_picker_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.btn_photo_picker),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Clearly Labeled Synthetic Demonstration Prescriptions for Testing (Requirement 15)
                    Text(
                        text = "Or test with synthetic clinical demonstration prescriptions:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SyntheticPresetButton(
                            title = "🧪 Demo Rx A (Infection)",
                            subtitle = "Amoxicillin, Lisinopril, Metformin",
                            isSelected = scannerState.selectedPresetId == 1 && scannerState.selectedImageUri == null,
                            onClick = { onSelectPreset(1) },
                            modifier = Modifier.weight(1f)
                        )
                        SyntheticPresetButton(
                            title = "🧪 Demo Rx B (Cardiology)",
                            subtitle = "Atorvastatin, Omeprazole, Metoprolol",
                            isSelected = scannerState.selectedPresetId == 2 && scannerState.selectedImageUri == null,
                            onClick = { onSelectPreset(2) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 4. Same-Screen Prescription Image Preview Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.preview_title),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = scannerState.sourceLabel,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            )
                        }

                        if (scannerState.previewBitmap != null || scannerState.selectedImageUri != null) {
                            IconButton(
                                onClick = { showFullImageDialog = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ZoomIn,
                                    contentDescription = "Zoom prescription image",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                            .clickable {
                                if (scannerState.previewBitmap != null || scannerState.selectedImageUri != null) {
                                    showFullImageDialog = true
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (scannerState.previewBitmap != null) {
                            Image(
                                bitmap = scannerState.previewBitmap.asImageBitmap(),
                                contentDescription = "Prescription Document Preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else if (scannerState.selectedImageUri != null) {
                            AsyncImage(
                                model = scannerState.selectedImageUri,
                                contentDescription = "User Prescription Photo Preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.DocumentScanner,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Capture a photo or select an image above",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }

                    // Feature: Image Quality Assessment Feedback Card (Priority 4 & 5)
                    if (scannerState.isEvaluatingQuality) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .padding(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Evaluating prescription focus, lighting, and contrast…",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                    } else if (scannerState.qualityReport != null) {
                        val report = scannerState.qualityReport
                        val hasIssues = report.isBlurry || report.isLowLight || report.hasShadows ||
                                report.hasPoorContrast || report.isLowResolution || report.isPotentiallyCropped

                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("image_quality_feedback_card"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (hasIssues) {
                                    if (isDark) DarkStatusWarningBg.copy(alpha = 0.4f) else StatusWarningBg.copy(alpha = 0.6f)
                                } else {
                                    if (isDark) DarkStatusTakenBg.copy(alpha = 0.3f) else StatusTakenBg.copy(alpha = 0.5f)
                                }
                            ),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (hasIssues) Icons.Default.Warning else Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = if (hasIssues) {
                                            if (isDark) DarkStatusWarningAmber else StatusWarningAmber
                                        } else {
                                            if (isDark) DarkStatusTakenGreen else StatusTakenGreen
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (hasIssues) "Quality Feedback (${report.overallReadability})"
                                        else "Prescription Quality: Good for Analysis",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }

                                if (report.warnings.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    report.warnings.forEach { warning ->
                                        Row(
                                            modifier = Modifier.padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Text(
                                                text = "• ",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (hasIssues) {
                                                        if (isDark) DarkStatusWarningAmber else StatusWarningAmber
                                                    } else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            )
                                            Text(
                                                text = warning,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    fontSize = 12.sp
                                                )
                                            )
                                        }
                                    }
                                }

                                if (report.autoEnhanced) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(HealthTeal.copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "✨ Restrained contrast & brightness adjustment applied for OCR (Note: Genuinely unreadable text cannot be invented).",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = HealthTeal,
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                    }
                                }

                                if (hasIssues) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "You can still proceed with AI extraction below. Any unclear handwriting or missing fields will be flagged for your review.",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Scan Action Button with Loading Progress (Requirement 14)
                    Button(
                        onClick = onRunScan,
                        enabled = !scannerState.isAnalyzing &&
                                (scannerState.previewBitmap != null || scannerState.selectedImageUri != null),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("extract_with_ai_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (scannerState.isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = scannerState.analysisStageText.ifEmpty { stringResource(R.string.analyzing_prescription) },
                                style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.btn_extract_ai),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            )
                        }
                    }

                    // Local Error or API Error Card
                    val displayedError = scannerState.scanError ?: localError
                    if (displayedError != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
                                .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Prescription Extraction Notice",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    )
                                    Text(
                                        text = displayedError,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        ),
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Extracted Results & Verification Section on the SAME screen (Requirement 11)
        if (scannerState.extractedMedicines.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) DarkStatusTakenBg.copy(alpha = 0.35f) else StatusTakenBg.copy(alpha = 0.5f)
                    ),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isDark) DarkStatusTakenGreen else StatusTakenGreen,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "2. Review Extracted Medications (${scannerState.extractedMedicines.size})",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Text(
                                    text = stringResource(R.string.verify_instructions),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }

                        if (scannerState.isSyntheticDemo) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "🧪 Labeled Synthetic Demonstration Prescription for Testing",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Extracted Medicine Cards with detailed fields and editing
            itemsIndexed(
                scannerState.extractedMedicines,
                key = { _, item -> item.id }
            ) { index, med ->
                ExtractedMedicineDetailedCard(
                    index = index + 1,
                    medicine = med,
                    onUpdate = onUpdateExtractedMedicine,
                    onRemove = { onRemoveExtractedMedicine(med.id) }
                )
            }

            // Explicit User Confirmation Checkbox (Requirement 13)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSetConfirmedDisclaimer(!scannerState.userConfirmedDisclaimer) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = scannerState.userConfirmedDisclaimer,
                            onCheckedChange = { onSetConfirmedDisclaimer(it) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.checkbox_verify_disclaimer),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp
                            )
                        )
                    }
                }
            }

            // Confirm Schedule Button (Enabled ONLY when explicit user confirmation is checked AND all meds have reminder times)
            item {
                Spacer(modifier = Modifier.height(4.dp))
                val unconfiguredMedicines = scannerState.extractedMedicines.filter { it.reminderTimes.isEmpty() }

                if (unconfiguredMedicines.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                            .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "⚠️ Please configure at least one reminder time for ${unconfiguredMedicines.first().name} before saving schedule.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    onClick = onConfirmAndSchedule,
                    enabled = scannerState.userConfirmedDisclaimer && unconfiguredMedicines.isEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("confirm_and_schedule_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDark) DarkStatusTakenGreen else StatusTakenGreen,
                        contentColor = if (isDark) Color(0xFF003915) else Color.White,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            !scannerState.userConfirmedDisclaimer -> "Check Confirmation Above to Schedule"
                            unconfiguredMedicines.isNotEmpty() -> "Set Reminder Times to Confirm"
                            else -> stringResource(R.string.btn_confirm_schedule)
                        },
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Full Screen Zoom Dialog for Prescription Document
    if (showFullImageDialog) {
        Dialog(onDismissRequest = { showFullImageDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Original Prescription Document",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        IconButton(onClick = { showFullImageDialog = false }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(440.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (scannerState.previewBitmap != null) {
                            Image(
                                bitmap = scannerState.previewBitmap.asImageBitmap(),
                                contentDescription = "Full Prescription Document",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else if (scannerState.selectedImageUri != null) {
                            AsyncImage(
                                model = scannerState.selectedImageUri,
                                contentDescription = "Full Prescription Photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyntheticPresetButton(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
            .border(
                1.5.dp,
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 10.dp)
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            )
        }
    }
}

@Composable
private fun ExtractedMedicineDetailedCard(
    index: Int,
    medicine: ExtractedMedicine,
    onUpdate: (ExtractedMedicine) -> Unit,
    onRemove: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    var name by remember(medicine.id) { mutableStateOf(medicine.name) }
    var strength by remember(medicine.id) { mutableStateOf(medicine.strength) }
    var form by remember(medicine.id) { mutableStateOf(medicine.form) }
    var dosageInstructions by remember(medicine.id) { mutableStateOf(medicine.dosageInstructions) }
    var originalFrequency by remember(medicine.id) { mutableStateOf(medicine.originalFrequency) }
    var timingInstructions by remember(medicine.id) { mutableStateOf(medicine.timingInstructions) }
    var additionalInstructions by remember(medicine.id) { mutableStateOf(medicine.additionalInstructions) }
    var morning by remember(medicine.id) { mutableStateOf(medicine.morningSlot) }
    var afternoon by remember(medicine.id) { mutableStateOf(medicine.afternoonSlot) }
    var night by remember(medicine.id) { mutableStateOf(medicine.nightSlot) }
    var mealInstruction by remember(medicine.id) { mutableStateOf(medicine.mealInstruction) }
    var mealInstructionRaw by remember(medicine.id) { mutableStateOf(medicine.mealInstructionRaw) }
    var currentQuantityStr by remember(medicine.id) { mutableStateOf(medicine.currentQuantity.toString()) }
    var quantityUnit by remember(medicine.id) { mutableStateOf(medicine.quantityUnit) }
    var doseQuantityStr by remember(medicine.id) { mutableStateOf(medicine.doseQuantity.toString()) }
    var lowStockThresholdStr by remember(medicine.id) { mutableStateOf(medicine.lowStockThreshold.toString()) }
    var refillAlertEnabled by remember(medicine.id) { mutableStateOf(medicine.refillAlertEnabled) }
    var reminderTimes by remember(medicine.id) { mutableStateOf(medicine.reminderTimes) }

    fun notifyUpdate() {
        val hasMorning = reminderTimes.any { AlarmScheduler.parseSlotFromTime(it) == "MORNING" }
        val hasAfternoon = reminderTimes.any { AlarmScheduler.parseSlotFromTime(it) == "AFTERNOON" }
        val hasNight = reminderTimes.any { AlarmScheduler.parseSlotFromTime(it) == "NIGHT" }

        onUpdate(
            medicine.copy(
                name = name,
                strength = strength,
                form = form,
                dosageInstructions = dosageInstructions,
                originalFrequency = originalFrequency,
                timingInstructions = timingInstructions,
                additionalInstructions = additionalInstructions,
                morningSlot = if (reminderTimes.isNotEmpty()) hasMorning else morning,
                afternoonSlot = if (reminderTimes.isNotEmpty()) hasAfternoon else afternoon,
                nightSlot = if (reminderTimes.isNotEmpty()) hasNight else night,
                mealInstruction = mealInstruction,
                mealInstructionRaw = mealInstructionRaw,
                currentQuantity = currentQuantityStr.toIntOrNull() ?: 30,
                quantityUnit = quantityUnit.ifBlank { "tablets" },
                doseQuantity = doseQuantityStr.toIntOrNull() ?: 1,
                lowStockThreshold = lowStockThresholdStr.toIntOrNull() ?: 5,
                refillAlertEnabled = refillAlertEnabled,
                reminderTimes = reminderTimes,
                specifiedTimeInPrescription = medicine.specifiedTimeInPrescription
            )
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("extracted_card_$index"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row with Medicine Number and Delete Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Medication #$index",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove this medication",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Uncertainty Warning Banner if information is unclear or missing (Requirement 10)
            if (medicine.isUncertain) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isDark) DarkStatusWarningBg.copy(alpha = 0.4f)
                            else StatusWarningBg
                        )
                        .border(
                            1.dp,
                            if (isDark) DarkStatusWarningAmber else StatusWarningAmber,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Uncertainty Warning",
                            tint = if (isDark) DarkStatusWarningAmber else StatusWarningAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.uncertainty_warning_title),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) DarkStatusWarningAmber else Color(0xFF92400E)
                                )
                            )
                            if (medicine.uncertainFields.isNotEmpty()) {
                                Text(
                                    text = "Uncertain fields: ${medicine.uncertainFields.joinToString(", ")}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFF92400E),
                                        fontSize = 12.sp
                                    )
                                )
                            }
                            Text(
                                text = medicine.uncertaintyReason.ifEmpty {
                                    "Handwriting or dosage text is partially unclear. Please verify against your prescription bottle before saving."
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF92400E),
                                    fontSize = 12.sp
                                ),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // 1. Medicine Name & Strength (Editable)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        notifyUpdate()
                    },
                    label = { Text(stringResource(R.string.field_med_name)) },
                    modifier = Modifier.weight(1.3f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = strength,
                    onValueChange = {
                        strength = it
                        notifyUpdate()
                    },
                    label = { Text(stringResource(R.string.field_strength)) },
                    modifier = Modifier.weight(0.9f),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Dosage Form (e.g. Tablet, Capsule)
            OutlinedTextField(
                value = form,
                onValueChange = {
                    form = it
                    notifyUpdate()
                },
                label = { Text(stringResource(R.string.field_form)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Dosage Instructions (e.g. "Take 1 capsule")
            OutlinedTextField(
                value = dosageInstructions,
                onValueChange = {
                    dosageInstructions = it
                    notifyUpdate()
                },
                label = { Text(stringResource(R.string.field_dosage)) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 4. Original Frequency (e.g. "BID", "Once daily")
            OutlinedTextField(
                value = originalFrequency,
                onValueChange = {
                    originalFrequency = it
                    notifyUpdate()
                },
                label = { Text(stringResource(R.string.field_frequency)) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 5. Timing Instructions
            OutlinedTextField(
                value = timingInstructions,
                onValueChange = {
                    timingInstructions = it
                    notifyUpdate()
                },
                label = { Text(stringResource(R.string.field_timing)) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 6. Additional Instructions / Warnings
            OutlinedTextField(
                value = additionalInstructions,
                onValueChange = {
                    additionalInstructions = it
                    notifyUpdate()
                },
                label = { Text(stringResource(R.string.field_notes)) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Time Slots Selection Chips
            Text(
                text = "Daily Schedule Slots:",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = morning,
                    onClick = {
                        morning = !morning
                        notifyUpdate()
                    },
                    label = { Text("Morning") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
                FilterChip(
                    selected = afternoon,
                    onClick = {
                        afternoon = !afternoon
                        notifyUpdate()
                    },
                    label = { Text("Afternoon") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
                FilterChip(
                    selected = night,
                    onClick = {
                        night = !night
                        notifyUpdate()
                    },
                    label = { Text("Night") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                )
            }
            Spacer(modifier = Modifier.height(14.dp))

            // Feature 2: Smart Meal-Time Scheduling Section
            Text(
                text = "Prescription Meal Instruction:",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            if (mealInstructionRaw.isNotBlank()) {
                Text(
                    text = "Original Rx text: \"$mealInstructionRaw\"",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    ),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            if (medicine.mealInstructionFlagged) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(StatusWarningBg)
                        .border(1.dp, StatusWarningAmber, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "⚠️ Unclear or conflicting meal instruction flagged on prescription. Please verify with doctor's order before confirming schedule.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF92400E),
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Meal rule selection chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "Before food",
                    "After food",
                    "With food",
                    "No meal instruction specified"
                ).forEach { option ->
                    val isSelected = mealInstruction.equals(option, ignoreCase = true)
                    val label = when (option) {
                        "No meal instruction specified" -> "None"
                        else -> option
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            mealInstruction = option
                            notifyUpdate()
                        },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Feature 4: Per-Medicine Reminder Time Selection & Review
            ReminderTimesSelector(
                reminderTimes = reminderTimes,
                onTimesChanged = { updatedList ->
                    reminderTimes = updatedList
                    notifyUpdate()
                },
                showErrorIfEmpty = reminderTimes.isEmpty(),
                frequencyHint = originalFrequency.ifBlank { timingInstructions },
                specifiedPrescriptionTime = medicine.specifiedTimeInPrescription
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Feature 3: Medicine Refill & Supply Tracking
            Text(
                text = "Refill & Supply Tracking:",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = currentQuantityStr,
                    onValueChange = {
                        currentQuantityStr = it.filter { ch -> ch.isDigit() }
                        notifyUpdate()
                    },
                    label = { Text("Starting Qty", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = quantityUnit,
                    onValueChange = {
                        quantityUnit = it
                        notifyUpdate()
                    },
                    label = { Text("Unit", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = doseQuantityStr,
                    onValueChange = {
                        doseQuantityStr = it.filter { ch -> ch.isDigit() }
                        notifyUpdate()
                    },
                    label = { Text("Qty/Dose", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = lowStockThresholdStr,
                    onValueChange = {
                        lowStockThresholdStr = it.filter { ch -> ch.isDigit() }
                        notifyUpdate()
                    },
                    label = { Text("Alert Below", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}


private fun createTempImageUri(context: Context): Uri {
    val appContext = context.applicationContext ?: context
    val storageDir = appContext.externalCacheDir ?: appContext.cacheDir
    if (!storageDir.exists()) {
        storageDir.mkdirs()
    }
    val tempFile = File.createTempFile("medtracker_camera_", ".jpg", storageDir).apply {
        createNewFile()
    }
    val authority = "${appContext.packageName}.fileprovider"
    return FileProvider.getUriForFile(appContext, authority, tempFile)
}
