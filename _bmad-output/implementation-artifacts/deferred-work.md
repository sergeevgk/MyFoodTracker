# Deferred Work

## Deferred from: code review of story 1-2-passcode-authentication-and-startup-profile-lock (2026-09-13)

- **D-1: Single-iteration SHA-256 for 4-digit PINs** - PasscodeHasher uses SHA-256 (fast hash) for a 4-digit PIN keyspace (10K combinations). A slow KDF (PBKDF2/Argon2) would improve resistance to offline brute-force if the SQLite DB is extracted. Pre-existing design decision per spec.
- **D-2: No attempt rate limiting / lockout** - No failed-attempt counter, exponential backoff, or temporary lockout after repeated wrong passcodes. Desirable hardening but not specified in any acceptance criteria.
- **D-3: No logout action in settings screen** - AC #6 mentions "dashboard or settings" but no settings screen exists (template no-op). Dashboard logout is implemented. Settings screen is a future concern.
- **D-4: Dashboard has no ViewModel (MVVM violation)** - DashboardFragment directly injects SessionRepository and LogoutUseCase. Acceptable for the current placeholder; ViewModel expected when the dashboard gets real functionality in Epic 2.
- **D-5: Passcode hashes circulate through UI states** - UserProfile domain model includes passcodeHash, which flows into PasscodeAuthState.Content.profiles and the in-memory session. Refactoring to exclude sensitive data from presentation contexts is a broader model change.
