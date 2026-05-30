# Screen Chat Workspace (Screens-Trans-Chatbot)

Screen Chat Workspace is a high-performance Android application engineered to unify mobile screen interaction, multi-provider LLM integration, speech processing, and continuous meeting synchronization. It consists of a non-blocking Floating Overlay (Float UI) for ambient, on-screen captures and a comprehensive Screen Chat Workspace (Core UI) for full-session context management, attachment tracking, and customizable routing.

---

## 1. Architectural Integrity & Core Components

This application utilizes Clean Architecture principles layered with Jetpack Compose, the Room Database, Kotlin Coroutines, and StateFlow structures.

### A. Non-Blocking Floating HUD Overlay (Float UI)
* **Ambient Floating Microphone**: Enables micro-capturing, fast Speech-To-Text translation on on-screen targets, and quick-reply execution.
* **Adaptive Integration**: Serves as a quick-launch utility. Toggling between standard workspace panes and overlay systems preserves full session states.

### B. Screen Chat Workspace Core
* **Context Storage**: Fully managed Room SQLite persistence layer storing historical sessions, structured multi-role chat history, media attachments, and custom prompt templates.
* **Unified Workspace Sync**: Synchronizes all active media uploads (Images, Raw TXT, PDFs, MP3, and WAV recordings) with underlying conversational models.

### C. Meeting Recorder
* **Low-Overhead Pipeline**: Employs a continuous PCM-WAV capturing core designed for hours of uninterrupted conference recording.
* **On-Device Hybrid STT**: Integrates the Android local `SpeechRecognizer` to stream incremental, real-time draft segments on-device without blocking.
* **Data-Flush**: Live draft segments and captured chunks are periodically flushed to storage caches (.txt and .wav).

### D. Multimodal AI Polishing Engine
* **Multimodal Joint Processing**: Employs Gemini API's multimodal framework to simultaneously ingest raw WAV recordings and draft `.txt` segments.
* **Vietnamese & English Linguistic Synthesis**: Corrects speech stutters, mispronounced jargon, spelling errors, and background noise to generate structured, punctuated, corporate-grade meeting transcripts.
* **Graceful Local Fallback**: Falls back automatically to pure text-based linguistic polishing when high-bandwidth audio-payload delivery is constrained by limited networking conditions.

### E. LLM Adaptive Routing Engine
* **Load Allocation Policies**:
  - **Round Robin**: Evenly cycles requests through configured API channels and credentials to bypass per-key rate limitations.
  - **Sticky Execution**: Dedicates calls to a preferred primary channel to maintain long-term session context continuity, falling back to auxiliary channels only when quota exhausts, throttling, or network faults are raised on-device.
  - **Combo Cycle**: Loops requests across diverse provider combinations and model weights sequentially.
* **Fault-Backoff Throttling**: Restricts immediate retries on highly limited or faulted endpoints. Incorporates a smart cooldown window to prevent rapid rate-limit loops.

### F. CI/CD & Build Notes
* **Local Builds**: Ensure `JAVA_HOME` is set to JDK 17+ and the `gradlew` script has executable permissions (`chmod +x gradlew`).
* **Tests**: Test suites rely on Robolectric and require proper JDK environments. Always run `./gradlew testDebugUnitTest` prior to merging features.

---

## 2. Dynamic Environment & Secret Management

All service credentials and API keys are stored securely using the **Secrets Gradle Plugin** combined with Android's environment systems.

* **Secrets Management**: Setup your credentials in the **Secrets panel in AI Studio** or copy `.env.example` to `.env` at the root directory of your project:
  ```env
  GEMINI_API_KEY=YOUR_GEMINI_API_KEY_HERE
  ```
* **Injecting Secrets**: The gradle plugin loads values from `.env` and maps them cleanly into `BuildConfig.GEMINI_API_KEY` at compile time.
* **Security Notice**: Never hardcode API keys or secret credentials directly inside source repositories, build files, or `local.properties`.

---

## 3. Setup and Prerequisites

To build or inspect this project, ensure your local development machine contains the following dependencies:

* **Java Development Kit (JDK)**: Version 17 or higher. Set your `JAVA_HOME` environment path dynamically.
* **Android Studio**: Android Studio Koala (2024.1.1) or higher is recommended.
* **Android SDK**: Build Tools `34.0.0` or higher, Target SDK `34`, Minimum SDK `26`.
* **Gradle Wrapper**: This repository strictly utilizes modern Gradle project builds (`build.gradle.kts` with Kotlin DSL).

---

## 4. Compilation & Build Guide

You can easily compile, test, and build the application through the command-line interface.

> **Note**: Always use `gradle` directly instead of `./gradlew` or `./gradlew.bat` in this container sandbox environment.

### A. General Development Build (Debug APK)
To build a debuggable installation file containing structural logging and instant-run features:
```bash
gradle assembleDebug
```
The compiled output is output to:
`app/build/outputs/apk/debug/app-debug.apk`

### B. Unit & Robolectric Testing
To execute fast JUnit business-logic tests alongside JVM-based shadow framework tests:
```bash
gradle :app:testDebugUnitTest
```

### C. Production Release Package (Release APK / Bundle)
To generate a fully minimized, high-performance binary optimized and signed for Google Play distribution:
1. Register signature keys inside `/app/build.gradle.kts` or `gradle.properties`.
2. Clean existing caches (only as a last resort) and execute:
   ```bash
   gradle assembleRelease
   ```
   Or to build a dynamic distribution bundle (AAB):
   ```bash
   gradle bundleRelease
   ```

---

## 5. Instant Installation Guide (Direct APK Retrieval)

If you are a tester, client, or end-user who does not wish to configure compiler environments locally, you can retrieve the compiled application package instantly from the cloud streaming emulator workspace:

1. Locate the **AI Studio Project Settings** or **Project Export Menu** on your workspace control panels.
2. Direct your action to **Build APK** or **Download Build Outputs**.
3. Download the generated `.apk` file directly onto your test mobile device.
4. On your Android device, navigate to your downloads catalog, locate the package, and click to install.
   - *Note*: Ensure "Install from Unknown Sources" is toggled in your device's security preferences if prompt warnings trigger.
5. Grant necessary runtime permissions upon initial launch (Microphone for audio recording, Display Draw Overlays for the Floating Float UI) to access full services.

---

## 6. Functional Usage Manual

### A. Provisioning LLMs
1. Open the workspace panel, navigate to the **Settings** cog ⚙️.
2. In the **Manage LLM Providers** view, select your designated channel (e.g. Gemini, OpenAI compatibles) and click **Edit**.
3. Input your secret API Key, define list components of model tags (comma separated, e.g. `gemini-1.5-flash,gemini-1.5-pro,gemini-2.0-flash-exp`), and establish token allocation limits using the slider mechanism. Save changes.

### B. Triggering Route Allocation Rules
1. Inside the LLM routing tab, select your preferred routing policy: **Round Robin**, **Sticky**, or **Combo Cycle**.
2. Define failure-cooldown windows to handle automatic API rate limits gracefully.

### C. Persistent Recording & Live Transcription
1. Go to the workspace panel and tap the **Meeting 🎙️** tab.
2. Press **BẮT ĐẦU HỌP 🎙️** to begin a synchronous, continuous meeting recording.
3. Observers can view raw speech segments rolling down the text box in real-time.
4. After completing your session, click **HOÀN THÀNH HỌP ⏹️**. The recording card is stored inside the **Record Library**.
5. To post-process raw text, click **Hiệu Đính AI 🌌** on the recording card. Your device will invoke the multimodal polisher to restructure a punctuated, professional Vietnamese record.
6. Click the **Input 📥** button to insert any transcript slice back into active composer dialogues seamlessly.
