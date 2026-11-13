import { applicationDefault, cert, initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { createApp } from "./app.mjs";

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

const port = Number(process.env.PORT ?? 3000);
app.listen(port, "0.0.0.0", () => console.log(`FortRush profile API listening on ${port}`));
