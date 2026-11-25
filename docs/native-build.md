# Native build configuration

The CMake project uses FetchContent with pinned upstream commit IDs. It configures both projects with the Android NDK toolchain, disables optional desktop/GPU backends and links the llama and whisper targets into phantom_native.

Pinned revisions:
- llama.cpp: 9c003a97ab34a69cc49243f9b02014038205ffae
- whisper.cpp: 868e55578ea5f69550c046da22aefe11dc6e04c0

The first native build needs network access to fetch these repositories and their Git submodules. This can make CI slower. For reproducible offline builds, mirror the source trees and use FetchContent source overrides.

## ABI

The app defaults to arm64-v8a. To build armeabi-v7a for older 32-bit devices, run scripts/build-armeabi-v7a.sh or pass -PtargetAbi=armeabi-v7a to Gradle.

## Important integration boundary

CMake fetching and linking upstream libraries is not the same as a working JNI inference bridge. The current Kotlin API and C++ JNI implementation still need to be compiled against these exact pinned revisions, tested with real GGUF/Whisper model files, and profiled on physical devices. Do not ship a build that reports native inference as available until model load, token generation, transcription and cancellation all pass tests.

## Audio format

Whisper expects mono 16 kHz audio. Convert PCM16 samples to float samples by dividing by 32768.0f before calling whisper_full. Tiny/base are the recommended Android starting models.

## Piper

Piper is not yet part of this native CMake target. Use a maintained Android Piper wrapper or sherpa-onnx Android integration, and include only compatible model/config pairs. Voice files remain separate downloads.
