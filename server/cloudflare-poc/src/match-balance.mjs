const freezeRecursively = (value) => {
  if (value && typeof value === "object" && !Object.isFrozen(value)) {
    Object.values(value).forEach(freezeRecursively);
    Object.freeze(value);
  }
  return value;
};

// Bump this version whenever a match-affecting value changes. Matches keep a
// copy so later deployments cannot silently alter an already-started game.
export const MATCH_BALANCE = freezeRecursively({
  version: "fort-rush-2026-11-23.1",
  match: {
    durationMs: 240_000,
    maxLivingTroopsPerPlayer: 20
  },
  economy: {
    startingCoins: 150,
    maximumCoins: 1_000,
    incomeBands: [
      { fromElapsedMs: 0, untilElapsedMs: 180_000, coinsPerSecond: 5 },
      { fromElapsedMs: 180_000, untilElapsedMs: 240_000, coinsPerSecond: 10 }
    ],
    wallBreakReward: { attacker: 50, defender: 30 }
  },
  cards: {
    troops: {
      espadachin: {
        cost: 45,
        unitsPerDeployment: 3,
        health: 60,
        attackDamage: 10,
        attackIntervalMs: 1_000,
        pathTravelMs: 25_000,
        cooldownMs: 3_000
      },
      escudero: {
        cost: 55,
        unitsPerDeployment: 2,
        health: 110,
        attackDamage: 6,
        attackIntervalMs: 1_000,
        pathTravelMs: 30_000,
        cooldownMs: 3_000,
        damageReduction: {
          againstArrow: { chance: 0.10, multiplier: 0.70 },
          againstSword: { chance: 0.03, multiplier: 0.70 },
          againstCannon: { chance: 0, multiplier: 1 }
        }
      }
    },
    towers: {
      arrow_tower: {
        cost: 100,
        damage: 8,
        attackIntervalMs: 700,
        constructionCooldownMs: 0,
        rangeAsBoardWidth: 0.25
      },
      cannon_tower: {
        cost: 130,
        damage: 20,
        attackIntervalMs: 2_000,
        constructionCooldownMs: 0,
        rangeAsBoardWidth: 0.20,
        explosionRadiusAsOwnRange: 0.10
      }
    }
  },
  structures: {
    wallHealth: 500,
    castleHealth: 400
  }
});

export function cloneMatchBalance() {
  return structuredClone(MATCH_BALANCE);
}

export function incomeEarnedThroughSecond(elapsedSecond, economy = MATCH_BALANCE.economy) {
  const second = Math.max(0, Math.floor(elapsedSecond));
  return economy.incomeBands.reduce((total, band) => {
    const fromSecond = Math.floor(band.fromElapsedMs / 1_000);
    const untilSecond = Math.floor(band.untilElapsedMs / 1_000);
    return total + Math.max(0, Math.min(second, untilSecond) - fromSecond) * band.coinsPerSecond;
  }, 0);
}

export function incomeRateAtElapsedMs(elapsedMs, economy = MATCH_BALANCE.economy) {
  const elapsed = Math.max(0, elapsedMs);
  return economy.incomeBands.find((band) =>
    elapsed >= band.fromElapsedMs && elapsed < band.untilElapsedMs
  )?.coinsPerSecond ?? 0;
}

export function accruePassiveCoins(currentCoins, lastAppliedSecond, elapsedSecond, economy = MATCH_BALANCE.economy) {
  const from = Math.max(0, Math.floor(lastAppliedSecond));
  const through = Math.max(from, Math.floor(elapsedSecond));
  const newIncome = incomeEarnedThroughSecond(through, economy) - incomeEarnedThroughSecond(from, economy);
  const balance = Math.max(0, currentCoins);
  if (balance >= economy.maximumCoins) return balance;
  return Math.min(economy.maximumCoins, balance + newIncome);
}
