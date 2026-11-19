import assert from "node:assert/strict";
import test from "node:test";
import { generateMap, getFallbackMaps, inspectPath, MAP_ARCHETYPES } from "./src/map-v2.mjs";

test("la misma semilla produce exactamente el mismo JSON", () => {
  const first = generateMap("fortrush-seed-42");
  const second = generateMap("fortrush-seed-42");
  assert.equal(JSON.stringify(first), JSON.stringify(second));
  assert.equal(first.width, 1000);
  assert.equal(first.height, 1800);
  assert.ok(first.path.points.length > 20);
  assert.equal(first.path.points.length, first.path.cumLen.length);
  assert.equal(first.path.cumLen.at(-1), first.path.totalLength);
  assert.deepEqual(first.slots, []);
});

test("1.000 semillas generan caminos válidos, acotados y variados", () => {
  const maps = Array.from({ length: 1000 }, (_, index) => generateMap(`seed-${index}`));
  const archetypes = new Set();
  const paths = new Set();

  for (const map of maps) {
    const validation = inspectPath(map.path);
    assert.equal(validation.valid, true, `${map.meta.seed}: ${validation.issues.join(", ")}`);
    assert.ok(map.path.totalLength >= 1800 * 1.25);
    assert.ok(map.path.totalLength <= 1800 * 3.0);
    assert.ok(validation.metrics.widthUsage >= 0.70);
    assert.ok(validation.metrics.minimumRadius === null || validation.metrics.minimumRadius >= map.path.width * 1.6);
    assert.equal(map.meta.usedFallback, false, `${map.meta.seed} no debería depender del respaldo`);
    archetypes.add(map.meta.archetype);
    paths.add(JSON.stringify(map.path.points));
  }

  assert.deepEqual([...archetypes].sort(), [...MAP_ARCHETYPES].sort());
  assert.ok(paths.size > 990, "las semillas deben producir formas distintas");
});

test("ambos extremos pasan verticalmente por las puertas y la ruta tiene simetría de 180°", () => {
  for (let index = 0; index < 100; index += 1) {
    const map = generateMap(`gate-${index}`);
    const points = map.path.points;
    const tolerance = map.width * 0.002;
    const verticalSampleCount = 5;

    for (const point of points.slice(0, verticalSampleCount)) {
      assert.ok(Math.abs(point.x - map.width / 2) <= tolerance);
    }
    for (const point of points.slice(-verticalSampleCount)) {
      assert.ok(Math.abs(point.x - map.width / 2) <= tolerance);
    }
    for (let pointIndex = 0; pointIndex < points.length; pointIndex += 1) {
      const mirrored = points[points.length - pointIndex - 1];
      assert.ok(Math.abs(points[pointIndex].x + mirrored.x - map.width) <= 0.002);
      assert.ok(Math.abs(points[pointIndex].y + mirrored.y - map.height) <= 0.002);
    }
  }
});

test("hay cinco respaldos geométricamente válidos y diferentes", () => {
  const backups = getFallbackMaps();
  assert.equal(backups.length, 5);
  assert.ok(backups.every((map) => inspectPath(map.path).valid));
  assert.equal(new Set(backups.map((map) => JSON.stringify(map.path.points))).size, 5);
});

test("si se agotan los intentos, el resultado usa un respaldo determinista", () => {
  const first = generateMap("fallback-case", { maxAttempts: 0 });
  const second = generateMap("fallback-case", { maxAttempts: 0 });
  assert.equal(first.meta.usedFallback, true);
  assert.equal(first.meta.attempts, 0);
  assert.equal(JSON.stringify(first), JSON.stringify(second));
  assert.equal(inspectPath(first.path).valid, true);
});
