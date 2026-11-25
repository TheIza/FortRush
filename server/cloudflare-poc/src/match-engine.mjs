import { accruePassiveCoins } from "./match-balance.mjs";

const DEFAULT_STEP_MS = 100;
const DEFAULT_CONTACT_DISTANCE = 40;
const DEFAULT_FORMATION_SPACING = 24;
const DEFAULT_SUDDEN_DEATH_MS = 3_000;

function hashSeed(value) {
  let hash = 0x811c9dc5;
  for (const character of String(value ?? "")) {
    hash ^= character.charCodeAt(0);
    hash = Math.imul(hash, 0x01000193);
  }
  return hash >>> 0;
}

function nextRandom(match) {
  const game = match.game;
  game.randomState = (game.randomState + 0x6d2b79f5) >>> 0;
  let value = game.randomState;
  value = Math.imul(value ^ (value >>> 15), value | 1);
  value ^= value + Math.imul(value ^ (value >>> 7), value | 61);
  return ((value ^ (value >>> 14)) >>> 0) / 4_294_967_296;
}

function playerByNumber(match, number) {
  return match.players.find((player) => player.playerNumber === number);
}

function settleCoins(match, player, nowMs) {
  const balance = match.balance;
  const elapsedSecond = Math.floor(Math.max(0, nowMs - match.startedAtServerMs) / 1_000);
  player.coinsAtLastUpdate = accruePassiveCoins(
    player.coinsAtLastUpdate ?? balance.economy.startingCoins,
    player.incomeAppliedThroughSecond ?? 0,
    elapsedSecond,
    balance.economy
  );
  player.incomeAppliedThroughSecond = elapsedSecond;
  return player.coinsAtLastUpdate;
}

export function initializeCombatState(match, startedAtServerMs) {
  const balance = match.balance;
  match.phase = "IN_MATCH";
  match.startedAtServerMs = startedAtServerMs;
  match.troops = [];
  match.projectiles = [];
  match.towers = [];
  match.game = {
    lastAdvancedElapsedMs: 0,
    nextEntityId: 1,
    nextSnapshotSequence: 0,
    randomState: hashSeed(match.map?.seed ?? match.matchId),
    suddenDeathStartedElapsedMs: null,
    result: null
  };
  for (const player of match.players) {
    player.wallHealth = balance.structures.wallHealth;
    player.castleHealth = balance.structures.castleHealth;
    player.wallBreakRewardGranted = false;
    player.troopCooldowns = {};
    player.coinsAtLastUpdate = balance.economy.startingCoins;
    player.incomeAppliedThroughSecond = 0;
    player.coinsSpent = 0;
    player.lastTowerBuiltAtMs = null;
    player.processedActionIds = [];
  }
  return match;
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}

export function pointAtDistance(map, distanceAlongPath) {
  const points = map?.path?.points ?? [];
  const distances = map?.path?.cumLen ?? [];
  const total = map?.path?.totalLength ?? 0;
  if (points.length === 0) return { x: 0, y: 0 };
  if (points.length === 1 || distances.length !== points.length) return points[0];
  const station = clamp(distanceAlongPath, 0, total);
  let right = distances.findIndex((value) => value >= station);
  if (right < 1) right = 1;
  if (right < 0) right = points.length - 1;
  const left = right - 1;
  const span = Math.max(0.0001, distances[right] - distances[left]);
  const fraction = clamp((station - distances[left]) / span, 0, 1);
  return {
    x: points[left].x + (points[right].x - points[left].x) * fraction,
    y: points[left].y + (points[right].y - points[left].y) * fraction
  };
}

function actionSeen(player, actionId) {
  return (player.processedActionIds ?? []).includes(actionId);
}

function rememberAction(player, actionId) {
  player.processedActionIds = [...(player.processedActionIds ?? []), actionId].slice(-64);
}

export function deployTroops(match, playerNumber, cardId, position, actionId, nowMs) {
  const player = playerByNumber(match, playerNumber);
  if (!player || match.phase !== "IN_MATCH") return { ok: false, reason: "INVALID_STATE" };
  if (typeof actionId !== "string" || actionId.length < 1 || actionId.length > 80) return { ok: false, reason: "INVALID_ACTION_ID" };
  if (actionSeen(player, actionId)) return { ok: true, duplicate: true, troopIds: [] };
  const definition = Object.hasOwn(match.balance.cards.troops, cardId) ? match.balance.cards.troops[cardId] : null;
  if (!definition) return { ok: false, reason: "NOT_A_TROOP_CARD" };

  const currentLiving = match.troops.filter((troop) => troop.ownerPlayerNumber === playerNumber && troop.health > 0).length;
  if (currentLiving + definition.unitsPerDeployment > match.balance.match.maxLivingTroopsPerPlayer) {
    return { ok: false, reason: "TROOP_LIMIT_REACHED" };
  }

  const totalLength = match.map.path.totalLength;
  const safeHalf = totalLength * 0.475;
  if (!Number.isFinite(position) || position < 0 || position > totalLength ||
      (playerNumber === 1 ? position > safeHalf : position < totalLength - safeHalf)) {
    return { ok: false, reason: "INVALID_POSITION" };
  }

  const cooldownMs = Math.max(0, Math.floor(definition.cooldownMs ?? 0));
  const lastDeployedAt = player.troopCooldowns?.[cardId];
  if (cooldownMs > 0 && Number.isFinite(lastDeployedAt) && nowMs - lastDeployedAt < cooldownMs) {
    return { ok: false, reason: "ON_COOLDOWN" };
  }
  const availableCoins = settleCoins(match, player, nowMs);
  if (availableCoins < definition.cost) return { ok: false, reason: "NOT_ENOUGH_COINS" };

  player.coinsAtLastUpdate -= definition.cost;
  player.coinsSpent += definition.cost;
  player.troopCooldowns = { ...(player.troopCooldowns ?? {}), [cardId]: nowMs };
  const elapsedMs = Math.max(0, nowMs - match.startedAtServerMs);
  const step = match.balance.combat?.formationSpacing ?? DEFAULT_FORMATION_SPACING;
  const offsets = Array.from({ length: definition.unitsPerDeployment }, (_, index) =>
    (index - (definition.unitsPerDeployment - 1) / 2) * step
  );
  const minPosition = playerNumber === 1 ? 0 : totalLength - safeHalf;
  const maxPosition = playerNumber === 1 ? safeHalf : totalLength;
  const troopIds = [];
  for (const offset of offsets) {
    const id = `u-${playerNumber}-${match.game.nextEntityId++}`;
    const troop = {
      id,
      ownerPlayerNumber: playerNumber,
      card: cardId,
      position: clamp(position + offset, minPosition, maxPosition),
      health: definition.health,
      maxHealth: definition.health,
      attackDamage: definition.attackDamage,
      attackIntervalMs: definition.attackIntervalMs,
      pathTravelMs: definition.pathTravelMs,
      nextAttackAtElapsedMs: elapsedMs + definition.attackIntervalMs
    };
    match.troops.push(troop);
    troopIds.push(id);
  }
  rememberAction(player, actionId);
  return { ok: true, duplicate: false, troopIds, cost: definition.cost };
}

function closestEnemyAhead(troop, troops) {
  const direction = troop.ownerPlayerNumber === 1 ? 1 : -1;
  return troops
    .filter((other) => other.health > 0 && other.ownerPlayerNumber !== troop.ownerPlayerNumber &&
      direction * (other.position - troop.position) >= -0.001)
    .sort((a, b) => Math.abs(a.position - troop.position) - Math.abs(b.position - troop.position) || a.id.localeCompare(b.id))[0] ?? null;
}

function isEscudero(troop) { return troop.card === "escudero"; }

function defendedDamage(match, target, damage, source) {
  if (!isEscudero(target)) return damage;
  const defense = match.balance.cards.troops.escudero.damageReduction;
  const rule = source === "arrow" ? defense.againstArrow : source === "sword" ? defense.againstSword : defense.againstCannon;
  return nextRandom(match) < rule.chance ? damage * rule.multiplier : damage;
}

function distanceBetween(a, b) { return Math.hypot(a.x - b.x, a.y - b.y); }

function slotForTower(match, tower) {
  return match.map.slots.find((slot) => slot.id === tower.slotId) ?? null;
}

function towerRange(match, tower) {
  const definition = match.balance.cards.towers[tower.card];
  return match.map.width * definition.rangeAsBoardWidth;
}

function isTroopInTowerRange(match, tower, troop) {
  const slot = slotForTower(match, tower);
  if (!slot) return false;
  return distanceBetween(slot, pointAtDistance(match.map, troop.position)) <= towerRange(match, tower);
}

function chooseTowerTarget(match, tower) {
  const enemies = match.troops.filter((troop) => troop.health > 0 &&
    troop.ownerPlayerNumber !== tower.ownerPlayerNumber && isTroopInTowerRange(match, tower, troop));
  enemies.sort((a, b) => {
    const advancement = tower.ownerPlayerNumber === 1 ? a.position - b.position : b.position - a.position;
    return advancement || a.health - b.health || a.id.localeCompare(b.id);
  });
  return enemies[0] ?? null;
}

function shootTowers(match, elapsedMs) {
  for (const tower of match.towers) {
    const definition = match.balance.cards.towers[tower.card];
    if (elapsedMs < tower.nextShotAtElapsedMs) continue;
    const target = chooseTowerTarget(match, tower);
    if (!target) continue;
    const slot = slotForTower(match, tower);
    if (!slot) continue;
    const targetPoint = pointAtDistance(match.map, target.position);
    const travelMs = Math.max(100, distanceBetween(slot, targetPoint) / definition.projectileSpeed * 1_000);
    const projectile = {
      id: `p-${match.game.nextEntityId++}`,
      ownerPlayerNumber: tower.ownerPlayerNumber,
      card: tower.card,
      targetTroopId: tower.card === "arrow_tower" ? target.id : null,
      targetPosition: target.position,
      origin: { x: slot.x, y: slot.y },
      impactAtElapsedMs: elapsedMs + travelMs
    };
    match.projectiles.push(projectile);
    tower.nextShotAtElapsedMs = elapsedMs + definition.attackIntervalMs;
  }
}

function grantWallReward(match, attackerNumber, nowMs) {
  const attacker = playerByNumber(match, attackerNumber);
  const defender = playerByNumber(match, attackerNumber === 1 ? 2 : 1);
  if (!attacker || !defender || defender.wallBreakRewardGranted) return;
  defender.wallBreakRewardGranted = true;
  settleCoins(match, attacker, nowMs);
  settleCoins(match, defender, nowMs);
  attacker.coinsAtLastUpdate += match.balance.economy.wallBreakReward.attacker;
  defender.coinsAtLastUpdate += match.balance.economy.wallBreakReward.defender;
}

function finishMatch(match, winnerPlayerNumber, reason, elapsedMs) {
  if (match.phase === "FINISHED") return;
  match.phase = "FINISHED";
  match.game.result = {
    winnerPlayerNumber,
    reason,
    elapsedMatchMs: elapsedMs,
    player1WallHealth: playerByNumber(match, 1)?.wallHealth ?? 0,
    player2WallHealth: playerByNumber(match, 2)?.wallHealth ?? 0,
    player1CastleHealth: playerByNumber(match, 1)?.castleHealth ?? 0,
    player2CastleHealth: playerByNumber(match, 2)?.castleHealth ?? 0
  };
  match.troops = [];
  match.projectiles = [];
}

function simulateNormalStep(match, elapsedMs, deltaMs) {
  const totalLength = match.map.path.totalLength;
  const contact = match.balance.combat?.contactDistance ?? DEFAULT_CONTACT_DISTANCE;
  const troopsAtStart = match.troops.filter((troop) => troop.health > 0);
  for (const troop of troopsAtStart) {
    const enemy = closestEnemyAhead(troop, troopsAtStart);
    if (enemy && Math.abs(enemy.position - troop.position) <= contact) continue;
    const direction = troop.ownerPlayerNumber === 1 ? 1 : -1;
    const speed = totalLength / troop.pathTravelMs;
    troop.position = clamp(troop.position + direction * speed * deltaMs, 0, totalLength);
  }

  const livingAtStart = match.troops.filter((troop) => troop.health > 0);
  const troopDamage = [];
  const wallDamage = new Map();
  const castleDamage = new Map();
  const castleEntryTroops = new Set();

  for (const troop of livingAtStart) {
    if (elapsedMs < troop.nextAttackAtElapsedMs) continue;
    const enemy = closestEnemyAhead(troop, livingAtStart);
    if (enemy && Math.abs(enemy.position - troop.position) <= contact) {
      troop.nextAttackAtElapsedMs = elapsedMs + troop.attackIntervalMs;
      troopDamage.push({ targetId: enemy.id, damage: troop.attackDamage, source: "sword" });
      continue;
    }
    const reachedGate = troop.ownerPlayerNumber === 1 ? troop.position >= totalLength - 0.001 : troop.position <= 0.001;
    if (!reachedGate) continue;
    troop.nextAttackAtElapsedMs = elapsedMs + troop.attackIntervalMs;
    const defender = playerByNumber(match, troop.ownerPlayerNumber === 1 ? 2 : 1);
    if (defender.wallHealth > 0) wallDamage.set(defender.playerNumber, (wallDamage.get(defender.playerNumber) ?? 0) + troop.attackDamage);
    else {
      castleDamage.set(defender.playerNumber, (castleDamage.get(defender.playerNumber) ?? 0) + troop.attackDamage);
      castleEntryTroops.add(troop.id);
    }
  }

  shootTowers(match, elapsedMs);
  const stillLivingAtImpact = new Set(livingAtStart.map((troop) => troop.id));
  const impactProjectiles = match.projectiles.filter((projectile) => projectile.impactAtElapsedMs <= elapsedMs);
  const projectileDamage = [];
  for (const projectile of impactProjectiles) {
    const definition = match.balance.cards.towers[projectile.card];
    if (projectile.card === "arrow_tower") {
      if (stillLivingAtImpact.has(projectile.targetTroopId)) {
        projectileDamage.push({ targetId: projectile.targetTroopId, damage: definition.damage, source: "arrow" });
      }
      continue;
    }
    const blastPoint = pointAtDistance(match.map, projectile.targetPosition);
    // The cannon's locked aim position is stored as a path station. Its blast does not track a dead target.
    const actualRadius = match.map.width * definition.rangeAsBoardWidth * definition.explosionRadiusAsOwnRange;
    for (const enemy of livingAtStart) {
      if (enemy.ownerPlayerNumber === projectile.ownerPlayerNumber) continue;
      if (distanceBetween(blastPoint, pointAtDistance(match.map, enemy.position)) <= actualRadius) {
        projectileDamage.push({ targetId: enemy.id, damage: definition.damage, source: "cannon" });
      }
    }
  }
  if (impactProjectiles.length) {
    const impacted = new Set(impactProjectiles.map((projectile) => projectile.id));
    match.projectiles = match.projectiles.filter((projectile) => !impacted.has(projectile.id));
  }

  const allTroopDamage = [...troopDamage, ...projectileDamage];
  const totals = new Map();
  for (const attack of allTroopDamage) {
    const target = livingAtStart.find((troop) => troop.id === attack.targetId);
    if (!target) continue;
    const damage = defendedDamage(match, target, attack.damage, attack.source);
    totals.set(target.id, (totals.get(target.id) ?? 0) + damage);
  }
  for (const troop of livingAtStart) {
    troop.health = Math.max(0, troop.health - (totals.get(troop.id) ?? 0));
  }

  const damagedWalls = [];
  for (const [playerNumber, damage] of wallDamage) {
    const defender = playerByNumber(match, playerNumber);
    const before = defender.wallHealth;
    defender.wallHealth = Math.max(0, before - damage);
    if (before > 0 && defender.wallHealth === 0) damagedWalls.push(playerNumber);
  }
  for (const defenderNumber of damagedWalls) {
    const attackerNumber = defenderNumber === 1 ? 2 : 1;
    grantWallReward(match, attackerNumber, match.startedAtServerMs + elapsedMs);
  }
  for (const [playerNumber, damage] of castleDamage) {
    const defender = playerByNumber(match, playerNumber);
    defender.castleHealth = Math.max(0, defender.castleHealth - damage);
  }
  if (castleEntryTroops.size) match.troops = match.troops.filter((troop) => !castleEntryTroops.has(troop.id));
  else match.troops = match.troops.filter((troop) => troop.health > 0);

  const destroyedCastles = match.players.filter((player) => player.castleHealth <= 0);
  if (destroyedCastles.length === 2) finishMatch(match, null, "SIMULTANEOUS_CASTLE_DESTRUCTION", elapsedMs);
  else if (destroyedCastles.length === 1) finishMatch(match, destroyedCastles[0].playerNumber === 1 ? 2 : 1, "CASTLE_DESTROYED", elapsedMs);
}

function compareFinalWalls(match, elapsedMs) {
  const first = playerByNumber(match, 1);
  const second = playerByNumber(match, 2);
  if ((first.wallHealth > 0) !== (second.wallHealth > 0)) {
    finishMatch(match, first.wallHealth > 0 ? 1 : 2, "SUDDEN_DEATH_WALL", elapsedMs);
  } else if (first.wallHealth <= 0 && second.wallHealth <= 0) {
    const winner = first.castleHealth === second.castleHealth ? null : first.castleHealth > second.castleHealth ? 1 : 2;
    finishMatch(match, winner, winner == null ? "SUDDEN_DEATH_DRAW" : "SUDDEN_DEATH_CASTLE_HEALTH", elapsedMs);
  }
}

function beginSuddenDeath(match, elapsedMs) {
  match.phase = "SUDDEN_DEATH";
  match.game.suddenDeathStartedElapsedMs = elapsedMs;
  match.troops = [];
  match.towers = [];
  match.projectiles = [];
  compareFinalWalls(match, elapsedMs);
}

function simulateSuddenDeathStep(match, elapsedMs, deltaMs) {
  const first = playerByNumber(match, 1);
  const second = playerByNumber(match, 2);
  const duration = match.balance.match.suddenDeathDurationMs ?? DEFAULT_SUDDEN_DEATH_MS;
  const suddenElapsed = elapsedMs - match.game.suddenDeathStartedElapsedMs;
  if (suddenElapsed >= duration) {
    first.wallHealth = 0;
    second.wallHealth = 0;
  } else {
    const damage = match.balance.structures.wallHealth * deltaMs / duration;
    first.wallHealth = Math.max(0, first.wallHealth - damage);
    second.wallHealth = Math.max(0, second.wallHealth - damage);
  }
  if (first.wallHealth <= 0 || second.wallHealth <= 0) compareFinalWalls(match, elapsedMs);
}

export function advanceCombat(match, nowMs) {
  if (!match.game || !["IN_MATCH", "SUDDEN_DEATH"].includes(match.phase)) return match;
  const balance = match.balance;
  const duration = balance.match.durationMs;
  const suddenDeathDuration = balance.match.suddenDeathDurationMs ?? DEFAULT_SUDDEN_DEATH_MS;
  const target = Math.min(Math.max(0, nowMs - match.startedAtServerMs), duration + suddenDeathDuration);
  const step = Math.max(20, Math.floor(balance.combat?.simulationStepMs ?? DEFAULT_STEP_MS));
  while (match.game.lastAdvancedElapsedMs < target && match.phase !== "FINISHED") {
    const nextElapsed = Math.min(target, match.game.lastAdvancedElapsedMs + step);
    const delta = nextElapsed - match.game.lastAdvancedElapsedMs;
    if (match.phase === "IN_MATCH") {
      simulateNormalStep(match, nextElapsed, delta);
      match.game.lastAdvancedElapsedMs = nextElapsed;
      if (match.phase !== "FINISHED" && nextElapsed >= duration) beginSuddenDeath(match, nextElapsed);
    } else {
      simulateSuddenDeathStep(match, nextElapsed, delta);
      match.game.lastAdvancedElapsedMs = nextElapsed;
    }
  }
  return match;
}

export function sellTower(match, playerNumber, slotId, actionId, nowMs) {
  const player = playerByNumber(match, playerNumber);
  if (!player || match.phase !== "IN_MATCH") return { ok: false, reason: "INVALID_STATE" };
  if (typeof actionId !== "string" || actionId.length < 1 || actionId.length > 80) return { ok: false, reason: "INVALID_ACTION_ID" };
  if (actionSeen(player, actionId)) return { ok: true, duplicate: true };
  const index = match.towers.findIndex((tower) => tower.slotId === slotId && tower.ownerPlayerNumber === playerNumber);
  if (index < 0) return { ok: false, reason: "NOT_YOUR_TOWER" };
  const [tower] = match.towers.splice(index, 1);
  const refund = Math.floor(tower.cost * 0.5);
  settleCoins(match, player, nowMs);
  player.coinsAtLastUpdate += refund;
  player.coinsSpent = Math.max(0, player.coinsSpent - refund);
  rememberAction(player, actionId);
  return { ok: true, duplicate: false, refund, slotId };
}

export function surrenderMatch(match, playerNumber, nowMs) {
  if (match.phase !== "IN_MATCH") return { ok: false, reason: "INVALID_STATE" };
  const player = playerByNumber(match, playerNumber);
  if (!player) return { ok: false, reason: "INVALID_STATE" };
  const elapsed = Math.max(0, nowMs - match.startedAtServerMs);
  finishMatch(match, playerNumber === 1 ? 2 : 1, "SURRENDER", elapsed);
  return { ok: true };
}

export function createGameSnapshot(match, playerNumber, nowMs) {
  const elapsed = Math.max(0, Math.min(nowMs - match.startedAtServerMs, match.balance.match.durationMs + (match.balance.match.suddenDeathDurationMs ?? DEFAULT_SUDDEN_DEATH_MS)));
  const worldTroops = (match.troops ?? []).map((troop) => {
    const point = pointAtDistance(match.map, troop.position);
    return { id: troop.id, ownerPlayerNumber: troop.ownerPlayerNumber, card: troop.card, x: point.x / match.map.width, y: point.y / match.map.height, pathDistance: troop.position, health: troop.health, maxHealth: troop.maxHealth };
  });
  const worldTowers = (match.towers ?? []).map((tower) => ({ slotId: tower.slotId, ownerPlayerNumber: tower.ownerPlayerNumber, card: tower.card }));
  const worldProjectiles = (match.projectiles ?? []).map((projectile) => {
    const target = projectile.card === "arrow_tower"
      ? match.troops.find((troop) => troop.id === projectile.targetTroopId)
      : null;
    const targetPoint = target ? pointAtDistance(match.map, target.position) : pointAtDistance(match.map, projectile.targetPosition);
    return {
      id: projectile.id,
      ownerPlayerNumber: projectile.ownerPlayerNumber,
      card: projectile.card,
      fromX: projectile.origin.x / match.map.width,
      fromY: projectile.origin.y / match.map.height,
      toX: targetPoint.x / match.map.width,
      toY: targetPoint.y / match.map.height,
      progress: clamp((elapsed - (projectile.impactAtElapsedMs - 400)) / 400, 0, 1)
    };
  });
  return {
    sequence: match.game.nextSnapshotSequence,
    phase: match.phase,
    elapsedMatchMs: elapsed,
    remainingMs: Math.max(0, match.balance.match.durationMs - elapsed),
    player1: { wallHealth: playerByNumber(match, 1)?.wallHealth ?? 0, castleHealth: playerByNumber(match, 1)?.castleHealth ?? 0 },
    player2: { wallHealth: playerByNumber(match, 2)?.wallHealth ?? 0, castleHealth: playerByNumber(match, 2)?.castleHealth ?? 0 },
    troops: worldTroops,
    towers: worldTowers,
    projectiles: worldProjectiles,
    result: match.game.result,
    viewerPlayerNumber: playerNumber
  };
}
