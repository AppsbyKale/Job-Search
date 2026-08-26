# JobSearch Project Blueprint

## PROJECT_SIGNATURE
JobSearch is a professional job application management app designed to streamline the process of tracking, tailoring, and preparing for job opportunities. It follows a Clean Architecture inspired approach using MVVM, Hilt for dependency injection, and Jetpack Compose for the UI. The core innovation is its "Local-First AI" strategy, leveraging Google's MediaPipe (Gemma) for sensitive tasks like resume tailoring and interview coaching directly on-device, combined with a Desktop Sync protocol (Ktor) to bridge web browsing with mobile management.

## SCREENS
- **JobListScreen**: Main entry point. Displays a filtered list of jobs (Saved, Applied, etc.). Includes a "Synced Jobs" full-screen dialog (triggered by a Sync icon in the top bar) to review incoming jobs from the desktop extension.
- **AddJobScreen**: Handles manual entry and URL parsing (via Jsoup/WebView). Includes AI-driven "Smart Clean" for job descriptions and "Match Analysis" to score resumes against job requirements.
- **JobDetailScreen**: Comprehensive view of a single job. Controls for generating resumes, cover letters, and interview cheat sheets. Access to interview prep tools and notes.
- **DocumentViewScreen**: A rich-text enabled editor for viewing and fine-tuning generated PDFs/resumes before export.
- **InterviewScreen**: A voice-enabled mock interview interface using speech-to-text and AI feedback to help users practice for specific roles.
- **SettingsScreen**: Management of global user data (base resume), AI model downloads, Desktop Sync server configuration, and database backups.

## LOGIC_TREE
1. **Local AI Pipeline**: `ModelManager` orchestrates the lifecycle of the Gemma `.task` file. `GenerationRepository` manages high-level requests (Resume/Cover Letter/Interview) by mapping them to specific `PromptBuilder` strategies and handling on-device execution through a foreground `AiModelService`.
2. **Desktop Sync Protocol**: `SyncRepository` runs an embedded Netty server (Ktor). It listens for POST requests from a companion Chrome Extension, assigns a `SYNCED` status to new jobs, and notifies the user for review.
3. **Smart Job Parsing**: `JobParser` uses Jsoup for direct HTML extraction and a fallback WebView approach to handle sites with anti-bot/SPA logic. It uses AI or regex to "distill" raw HTML into structured job data.
4. **Interview Feedback Loop**: Captures audio via `AudioRecorder`, converts to text via `SpeechToText`, and passes the transcript to the AI with a specific evaluation prompt to generate scores and actionable advice.

## DATA_SCHEMA
- **Job**: Core entity tracking title, company, description, status (`SYNCED`, `SAVED`, `APPLIED`, etc.), and storage for tailored documents (resume, cover letter, cheat sheet).
- **InterviewQuestion/Answer**: Tracks mock interview data linked to specific jobs, including scores and AI feedback.
- **ResumeData**: Structured representation of a candidate's history (experience, education, skills) used for AI tailoring.
- **Settings**: Persistent configuration (DataStore) for server ports, model URLs, and the master resume text.

## BUILD & TOOLING
- **Language**: Kotlin 1.9.24 / JVM 17.
- **UI**: Jetpack Compose (BOM 2024.04.01) with Material 3.
- **Data**: Room 2.6.1 for SQLite persistence; DataStore for preferences.
- **AI**: LiteRT-LM (0.16.1) for local LLM (Gemma 4 E2B); Google Generative AI (Gemini 3.5 Flash Lite) for cloud-heavy tasks.
- **Network**: Ktor 2.3.12 (Server/Netty) for Desktop Sync (pinned for Gemini compatibility).
- **DI**: Hilt 2.51.1.

## TIMELINE & DECISIONS
- **2026-08-20, 23:15**: Implemented inline "Review Required" section for synced jobs to allow description scrubbing.
- **2026-08-20, 23:45**: Moved synced jobs to a separate full-screen dialog accessible via top bar icon to keep main list focused.
- **2026-08-21, 00:45**: Updated Add Job screen to allow saving supplemental questions/answers directly to DB without forcing resume generation.
- **2026-08-22, 10:45**: Relocated AI model storage to a shared public folder (`Downloads/AI_Models`) to enable multi-app model sharing without redundant downloads.
- **2026-08-23, 16:15**: Initial JS_blueprint.md creation to document current architectural state and logic flows.
- **2026-08-23, 19:00**: Overhauled `AppDialog` to include anchored Save/Cancel headers and fixed footer buttons. Updated all feature dialogs (Supplemental Questions, Cheat Sheet, Match Analysis, etc.) to use this layout, resolving scrolling issues and system bar obstructions.
- **2026-08-24, 00:30**: Successfully migrated to LiteRT-LM (0.16.1) with Coroutines 1.11.0. Enabled GPU backend via OpenCL/VNDK native library entries in Manifest.
- **2026-08-25, 23:30**: Optimized pipeline for S23 Ultra (16GB RAM): Increased local token count to 4096 and enabled Multi-Token Prediction (MTP). Moved Phase 1 (Job Analysis) to Gemini 3.5 Flash Lite to resolve local stalls on long documents.
- **2026-08-26, 01:25**: Implemented "Auto-Sweep" for job descriptions (local AI) and added "System Logs" to Settings for real-time diagnostics.

## CURRENT_CONTEXT
App is running on LiteRT-LM 0.16.1 with GPU acceleration. Local AI (Gemma 4 E2B) handles Auto-Sweep, Interview Prep, and Cheat Sheets. Cloud AI (Gemini 3.5 Flash Lite) handles Job Analysis and Resume/Cover Letter tailoring. System Logs are active in Settings.

