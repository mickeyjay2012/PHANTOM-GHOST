# PHANTOM GHOST integration status

## Implemented in this revision

- Pinned llama.cpp and whisper.cpp native libraries with JNI model loading, Llama token streaming callbacks, cancellation, and Whisper transcription of mono 16 kHz PCM16.
- Llama context allocation retries 2048, 1024, then 512 tokens. This cannot recover from an OS-level process kill or all native allocator failures.
- Supabase Auth REST sign-up, six-digit signup OTP verification, password sign-in, refresh-token rotation, and sign-out.
- Session tokens are encrypted at rest with AES-GCM and an Android Keystore key.
- Room conversation and memory records are scoped by userId. The migration assigns legacy records to an empty user ID instead of exposing them to authenticated users.
- Room 2.7.2 addresses the Kotlin 2.1 metadata incompatibility reported by CI.

## Configuration

Provide Gradle properties or CI environment variables:
- SUPABASE_URL=https://<project-ref>.supabase.co
- SUPABASE_ANON_KEY=<public-anon-key>

Configure Supabase email OTP templates/provider and enforce Row Level Security for all server tables. Never place a service-role key in the Android app.

## Still incomplete / not claimed as production-ready

- No licensed speaker-embedding ONNX model is selected or bundled. Without its known input/output tensor contract, voice enrollment and embedding extraction are not integrated. The existing cosine utility is not an embedding model or anti-spoofing system.
- No voiceprint biometric database table or enrollment recorder exists yet.
- No AccessibilityService or NotificationListenerService is enabled. A future navigation-only feature must be user-initiated and visible; arbitrary notification scraping is not enabled.
- No proactive calendar/battery foreground polling service is installed. Use Doze-aware WorkManager scheduling and explicit calendar permission rather than a five-minute polling loop.
- No model is downloaded automatically; model source, license, SHA-256, and memory-tier preflight remain required.
- Native inference is not yet device-validated. CI and physical ARM testing must pass before release.
