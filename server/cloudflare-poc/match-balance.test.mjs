import assert from "node:assert/strict";
import test from "node:test";
import {
  accruePassiveCoins,
  cloneMatchBalance,
  incomeEarnedThroughSecond,
  incomeRateAtElapsedMs,
  MATCH_BALANCE
} from "./src/match-balance.mjs";

test("the shared match balance contains the confirmed versioned values", () => {
  assert.equal(MATCH_BALANCE.version, "fort-rush-2026-11-24.1");
  assert.equal(MATCH_BALANCE.economy.startingCoins, 150);
  assert.equal(MATCH_BALANCE.economy.maximumCoins, 1_000);
  assert.deepEqual(MATCH_BALANCE.economy.incomeBands.map((band) => band.coinsPerSecond), [5, 10]);
  assert.equal(MATCH_BALANCE.cards.troops.espadachin.cost, 45);
  assert.equal(MATCH_BALANCE.cards.troops.escudero.cost, 55);
  assert.equal(MATCH_BALANCE.cards.towers.arrow_tower.cost, 100);
  assert.equal(MATCH_BALANCE.cards.towers.cannon_tower.cost, 130);
  assert.equal(MATCH_BALANCE.structures.wallHealth, 500);
  assert.equal(MATCH_BALANCE.structures.castleHealth, 400);
});

test("income changes at 180 seconds and is capped without discarding the current balance", () => {
  assert.equal(incomeRateAtElapsedMs(179_999), 5);
  assert.equal(incomeRateAtElapsedMs(180_000), 10);
  assert.equal(incomeEarnedThroughSecond(180), 900);
  assert.equal(incomeEarnedThroughSecond(240), 1_500);
  assert.equal(accruePassiveCoins(150, 0, 170), 1_000);
  assert.equal(accruePassiveCoins(900, 180, 181), 910);
  assert.equal(accruePassiveCoins(1_050, 180, 181), 1_050);
});

test("a match gets a mutable snapshot without changing the shared balance source", () => {
  const matchBalance = cloneMatchBalance();
  matchBalance.cards.towers.arrow_tower.cost = 999;
  assert.equal(MATCH_BALANCE.cards.towers.arrow_tower.cost, 100);
});
