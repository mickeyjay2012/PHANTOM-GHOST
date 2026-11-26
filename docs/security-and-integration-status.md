# Security and integration status

This commit adds application-layer foundations, not a production-ready identity or AI service.

## Neural core
- PersonaManager supplies the PHANTOM GHOST style prompt. It does not bypass safety, permission or privacy controls.
- SlidingWindowContext enforces a token budget and delegates older turns to a user-partitioned archive adapter.
- The existing JNI llama.cpp bridge still reports inference as unavailable. Real token-by-token native callbacks, model compatibility testing and on-device profiling remain required.

## Authentication
- DevMockAuthRepository is an in-memory development scaffold. Its random session value is an opaque development token, NOT a JWT.
- The mock verification mailer is injected. A local development mail sink may display codes only in a debug build.
- Production requires Firebase Auth or Supabase Auth, server-side email verification, secure token refresh/revocation and backend authorization. Never trust a client-issued token for server access.

## Voice privacy
- VoiceIsolator compares caller-supplied 256-dimensional embeddings. It does not record or store samples and no speaker-embedding model is bundled.
- Voice matching is spoofable and must never be the only authentication factor or authorize sensitive actions.
- Enrollment must be opt-in, explain biometric processing, keep embeddings encrypted on-device, support deletion, and require a separate device credential for sensitive operations.

## Android actions
- Actions must remain allow-listed, user-visible and confirmed where they affect other apps.
- Accessibility and notification access are not enabled by this scaffold. If later added, they must be optional, user-enabled in Android settings, narrowly scoped and clearly disclosed.
- Background calendar/battery monitoring requires explicit permissions and user controls.

## Release gate
Do not publish a production APK until native inference, real auth, database user partitioning/encryption, voice model provenance, accessibility review, automated tests and physical-device testing are complete.
