import assert from "node:assert/strict";
import test from "node:test";
import { WebSocketProbe } from "./src/index.mjs";
import { createSharedMap } from "./src/shared-map.mjs";
import { cloneMatchBalance } from "./src/match-balance.mjs";
import { initializeCombatState } from "./src/match-engine.mjs";

function makeGameplayHarness({ startedAgoMs = 0 } = {}) {
  const start = Date.now() - startedAgoMs;
  const match = {
    matchId: "online-gameplay-match",
    balance: cloneMatchBalance(),
    map: createSharedMap("online-gameplay-map"),
    players: [
      { uid: "uid-one", playerNumber: 1, sequence: 1, coinsAtLastUpdate: 150, coinsSpent: 0, incomeAppliedThroughSecond: 0, processedActionIds: [] },
      { uid: "uid-two", playerNumber: 2, sequence: 2, coinsAtLastUpdate: 150, coinsSpent: 0, incomeAppliedThroughSecond: 0, processedActionIds: [] }
    ],
    towers: []
  };
  initializeCombatState(match, start);
  const messages = new Map();
  const sockets = match.players.map((player) => {
    let attachment = { uid: player.uid, matchId: match.matchId, playerNumber: player.playerNumber, queueSequence: null };
    const sent = [];
    messages.set(player.uid, sent);
    return {
      readyState: WebSocket.OPEN,
      send: (text) => sent.push(JSON.parse(text)),
      deserializeAttachment: () => attachment,
      serializeAttachment: (value) => { attachment = value; }
    };
  });
  const storage = new Map([["match", match]]);
  const context = {
    storage: {
      get: async (key) => storage.get(key),
      put: async (key, value) => { storage.set(key, value); },
      setAlarm: async () => {},
      deleteAlarm: async () => {}
    },
    getWebSockets: () => sockets
  };
  return { match, messages, sockets, storage, probe: new WebSocketProbe(context, {}) };
}

test("deploy_troop uses the authenticated socket identity, charges once, and synchronizes both clients", async () => {
  const harness = makeGameplayHarness();
  const { match, messages, probe, sockets } = harness;

  await probe.webSocketMessage(sockets[0], JSON.stringify({
    type: "deploy_troop",
    card: "espadachin",
    position: match.map.path.totalLength * 0.2,
    playerNumber: 2,
    actionId: "deploy-action-1"
  }));

  assert.equal(match.troops.length, 3);
  assert.ok(match.troops.every((troop) => troop.ownerPlayerNumber === 1));
  assert.equal(match.players[0].coinsSpent, 45);
  assert.equal(match.players[0].coinsAtLastUpdate, 105);
  for (const uid of ["uid-one", "uid-two"]) {
    const deployed = messages.get(uid).find((message) => message.type === "troops_deployed");
    const state = messages.get(uid).find((message) => message.type === "match.state");
    assert.equal(deployed.ownerPlayerNumber, 1);
    assert.equal(deployed.troopIds.length, 3);
    assert.equal(state.state.troops.length, 3);
    assert.equal(state.economy.coins, uid === "uid-one" ? 105 : 150);
  }
});

test("invalid troop deployment is rejected without charging", async () => {
  const harness = makeGameplayHarness();
  await harness.probe.webSocketMessage(harness.sockets[0], JSON.stringify({
    type: "deploy_troop",
    card: "espadachin",
    position: harness.match.map.path.totalLength / 2,
    actionId: "invalid-deploy"
  }));

  assert.equal(harness.match.players[0].coinsSpent, 0);
  assert.equal(harness.match.troops.length, 0);
  assert.equal(harness.messages.get("uid-one").find((message) => message.type === "action_rejected").reason, "INVALID_POSITION");
});

test("tower build, sale, and the authoritative state stay synchronized", async () => {
  const harness = makeGameplayHarness();
  const ownSlotId = harness.match.map.slots.find((slot) => slot.owner === "player1").id;
  const socket = harness.sockets[0];

  await harness.probe.webSocketMessage(socket, JSON.stringify({
    type: "build_tower", card: "arrow_tower", slotId: ownSlotId, actionId: "build-1"
  }));
  assert.equal(harness.match.towers.length, 1);
  assert.ok(harness.match.towers[0].nextShotAtElapsedMs > 0);
  assert.equal(harness.match.players[0].coinsAtLastUpdate, 50);

  await harness.probe.webSocketMessage(socket, JSON.stringify({
    type: "sell_tower", slotId: ownSlotId, actionId: "sell-1"
  }));
  assert.equal(harness.match.towers.length, 0);
  assert.equal(harness.match.players[0].coinsAtLastUpdate, 100);
  assert.ok(harness.messages.get("uid-two").some((message) => message.type === "tower_sold" && message.refund === 50));
  const latestState = harness.messages.get("uid-one").filter((message) => message.type === "match.state").at(-1);
  assert.deepEqual(latestState.state.towers, []);
});

test("surrender ends the match once and publishes the same winner to both players", async () => {
  const harness = makeGameplayHarness();
  await harness.probe.webSocketMessage(harness.sockets[0], JSON.stringify({ type: "match.surrender" }));

  assert.equal(harness.match.phase, "FINISHED");
  for (const uid of ["uid-one", "uid-two"]) {
    const result = harness.messages.get(uid).find((message) => message.type === "match.result");
    assert.equal(result.winnerPlayerNumber, 2);
    assert.equal(result.reason, "SURRENDER");
  }
});

test("a simulation alarm advances both clients from the server clock", async () => {
  const harness = makeGameplayHarness({ startedAgoMs: 2_000 });
  await harness.probe.alarm();
  for (const uid of ["uid-one", "uid-two"]) {
    const state = harness.messages.get(uid).find((message) => message.type === "match.state");
    assert.ok(state.state.elapsedMatchMs >= 2_000);
    assert.equal(state.state.sequence, 1);
  }
  assert.equal(harness.match.players[0].coinsAtLastUpdate, 150);
});
