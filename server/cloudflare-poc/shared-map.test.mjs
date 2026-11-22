import assert from "node:assert/strict";
import test from "node:test";
import { createSharedMap } from "./src/shared-map.mjs";
import { WebSocketProbe } from "./src/index.mjs";
import { MAP_ARCHETYPES } from "./src/map-v2.mjs";

function towerBuildHarness({ phase = "IN_MATCH", env = {}, coinsSpent = 0, lastTowerBuiltAtMs = null, occupiedSlotId = null, startedAgoMs = 0 } = {}) {
  const map = createSharedMap("tower-build-map");
  const startedAtServerMs = Date.now() - startedAgoMs;
  const match = {
    matchId: "tower-build-match",
    phase,
    startedAtServerMs: phase === "IN_MATCH" ? startedAtServerMs : null,
    players: [
      { uid: "uid-one", playerNumber: 1, coinsSpent, lastTowerBuiltAtMs, processedActionIds: [] },
      { uid: "uid-two", playerNumber: 2, coinsSpent: 0, lastTowerBuiltAtMs: null, processedActionIds: [] }
    ],
    map,
    towers: occupiedSlotId ? [{ slotId: occupiedSlotId, ownerPlayerNumber: 1, card: "arrow_tower", cost: 100 }] : []
  };
  const stored = new Map([["match", match]]);
  const messages = new Map();
  const sockets = [
    { uid: "uid-one", playerNumber: 1 },
    { uid: "uid-two", playerNumber: 2 }
  ].map(({ uid, playerNumber }) => {
    let attachment = { uid, playerNumber, matchId: match.matchId };
    const sent = [];
    messages.set(uid, sent);
    return {
      readyState: WebSocket.OPEN,
      send: (value) => sent.push(JSON.parse(value)),
      deserializeAttachment: () => attachment,
      serializeAttachment: (value) => { attachment = value; }
    };
  });
  let writes = 0;
  const context = {
    storage: {
      get: async (key) => stored.get(key),
      put: async (key, value) => { writes += 1; stored.set(key, value); }
    },
    getWebSockets: () => sockets
  };
  return {
    map,
    match,
    stored,
    messages,
    writes: () => writes,
    probe: new WebSocketProbe(context, env),
    playerOneSocket: sockets[0],
    playerTwoSocket: sockets[1]
  };
}

async function sendBuild(probe, socket, values = {}) {
  await probe.webSocketMessage(socket, JSON.stringify({
    type: "build_tower",
    card: "arrow_tower",
    slotId: "p1-01",
    actionId: "tower-action-1",
    ...values
  }));
}

function lastMessage(harness, uid = "uid-one") {
  return harness.messages.get(uid).at(-1);
}

async function assertBuildRejectedWithoutChangingGame(harness, reason, values = {}) {
  const before = structuredClone(harness.match);
  const writesBefore = harness.writes();
  await sendBuild(harness.probe, harness.playerOneSocket, values);
  assert.equal(lastMessage(harness)?.type, "action_rejected");
  assert.equal(lastMessage(harness)?.reason, reason);
  assert.deepEqual(harness.match, before);
  assert.equal(harness.writes(), writesBefore);
}

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

test("game_start sends the confirmed 150 coins and 5 coins per second", async () => {
  const stored = new Map([["match", {
    matchId: "economy-match",
    phase: "COUNTDOWN",
    countdown: 1,
    startedAtServerMs: null,
    players: [
      { uid: "uid-one", playerNumber: 1, coinsSpent: 0 },
      { uid: "uid-two", playerNumber: 2, coinsSpent: 0 }
    ],
    map: createSharedMap("economy-map")
  }]]);
  const messages = [[], []];
  const sockets = ["uid-one", "uid-two"].map((uid, index) => {
    let attachment = { uid, playerNumber: index + 1 };
    return {
      readyState: WebSocket.OPEN,
      send: (value) => messages[index].push(JSON.parse(value)),
      deserializeAttachment: () => attachment,
      serializeAttachment: (value) => { attachment = value; }
    };
  });
  const context = {
    storage: { get: async (key) => stored.get(key), put: async (key, value) => stored.set(key, value) },
    getWebSockets: () => sockets
  };
  await new WebSocketProbe(context, {}).alarm();

  for (const playerMessages of messages) {
    assert.equal(playerMessages[0].type, "game_start");
    assert.deepEqual(playerMessages[0].economy, {
      startingCoins: 150,
      incomePerSecond: 5,
      startedAtServerMs: stored.get("match").startedAtServerMs,
      serverTimeMs: stored.get("match").startedAtServerMs,
      coinsSpent: 0
    });
  }
});

test("build_tower rejects an unknown slot without changing coins or game state", async () => {
  await assertBuildRejectedWithoutChangingGame(towerBuildHarness(), "UNKNOWN_SLOT", { slotId: "no-such-slot" });
});

test("build_tower uses the socket identity and rejects a rival slot", async () => {
  const harness = towerBuildHarness();
  const rivalSlot = harness.map.slots.find((slot) => slot.owner === "player2").id;
  await assertBuildRejectedWithoutChangingGame(harness, "NOT_YOUR_SLOT", {
    slotId: rivalSlot,
    playerNumber: 2
  });
});

test("build_tower rejects an occupied slot without changing coins or game state", async () => {
  const harness = towerBuildHarness({ occupiedSlotId: "p1-01" });
  await assertBuildRejectedWithoutChangingGame(harness, "SLOT_OCCUPIED");
});

test("build_tower rejects a non-tower card without changing coins or game state", async () => {
  await assertBuildRejectedWithoutChangingGame(towerBuildHarness(), "NOT_A_TOWER_CARD", { card: "swordsman" });
});

test("build_tower rejects inherited object keys as non-tower cards", async () => {
  await assertBuildRejectedWithoutChangingGame(towerBuildHarness(), "NOT_A_TOWER_CARD", { card: "__proto__" });
});

test("build_tower rejects insufficient coins without changing coins or game state", async () => {
  await assertBuildRejectedWithoutChangingGame(towerBuildHarness({ coinsSpent: 150 }), "NOT_ENOUGH_COINS", {
    card: "cannon_tower"
  });
});

test("build_tower can reject an active cooldown when configured", async () => {
  const harness = towerBuildHarness({
    env: { TOWER_BUILD_COOLDOWN_MS: "5000" },
    lastTowerBuiltAtMs: Date.now()
  });
  await assertBuildRejectedWithoutChangingGame(harness, "ON_COOLDOWN");
});

test("build_tower rejects countdown and sudden death without changing coins or game state", async () => {
  await assertBuildRejectedWithoutChangingGame(towerBuildHarness({ phase: "COUNTDOWN" }), "INVALID_STATE");
  await assertBuildRejectedWithoutChangingGame(towerBuildHarness({ phase: "SUDDEN_DEATH" }), "INVALID_STATE");
});

test("build_tower charges the selected cost and ignores duplicate action IDs", async () => {
  const harness = towerBuildHarness();
  await sendBuild(harness.probe, harness.playerOneSocket, { actionId: "same-action" });

  const saved = harness.stored.get("match");
  assert.equal(saved.towers.length, 1);
  assert.deepEqual(saved.towers[0], {
    slotId: "p1-01",
    ownerPlayerNumber: 1,
    card: "arrow_tower",
    cost: 100,
    placedAtServerMs: saved.towers[0].placedAtServerMs
  });
  assert.equal(saved.players[0].coinsSpent, 100);
  const sentBeforeDuplicate = harness.messages.get("uid-one").length;
  await sendBuild(harness.probe, harness.playerOneSocket, { actionId: "same-action" });
  assert.equal(harness.stored.get("match").towers.length, 1);
  assert.equal(harness.stored.get("match").players[0].coinsSpent, 100);
  assert.equal(harness.messages.get("uid-one").length, sentBeforeDuplicate);
  for (const uid of ["uid-one", "uid-two"]) {
    const built = harness.messages.get(uid).find((message) => message.type === "tower_built");
    assert.equal(built.slotId, "p1-01");
    assert.equal(built.card, "arrow_tower");
    assert.equal(built.ownerPlayerNumber, 1);
  }
  const ownEconomy = harness.messages.get("uid-one").find((message) => message.type === "tower_built").economy;
  assert.equal(ownEconomy.coinsSpent, 100);
});

test("build_tower charges 130 coins for the cannon tower", async () => {
  const harness = towerBuildHarness({ startedAgoMs: 1_000 });
  await sendBuild(harness.probe, harness.playerOneSocket, {
    card: "cannon_tower",
    slotId: "p1-02",
    actionId: "cannon-action"
  });

  const saved = harness.stored.get("match");
  assert.equal(saved.towers.length, 1);
  assert.equal(saved.towers[0].card, "cannon_tower");
  assert.equal(saved.towers[0].cost, 130);
  assert.equal(saved.players[0].coinsSpent, 130);
});
