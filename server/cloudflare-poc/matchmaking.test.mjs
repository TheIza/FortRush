import assert from "node:assert/strict";
import test from "node:test";
import { WebSocketProbe } from "./src/index.mjs";

function makeSocket(uid = null) {
  let attachment = { uid, queueSequence: null, matchId: null, playerNumber: null };
  const messages = [];
  const closeCalls = [];
  const socket = {
    readyState: WebSocket.OPEN,
    send: (value) => messages.push(JSON.parse(value)),
    close: (code, reason) => closeCalls.push({ code, reason }),
    deserializeAttachment: () => attachment,
    serializeAttachment: (value) => { attachment = value; }
  };
  return { socket, messages, closeCalls, get attachment() { return attachment; } };
}

function makeContext(sockets) {
  const storage = new Map([
    ["queue", []],
    ["queueSequence", 0]
  ]);
  return {
    storage,
    context: {
      storage: {
        get: async (key) => storage.get(key),
        put: async (key, value) => { storage.set(key, value); }
      },
      getWebSockets: () => sockets
    }
  };
}

test("two distinct Firebase UIDs create one match and receive its same ID", async () => {
  const first = makeSocket("uid-one");
  const second = makeSocket();
  const { context, storage } = makeContext([first.socket, second.socket]);
  const probe = new WebSocketProbe(context, { FIREBASE_PROJECT_ID: "test-project" }, async (token) => {
    assert.equal(token, "firebase-token-two");
    return { uid: "uid-two" };
  });

  await probe.authenticate(second.socket, second.attachment, { type: "auth", token: "firebase-token-two" });
  assert.equal(second.messages[0].type, "auth.ok");
  assert.equal(second.attachment.uid, "uid-two");

  await probe.joinQueue(first.socket, first.attachment, { requestId: "join-one" });
  await probe.joinQueue(second.socket, second.attachment, { requestId: "join-two" });

  const match = storage.get("match");
  assert.ok(match.matchId);
  assert.equal(match.players.length, 2);
  assert.deepEqual(match.players.map((player) => player.uid), ["uid-one", "uid-two"]);
  assert.deepEqual(match.players.map((player) => player.playerNumber), [1, 2]);

  const matchFound = [...first.messages, ...second.messages].filter((message) => message.type === "match.found");
  assert.equal(matchFound.length, 2);
  assert.deepEqual(new Set(matchFound.map((message) => message.matchId)), new Set([match.matchId]));
});

test("authentication rejects a second socket for the same Firebase UID", async () => {
  const first = makeSocket("uid-one");
  const duplicate = makeSocket();
  const { context } = makeContext([first.socket, duplicate.socket]);
  const probe = new WebSocketProbe(context, { FIREBASE_PROJECT_ID: "test-project" }, async () => ({ uid: "uid-one" }));

  await probe.authenticate(duplicate.socket, duplicate.attachment, { type: "auth", token: "duplicate-token" });

  assert.equal(duplicate.messages.length, 1);
  assert.deepEqual(duplicate.messages[0], {
    type: "error",
    code: "ALREADY_CONNECTED",
    message: "Esta cuenta ya está conectada a la prueba."
  });
  assert.deepEqual(duplicate.closeCalls, [{ code: 1008, reason: "Duplicate account" }]);
  assert.equal(duplicate.attachment.uid, null);
});