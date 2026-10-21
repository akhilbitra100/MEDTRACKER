# 💊 MEDTRACKER

### Smart Medication Tracking & Healthcare Management Powered by Gemini AI

**MEDTRACKER** is a modern, privacy-focused Android application designed to make medication management effortless, intelligent, and accessible. Built with Jetpack Compose and integrated with Google's Gemini API, it helps users digitize prescriptions with AI camera scanning, consult an AI pharmacotherapy assistant, manage dose schedules, track refill inventories, and monitor adherence history.

**Repository:** [akhilbitra100/MEDTRACKER](https://github.com/akhilbitra100/MEDTRACKER)

---

## 📸 Screenshots & App Tour

| **Home Dashboard** | **Prescription & Inventory** | **AI Medicine Information** |
|:---:|:---:|:---:|
| <img src="1000082773.jpg" alt="Home Screen" width="260"/> | <img src="1000082774.jpg" alt="Prescription List" width="260"/> | <img src="1000082775.jpg" alt="AI Med Info Chat" width="260"/> |
| *Dose summary, scheduled alerts, & AI prescription scanner* | *Active prescription cards, dose times, & refill tracker* | *Gemini-powered clinical guidance on uses & side effects* |

| **Adherence History** | **Settings & Voice Reminders** |
|:---:|:---:|
| <img src="1000082776.jpg" alt="Adherence History" width="260"/> | <img src="1000082777.jpg" alt="Settings" width="260"/> |
| *7-day adherence scoring, dose logs, and trends* | *TTS voice alerts in English, Hindi, and Telugu* |

---

## ✨ Features

- 📸 **AI Prescription Scanning (`Gemini AI`)** — Scan paper prescriptions using your camera; Gemini extracts medications, dosages, frequency, instructions, and automatically drafts your schedule.
- 💬 **Clinical Pharmacotherapy Assistant** — Ask questions about prescribed medicines, common side effects, mechanisms of action, food interactions, and precautions powered by Gemini AI.
- ⏰ **Dynamic Dose Schedules & Status** — Real-time tracking of scheduled, taken, skipped, and pending daily doses with quick action logging.
- 📦 **Refill & Inventory Tracker** — Automatic countdown of remaining pills/capsules with estimated run-out dates and low-stock alerts.
- 📊 **7-Day Adherence Analytics** — Visual weekly adherence scoring, daily breakdown, and progress tracking.
- 🗣️ **Multilingual Voice Reminders** — Spoken medication dose reminders using Android TextToSpeech (TTS) with support for English, Hindi (हिंदी), and Telugu (తెలుగు).
- 🎨 **Modern Native Android UI** — Clean, responsive, and accessible user experience built 100% with Jetpack Compose and Material 3 design principles.

---

## 🛠️ Tech Stack

| Technology | Purpose |
|---|---|
| **Kotlin** | Primary Android development language |
| **Jetpack Compose** | Declarative, modern UI development |
| **Google Gemini API** | AI prescription OCR extraction & clinical information chatbot |
| **Android Room DB** | Robust offline-first local database storage |
| **Retrofit & OkHttp** | Networking & REST client |
| **Kotlin Coroutines & Flow** | Asynchronous operations and reactive data streams |
| **Android TextToSpeech (TTS)** | Multilingual spoken voice dose reminders |
| **Gradle Kotlin DSL** | Modern dependency & build configuration |

---

## 📂 Project Structure

```text
MEDTRACKER/
├── app/
│   ├── src/main/java/com/medtracker/
│   │   ├── data/             # Room database, DAOs, entities, repositories
│   │   ├── di/               # Dependency injection modules
│   │   ├── network/          # Retrofit clients & Gemini API services
│   │   ├── ui/               # Jetpack Compose screens, components, theme
│   │   │   ├── home/         # Dashboard & daily dose summary
│   │   │   ├── medicines/    # Prescriptions & refill inventory
│   │   │   ├── medinfo/      # AI Clinical assistant chat
│   │   │   ├── history/      # Adherence history & analytics
│   │   │   └── settings/     # Voice TTS, sounds, & language settings
│   │   └── util/             # Notification managers, TTS helpers, alarm managers
├── gradle/                   # Gradle wrapper configuration
├── .env.example              # Template environment variables
├── build.gradle.kts          # Root build script
├── settings.gradle.kts       # Subproject declarations
└── README.md
