# ON36 Code Generator

Native Android administrator console for the existing Supabase invite-code backend.

## Build
GitHub Actions builds a debug APK on every push to `main` and on manual dispatch. Download the artifact named **ON36-Code-Generator-debug-apk** from the workflow run.

## Backend
Uses Supabase project `qoujujwecszmwcabevmv` and the deployed `invite-code-admin` Edge Function. Authorization is checked server-side on each privileged action. The app embeds only the publishable key, never the service-role key.

## Included
- Admin login and server-side access check
- Overview metrics
- Group-based random batch generation (1–200)
- Code list and revocation
- Administrator list/add/remove
- Audit activity

Generation requires an existing Study group. The current backend generates random codes; custom-code insertion and redeemer email/activation timeline require additional backend support.
