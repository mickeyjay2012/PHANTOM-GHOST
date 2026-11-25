# Native inference build notes

## Dependency layout

The Android module expects these source trees under app/src/main/cpp/external:

    external/
      llama.cpp/
      whisper.cpp/
      piper/

Do not copy model weights into Git or the APK. Keep model files in app-private external files storage and verify SHA-256 before loading.

## Android CMake

Android Gradle configures CMake with the Android NDK toolchain. ExternalProject_Add normally launches a separate configure/build and can accidentally build for the host. This project therefore uses add_subdirectory so upstream projects inherit the Android ABI, API level and compiler.

Place reviewed upstream checkouts under external/, pin them to tested commit IDs in your build process, then enable PHANTOM_WITH_LLAMA and/or PHANTOM_WITH_WHISPER in Gradle CMake arguments. Upstream target names and options can change; validate against the exact pinned revisions before release.

## llama.cpp

Build initially for arm64-v8a. A Llama 3 8B Q4_K_M GGUF is several gigabytes and may exceed the RAM budget of many phones once the KV cache and Android are included. Start with a smaller instruct model on lower-memory devices. The app requires a real model URL and SHA-256 digest rather than guessing a mutable Hugging Face file.

## whisper.cpp

Use a Whisper base model first on mobile. Audio must be mono 16 kHz PCM. The app-side ring buffer and energy VAD are capture utilities, not a complete AudioRecord foreground capture pipeline.

## Piper

Piper requires a compatible ONNX Runtime Android build, Piper inference code, required phonemization data, and matching voice/config files. The Kotlin adapter deliberately fails clearly until those pieces are built and wired. It does not claim to synthesize speech yet.

## FAISS and embeddings

Room stores conversation turns and embedding vectors. This milestone ranks stored vectors with cosine similarity in Kotlin; it is not FAISS and it does not bundle an embedding model. Supply a tested ONNX Runtime Mobile embedding model and implement LocalEmbedder before enabling semantic-memory claims.

## Security boundaries

No arbitrary shell execution is provided. Android actions are restricted to allow-listed intents, and launching another installed app requires explicit confirmation. Microphone permission must be requested at runtime and recording must be visible to the user.
