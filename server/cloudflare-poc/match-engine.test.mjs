import assert from "node:assert/strict";
import test from "node:test";
import { createSharedMap } from "./src/shared-map.mjs";
import { cloneMatchBalance } from "./src/match-balance.mjs";
import { advanceCombat, deployTroops, initializeCombatState, pointAtDistance } from "./src/match-engine.mjs";

function createMatch(seed = "combat-test-seed", start = 1_000_000) {
  const match = {
    matchId: "combat-test-match",
    balance: cloneMatchBalance(),
    map: createSharedMap(seed),
    players: [
      { uid: "uid-one", playerNumber: 1, coinsAtLastUpdate: 150, coinsSpent: 0, incomeAppliedThroughSecond: 0, processedActionIds: [] },
      { uid: "uid-two", playerNumber: 2, coinsAtLastUpdate: 150, coinsSpent: 0, incomeAppliedThroughSecond: 0, processedActionIds: [] }
    ]
  };
  initializeCombatState(match, start);
  return match;
}

test("troop deployment spends once, creates a complete squad, and rejects the central band", () => {
  const match = createMatch();
  const start = match.startedAtServerMs;
  const first = deployTroops(match, 1, "espadachin", match.map.path.totalLength * 0.2, "deploy-1", start);

  assert.equal(first.ok, true);
  assert.equal(first.troopIds.length, 3);
  assert.equal(match.players[0].coinsAtLastUpdate, 105);
  assert.equal(match.players[0].coinsSpent, 45);
  assert.equal(deployTroops(match, 1, "espadachin", 500, "deploy-1", start).duplicate, true);
  assert.equal(match.troops.length, 3);
  assert.equal(match.players[0].coinsSpent, 45);

  const rejected = deployTroops(match, 1, "espadachin", match.map.path.totalLength / 2, "deploy-2", start);
  assert.deepEqual(rejected, { ok: false, reason: "INVALID_POSITION" });
  assert.equal(match.players[0].coinsAtLastUpdate, 105);
});

test("the server enforces the per-player living troop cap without charging", () => {
  const match = createMatch();
  match.players[0].coinsAtLastUpdate = 1_000;
  const now = match.startedAtServerMs;
  for (let index = 0; index < 6; index += 1) {
    assert.equal(deployTroops(match, 1, "espadachin", 200, `deploy-${index}`, now + index * 3_000).ok, true);
  }
  const balanceBefore = match.players[0].coinsAtLastUpdate;
  const rejected = deployTroops(match, 1, "espadachin", 300, "deploy-over-limit", now + 30_000);
  assert.equal(rejected.reason, "TROOP_LIMIT_REACHED");
  assert.equal(match.players[0].coinsAtLastUpdate, balanceBefore);
  assert.equal(match.troops.filter((troop) => troop.ownerPlayerNumber === 1).length, 18);
});

test("a group crosses the full path in the configured travel time and hits the wall at arrival", () => {
  const match = createMatch();
  const start = match.startedAtServerMs;
  assert.equal(deployTroops(match, 1, "espadachin", 0, "deploy-at-start", start).ok, true);
  advanceCombat(match, start + 25_000);
  assert.ok(match.troops.every((troop) => troop.position === match.map.path.totalLength));
  assert.equal(match.players[1].wallHealth, 470);
  advanceCombat(match, start + 26_000);
  assert.equal(match.players[1].wallHealth, 440);
});

test("troop attacks apply simultaneously and can kill both units", () => {
  const match = createMatch();
  const distance = match.map.path.totalLength / 2;
  match.troops = [
    { id: "p1", ownerPlayerNumber: 1, card: "espadachin", position: distance, health: 10, maxHealth: 60, attackDamage: 10, attackIntervalMs: 1_000, pathTravelMs: 25_000, nextAttackAtElapsedMs: 100 },
    { id: "p2", ownerPlayerNumber: 2, card: "espadachin", position: distance + 20, health: 10, maxHealth: 60, attackDamage: 10, attackIntervalMs: 1_000, pathTravelMs: 25_000, nextAttackAtElapsedMs: 100 }
  ];
  advanceCombat(match, match.startedAtServerMs + 100);
  assert.equal(match.troops.length, 0);
});

test("a wall breaks once, grants each player the confirmed reward, then the troop damages the castle", () => {
  const match = createMatch();
  const attacker = match.players[0];
  const defender = match.players[1];
  const end = match.map.path.totalLength;
  defender.wallHealth = 10;
  match.troops = [
    { id: "breaker", ownerPlayerNumber: 1, card: "espadachin", position: end, health: 60, maxHealth: 60, attackDamage: 10, attackIntervalMs: 1_000, pathTravelMs: 25_000, nextAttackAtElapsedMs: 100 }
  ];
  advanceCombat(match, match.startedAtServerMs + 100);

  assert.equal(defender.wallHealth, 0);
  assert.equal(attacker.coinsAtLastUpdate, 200);
  assert.equal(defender.coinsAtLastUpdate, 180);
  assert.equal(defender.wallBreakRewardGranted, true);
  assert.equal(defender.castleHealth, 400);

  advanceCombat(match, match.startedAtServerMs + 1_100);
  assert.equal(defender.castleHealth, 390);
  assert.equal(match.troops.length, 0);
});

test("four minutes enter sudden death and equal walls break together into a draw", () => {
  const match = createMatch();
  advanceCombat(match, match.startedAtServerMs + match.balance.match.durationMs + 3_000);
  assert.equal(match.phase, "FINISHED");
  assert.equal(match.game.result.winnerPlayerNumber, null);
  assert.equal(match.game.result.reason, "SUDDEN_DEATH_DRAW");
  assert.equal(match.players[0].wallHealth, 0);
  assert.equal(match.players[1].wallHealth, 0);
});

test("sudden death gives the win to the remaining wall, or compares castles if neither remains", () => {
  const wallWinner = createMatch("one-wall");
  wallWinner.players[0].wallHealth = 0;
  advanceCombat(wallWinner, wallWinner.startedAtServerMs + wallWinner.balance.match.durationMs);
  assert.equal(wallWinner.game.result.winnerPlayerNumber, 2);
  assert.equal(wallWinner.game.result.reason, "SUDDEN_DEATH_WALL");

  const castleWinner = createMatch("no-walls");
  castleWinner.players[0].wallHealth = 0;
  castleWinner.players[1].wallHealth = 0;
  castleWinner.players[0].castleHealth = 200;
  castleWinner.players[1].castleHealth = 250;
  advanceCombat(castleWinner, castleWinner.startedAtServerMs + castleWinner.balance.match.durationMs);
  assert.equal(castleWinner.game.result.winnerPlayerNumber, 2);
  assert.equal(castleWinner.game.result.reason, "SUDDEN_DEATH_CASTLE_HEALTH");
});

test("arrow towers choose an enemy in their actual range and apply damage after projectile flight", () => {
  const match = createMatch();
  const slot = match.map.slots.find((candidate) => candidate.owner === "player1");
  const coverage = slot.coverage[0];
  const station = (coverage[0] + coverage[1]) / 2;
  match.towers = [{ slotId: slot.id, ownerPlayerNumber: 1, card: "arrow_tower", cost: 100, nextShotAtElapsedMs: 100 }];
  match.troops = [
    { id: "target", ownerPlayerNumber: 2, card: "espadachin", position: station, health: 60, maxHealth: 60, attackDamage: 10, attackIntervalMs: 1_000, pathTravelMs: 25_000, nextAttackAtElapsedMs: 60_000 }
  ];

  advanceCombat(match, match.startedAtServerMs + 300);
  assert.equal(match.troops[0].health, 52);
  assert.equal(match.projectiles.length, 0);
});

test("a cannon projectile damages every enemy in its blast area once", () => {
  const match = createMatch();
  const slot = match.map.slots.find((candidate) => candidate.owner === "player1");
  const coverage = slot.coverage[0];
  const station = (coverage[0] + coverage[1]) / 2;
  match.towers = [{ slotId: slot.id, ownerPlayerNumber: 1, card: "cannon_tower", cost: 130, nextShotAtElapsedMs: 100 }];
  match.troops = ["target-a", "target-b"].map((id) => ({
    id, ownerPlayerNumber: 2, card: "espadachin", position: station, health: 60, maxHealth: 60,
    attackDamage: 10, attackIntervalMs: 1_000, pathTravelMs: 25_000, nextAttackAtElapsedMs: 60_000
  }));

  advanceCombat(match, match.startedAtServerMs + 500);
  assert.deepEqual(match.troops.map((troop) => troop.health), [40, 40]);
  assert.equal(match.projectiles.length, 0);
});

test("escudero arrow resistance keeps decimal health and the simulation seed is reproducible", () => {
  const first = createMatch("shielded-seed");
  const second = createMatch("shielded-seed");
  for (const match of [first, second]) {
    match.balance.cards.troops.escudero.damageReduction.againstArrow.chance = 1;
    const slot = match.map.slots.find((candidate) => candidate.owner === "player1");
    const station = (slot.coverage[0][0] + slot.coverage[0][1]) / 2;
    const point = pointAtDistance(match.map, station);
    assert.ok(Number.isFinite(point.x));
    match.towers = [{ slotId: slot.id, ownerPlayerNumber: 1, card: "arrow_tower", cost: 100, nextShotAtElapsedMs: 100 }];
    match.troops = [
      { id: "shield", ownerPlayerNumber: 2, card: "escudero", position: station, health: 110, maxHealth: 110, attackDamage: 6, attackIntervalMs: 1_000, pathTravelMs: 30_000, nextAttackAtElapsedMs: 60_000 }
    ];
    advanceCombat(match, match.startedAtServerMs + 300);
  }
  assert.equal(first.troops[0].health, 104.4);
  assert.deepEqual(first.troops, second.troops);
  assert.equal(first.game.randomState, second.game.randomState);
});
