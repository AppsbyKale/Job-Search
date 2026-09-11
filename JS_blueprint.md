# JobSearch Project Blueprint

## PROJECT_SIGNATURE
JobSearch is a modern, enterprise-grade job application management system designed to track, tailor, and prepare for career opportunities. Built following Clean Architecture principles, modern MVVM, Hilt dependency injection, and Jetpack Compose. Its signature capability is a **Hybrid AI Strategy**—combining on-device local AI (Gemma 4 E2B via LiteRT-LM) for private data processing with Cloud AI (Gemini) for long-document reasoning—supported by a secure Desktop Sync protocol (Ktor) with 30-day token authentication.

## SCREENS & UI ARCHITECTURE
- **JobListScreen**: Main dashboard displaying filtered jobs, status badges, search query filtering, and a top app bar with quick-access Settings dropdown menu (Resume section at top).
- **AddJobScreen**: Manual entry and web posting URL fetcher. Features AI-driven "Auto-Sweep" and "Auto-Tagging" upon receipt.
- **JobDetailScreen**: Comprehensive job view featuring modularized sub-components (`JobDetailHeader`, `JobDetailActionRow`), expandable saved document workbench, steered document generation, and external document uploads.
- **DocumentViewScreen**: Rich-text document editor and live PDF previewer. Supports structural bullet management and instant PDF export.
- **InterviewScreen**: Voice-enabled mock interview coach utilizing Android Speech-to-Text and AI evaluation reports.
- **SettingsScreen**: Modularized settings view (`ResumeSection`, `ModelSection`, `ServerSettingsDialog`) managing user resume data, model downloads, encrypted API key storage, Desktop Sync pairing, and system diagnostics.

## LOGIC_TREE & SECURITY ARCHITECTURE
1. **Hybrid AI Engine**:
    - **Local (Gemma 4 E2B)**: Executes description cleaning, auto-tagging, interview evaluation, and match analysis locally on-device.
    - **Cloud (Gemini)**: Handles multi-page resume and cover letter tailoring.
2. **Secure Desktop Sync Protocol (`SyncServer`)**: Ktor Netty server running on port 8080. Implements 6-digit PIN pairing (`/pair`) returning a secure **30-day Bearer Token**. Enforces `Authorization: Bearer <token>` on `/add-job`.
3. **Hardware-Backed Encryption**: API keys and sensitive user preferences are encrypted using `EncryptedSharedPreferences` backed by the **Android KeyStore**.
4. **Network & Storage Policy**: Enforces HTTPS globally (`network_security_config.xml`), restricting cleartext HTTP strictly to local loopback pairing. Complies 100% with Android Scoped Storage.
5. **Document Rendering Engine**: `DocumentExporter` delegates coordinate math and line wrapping to `PdfLayoutEngine` using PDFBox-Android.

## DATA_SCHEMA
- **Job**: Core Room entity tracking title, company, description, status, notes, tags, and document storage.
- **InterviewQuestion/Answer**: Tracks voice mock interview questions, user transcripts, and AI scores.
- **TrainingExample**: Logs prompt/response pairs for local LoRA fine-tuning.
- **Encrypted Settings**: Preferences stored securely via DataStore and EncryptedSharedPreferences.

## BUILD & TOOLING
- **Gradle Version Catalog**: Centralized dependency management via `gradle/libs.versions.toml`.
- **Language**: Kotlin 2.1.0 / JVM 17.
- **UI**: Jetpack Compose (Compose BOM 2026.08.00) with Material 3.
- **Dependency Injection**: Hilt 2.60.1 (`@HiltViewModel`, `@ApplicationScope`).
- **Data & Security**: Room 2.8.4, DataStore 1.2.1, `androidx.security:security-crypto:1.1.0`.
- **AI Core**: LiteRT-LM (0.16.1), Google Generative AI SDK (0.9.0).
- **Network**: Ktor 2.3.12 (Netty Server & Client).

## REFACTORING & RECENT DECISIONS
- **Security Hardening**: Removed all hardcoded secrets; injected API keys via Gradle `BuildConfig` and `EncryptedSharedPreferences`.
- **Architecture Decoupling**: Decoupled Ktor server into `SyncServer.kt` (`network` package) and HTTP downloader into `ModelDownloader.kt` (`ai` package).
- **UI Modularization**: Split oversized screens into clean sub-components (`ResumeSection`, `ModelSection`, `ServerSettingsDialog`, `JobDetailHeader`, `JobDetailActionRow`, `PdfLayoutEngine`).
- **Unified Test Suite**: Unified all unit, ViewModel coroutine, Compose UI, and End-to-End tests into package `com.example.jobsearch.testing` (`DataJsonScrubberTest`, `SyncServerSecurityTest`, `JobListScreenUiTest`, `AppEndToEndTest`).

## CURRENT_CONTEXT
App is fully feature-complete, production-ready, secure, and passing 100% of unit and instrumented end-to-end tests.

## FUTURE_CONSIDERATIONS
- **Desktop Extension Release**: Publish Chrome extension with 30-day token pairing UI.
- **Compose Multiplatform**: Evaluate CMP desktop companion app.
- **LoRA Adapter Tuning**: Fine-tune on-device Gemma model using logged `TrainingExample` JSONL data.
