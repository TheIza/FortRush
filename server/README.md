# FortRush — servidor Node.js local

El Worker de Cloudflare es el backend activo para perfiles y emparejamiento. Este servidor Node.js se conserva para desarrollo y pruebas locales; no es necesario para que la app instalada se conecte al Worker.

This Node service is the trusted writer for `Usuarios/{uid}` and `Apodos/{normalizedNickname}`. Android sends its Firebase Authentication ID token over HTTPS; the Admin SDK verifies it and derives the UID from the verified token. The UID is never accepted from request JSON.

## Nickname policy

Both Android and Node normalize input to Unicode NFC, then accept only 3–16 Unicode code points consisting of ASCII letters, Spanish `áéíóúüñ` (uppercase is also accepted), ASCII digits, or `_`. Other Unicode letters, standalone combining marks, spaces, and symbols are rejected. The normalized reservation key is lowercase with locale-independent Unicode lowercase. The shared test vectors live at `shared/nickname-vectors.tsv` and are consumed by both Kotlin and Node tests.

## Run locally

1. Install Node.js 20 or newer and run `npm install` in this folder.
2. Start the Firestore Emulator (see the repository's Firebase Emulator instructions) and set `FIRESTORE_EMULATOR_HOST=127.0.0.1:8080`.
3. For emulator-only local testing, `FIREBASE_PROJECT_ID=demo-fortrush npm start` is sufficient; production credentials are not used.
4. Set `fortRushApiBaseUrl=http://10.0.2.2:3000` in Android `local.properties` for the Android emulator. A physical phone needs the computer's reachable LAN address. Release builds must use an HTTPS URL.

## Firebase para pruebas locales

Set `FIREBASE_PROJECT_ID` and `FIREBASE_SERVICE_ACCOUNT_JSON` only in the local server environment when testing against Firebase. Never commit the service account file or put it in the APK. The production Worker uses its own Cloudflare secret. No Firebase Cloud Function is used.

The local service does not deploy itself, create a service account, or publish Firestore rules. Test rule changes with the emulator before publishing the root `firestore.rules`.

## Matchmaking WebSocket

- Connect to the Cloudflare Worker URL in production (`ws://` is for local development).
- The first JSON message is `{ "type": "auth", "token": "<Firebase ID token>" }`. The server verifies the token and reads the nickname from `Usuarios/{uid}`.
- After `auth.ok`, send `queue.join` with a `requestId`. `queue.waiting` reports position. `queue.cancel` is accepted only while queued.
- `match.found` includes the opponent nickname and one server-generated map shared by both clients. Each client acknowledges the received/rendered geometry with `match.loaded`.
- The server gives map loading 30 seconds. If both clients acknowledge before the deadline, a server-controlled 3, 2, 1 countdown starts. `match.started` marks the transition out of matchmaking.
- If a player disconnects or fails map loading before the countdown, the remaining connected player returns to the queue at their original sequence position. Manual cancellation after `match.found` is rejected.
- The local Node server keeps unfinished matches in process memory. The production Cloudflare prototype uses a Durable Object, but reconnecting a dropped WebSocket is not implemented.
- The current Android `STARTED` screen is a preparation placeholder. The combat arena is a separate pending implementation.

## API

- `GET /health`: liveness only.
- `GET /api/profile/me`: creates a minimal profile only when absent, preserves all existing fields, and registers the reservation for a valid legacy nickname.
- `GET /api/nicknames/availability?nickname=...`: advisory availability check; never reserves the nickname.
- `PUT /api/profile/nickname` with `{ "nickname": "..." }`: authoritative atomic reservation/update. A Firestore transaction writes the new reservation, updates the same existing user profile, and releases the old reservation. Server time controls the seven-day cooldown. A repeated request for the current nickname is idempotent.

An interrupted transaction is all-or-nothing. If Android loses the response after a successful commit, retrying the same nickname returns success without duplicating a change.
