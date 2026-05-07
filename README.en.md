# Codex Blanche (Android)

**English** | [日本語](README.md)

This repository is the **native Android** edition of [Codex Blanche](https://github.com/Tamari-log/Codex-Blanche). Like the web app, it provides a chat experience on your device using the **Gemini** and **ChatGPT (OpenAI)** APIs via a Jetpack Compose UI. Data shapes and settings follow the web version so you can use **imports/exports and optional Google Drive sync** alongside the web app.

| | Repository |
|---|------------|
| **Web app** | [Tamari-log/Codex-Blanche](https://github.com/Tamari-log/Codex-Blanche) |
| **This repo (Android)** | [Tamari-log/Codex-Blanche-App](https://github.com/Tamari-log/Codex-Blanche-App) |

## Legal

- [Terms of Service](docs/TERMS_OF_SERVICE.en.md) (English) · [日本語](docs/TERMS_OF_SERVICE.md)
- [Privacy Policy](docs/PRIVACY_POLICY.en.md) (English) · [日本語](docs/PRIVACY_POLICY.md)

## Features

- **Multiple providers**: Google Gemini and OpenAI (models, temperature, max tokens, etc., configurable in Settings)
- **Sessions**: Multiple chats, pins, per-session overrides (`SessionOverrides`)
- **Personas**: Flexible presets compatible with the web app’s JSON-oriented model
- **Attachments**: Images plus PDF / Office document ingestion (`AttachmentProcessor`)
- **Backup & sync**: Chat JSON export/import, optional Google Drive folder/file names (`DriveSyncRepository`)
- **Look & feel**: Theme aligned with the web app (`CodexWebPalette` / `CodexWebTextures`)

Application ID: `com.tamarilog.codexblanche`  
Minimum SDK: **26** (Android 8.0) · target / compile SDK: **34**

## Requirements

- **JDK 17**
- **Android Studio** (recommended) or an environment with the Android SDK
- Network access from device or emulator to reach provider APIs
- Your own **Gemini API key** and/or **OpenAI API key** as needed (entered in-app; persistence depends on your settings)

## Build & run

From the repository root:

```bash
./gradlew assembleDebug
```

Windows:

```bash
gradlew.bat assembleDebug
```

Debug APK output: `app/build/outputs/apk/debug/`. For day-to-day development, open the `app` module in Android Studio and use Run.

## Project layout (overview)

```
app/
├── src/main/java/...   # UI, ViewModel, API clients, data layer, Drive sync, etc.
└── src/main/res/       # Resources (theme, launcher icons, strings)
```

The root `settings.gradle.kts` names the Gradle project `CodexBlanche`.

## Web vs Android

- **Browser-first or hosted deployment** → [Codex-Blanche](https://github.com/Tamari-log/Codex-Blanche) (web)
- **Native UX, local storage, future store distribution** → this repository (Android)

Please report bugs and feature requests in the Issues tab of the appropriate repository.

---

*This README explains how this Android repository relates to the web project and how to build the app.*
