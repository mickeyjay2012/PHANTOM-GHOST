# ༒︎ PHANTOM GHOST ༒︎
### Intelligence Beyond the Ordinary.

An Android AI-assistant project built around a futuristic, privacy-conscious interface and modular on-device intelligence.

## Design system
- Obsidian Black `#0A0A0A`
- Charcoal `#121212`
- Neon Cyan `#00F0FF`
- Phantom Purple `#9D00FF`
- Soft White `#E0E0E0`

## Current implementation status

The first source milestone contains:
- Android application foundation using Kotlin and Jetpack Compose.
- A minimal C++ JNI/NDK bridge.
- An assistant interface with a demo response adapter.
- A bounded in-process conversation buffer.
- A conservative read-only tool allowlist.
- A GitHub Actions Android build workflow.

**Important:** the chat screen is currently a UI demo, not a functioning AI model. llama.cpp, Whisper.cpp, Piper TTS, persistent semantic memory, and Android automation remain planned integrations. No model weights are included.

## Project structure

```
app/src/main/java/com/phantom/ghost/
  ai/       Assistant contract, demo adapter, tool policy, JNI wrapper
  memory/   In-process conversation buffer
app/src/main/cpp/ Native bridge
.github/workflows/ Android CI
```

## Build requirements
- Android Studio and Android SDK 35
- JDK 17
- Android NDK 27.2.12479018
- CMake 3.22.1
- Gradle 8.11.1

Open the project in Android Studio and sync Gradle. With Android SDK tools configured, run `gradle assembleDebug` and `gradle testDebugUnitTest`.

## Roadmap
- [x] Compose app foundation
- [x] JNI/NDK starter bridge
- [x] Assistant contract and demo adapter
- [x] In-memory conversation buffer
- [x] Initial CI workflow
- [ ] llama.cpp model runtime and model download guidance
- [ ] Whisper.cpp speech recognition
- [ ] Piper text-to-speech
- [ ] Persistent local retrieval memory
- [ ] Permission-gated, user-confirmed Android actions
- [ ] Device testing and release packaging

## Privacy and safety
Keep model files and personal data out of source control. Request permissions only when needed and explain why. Cross-app actions must be visible, user-authorized, allowlisted, and confirmed before consequential changes. Never commit API keys, signing keys, passwords, or private model data.

See [LICENSE](LICENSE). Third-party runtimes and model weights may have separate licenses.
