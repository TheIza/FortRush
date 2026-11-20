import assert from "node:assert/strict";
import test from "node:test";
import { createSharedMap } from "./src/shared-map.mjs";
import { WebSocketProbe } from "./src/index.mjs";
import { MAP_ARCHETYPES } from "./src/map-v2.mjs";

test("shared payload carries complete deterministic geometry and fixed gate/castle positions", () => {
  const first = createSharedMap("shared-payload-seed");
  const second = createSharedMap("shared-payload-seed");

  assert.equal(JSON.stringify(first), JSON.stringify(second));
  assert.equal(first.width, 1000);
  assert.equal(first.height, 1800);
  assert.ok(Array.isArray(first.path.points));
  assert.ok(Array.isArray(first.path.cumLen));
  assert.equal(first.path.cumLen.at(-1), first.path.totalLength);
  assert.equal(first.slots.length, 14);
  assert.equal(first.slots.filter((slot) => slot.owner === "player1").length, 7);
  assert.equal(first.slots.filter((slot) => slot.owner === "player2").length, 7);
  assert.deepEqual(first.wallGates.player1, { x: 500, y: 1548 });
  assert.deepEqual(first.wallGates.player2, { x: 500, y: 252 });
  assert.equal(first.castlePositions.player1.x, 500);
  assert.equal(first.castlePositions.player2.x, 500);
  assert.ok(Math.abs(first.castlePositions.player1.y - 1677.6) < 1e-9);
  assert.ok(Math.abs(first.castlePositions.player2.y - 122.4) < 1e-9);
  assert.equal("decorations" in first, false, "cosmetic decoration is generated on each client");
});

test("game_start sends the complete authoritative map to both players", async () => {
  const map = createSharedMap("game-start-map-seed");
  const stored = new Map([["match", {
    matchId: "match-for-test",
    phase: "COUNTDOWN",
    countdown: 1,
    players: [],
    map
  }]]);
  const messages = [[], []];
  const sockets = messages.map((sent) => ({
    readyState: WebSocket.OPEN,
    send: (value) => sent.push(JSON.parse(value))
  }));
  const context = {
    storage: {
      get: async (key) => stored.get(key),
      put: async (key, value) => { stored.set(key, value); }
    },
    getWebSockets: () => sockets
  };
  const probe = new WebSocketProbe(context, {});

  await probe.alarm();

  for (const playerMessages of messages) {
    assert.equal(playerMessages.length, 1);
    assert.equal(playerMessages[0].type, "game_start");
    assert.equal(playerMessages[0].matchId, "match-for-test");
    assert.deepEqual(playerMessages[0].map, map);
    assert.equal(playerMessages[0].map.slots.length, 14);
    assert.ok(Array.isArray(playerMessages[0].map.path.points));
  }
  assert.equal(stored.get("match").phase, "IN_MATCH");
});

test("pairing logs the generated seed and map shape", async () => {
  const stored = new Map();
  const sentMessages = [];
  const sockets = ["uid-one", "uid-two"].map((uid) => {
    let attachment = { uid };
    return {
      readyState: WebSocket.OPEN,
      deserializeAttachment: () => attachment,
      serializeAttachment: (value) => { attachment = value; },
      send: (value) => sentMessages.push(JSON.parse(value))
    };
  });
  const context = {
    storage: {
      get: async (key) => stored.get(key),
      put: async (key, value) => { stored.set(key, value); }
    },
    getWebSockets: () => sockets
  };
  const probe = new WebSocketProbe(context, {});
  const logEntries = [];
  const originalLog = console.log;

  try {
    console.log = (...args) => logEntries.push(args);
    await probe.pairQueue([
      { uid: "uid-one", sequence: 1 },
      { uid: "uid-two", sequence: 2 }
    ]);
  } finally {
    console.log = originalLog;
  }

  const mapLog = logEntries.find(([eventName]) => eventName === "match-map");
  assert.ok(mapLog);
  const details = mapLog[1];
  const storedMatch = stored.get("match");
  assert.equal(details.seed, storedMatch.map.seed);
  assert.equal(details.archetype, storedMatch.map.meta.archetype);
  assert.ok(MAP_ARCHETYPES.includes(details.archetype));
  assert.ok(sentMessages.every((message) => message.type === "match.found"));
});
