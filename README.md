# ༒︎ PHANTOM GHOST ༒︎

**Intelligence Beyond the Ordinary.**

PHANTOM GHOST is an Android edge-AI assistant project built with Kotlin, Jetpack Compose and the Android NDK. This repository contains the Android application foundation and integration adapters for local language, speech, memory and user-approved actions.

## Implementation status

| Component | Status |
|---|---|
| Android / Compose foundation | Scaffold present |
| Model download + SHA-256 verification | Implemented |
| Foreground model download notification | Implemented |
| Llama Kotlin generation facade | Implemented; native inference backend not linked |
| Whisper PCM ring buffer + energy VAD | Implemented; native inference backend not linked |
| Piper voice catalog + AudioTrack player | Adapter only; Piper/ONNX backend not linked |
| Room conversation + vector persistence | Implemented; local cosine ranking, not FAISS |
| ONNX embedding model | Not included; provide a tested LocalEmbedder |
| Android action executor | Allow-listed intents only; no shell execution |
| Native CMake | Optional dependency switches; upstream sources not vendored |
| APK / native build verification | Not yet verified |

## Model downloads

Model files are not bundled in the APK or Git. ModelManager requires an HTTPS URL and a pinned 64-character SHA-256 digest. Obtain model files from their official publishers and comply with each model's license.

A Llama 3 8B Q4_K_M model is several gigabytes and may exceed the memory budget of many phones once Android and the KV cache are included. Start with a smaller instruct model for initial device testing.

## Native dependencies

See docs/native-build.md. The CMake project is prepared for Android add_subdirectory integration of reviewed llama.cpp and whisper.cpp source checkouts. Piper requires a separate ONNX Runtime Android integration and voice assets.

## Privacy and safety

- Microphone capture must be started with runtime permission and visible user feedback.
- Cross-app launching requires user confirmation.
- No arbitrary shell execution, credential extraction or hidden app control is provided.
- Conversation and vector data are stored locally in the app database.
- Keep model weights, signing keys and secrets out of Git.

## Design

Obsidian Black #0A0A0A, Charcoal #121212, Neon Cyan #00F0FF, Phantom Purple #9D00FF, and Soft White #E0E0E0.

## Build

Open the repository in Android Studio with JDK 17. Install Android SDK 35, Android NDK and CMake 3.22.1. Run ./gradlew assembleDebug. Native inference is unavailable until upstream sources are added and the JNI implementation is completed and tested.
