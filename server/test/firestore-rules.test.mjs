import test, { before, beforeEach, after } from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";
import { initializeTestEnvironment, assertFails, assertSucceeds } from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc, updateDoc, deleteDoc } from "firebase/firestore";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
let env;
before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-fortrush-rules",
    firestore: { rules: await readFile(resolve(root, "firestore.rules"), "utf8") }
  });
});
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "Usuarios/alice"), { id: "alice", apodo: "Guerrero", monedas: 100 });
    await setDoc(doc(context.firestore(), "Apodos/guerrero"), { uid: "alice", apodo: "Guerrero" });
  });
});
after(async () => env?.cleanup());

test("a player reads their own profile only", async () => {
  const alice = env.authenticatedContext("alice").firestore();
  const bob = env.authenticatedContext("bob").firestore();
  const anonymous = env.unauthenticatedContext().firestore();
  await assertSucceeds(getDoc(doc(alice, "Usuarios/alice")));
  await assertFails(getDoc(doc(bob, "Usuarios/alice")));
  await assertFails(getDoc(doc(anonymous, "Usuarios/alice")));
});

test("clients cannot alter protected profile fields or create/delete profiles", async () => {
  const alice = env.authenticatedContext("alice").firestore();
  for (const patch of [
    { apodo: "Intruso" }, { apodoNormalizado: "intruso" },
    { fechaUltimoCambioApodo: new Date() }, { monedas: 999999 }
  ]) await assertFails(updateDoc(doc(alice, "Usuarios/alice"), patch));
  await assertFails(setDoc(doc(alice, "Usuarios/bob"), { apodo: "Intruso" }));
  await assertFails(deleteDoc(doc(alice, "Usuarios/alice")));
});

test("clients cannot read/write nickname reservation documents", async () => {
  const alice = env.authenticatedContext("alice").firestore();
  await assertFails(getDoc(doc(alice, "Apodos/guerrero")));
  await assertFails(setDoc(doc(alice, "Apodos/intruso"), { uid: "alice" }));
  await assertFails(deleteDoc(doc(alice, "Apodos/guerrero")));
});
