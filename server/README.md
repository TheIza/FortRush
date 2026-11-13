# FortRush profile API

This Node service is the trusted writer for `Usuarios/{uid}` and `Apodos/{normalizedNickname}`. Android sends its Firebase Authentication ID token over HTTPS; the Admin SDK verifies it and derives the UID from the verified token. The UID is never accepted from request JSON.

## Nickname policy

Both Android and Node normalize input to Unicode NFC, then accept only 3–16 Unicode code points consisting of ASCII letters, Spanish `áéíóúüñ` (uppercase is also accepted), ASCII digits, or `_`. Other Unicode letters, standalone combining marks, spaces, and symbols are rejected. The normalized reservation key is lowercase with locale-independent Unicode lowercase. The shared test vectors live at `shared/nickname-vectors.tsv` and are consumed by both Kotlin and Node tests.

## Run locally

1. Install Node.js 20 or newer and run `npm install` in this folder.
2. Start the Firestore Emulator (see the repository's Firebase Emulator instructions) and set `FIRESTORE_EMULATOR_HOST=127.0.0.1:8080`.
3. For emulator-only local testing, `FIREBASE_PROJECT_ID=demo-fortrush npm start` is sufficient; production credentials are not used.
4. Set `fortRushApiBaseUrl=http://10.0.2.2:3000` in Android `local.properties` for the Android emulator. A physical phone needs the computer's reachable LAN address. Release builds must use an HTTPS URL.

## Render and Firebase

Set `FIREBASE_PROJECT_ID` and `FIREBASE_SERVICE_ACCOUNT_JSON` as private environment variables in the server host. Create a restricted service account for this project and store its JSON only in the host's secret environment. Do not commit service account keys or put them in the APK. Configure Android's `fortRushApiBaseUrl` to the HTTPS service URL. No Firebase Cloud Function is used.

The service does not deploy itself, create a service account, publish Firestore rules, or configure a Render service. Those steps require the project owner's Firebase/Render accounts and must be done separately. Publish the root `firestore.rules` after testing them with the emulator.

## API

- `GET /health`: liveness only.
- `GET /api/profile/me`: creates a minimal profile only when absent, preserves all existing fields, and registers the reservation for a valid legacy nickname.
- `GET /api/nicknames/availability?nickname=...`: advisory availability check; never reserves the nickname.
- `PUT /api/profile/nickname` with `{ "nickname": "..." }`: authoritative atomic reservation/update. A Firestore transaction writes the new reservation, updates the same existing user profile, and releases the old reservation. Server time controls the seven-day cooldown. A repeated request for the current nickname is idempotent.

An interrupted transaction is all-or-nothing. If Android loses the response after a successful commit, retrying the same nickname returns success without duplicating a change.
