# JobSearch AI: The Professional Career Command Center

**JobSearch AI** is a high-performance, enterprise-grade Android application designed to revolutionize the job application workflow. Built with modern Android architecture, a **Hybrid AI Engine**, and an emphasis on security and testability, it equips job seekers with tools to tailor resumes, generate cover letters, and master interviews—all with hardware-backed on-device privacy.

---

## 🌟 Key Features

### 📄 The Resume Studio & Document Workbench
A block-based document workbench and rich editor that mirrors final PDF outputs in real time.
- **Live Preview**: Inspect PDF margins, line heights, and layout rules on digital A4 preview sheets.
- **Rich Formatting**: Toggle bold, italic, and bullet point structures.
- **Steered Generation**: Provide custom guidance (e.g. *"Highlight leadership and remote experience"*) during AI generation.

### 🧠 Hybrid AI Engine
Combines local privacy with cloud reasoning power:
- **Local AI (Gemma 4 E2B)**: Executes job description cleaning, match scoring, auto-tagging, and interview feedback locally via LiteRT-LM.
- **Cloud AI (Gemini 1.5/3.5)**: Handles complex document tailoring for multi-page resumes and cover letters.

### 🎯 ATS Match Analysis & Skill Hub
- **Keyword Visualizer**: Interactive chip view comparing "Found" vs. "Missing" skills against job descriptions.
- **Match Hub**: Provides an overall match score and generates custom questions to bridge missing skill gaps.

### 🎙️ AI Interview Coach
- **Voice Practice**: Uses Android Speech-to-Text for practicing job-specific interview questions out loud.
- **Evaluation Reports**: Provides strength/weakness feedback and model answer scripts.

### 🔒 Secure Desktop Sync (Chrome Extension Protocol)
Pairs over local Wi-Fi with an embedded Netty Ktor server (`SyncServer`). Features **PIN Pairing** and a **30-day Bearer Token** session security protocol to safely sync job postings clipped from LinkedIn or Indeed.

---

## 🛠️ Architecture & Engineering Best Practices

- **Clean Architecture & MVVM:** Strict Unidirectional Data Flow (UDF) with state hoisting using Kotlin `StateFlow` and `collectAsStateWithLifecycle()`.
- **Hardware-Backed Security:** Sensitive API keys and credentials are encrypted using `EncryptedSharedPreferences` backed by the **Android KeyStore system**.
- **Network Security Configuration:** Strict HTTPS enforcement (`network_security_config.xml`), restricting cleartext HTTP solely to local loopback pairing interfaces.
- **Scoped Storage Compliance:** Built using modern Scoped Storage and Storage Access Framework (SAF) with 0 broad storage permissions.
- **Dependency Injection:** Fully modularized using **Hilt** (`@HiltViewModel`, `@ApplicationScope`).
- **Gradle Version Catalog:** Centralized dependency management via `gradle/libs.versions.toml`.
- **PDF Core:** Multi-page PDF rendering via **PDFBox-Android** with custom `PdfLayoutEngine` coordinate math.

---

## 🧪 Comprehensive Test Suite

All tests are organized in a unified testing package (`com.example.jobsearch.testing`):

- **Unit Tests:** JSON scrubbing (`DataJsonScrubberTest`), resume parsing (`DataResumeParsingTest`), cover letter composition (`AiCoverLetterComposerTest`), and Sync Server security (`SyncServerSecurityTest`).
- **ViewModel Coroutine Tests:** Testing UI state flows and coroutines using `StandardTestDispatcher` and `runTest` (`JobListViewModelTest`, `JobDetailViewModelTest`).
- **Compose UI Tests:** Testing Composable rendering and click callbacks using `createComposeRule()` (`JobListScreenUiTest`).
- **End-to-End (E2E) Tests:** Full activity launch and navigation tests using `createAndroidComposeRule<MainActivity>()` (`AppEndToEndTest`).

---

## 📁 Project Structure

```
com.example.jobsearch
├── ai          # On-device AI (LiteRT-LM Engine, ModelDownloader, Prompts)
├── data        # Repositories, Room Database Entities, DAOs, & Encrypted Preferences
├── di          # Hilt Dependency Injection Modules
├── document    # PDF Generation, PdfLayoutEngine, & Exporters
├── network     # SyncServer Engine (Embedded Ktor Netty Server & Auth)
├── parsing     # Jsoup HTML Web Scraping & JobParser
├── resume      # Resume Importer (PDF/DOCX/TXT)
├── speech      # Audio Recorder & SpeechToText
├── ui          # Jetpack Compose UI (Screens, ViewModels, Components, Themes)
│   ├── addjob
│   ├── components
│   ├── documented
│   ├── interview
│   ├── jobdetail   # Job Detail & Components (Header, ActionRow)
│   ├── joblist
│   └── settings   # Settings & Sections (ResumeSection, ModelSection, ServerDialog)
└── testing     # Unified Unit, ViewModel, UI, and End-to-End Test Suite
```

---

## 🔑 Setup & Build

1. Clone the repository.
2. Set your Gemini API key in `local.properties`:
   ```properties
   GEMINI_API_KEY=your_gemini_api_key_here
   ```
3. Open in **Android Studio** and sync Gradle (`gradle/libs.versions.toml`).
4. Run the app on an emulator or device.
5. *(Optional)* Download the local Gemma 4 E2B model (~2GB) in Settings.
6. *(Optional)* Run unit and E2E tests in Android Studio from `app/src/test/java/com/example/jobsearch/testing` and `app/src/androidTest/java/com/example/jobsearch/testing`.

---

*Developed as an enterprise-grade portfolio piece demonstrating modern Android architecture, local AI integration, and security best practices.*
