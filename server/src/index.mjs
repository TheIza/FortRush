import { applicationDefault, cert, initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { createServer } from "node:http";
import { createApp } from "./app.mjs";
import { attachMatchmaking } from "./matchmaking.mjs";

const projectId = process.env.FIREBASE_PROJECT_ID;
const serviceAccountJson = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
let credential;
if (serviceAccountJson) {
  credential = cert(JSON.parse(serviceAccountJson));
} else if (process.env.FIRESTORE_EMULATOR_HOST || process.env.FIREBASE_AUTH_EMULATOR_HOST) {
  credential = undefined;
} else {
  credential = applicationDefault();
}

initializeApp({ credential, ...(projectId ? { projectId } : {}) });
const app = createApp({
  auth: getAuth(),
  db: getFirestore(),
  serverTimestamp: () => FieldValue.serverTimestamp()
});
const httpServer = createServer(app);
attachMatchmaking(httpServer, { auth: getAuth(), db: getFirestore() });

const port = Number(process.env.PORT ?? 3000);
httpServer.listen(port, "0.0.0.0", () => console.log(`FortRush API listening on ${port}`));
