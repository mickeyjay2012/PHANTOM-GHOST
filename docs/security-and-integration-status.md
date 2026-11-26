# PHANTOM GHOST integration status

## Implemented
- Pinned llama.cpp and whisper.cpp JNI inference adapters, streaming token callbacks, cancellation and smaller-context fallback.
- Supabase Auth REST integration with encrypted Android Keystore sessions and per-user Room memory partitions.
- Optional AccessibilityService limited to one-shot search-field entry in approved browser/maps packages after an in-app confirmation. It does not read/store notification content or arbitrary screen text.
- Doze-aware WorkManager battery/calendar checks at 15-minute intervals. Calendar access and notification permission are required; the reminder uses generic private lock-screen text.

## Configuration and remaining limitations
- Configure SUPABASE_URL and SUPABASE_ANON_KEY through Gradle properties or CI environment variables. Configure Supabase signup OTP delivery and Row Level Security. Never ship a service-role key.
- Accessibility must be enabled manually in Android Settings; calendar and notifications require runtime permissions. The app still needs UI wiring to request these permissions and schedule/cancel work after user opt-in.
- Android WorkManager periodic scheduling has a 15-minute minimum; this is not an exact five-minute timer.
- Gmail/email summaries and notification reading are not implemented.
- No licensed ONNX speaker-embedding model has been selected. Voice enrollment and actual speaker embedding inference are not implemented; cosine comparison alone is not voice isolation or identity proof.
- Native inference still requires CI and physical ARM device validation.
