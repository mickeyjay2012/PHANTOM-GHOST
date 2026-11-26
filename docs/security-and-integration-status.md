# PHANTOM GHOST integration status

## Native integration in this revision

- llama.cpp and whisper.cpp are built from pinned upstream commits and linked into the JNI shared library when the corresponding CMake targets are available.
- Llama model loading creates a 2048-token context and retries context allocation at 1024 and 512 tokens. This does not recover from every allocator failure or OS process kill.
- Llama generation tokenizes the prompt, evaluates it with llama_decode, samples output tokens, and calls the Kotlin callback incrementally. Cancellation is cooperative between decode steps.
- Whisper accepts mono 16 kHz signed PCM16 samples and calls whisper_full.
- Room compiler/runtime are upgraded to 2.7.2 to resolve the Kotlin 2.1 metadata incompatibility seen in CI.

## Not yet production-complete

- No speaker embedding model is bundled. A licensed ONNX model and its exact tensor contract must be selected before real speaker embedding inference or enrollment can be implemented.
- No biometric voiceprint is stored. The cosine utility alone is not speaker isolation or identity verification.
- Supabase auth, token storage, notification access, AccessibilityService, calendar monitoring, and background scheduling are not implemented in this native-only revision.
- Native inference must still pass CI and be tested on physical ARM devices with real model files. The callback stream and latency are not yet device-validated.
- No model is downloaded automatically. Model source, license, SHA-256 and memory-tier checks are required.
