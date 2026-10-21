# 💊 MEDTRACKER

### Smart Medication Tracking & Healthcare Management

MEDTRACKER is an Android application designed to make medication management simpler, more organized, and accessible. It aims to help users keep track of their medicines, manage medication schedules, and stay consistent with their healthcare routines.

The project is built using modern Android development tools and includes integration support for Google's Gemini API.

**Repository:** [akhilbitra100/MEDTRACKER](https://github.com/akhilbitra100/MEDTRACKER)

---

## ✨ Features

- 💊 **Medication Management** — Organize and keep track of medicines.
- ⏰ **Medication Schedules** — Support medication routine management.
- 📋 **Prescription Tracking** — Keep medication-related information organized.
- 🔔 **Reminder-Focused Design** — Help users maintain consistent medication routines.
- 🤖 **Gemini API Integration** — Supports AI-powered functionality where implemented and configured.
- 📱 **Native Android App** — Built with Android development technologies.
- 🎨 **Modern UI** — Uses Jetpack Compose for Android user interface development.

*Note: The feature list describes the project's intended functionality. Individual features depend on their implementation in the current source code.*

## 🛠️ Tech Stack

| Technology | Purpose |
|---|---|
| Kotlin | Primary Android development language |
| Jetpack Compose | UI development |
| Gradle Kotlin DSL | Build configuration |
| Android SDK | Android application development |
| Room | Local database support |
| Retrofit & OkHttp | Networking |
| Kotlin Coroutines | Asynchronous operations |
| Google Gemini API | AI-powered functionality |
| Firebase libraries | Firebase integration support |

## 📂 Project Structure

```text
MEDTRACKER/
├── app/                  # Android application source code
├── gradle/               # Gradle wrapper and configuration
├── .env.example          # Example environment configuration
├── .gitignore            # Git ignored files
├── build.gradle.kts      # Root build configuration
├── gradle.properties     # Gradle project properties
├── settings.gradle.kts   # Project and module settings
└── metadata.json         # Project metadata
```

## 🚀 Getting Started

### Prerequisites

Before running the project, install:

- [Android Studio](https://developer.android.com/studio)
- Android SDK compatible with the project's configuration
- Git
- A configured Android emulator or physical Android device

### 1. Clone the repository

```bash
git clone https://github.com/akhilbitra100/MEDTRACKER.git
```

### 2. Open the project

Open Android Studio, select **Open**, and choose the cloned `MEDTRACKER` folder.

### 3. Sync Gradle

Allow Android Studio to download the required Gradle plugins and dependencies. Resolve any SDK or configuration requirements reported by the IDE.

### 4. Configure the Gemini API key

The project includes an `.env.example` file for environment configuration.

If you use Gemini API functionality, obtain an API key from [Google AI Studio](https://aistudio.google.com/) and configure it through the supported secrets mechanism.

The example configuration contains:

```dotenv
GEMINI_API_KEY=MY_GEMINI_API_KEY
```

Replace the placeholder with your own key using the appropriate local configuration. Never commit real API keys or other credentials to GitHub.

### 5. Run the application

Select an Android emulator or connected Android device in Android Studio and click **Run**.

You can also build the debug APK from the project root using the Gradle wrapper:

**Windows:**

```bash
gradlew.bat assembleDebug
```

**macOS/Linux:**

```bash
./gradlew assembleDebug
```

If the build succeeds, the debug APK is normally generated under:

```text
app/build/outputs/apk/debug/
```

## 🔐 Security & Privacy

- Do not commit API keys, passwords, signing keys, or other secrets.
- Keep local environment files and private configuration out of version control.
- Protect personal medication and health information.
- Review API permissions and data-handling practices before using the app with real health information.

## ⚠️ Medical Disclaimer

MEDTRACKER is intended as a medication-management aid and is not a substitute for professional medical advice, diagnosis, or treatment.

Always follow your healthcare professional's instructions. Do not change medication dosages or discontinue prescribed medicines based solely on information provided by an application or AI system.

## 🔮 Future Improvements

Potential enhancements include:

- Medication reminders and notification scheduling
- Medication history and adherence tracking
- Prescription details and refill tracking
- Personalized medication-management assistance
- Improved accessibility and user experience
- Data backup and synchronization

## 🤝 Contributing

Contributions, suggestions, and bug reports are welcome.

1. Fork this repository.
2. Create a feature branch.
3. Make and test your changes.
4. Submit a pull request describing your improvements.

## 👨‍💻 Author

**Akhil Bitra**

- GitHub: [@akhilbitra100](https://github.com/akhilbitra100)
- Project: [MEDTRACKER](https://github.com/akhilbitra100/MEDTRACKER)

## 📄 License

No license has been specified in this README. Check the repository for an existing license before reusing or distributing the source code.

---

⭐ If you find this project useful, consider starring the repository!

**Made with ❤️ to support smarter medication management.**
