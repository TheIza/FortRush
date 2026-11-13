import test, { before, beforeEach, after } from "node:test";
import assert from "node:assert/strict";
import { once } from "node:events";
import { initializeApp, deleteApp } from "firebase-admin/app";
import { FieldValue, Timestamp, getFirestore } from "firebase-admin/firestore";
import { createApp } from "../src/app.mjs";

const projectId = "demo-fortrush-nicknames";
let adminApp, db, server, baseUrl, nowMs;
const fakeAuth = {
  async verifyIdToken(token) {
    if (!token.startsWith("valid:")) throw new Error("invalid ID token");
    const uid = token.slice(6);
    return { uid, email: `${uid}@example.test`, name: "Google name" };
  }
};
async function clearCollection(name) {
  const snapshot = await db.collection(name).get();
  const batch = db.batch();
  snapshot.docs.forEach((entry) => batch.delete(entry.ref));
  if (!snapshot.empty) await batch.commit();
}
async function call(path, { uid = "alice", method = "GET", body } = {}) {
  return fetch(`${baseUrl}${path}`, {
    method,
    headers: { Authorization: `Bearer valid:${uid}`, ...(body ? { "Content-Type": "application/json" } : {}) },
    ...(body ? { body: JSON.stringify(body) } : {})
  });
}
before(async () => {
  if (!process.env.FIRESTORE_EMULATOR_HOST) throw new Error("Run with npm run test:integration.");
  adminApp = initializeApp({ projectId }, `fort-rush-test-${Date.now()}`);
  db = getFirestore(adminApp);
  const app = createApp({ auth: fakeAuth, db, serverTimestamp: () => FieldValue.serverTimestamp(), now: () => nowMs });
  server = app.listen(0, "127.0.0.1");
  await once(server, "listening");
  baseUrl = `http://127.0.0.1:${server.address().port}`;
});
beforeEach(async () => {
  nowMs = Date.now();
  await clearCollection("Usuarios");
  await clearCollection("Apodos");
});
after(async () => {
  if (server) await new Promise((resolve) => server.close(resolve));
  if (adminApp) await deleteApp(adminApp);
});

test("new profile requires an initial nickname and preserves existing profile fields", async () => {
  let response = await call("/api/profile/me");
  assert.equal(response.status, 200);
  assert.equal((await response.json()).nickname, null);
  assert.equal((await db.collection("Usuarios").doc("alice").get()).get("nombreUsuario"), "");
  await db.collection("Usuarios").doc("bob").set({ id: "bob", gmail: "old@example.test", apodo: "", monedas: 123, experiencia: 5, nivel: 2, retained: true });
  response = await call("/api/profile/nickname", { uid: "bob", method: "PUT", body: { nickname: "Guerrero" } });
  assert.equal(response.status, 200);
  const bob = (await db.collection("Usuarios").doc("bob").get()).data();
  assert.equal(bob.monedas, 123);
  assert.equal(bob.experiencia, 5);
  assert.equal(bob.nivel, 2);
  assert.equal(bob.retained, true);
  assert.equal(bob.apodoNormalizado, "guerrero");
  assert.equal(bob.fechaUltimoCambioApodo, null);
});

test("short, long, spaced, and special-character nicknames are rejected", async () => {
  for (const nickname of ["ab", "abcdefghijklmnopq", "No espacios", "jugador!"]) {
    const response = await call("/api/profile/nickname", { method: "PUT", body: { nickname } });
    assert.equal(response.status, 400, nickname);
  }
  assert.equal((await db.collection("Apodos").get()).size, 0);
});

test("availability and reservations ignore case; occupied nickname is rejected", async () => {
  assert.equal((await call("/api/profile/nickname", { method: "PUT", body: { nickname: "Guerrero" } })).status, 200);
  const availability = await call("/api/nicknames/availability?nickname=GUERRERO", { uid: "bob" });
  assert.deepEqual(await availability.json(), { available: false });
  assert.equal((await call("/api/profile/nickname", { uid: "bob", method: "PUT", body: { nickname: "guerrero" } })).status, 409);
});

test("two users racing for one nickname produce exactly one winner", async () => {
  const responses = await Promise.all([
    call("/api/profile/nickname", { uid: "alice", method: "PUT", body: { nickname: "Fortaleza" } }),
    call("/api/profile/nickname", { uid: "bob", method: "PUT", body: { nickname: "FORTALEZA" } })
  ]);
  assert.deepEqual(responses.map((response) => response.status).sort(), [200, 409]);
  assert.equal((await db.collection("Apodos").doc("fortaleza").get()).exists, true);
});

test("seven-day cooldown blocks early changes and releases the old nickname after an allowed change", async () => {
  const lastChange = nowMs - 6 * 24 * 60 * 60 * 1000;
  await db.collection("Usuarios").doc("alice").set({ id: "alice", apodo: "Anterior", apodoNormalizado: "anterior", fechaUltimoCambioApodo: Timestamp.fromMillis(lastChange), monedas: 75 });
  await db.collection("Apodos").doc("anterior").set({ uid: "alice", apodo: "Anterior" });
  let response = await call("/api/profile/nickname", { method: "PUT", body: { nickname: "Nuevo" } });
  assert.equal(response.status, 423);
  assert.ok((await response.json()).remainingMs > 23 * 60 * 60 * 1000);
  nowMs = lastChange + 7 * 24 * 60 * 60 * 1000;
  response = await call("/api/profile/nickname", { method: "PUT", body: { nickname: "Nuevo" } });
  assert.equal(response.status, 200);
  assert.equal((await response.json()).nicknameChangeRemainingMs, 7 * 24 * 60 * 60 * 1000);
  assert.equal((await db.collection("Apodos").doc("anterior").get()).exists, false);
  assert.equal((await db.collection("Apodos").doc("nuevo").get()).get("uid"), "alice");
  const profile = (await db.collection("Usuarios").doc("alice").get()).data();
  assert.equal(profile.apodo, "Nuevo");
  assert.ok(profile.fechaUltimoCambioApodo.toMillis() > lastChange);
});

test("retry is idempotent; nickname persists after reopening the profile endpoint", async () => {
  await call("/api/profile/nickname", { method: "PUT", body: { nickname: "Persistente" } });
  const before = (await db.collection("Usuarios").doc("alice").get()).get("fechaUltimoCambioApodo");
  assert.equal((await call("/api/profile/nickname", { method: "PUT", body: { nickname: "PERSISTENTE" } })).status, 200);
  assert.equal((await (await call("/api/profile/me")).json()).nickname, "Persistente");
  const after = (await db.collection("Usuarios").doc("alice").get()).get("fechaUltimoCambioApodo");
  assert.equal(after, before);
});

test("profile bootstrap reconciles an existing nickname without overwriting the profile", async () => {
  await db.collection("Usuarios").doc("alice").set({ id: "alice", gmail: "saved@example.test", apodo: "Legado", monedas: 42, campoExtra: "ok" });
  const response = await call("/api/profile/me");
  assert.equal((await response.json()).nickname, "Legado");
  assert.equal((await db.collection("Apodos").doc("legado").get()).get("uid"), "alice");
  const data = (await db.collection("Usuarios").doc("alice").get()).data();
  assert.equal(data.gmail, "saved@example.test");
  assert.equal(data.monedas, 42);
  assert.equal(data.campoExtra, "ok");
});

test("missing or invalid ID tokens are rejected", async () => {
  assert.equal((await fetch(`${baseUrl}/api/profile/me`)).status, 401);
  assert.equal((await fetch(`${baseUrl}/api/profile/me`, { headers: { Authorization: "Bearer forged" } })).status, 401);
});
