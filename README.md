# Screen Chat Workspace (Screens-Trans-Chatbot)

Screen Chat Workspace is an Android application designed as an LLM workspace integration for text, media, and basic audio recording. It provides a central hub for managing chats and integrating with external large language models (LLMs).

_Note: This application is currently in an engineering prototype phase. Foreground microphone service exists, but comprehensive long-running chunking/recovery is still incomplete. Tool calling is implemented via MCP JSON-RPC with basic audit logging; explicit user approval UI for tools is pending._

---

## 1. Architectural Status & Core Components

This application is actively being refactored towards Clean Architecture principles. It uses Jetpack Compose, Room Database, and Kotlin Coroutines.

### A. Core Workspace Flow (Prototype)
* **Context Storage**: Basic Room SQLite persistence layer storing historical sessions, chat history, and prompt templates. Media attachments are handled via URI references. 
* **Workspace Chat & Tools**: Supports message exchanges with LLMs and basic file ingestions. Basic MCP (Model Context Protocol) tool-calling is implemented, including an audit log stored in Room.
* **No Overlay/OCR Services**: This application does not include screenshot parsing, OCR, or active screen-overlay systems.

### B. Meeting Recorder (Prototype)
* **Recording System**: Uses standard Android AudioRecord with a foreground service. Stop behavior handles file finalization and broadcast, but edge-case recovery from process deaths during recording remains a work in progress.
* **Platform STT Draft**: Integrates the Android `SpeechRecognizer` to stream incremental draft segments. Offline local STT is not guaranteed and depends entirely on the device's capability and network availability.
* **Data-Flush**: Live draft segments are immediately stored in Room tracking `TranscriptSegmentEntity`.

### C. Multimodal & LLM Integration
* **Multimodal Polish**: Supports sending recordings and transcripts to the Gemini API for polishing (e.g., structuring stutters and jargon into readable text). Reads raw source directly from Room segments.
* **AI Providers & Config-First Routing**: Supports integration with multiple endpoints. LLM routes and providers (including Gemini and OpenAI-compatible models) are driven strictly by a runtime `config.json` rule engine, not legacy defaults.
* **Capability Mapping**: Distinct models explicitly map to unique capabilities (e.g., chat vs. primary transcript polishing).

---

## 2. Dynamic Environment & Secret Management

* **Compile-Time Keys (Development)**: You can optionally set a default `GEMINI_API_KEY` for development using the **Secrets plugin** or `.env` file mapped to `BuildConfig`.
* **Runtime Keys (Production)**: User-provided API keys (such as custom OpenRouter or OpenAI keys) are entered at runtime via the App Settings UI and stored locally. Keystore-backed encryption is planned for advanced security.
* **Security Notice**: Never hardcode API keys or secret credentials directly inside source repositories or build files.

---

## 3. Setup and Prerequisites

To build or inspect this project, ensure your local development machine contains the following dependencies:

* **Java Development Kit (JDK)**: Version 17 or higher. Set your `JAVA_HOME` environment path dynamically.
* **Android Studio**: Android Studio Koala (2024.1.1) or higher is recommended.
* **Android SDK**: Build Tools `34.0.0` or higher, Target SDK `34`, Minimum SDK `26`.
* **Gradle Wrapper**: This repository strictly utilizes modern Gradle project builds (`build.gradle.kts` with Kotlin DSL). Ensure the `gradlew` script has executable permissions (`chmod +x gradlew`).

---

## 4. Compilation & Build Guide

You can easily compile, test, and build the application through the command-line interface using the provided Gradle wrapper.

### A. General Development Build (Debug APK)
To build a debuggable installation file containing structural logging and instant-run features:
```bash
./gradlew assembleDebug
```
The compiled output is output to:
`app/build/outputs/apk/debug/app-debug.apk`

### B. Unit & Robolectric Testing
To execute fast JUnit business-logic tests alongside JVM-based shadow framework tests:
```bash
./gradlew :app:testDebugUnitTest
```

### C. Production Release Package (Release APK / Bundle)
To generate a fully minimized, high-performance binary optimized and signed for Google Play distribution:
1. Register signature keys inside `/app/build.gradle.kts` or `gradle.properties`.
2. Clean existing caches (only as a last resort) and execute:
   ```bash
   ./gradlew assembleRelease
   ```
   Or to build a dynamic distribution bundle (AAB):
   ```bash
   ./gradlew bundleRelease
   ```

---

## 5. Instant Installation Guide (Direct APK Retrieval)

If you are a tester, client, or end-user who does not wish to configure compiler environments locally, you can retrieve the compiled application package instantly from the cloud streaming emulator workspace:

1. Locate the **AI Studio Project Settings** or **Project Export Menu** on your workspace control panels.
2. Direct your action to **Build APK** or **Download Build Outputs**.
3. Download the generated `.apk` file directly onto your test mobile device.
4. On your Android device, navigate to your downloads catalog, locate the package, and click to install.
   - *Note*: Ensure "Install from Unknown Sources" is toggled in your device's security preferences if prompt warnings trigger.
5. Grant necessary runtime permissions upon initial launch (Microphone for audio recording) to access full services.

---

## 6. Functional Usage Manual

### A. Provisioning LLMs
1. Open the workspace panel, navigate to the **Settings** cog ⚙️.
2. In the **Manage LLM Providers** view, select your designated channel (e.g. Gemini, custom HTTP providers) and click **Edit**.
3. Input your secret API Key, define list components of model tags (comma separated, e.g. `gemini-1.5-flash,gemini-1.5-pro,gemini-2.5-flash`), and establish token allocation limits using the slider mechanism. Save changes.

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
