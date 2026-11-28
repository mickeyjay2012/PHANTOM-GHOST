# PHANTOM GHOST Supabase setup

Project ref: `nkvkgnhmearznzwkabev`

## Deployed database
- `public.profiles` stores account profile data only.
- RLS is enabled. A signed-in user can read or update only their own profile.
- `private.handle_new_user()` creates the profile row from signup metadata in a database trigger.
- Local assistant memory, model files, voice recordings and voice identity templates are not uploaded to Supabase.

The current schema is documented in [schema.sql](./schema.sql).

## Email verification code setup

The Android client calls Supabase Auth signup and verifies a six-digit code using the `signup` verification type. In the Supabase Dashboard:

1. Open **Authentication → Email Templates → Confirm signup**.
2. Set the subject to `PHANTOM GHOST — Verify your email`.
3. Use the one-time token variable `{{ .Token }}` in the confirmation template. Example:

```html
<h2>Verify your PHANTOM GHOST account</h2>
<p>Enter this six-digit code in the app:</p>
<h1>{{ .Token }}</h1>
<p>If you did not create this account, ignore this email. Never share your code.</p>
```

4. In **Authentication → Providers → Email**, keep email confirmation enabled.
5. Save and test with an email address you control.

The Android app includes a resend-code action. Actual delivery depends on the project's configured email provider and rate limits.

## Email delivery for real users

Supabase's built-in SMTP service is restricted to pre-authorized team email addresses and is intended for testing. Before public account registration, the project owner must configure a custom SMTP provider under **Authentication → SMTP Settings**. Keep the SMTP password in Supabase's protected settings; never put it in the Android app or GitHub repository.

See the official [Supabase custom SMTP guide](https://supabase.com/docs/guides/auth/auth-smtp).
