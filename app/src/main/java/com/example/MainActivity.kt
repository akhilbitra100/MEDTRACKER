package com.example

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.MedicineEntity
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MedicineInfoScreen
import com.example.ui.screens.MedicinesScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.HealthTeal
import com.example.ui.theme.MedTrackerTheme
import com.example.ui.theme.MedicalBlue
import com.example.ui.viewmodel.DoseBuddyViewModel
import com.example.ui.viewmodel.MedTrackerViewModel
import com.example.ui.viewmodel.ScreenTab
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Feature 1: Support the device's available display refresh rate
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val display = display
                display?.supportedModes?.maxByOrNull { it.refreshRate }?.let { mode ->
                    window.attributes.preferredDisplayModeId = mode.modeId
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                val display = windowManager.defaultDisplay
                @Suppress("DEPRECATION")
                display?.supportedModes?.maxByOrNull { it.refreshRate }?.let { mode ->
                    window.attributes.preferredDisplayModeId = mode.modeId
                }
            }
        } catch (_: Exception) {
            // Fallback gracefully on devices without custom display modes
        }

        setContent {
            val viewModel: MedTrackerViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val languageCode by viewModel.languageCode.collectAsStateWithLifecycle()

            val safeLang = (languageCode ?: "system").lowercase()
            val targetLocale = remember(safeLang) {
                when (safeLang) {
                    "hi" -> Locale("hi")
                    "te" -> Locale("te")
                    "en" -> Locale.ENGLISH
                    else -> Locale.getDefault()
                }
            }

            val baseConfig = LocalConfiguration.current
            val localizedConfig = remember(targetLocale, baseConfig) {
                try {
                    Configuration(baseConfig).apply {
                        setLocale(targetLocale)
                    }
                } catch (_: Throwable) {
                    baseConfig
                }
            }
            val currentContext = LocalContext.current
            val localizedContext = remember(targetLocale, currentContext, localizedConfig) {
                try {
                    val configContext = currentContext.createConfigurationContext(localizedConfig)
                    object : android.content.ContextWrapper(currentContext) {
                        override fun getResources(): android.content.res.Resources {
                            return configContext.resources
                        }
                    }
                } catch (_: Throwable) {
                    currentContext
                }
            }

            CompositionLocalProvider(
                LocalConfiguration provides localizedConfig,
                LocalContext provides localizedContext,
                LocalActivityResultRegistryOwner provides this@MainActivity
            ) {
                MedTrackerTheme(themeMode = themeMode) {
                    MedTrackerApp(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun MedTrackerApp(viewModel: MedTrackerViewModel = viewModel()) {
    val isAppReady by viewModel.isAppReady.collectAsStateWithLifecycle()
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val todayDoses by viewModel.todayDoses.collectAsStateWithLifecycle()
    val allMedicines by viewModel.allMedicines.collectAsStateWithLifecycle()
    val allDoseHistory by viewModel.allDoseHistory.collectAsStateWithLifecycle()
    val scheduleTimes by viewModel.scheduleTimes.collectAsStateWithLifecycle()
    val scannerState by viewModel.scannerState.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val languageCode by viewModel.languageCode.collectAsStateWithLifecycle()
    val soundEnabled by viewModel.soundEnabled.collectAsStateWithLifecycle()
    val voiceRemindersEnabled by viewModel.voiceRemindersEnabled.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isChatLoading by viewModel.isChatLoading.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userMessage) {
        userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    // Feature 2: Loading experience / App Launch screen
    if (!isAppReady) {
        AppLaunchScreen()
        return
    }

    // Handle back button on sub-screens
    if (currentTab != ScreenTab.HOME) {
        BackHandler {
            viewModel.navigateTo(ScreenTab.HOME)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.testTag("medtracker_bottom_nav")
            ) {
                // 1. Home
                NavigationBarItem(
                    selected = currentTab == ScreenTab.HOME,
                    onClick = { viewModel.navigateTo(ScreenTab.HOME) },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == ScreenTab.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                            contentDescription = stringResource(R.string.nav_home)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.nav_home),
                            fontWeight = if (currentTab == ScreenTab.HOME) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.testTag("nav_item_home")
                )

                // 2. Medicines
                NavigationBarItem(
                    selected = currentTab == ScreenTab.MEDICINES,
                    onClick = { viewModel.navigateTo(ScreenTab.MEDICINES) },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == ScreenTab.MEDICINES) Icons.Filled.Medication else Icons.Outlined.Medication,
                            contentDescription = stringResource(R.string.nav_medicines)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.nav_medicines),
                            fontWeight = if (currentTab == ScreenTab.MEDICINES) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.testTag("nav_item_medicines")
                )

                // 3. Medicine Info (Feature 5: Dedicated Chatbot section)
                NavigationBarItem(
                    selected = currentTab == ScreenTab.INFO,
                    onClick = { viewModel.navigateTo(ScreenTab.INFO) },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == ScreenTab.INFO) Icons.Filled.MedicalServices else Icons.Outlined.MedicalServices,
                            contentDescription = stringResource(R.string.nav_info)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.nav_info),
                            fontWeight = if (currentTab == ScreenTab.INFO) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.testTag("nav_item_info")
                )

                // 4. History
                NavigationBarItem(
                    selected = currentTab == ScreenTab.HISTORY,
                    onClick = { viewModel.navigateTo(ScreenTab.HISTORY) },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == ScreenTab.HISTORY) Icons.Filled.History else Icons.Outlined.History,
                            contentDescription = stringResource(R.string.nav_history)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.nav_history),
                            fontWeight = if (currentTab == ScreenTab.HISTORY) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.testTag("nav_item_history")
                )

                // 5. Settings
                NavigationBarItem(
                    selected = currentTab == ScreenTab.SETTINGS,
                    onClick = { viewModel.navigateTo(ScreenTab.SETTINGS) },
                    icon = {
                        Icon(
                            imageVector = if (currentTab == ScreenTab.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.nav_settings)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.nav_settings),
                            fontWeight = if (currentTab == ScreenTab.SETTINGS) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.testTag("nav_item_settings")
                )
            }
        }
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)

        // Smooth restrained animated transition between screens
        AnimatedContent(
            targetState = currentTab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "screen_tab_transition"
        ) { targetTab ->
            when (targetTab) {
                ScreenTab.HOME -> {
                    HomeScreen(
                        todayFormattedDate = viewModel.todayFormattedDate,
                        todayDoses = todayDoses,
                        scheduleTimes = scheduleTimes,
                        medicines = allMedicines,
                        onNavigateToScan = { viewModel.navigateTo(ScreenTab.SCANNER) },
                        onNavigateToMedicines = { viewModel.navigateTo(ScreenTab.MEDICINES) },
                        onTaken = { dose -> viewModel.markDoseTaken(dose) },
                        onSkip = { dose -> viewModel.markDoseSkipped(dose) },
                        onUndo = { dose -> viewModel.undoDose(dose) },
                        onSpeakDose = { dose -> viewModel.speakReminderForDose(dose) },
                        onCancelSnooze = { dose -> viewModel.cancelSnooze(dose) },
                        onTestReminder = { viewModel.triggerTestNotification() },
                        modifier = contentModifier
                    )
                }
                ScreenTab.SCANNER -> {
                    ScannerScreen(
                        scannerState = scannerState,
                        onSelectPreset = { presetId -> viewModel.selectPreset(presetId) },
                        onSelectCustomUri = { uri, isCamera -> viewModel.selectCustomImage(uri, isCamera) },
                        onRunScan = { viewModel.runScan() },
                        onSetConfirmedDisclaimer = { confirmed -> viewModel.setUserConfirmedDisclaimer(confirmed) },
                        onUpdateExtractedMedicine = { updated -> viewModel.updateExtractedMedicine(updated) },
                        onRemoveExtractedMedicine = { id -> viewModel.removeExtractedMedicine(id) },
                        onConfirmAndSchedule = { viewModel.confirmAndAddToSchedule() },
                        modifier = contentModifier
                    )
                }
                ScreenTab.MEDICINES -> {
                    MedicinesScreen(
                        medicines = allMedicines,
                        onAddManual = { med -> viewModel.addManualMedicine(med) },
                        onDeleteMedicine = { id -> viewModel.deleteMedicine(id) },
                        onNavigateToScan = { viewModel.navigateTo(ScreenTab.SCANNER) },
                        onUpdateStock = { id, q, u, dq, th, al ->
                            viewModel.updateMedicineStock(id, q, u, dq, th, al)
                        },
                        onUpdateMedicine = { oldMed, updatedMed ->
                            viewModel.updateMedicine(oldMed, updatedMed)
                        },
                        modifier = contentModifier
                    )
                }
                ScreenTab.INFO -> {
                    MedicineInfoScreen(
                        activeMedicines = allMedicines,
                        chatMessages = chatMessages,
                        isLoading = isChatLoading,
                        onSendMessage = { query -> viewModel.sendChatMessage(query) },
                        modifier = contentModifier
                    )
                }
                ScreenTab.HISTORY -> {
                    HistoryScreen(
                        doseHistory = allDoseHistory,
                        modifier = contentModifier
                    )
                }
                ScreenTab.SETTINGS -> {
                    SettingsScreen(
                        scheduleTimes = scheduleTimes,
                        languageCode = languageCode,
                        soundEnabled = soundEnabled,
                        voiceRemindersEnabled = voiceRemindersEnabled,
                        onSetLanguageCode = { code -> viewModel.setLanguageCode(code) },
                        onSetSoundEnabled = { enabled -> viewModel.setSoundEnabled(enabled) },
                        onSetVoiceRemindersEnabled = { enabled -> viewModel.setVoiceRemindersEnabled(enabled) },
                        onTestVoiceReminder = { viewModel.testVoiceReminder() },
                        onTestNotification = { viewModel.triggerTestNotification() },
                        modifier = contentModifier
                    )
                }
            }
        }
    }
}

@Composable
private fun AppLaunchScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MedicalBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Medication,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "MedTracker",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "AI-Powered Prescription Scanner & Medicine Reminder",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            )
            Spacer(modifier = Modifier.height(32.dp))
            CircularProgressIndicator(
                color = HealthTeal,
                modifier = Modifier.size(36.dp),
                strokeWidth = 3.dp
            )
        }
    }
}

// Backwards compatibility alias
@Composable
fun DoseBuddyApp(viewModel: DoseBuddyViewModel = viewModel()) = MedTrackerApp(viewModel)
