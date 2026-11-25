# ON36 Code Generator

Native Android administrator console for invite-code control in the Private Study Community.

## Included
- Supabase email/password sign-in with server-side admin allowlist validation
- Dashboard counts and generation activity
- Random code batches with count, length, prefix, label, purpose, notes and expiry date or Forever
- Manual custom codes (one per line, up to 200 per batch), duplicate detection, and expiry date or Forever
- Code registry showing stored code value, status, batch, notes, expiry, redeemer email and activation time
- Elapsed time since activation and active-now status
- Per-code audit history and code revocation
- Admin add/remove by account email; administrator permissions are independent of Study group membership

## Code storage and security
The deployed `invite-code-admin` Edge Function authenticates the Supabase session and checks `code_admins` on every privileged request. The Android app contains only the project's publishable key, never a service-role key.

Original code values are kept in `public.code_admin_values`, a separate RLS-enabled table with privileges revoked from `anon` and `authenticated`; only the server's `service_role` can access it. The admin function returns values only after its server-side admin check. Existing codes created before this secure value table contain only SHA-256 hashes, so their original plaintext cannot be reconstructed; newly generated and custom codes are retained for authorized admin display.

## Build
GitHub Actions builds a debug APK. Open the latest successful run and download **ON36-Code-Generator-debug-apk** from its Artifacts section.
