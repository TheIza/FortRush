import assert from "node:assert/strict";
import { generateKeyPairSync } from "node:crypto";
import { test } from "node:test";
import { handleProfileApi, ProfileApiError } from "./src/profile-api.mjs";

test("profile API reserves nicknames atomically and enforces the cooldown", async () => {
  const originalFetch = globalThis.fetch;
  const docs = new Map();
  let revision = 0;
  let conflictOnFirstCreate = true;
  let batchReadSequence = 0;
  const { privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  const env = {
    FIREBASE_PROJECT_ID: "fortrush-test",
    FIREBASE_SERVICE_ACCOUNT_JSON: JSON.stringify({
      project_id: "fortrush-test",
      client_email: "worker-test@fortrush-test.iam.gserviceaccount.com",
      private_key: privateKey.export({ type: "pkcs8", format: "pem" }).toString(),
      token_uri: "https://oauth2.googleapis.com/token"
    })
  };
  const documentName = (collection, id) => `projects/fortrush-test/databases/(default)/documents/${collection}/${id}`;
  const firestoreValue = (value) => typeof value === "string" ? { stringValue: value }
    : typeof value === "number" ? { integerValue: String(value) }
      : value === null ? { nullValue: null } : value;

  globalThis.fetch = async (input, init = {}) => {
    const url = String(input);
    if (url === "https://oauth2.googleapis.com/token") return Response.json({ access_token: "test-token", expires_in: 3600 });
    if (url.endsWith("/documents:beginTransaction") || url.endsWith("/documents:rollback")) return Response.json({ transaction: "test-tx" });
    if (url.endsWith("/documents:batchGet")) {
      const { documents } = JSON.parse(init.body);
      const lines = documents.map((name) => docs.has(name) ? { found: structuredClone(docs.get(name)) } : { missing: name });
      batchReadSequence += 1;
      return batchReadSequence % 2 === 1
        ? Response.json(lines)
        : new Response(lines.map(JSON.stringify).join("\n"));
    }
    if (url.endsWith("/documents:commit")) {
      const { writes } = JSON.parse(init.body);
      if (conflictOnFirstCreate) {
        const create = writes.find((write) => write.update && write.currentDocument?.exists === false);
        if (create) {
          conflictOnFirstCreate = false;
          const name = create.update.name;
          const document = { name, fields: structuredClone(create.update.fields) };
          for (const transform of create.updateTransforms ?? []) {
            if (transform.setToServerValue === "REQUEST_TIME") document.fields[transform.fieldPath] = { timestampValue: new Date().toISOString() };
          }
          document.updateTime = new Date(Date.now() + ++revision).toISOString();
          docs.set(name, document);
          return Response.json({ error: { status: "ALREADY_EXISTS" } }, { status: 409 });
        }
      }
      const next = new Map(docs);
      for (const write of writes) {
        const name = write.update?.name ?? write.delete;
        const existing = next.get(name);
        if (write.currentDocument?.exists === false && existing) return Response.json({ error: { status: "ABORTED" } }, { status: 409 });
        if (write.currentDocument?.exists === true && !existing) return Response.json({ error: { status: "ABORTED" } }, { status: 409 });
        if (write.currentDocument?.updateTime && existing?.updateTime !== write.currentDocument.updateTime) return Response.json({ error: { status: "ABORTED" } }, { status: 409 });
        if (write.delete) { next.delete(name); continue; }
        const document = existing ? structuredClone(existing) : { name, fields: {} };
        for (const key of write.updateMask?.fieldPaths ?? Object.keys(write.update.fields)) {
          const value = write.update.fields[key];
          if (value === undefined) delete document.fields[key];
          else document.fields[key] = value;
        }
        for (const transform of write.updateTransforms ?? []) {
          if (transform.setToServerValue === "REQUEST_TIME") document.fields[transform.fieldPath] = { timestampValue: new Date().toISOString() };
        }
        document.updateTime = new Date(Date.now() + ++revision).toISOString();
        next.set(name, document);
      }
      docs.clear();
      for (const [name, document] of next) docs.set(name, document);
      return Response.json({ commitTime: new Date().toISOString() });
    }
    throw new Error(`Unexpected fetch: ${url}`);
  };

  const call = (uid, method, pathname, body) => handleProfileApi(
    new Request(`https://worker.test${pathname}`, {
      method,
      ...(body ? { headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) } : {})
    }), env, uid, `${uid}@example.test`
  );

  try {
    const empty = await call("alice", "GET", "/api/profile/me");
    assert.equal(empty.nickname, null);
    const initial = await call("alice", "PUT", "/api/profile/nickname", { nickname: "Guerrera" });
    assert.equal(initial.nickname, "Guerrera");
    assert.equal(initial.lastNicknameChangeAt, null);

    const renamed = await call("alice", "PUT", "/api/profile/nickname", { nickname: "Capitana" });
    assert.equal(renamed.nickname, "Capitana");
    assert.ok(renamed.lastNicknameChangeAt);
    await assert.rejects(
      call("alice", "PUT", "/api/profile/nickname", { nickname: "Fortaleza" }),
      (error) => error instanceof ProfileApiError && error.code === "NICKNAME_COOLDOWN"
    );

    const userName = documentName("Usuarios", "alice");
    const alice = docs.get(userName);
    alice.fields.monedas = firestoreValue(25);
    alice.fields.campoExtra = firestoreValue("se conserva");
    alice.fields.fechaUltimoCambioApodo = { timestampValue: new Date(Date.now() - 8 * 24 * 60 * 60 * 1000).toISOString() };
    alice.updateTime = new Date().toISOString();
    docs.set(userName, alice);

    const changed = await call("alice", "PUT", "/api/profile/nickname", { nickname: "Fortaleza" });
    assert.equal(changed.nickname, "Fortaleza");
    assert.equal(docs.get(userName).fields.monedas.integerValue, "25");
    assert.equal(docs.get(userName).fields.campoExtra.stringValue, "se conserva");
    assert.equal(docs.has(documentName("Apodos", "capitana")), false);
    assert.equal(docs.get(documentName("Apodos", "fortaleza")).fields.uid.stringValue, "alice");

    await call("bob", "GET", "/api/profile/me");
    await assert.rejects(
      call("bob", "PUT", "/api/profile/nickname", { nickname: "Fortaleza" }),
      (error) => error instanceof ProfileApiError && error.code === "NICKNAME_TAKEN"
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});
