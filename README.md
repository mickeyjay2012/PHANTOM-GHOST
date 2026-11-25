# ༒︎ PHANTOM GHOST ༒︎

**Intelligence Beyond the Ordinary.**

PHANTOM GHOST is a privacy-oriented Android edge-AI assistant built with Kotlin, Jetpack Compose and the Android NDK.

## Device Requirements

- Optimized for Android devices with 4 GB RAM or more.
- Recommended: 6 GB+ RAM for the optional 3B model.
- Default 4 GB profile: a 1B–1.5B quantized model, 2048-token context and Whisper tiny.
- 3B models are optional and must pass a runtime memory check. They are not guaranteed to fit on every 4 GB phone.
- 8B models are not part of the low-memory profile.
- Reserve several GB of free storage for model downloads and temporary files.

A model's quantized file size is not its full runtime memory use. Android, model weights, KV cache, audio and compute buffers all share RAM.

## Model Download

Model weights are not bundled in the APK or committed to Git. On first setup, PHANTOM GHOST can download the selected model to app-private storage. The default model should be a small quantized instruct model suitable for a 4 GB device. A 3B model is an optional download for devices with more available RAM.

Every model definition must provide an HTTPS download URL, exact file name, publisher-verified SHA-256 checksum, and license information. Downloads are written to a temporary file, verified, and only then renamed into the model cache. No guessed URL or checksum is supplied.

## Audio and Voice

Use Whisper tiny/base with mono 16 kHz PCM input. Do not reduce Whisper input to 8 kHz. Voice models are optional downloads and must have matching model/config files.

## Native Dependencies

CMake fetches pinned llama.cpp and whisper.cpp revisions using the Android NDK toolchain and links their library targets when available. See docs/native-build.md and docs/device-optimization.md. Piper/sherpa-onnx and the ONNX embedding model still require their Android dependency and model integration.

## Implementation Status

| Component | Status |
|---|---|
| Android UI foundation | Scaffold |
| Model download + SHA-256 validation | Implemented |
| Foreground model download notification | Implemented |
| 2048-token low-memory generation facade | Implemented |
| Native llama.cpp source build | Pinned FetchContent integration; JNI model loading/generation not yet enabled |
| Whisper PCM/VAD facade | Kotlin utilities; JNI transcription not yet enabled |
| Piper/sherpa-onnx | Not yet integrated |
| Room memory + cosine vector index | Local implementation; ONNX embedder still required |
| MemoryGuard | Hysteresis-based offload helper |
| Action executor | Allow-listed intents; passive mode below 20% battery |
| CI / APK build | Not yet passing; see Actions |

## Privacy and Safety

- Microphone capture requires runtime permission and visible user feedback.
- Cross-app launches require confirmation.
- No arbitrary shell execution, credential extraction or hidden app control.
- Conversation data is stored locally.
- Keep model weights, signing keys and secrets out of Git.

## Build

Use JDK 17, Android SDK 35, NDK 25.2.9519653 and CMake 3.22.1. Open the project in Android Studio and run ./gradlew assembleDebug. The native sources are pinned, but a successful Android compile and runtime test on physical ARM hardware are still required before calling this release-ready.
