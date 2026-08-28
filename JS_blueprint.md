# JobSearch Project Blueprint

## PROJECT_SIGNATURE
JobSearch is a professional job application management app designed to streamline the process of tracking, tailoring, and preparing for job opportunities. It follows a Clean Architecture inspired approach using MVVM, Hilt for dependency injection, and Jetpack Compose for the UI. The core innovation is its hybrid AI strategy, leveraging local Gemma 4 E2B for privacy-sensitive tasks and Gemini 3.5 Flash Lite for high-context tailoring, combined with a Desktop Sync protocol (Ktor) to bridge web browsing with mobile management.

## SCREENS
- **JobListScreen**: Main entry point. Displays a filtered list of jobs with status chips. Includes a "Synced Jobs" dialog for reviewing incoming desktop jobs, featuring a quick-delete action and status synchronization.
- **AddJobScreen**: Handles manual entry and URL parsing. Features AI-driven "Auto-Sweep" and "Auto-Tagging" during the fetch process to automate job requirement extraction and categorization.
- **JobDetailScreen**: Comprehensive view of a single job. Uses an expandable "Saved Documents" layout to keep the UI clean. Controls for **Steered Generation** of resumes and cover letters. Features new **Initial Application Email** and **External Document Upload** (PDF/DOCX/TXT) capabilities.
- **DocumentViewScreen**: A rich-text enabled editor for viewing and fine-tuning generated or uploaded documents. Features a live PDF-style preview and structural editing (e.g., bullet point management).
- **InterviewScreen**: A voice-enabled mock interview interface using speech-to-text and AI feedback to help users practice for specific roles.
- **SettingsScreen**: Management of global user data, AI model downloads (Gemma), Gemini API keys, Desktop Sync configuration, system diagnostics (logs), and database backups.

## LOGIC_TREE
1. **Hybrid AI Pipeline**: 
    - **Local (Gemma 4 E2B)**: Executes Auto-Sweep (description cleaning), Auto-Tagging, Interview Coaching, and Follow-up drafting.
    - **Cloud (Gemini 3.5 Flash Lite)**: Handles Phase 1 (Job Analysis) and steered document tailoring (Resume/Cover Letter) to ensure high-quality output on long job descriptions.
2. **Desktop Sync Protocol**: `SyncRepository` runs an embedded Netty server (Ktor 2.3.12). Listens for POST requests from the Chrome Extension, automatically tags and cleans descriptions via local AI before queuing for user review.
3. **Smart Job Parsing**: `JobParser` uses Jsoup and fallback WebView logic. Integrated with the AI pipeline to distill raw HTML into structured data immediately upon receipt.
4. **Document Management**: `DocumentExporter` uses PDFBox-Android for high-fidelity rendering. Supports structured JSON-to-human-readable transformations for Resumes, Cover Letters, Cheat Sheets, and Initial Emails.

## DATA_SCHEMA (Room Version 8)
- **Job**: Core entity tracking title, company, description, status, notes, tags, and document storage. Recently expanded with `initialEmailText`, `externalResumeText`, and `externalCoverLetterText`.
- **InterviewQuestion/Answer**: Tracks mock interview data linked to specific jobs, including scores and AI feedback.
- **TrainingExample**: Logs high-quality prompt/response pairs (including user-uploaded external docs) for future LoRA fine-tuning.
- **Settings**: Persistent configuration for ports, model URLs, and the master resume text.

## BUILD & TOOLING
- **Language**: Kotlin 1.9.24 / JVM 17.
- **UI**: Jetpack Compose (BOM 2026.08.00) with Material 3.
- **Data**: Room 2.8.4 for SQLite persistence; DataStore for preferences.
- **AI**: LiteRT-LM (0.16.1) for local LLM; Google Generative AI (Gemini 0.9.0) for cloud tasks.
- **Network**: Ktor 2.3.12 (Server/Netty/Client) pinned for Gemini SDK compatibility.

## TIMELINE & DECISIONS
- **2026-08-20 to 08-23**: Initial architectural setup, Sync Server implementation, and foreground AI service migration.
- **2026-08-24**: Migrated to LiteRT-LM (0.16.1). Enabled GPU acceleration (OpenCL/VNDK) for S23 Ultra hardware.
- **2026-08-25**: Optimized context window (4096 tokens) and MTP. Moved Job Analysis to Cloud AI to prevent local stalls on 2k+ word documents.
- **2026-08-26, 12:00**: Resolved Gemini SDK crash by pinning Ktor to 2.3.12. Refactored "Auto-Sweep" to trigger immediately on URL parse.
- **2026-08-26, 14:00**: Implemented **Resume/Cover Letter Steering**, allowing users to provide custom instructions (e.g., "Highlight leadership") during generation.
- **2026-08-26, 17:30**: Added **Initial Application Email** document type with direct "Open in Gmail" support.
- **2026-08-26, 23:00**: Implemented **External Document Uploads** (.pdf, .docx, .txt) and **AI Auto-Tagging** (Gemma 4). Updated DB to Version 8 with migrations.
- **2026-08-27, 01:00**: Polished "Synced Jobs" UI with a delete action and refreshed sync status icons.

## CURRENT_CONTEXT
Project is in a "Feature Complete" state for the Android target. AI pipeline is optimized with a hybrid local/cloud strategy. Supports full application tracking from discovery (Desktop Sync) to application (Steered Docs) to prep (Interview Coach). Ready for multiplatform desktop feasibility evaluation.
