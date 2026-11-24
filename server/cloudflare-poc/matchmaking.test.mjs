import assert from "node:assert/strict";
import test from "node:test";
import { WebSocketProbe } from "./src/index.mjs";
import { createSharedMap } from "./src/shared-map.mjs";

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

test("a third player waits behind the active pair and can cancel without affecting them", async () => {
  const first = makeSocket("uid-one");
  const second = makeSocket("uid-two");
  const third = makeSocket("uid-three");
  const { context, storage } = makeContext([first.socket, second.socket, third.socket]);
  const probe = new WebSocketProbe(context, {});

  await probe.joinQueue(first.socket, first.attachment, { requestId: "first" });
  await probe.joinQueue(second.socket, second.attachment, { requestId: "second" });
  const matchId = storage.get("match").matchId;
  await probe.joinQueue(third.socket, third.attachment, { requestId: "third" });

  assert.equal(storage.get("match").matchId, matchId);
  assert.deepEqual(storage.get("queue"), [{ uid: "uid-three", sequence: 3 }]);
  assert.ok(third.messages.some((message) => message.type === "queue.waiting" && message.position === 1));
  assert.equal(third.messages.some((message) => message.type === "match.found"), false);

  await probe.cancelQueue(third.socket, third.attachment, { requestId: "cancel-third" });

  assert.deepEqual(storage.get("queue"), []);
  assert.equal(storage.get("match").matchId, matchId);
  assert.ok(third.messages.some((message) => message.type === "queue.cancelled"));
  assert.ok(first.messages.some((message) => message.type === "match.found"));
  assert.ok(second.messages.some((message) => message.type === "match.found"));
});

test("loading timeout requeues the player who loaded at their original sequence", async () => {
  const first = makeSocket("uid-one");
  const second = makeSocket("uid-two");
  const match = {
    matchId: "loading-timeout",
    phase: "LOADING",
    loadingDeadlineMs: Date.now() - 1,
    players: [
      { uid: "uid-one", sequence: 4, playerNumber: 1, loaded: true },
      { uid: "uid-two", sequence: 5, playerNumber: 2, loaded: false }
    ]
  };
  first.socket.serializeAttachment({ ...first.attachment, queueSequence: null, matchId: match.matchId, playerNumber: 1 });
  second.socket.serializeAttachment({ ...second.attachment, queueSequence: null, matchId: match.matchId, playerNumber: 2 });
  const stored = new Map([["match", match], ["queue", []]]);
  const context = {
    storage: {
      get: async (key) => stored.get(key),
      put: async (key, value) => { stored.set(key, value); },
      setAlarm: async () => {}
    },
    getWebSockets: () => [first.socket, second.socket]
  };
  const probe = new WebSocketProbe(context, {});

  await probe.alarm();

  assert.equal(stored.get("match"), null);
  assert.deepEqual(stored.get("queue"), [{ uid: "uid-one", sequence: 4 }]);
  assert.equal(first.messages.at(-1).type, "queue.waiting");
  assert.ok(first.messages.some((message) => message.type === "match.cancelled" && message.requeued));
  assert.ok(second.messages.some((message) => message.type === "match.cancelled" && !message.requeued));
  assert.equal(first.attachment.queueSequence, 4);
  assert.equal(second.attachment.queueSequence, null);
});

test("both loaded players receive the same game start after the shared countdown", async () => {
  const first = makeSocket("uid-one");
  const second = makeSocket("uid-two");
  const map = createSharedMap("loaded-countdown-map");
  const match = {
    matchId: "loaded-countdown-match",
    balance: null,
    phase: "LOADING",
    countdown: 0,
    loadingDeadlineMs: Date.now() + 30_000,
    map,
    towers: [],
    startedAtServerMs: null,
    players: [
      { uid: "uid-one", sequence: 1, playerNumber: 1, loaded: false, coinsSpent: 0, processedActionIds: [] },
      { uid: "uid-two", sequence: 2, playerNumber: 2, loaded: false, coinsSpent: 0, processedActionIds: [] }
    ]
  };
  first.socket.serializeAttachment({ ...first.attachment, matchId: match.matchId, playerNumber: 1 });
  second.socket.serializeAttachment({ ...second.attachment, matchId: match.matchId, playerNumber: 2 });
  const stored = new Map([["match", match]]);
  const alarms = [];
  const context = {
    storage: {
      get: async (key) => stored.get(key),
      put: async (key, value) => { stored.set(key, value); },
      setAlarm: async (timestamp) => { alarms.push(timestamp); }
    },
    getWebSockets: () => [first.socket, second.socket]
  };
  const probe = new WebSocketProbe(context, {});

  await probe.markLoaded(first.socket, first.attachment, { requestId: "load-one" });
  assert.equal(match.phase, "LOADING");
  await probe.markLoaded(second.socket, second.attachment, { requestId: "load-two" });
  assert.equal(match.phase, "COUNTDOWN");
  assert.equal(match.countdown, 3);
  assert.ok(first.messages.some((message) => message.type === "match.countdown" && message.seconds === 3));
  assert.ok(second.messages.some((message) => message.type === "match.countdown" && message.seconds === 3));

  await probe.alarm();
  await probe.alarm();
  await probe.alarm();

  assert.equal(match.phase, "IN_MATCH");
  const firstStart = first.messages.find((message) => message.type === "game_start");
  const secondStart = second.messages.find((message) => message.type === "game_start");
  assert.ok(firstStart);
  assert.deepEqual(firstStart, secondStart);
  assert.deepEqual(firstStart.map, map);
  assert.ok(alarms.length >= 3);
});
