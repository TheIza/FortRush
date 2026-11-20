import assert from "node:assert/strict";
import test from "node:test";
import {
  generateMap,
  getFallbackMaps,
  inspectPath,
  inspectSlots,
  MAP_ARCHETYPES
} from "./src/map-v2.mjs";

function pointAtDistance(path, target) {
  for (let index = 0; index < path.cumLen.length - 1; index += 1) {
    if (target > path.cumLen[index + 1]) continue;
    const start = path.points[index];
    const end = path.points[index + 1];
    const span = path.cumLen[index + 1] - path.cumLen[index];
    const ratio = span === 0 ? 0 : (target - path.cumLen[index]) / span;
    return { x: start.x + (end.x - start.x) * ratio, y: start.y + (end.y - start.y) * ratio };
  }
  return path.points.at(-1);
}

function distanceToPath(point, path) {
  let closest = Number.POSITIVE_INFINITY;
  for (let index = 0; index < path.points.length - 1; index += 1) {
    const start = path.points[index];
    const end = path.points[index + 1];
    const dx = end.x - start.x;
    const dy = end.y - start.y;
    const lengthSquared = dx * dx + dy * dy;
    const ratio = lengthSquared === 0
      ? 0
      : Math.max(0, Math.min(1, ((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared));
    closest = Math.min(closest, Math.hypot(point.x - start.x - dx * ratio, point.y - start.y - dy * ratio));
  }
  return closest;
}

test("la misma semilla produce el mismo mapa, slots y cobertura", () => {
  const first = generateMap("fortrush-seed-42");
  const second = generateMap("fortrush-seed-42");
  assert.equal(JSON.stringify(first), JSON.stringify(second));
  assert.equal(first.width, 1000);
  assert.equal(first.height, 1800);
  assert.ok(first.path.points.length > 20);
  assert.equal(first.path.points.length, first.path.cumLen.length);
  assert.equal(first.path.cumLen.at(-1), first.path.totalLength);
  assert.equal(first.slots.length, 14);
  assert.equal(inspectSlots(first.slots, first.path).valid, true);
});

test("1.000 semillas producen caminos válidos y siete slots equilibrados por jugador", () => {
  const archetypes = new Set();
  const paths = new Set();

  for (let index = 0; index < 1000; index += 1) {
    const map = generateMap(`seed-${index}`);
    const pathValidation = inspectPath(map.path);
    const slotValidation = inspectSlots(map.slots, map.path);
    assert.equal(pathValidation.valid, true, `${map.meta.seed}: ${pathValidation.issues.join(", ")}`);
    assert.equal(slotValidation.valid, true, `${map.meta.seed}: ${slotValidation.issues.join(", ")}`);
    assert.equal(map.slots.length, 14);
    assert.deepEqual(slotValidation.metrics.countPerPlayer, [7, 7]);
    assert.ok(map.path.totalLength >= 1800 * 1.25);
    assert.ok(map.path.totalLength <= 1800 * 3.0);
    assert.ok(pathValidation.metrics.widthUsage >= 0.70);
    assert.ok(pathValidation.metrics.minimumRadius === null || pathValidation.metrics.minimumRadius >= map.path.width * 1.6);
    assert.equal(map.meta.usedFallback, false, `${map.meta.seed} no debería depender del respaldo`);
    assert.ok(map.meta.towerRange > 0);
    assert.equal(map.meta.candidatesEvaluated, 20);
    assert.ok(map.meta.candidateIndex >= 1 && map.meta.candidateIndex <= 20);
    assert.ok(map.meta.score >= 0 && map.meta.score <= 100);
    assert.ok(map.meta.scoreBreakdown.ownerValueDifferencePercent < 8);
    archetypes.add(map.meta.archetype);
    paths.add(JSON.stringify(map.path.points));
  }

  assert.deepEqual([...archetypes].sort(), [...MAP_ARCHETYPES].sort());
  assert.ok(paths.size > 990, "las semillas deben producir formas distintas");
});

test("las coberturas describen la distancia real al camino", () => {
  for (let index = 0; index < 20; index += 1) {
    const map = generateMap(`coverage-${index}`);
    for (const slot of map.slots) {
      assert.ok(slot.coverage.length > 0);
      for (const [start, end] of slot.coverage) {
        for (const along of [start, (start + end) / 2, end]) {
          const point = pointAtDistance(map.path, along);
          assert.ok(distanceToPath(slot, { points: [point, point] }) <= map.meta.towerRange + 0.01);
        }
      }
    }
  }
});

test("camino y slots del rival son simétricos por giro de 180 grados", () => {
  for (let index = 0; index < 100; index += 1) {
    const map = generateMap(`mirror-${index}`);
    const points = map.path.points;
    const tolerance = map.width * 0.002;

    for (let pointIndex = 0; pointIndex < points.length; pointIndex += 1) {
      const mirrored = points[points.length - pointIndex - 1];
      assert.ok(Math.abs(points[pointIndex].x + mirrored.x - map.width) <= 0.002);
      assert.ok(Math.abs(points[pointIndex].y + mirrored.y - map.height) <= 0.002);
    }

    const first = map.slots.filter((slot) => slot.owner === "player1");
    const second = map.slots.filter((slot) => slot.owner === "player2");
    for (let slotIndex = 0; slotIndex < first.length; slotIndex += 1) {
      assert.ok(Math.abs(first[slotIndex].x + second[slotIndex].x - map.width) <= 0.002);
      assert.ok(Math.abs(first[slotIndex].y + second[slotIndex].y - map.height) <= 0.002);
      assert.deepEqual(first[slotIndex].coverage.map(([start, end]) => [
        Number((map.path.totalLength - end).toFixed(3)),
        Number((map.path.totalLength - start).toFixed(3))
      ]).sort((a, b) => a[0] - b[0]), second[slotIndex].coverage);
    }

    for (const point of points.slice(0, 5)) assert.ok(Math.abs(point.x - map.width / 2) <= tolerance);
    for (const point of points.slice(-5)) assert.ok(Math.abs(point.x - map.width / 2) <= tolerance);
  }
});

test("hay cinco mapas de respaldo válidos, diferentes y con sus slots", () => {
  const backups = getFallbackMaps();
  assert.equal(backups.length, 5);
  assert.ok(backups.every((map) => inspectPath(map.path).valid));
  assert.ok(backups.every((map) => inspectSlots(map.slots, map.path).valid));
  assert.ok(backups.every((map) => map.slots.length === 14));
  assert.equal(new Set(backups.map((map) => JSON.stringify(map.path.points))).size, 5);
});

test("si se agotan los intentos, el respaldo completo es determinista", () => {
  const first = generateMap("fallback-case", { maxAttempts: 0 });
  const second = generateMap("fallback-case", { maxAttempts: 0 });
  assert.equal(first.meta.usedFallback, true);
  assert.equal(first.meta.attempts, 0);
  assert.equal(JSON.stringify(first), JSON.stringify(second));
  assert.equal(inspectPath(first.path).valid, true);
  assert.equal(inspectSlots(first.slots, first.path).valid, true);
});

test("el número de huecos se puede configurar", () => {
  const map = generateMap("four-slots", { slotsPerPlayer: 4 });
  assert.equal(map.slots.length, 8);
  assert.deepEqual(inspectSlots(map.slots, map.path, { slotsPerPlayer: 4 }).metrics.countPerPlayer, [4, 4]);
});
