# JobSearch AI: The Professional Career Command Center

**JobSearch AI** is a high-performance Android application designed to revolutionize the modern job hunt. By leveraging a sophisticated **Hybrid AI Architecture**, it provides job seekers with executive-level tools to tailor resumes, generate cover letters, and master interviews with on-device privacy.

---

## 🌟 Key Features

### 📄 The Resume Studio (WYSIWYG)
A professional, block-based document editor that mirrors your final PDF in real-time. 
- **Live Preview**: Edit content on a digital "white paper" with exact PDF margins and spacing.
- **Rich Formatting**: Toggle Bold, Italic, and Underline styles for individual bullet points.
- **Structured Content**: Manage experience, education, and projects as modular blocks.

### 🧠 Hybrid AI Engine
The app intelligently splits tasks between local and cloud intelligence to maximize speed, privacy, and reasoning power:
- **Local AI (Gemma 4 E2B)**: Handles job description "Smart Cleaning," match scoring, and interview feedback entirely on-device.
- **Cloud AI (Gemini 3.5 Flash Lite)**: Orchestrates complex, creative tailoring for high-fidelity Resumes and Cover Letters.

### 🎯 ATS Keyword Analysis (Bingo Card)
Transparency for the automated screening process.
- **Keyword Visualizer**: See "Found" vs. "Missing" skills as interactive chips.
- **Match Hub**: Deep-dive into why your resume scored a specific percentage and generate "Improve Match" questions to bridge the gap.

### 🎙️ AI Interview Coach
Prepare for the big day with real-time feedback.
- **Voice Practice**: Use native Google Speech-to-Text to answer job-specific questions out loud.
- **Evaluation Reports**: Receive a 1–10 score, strength/weakness analysis, and a "Model Answer" script for every response.

### 📦 Ecosystem Synergy: Chrome Extension
Pairs with a dedicated **Chrome Extension** (built with JavaScript/VSCode) to "clip" job postings directly from LinkedIn or Indeed and sync them instantly to your mobile command center.

---

## 🛠️ Technical Excellence (Engineering Rigor)

- **Architecture**: Modern MVVM (Model-View-ViewModel) with a strict Unidirectional Data Flow.
- **Dependency Injection**: Fully migrated to **Hilt** (Dagger) for enterprise-grade scalability and testability.
- **Data Persistence**: **Room Database** with secure Backup/Restore logic (`.jsbackup` ZIP format).
- **PDF Rendering**: High-fidelity document generation using **PDFBox-Android**, featuring balanced skill columns and "keep-together" logic for experience blocks.
- **Quality Assurance**: 
  - Comprehensive **Unit Testing** suite covering AI data scrubbing, heuristic parsing, and ViewModel states.
  - **Partial WakeLocks** and **Background Services** to ensure AI generations never stall, even when the screen is off.

---

## 🚀 Tech Stack

- **UI**: Jetpack Compose (100% Declarative)
- **Language**: Kotlin (Coroutines + Flow)
- **AI Core**: MediaPipe LLM Inference + Google Generative AI SDK
- **Design**: Material 3 (Standardized Design System)
- **PDF Core**: PDFBox-Android
- **Dependency Injection**: Hilt
- **Persistence**: Room + DataStore

---

## 📁 Project Structure (Clean Code)

```
com.example.jobsearch
├── ai          # AI Orchestration, Prompt Engineering, & Services
├── data        # Repositories, Room Entities, & Backup Logic
├── di          # Hilt Modules (Database & App providing)
├── ui          # Jetpack Compose Screens & Shared Components
│   ├── components  # Standardized Design System (Cards, Dialogs, Badges)
│   ├── jobdetail   # The "Studio" and "Match Hub" logic
│   └── ...         # Feature-specific packages
└── util        # Date Formatting, JSON Scrubbing, & UI Helpers
```

---

## 🔑 Setup
1. Clone the repository.
2. Obtain a **Gemini 3.5 API Key** from Google AI Studio.
3. Paste the key into `SettingsRepository.kt`.
4. Run the app and download the **Gemma 4 E2B** model in the Settings screen (~2GB).

---

*Developed as a high-fidelity portfolio piece demonstrating modern Android engineering and AI integration.*
