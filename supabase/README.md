# PHANTOM GHOST — Supabase setup

Supabase organization: **PHANTOM GHOST TECHNOLOGIES**  
Project: **༒︎ PHANTOM GHOST༒︎**  
Project ref: `eklwfehokrhnzxjplccv`  
Region: `eu-west-1`

## Database setup

Migration `create_profiles_rls_and_auth_user_sync` is applied to this project.

- `public.profiles` stores account profile data only.
- Each profile is linked to its Supabase Auth user.
- Row Level Security is enabled.
- Authenticated users can read, insert, and update only their own profile.
- Anonymous users have no profile-table access.
- `private.handle_new_user()` creates the profile row from signup metadata through an Auth trigger.
- Local assistant memory, model files, voice recordings, and voice identity templates remain on the device and are not uploaded to Supabase.

The deployed schema is mirrored in [schema.sql](./schema.sql).

## Android client configuration

The Android build reads `SUPABASE_URL` and `SUPABASE_ANON_KEY` from Gradle properties or environment variables. The CI workflow on this branch now points to this new project and uses its publishable client key.

Only a Supabase publishable/legacy anon key belongs in a public Android client. Never place a service-role or secret key in the app, GitHub source, or build artifacts.

## Six-digit email verification

The Android client signs up through Supabase Auth and verifies a six-digit code using the `signup` verification type.

In the Supabase Dashboard:

1. Open **Authentication → Email Templates → Confirm signup**.
2. Use a confirmation template that displays `{{ .Token }}` as the one-time code.
3. Keep email confirmation enabled under **Authentication → Providers → Email**.
4. Test signup using an email address you control.

The app includes a resend-code action. Successful database setup does not by itself prove that email delivery is working.

## Email delivery before public registration

Supabase's built-in SMTP sender is limited and intended for testing. Configure a trusted custom SMTP provider in the project's **Authentication → SMTP Settings** before relying on delivery to arbitrary users. Keep SMTP credentials only in Supabase's protected settings; never place them in Android or GitHub.

A parent/guardian may need to help with any provider account or billing setup. The project owner should complete an actual inbox test before treating email verification as production-ready.

See the official [Supabase custom SMTP guide](https://supabase.com/docs/guides/auth/auth-smtp).
