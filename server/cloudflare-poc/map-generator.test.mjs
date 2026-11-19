import assert from "node:assert/strict";
import test from "node:test";
import { generateSharedMap, getFallbackMaps, isValidSharedMap } from "./src/map-generator.mjs";

function screenDistanceToPath(point, path) {
  let minimum = Number.POSITIVE_INFINITY;
  for (let index = 0; index < path.length - 1; index += 1) {
    const start = path[index];
    const end = path[index + 1];
    const dx = end.x - start.x;
    const dy = (end.y - start.y) * (16 / 9);
    const px = point.x - start.x;
    const py = (point.y - start.y) * (16 / 9);
    const lengthSquared = dx * dx + dy * dy;
    const progress = lengthSquared === 0 ? 0 : Math.max(0, Math.min(1, (px * dx + py * dy) / lengthSquared));
    minimum = Math.min(minimum, Math.hypot(px - progress * dx, py - progress * dy));
  }
  return minimum;
}

test("la misma semilla produce la misma geometría curva y simétrica", () => {
  const first = generateSharedMap("a1b2c3d4");
  const second = generateSharedMap("a1b2c3d4");
  assert.deepEqual(first, second);
  assert.ok(first.path.length > 49);
  assert.ok(new Set(first.path.map((point) => point.x)).size > 10);
  assert.equal(first.towerSlots.player1.length, 4);
  assert.equal(first.towerSlots.player2.length, 4);
  assert.ok(isValidSharedMap(first));
});

test("una serie de semillas genera mapas válidos y variados", () => {
  const maps = Array.from({ length: 200 }, (_, index) => generateSharedMap(index.toString(16).padStart(8, "0")));
  for (const map of maps) assert.ok(isValidSharedMap(map), "el mapa debe superar la validación geométrica");
  const distinctPaths = new Set(maps.map((map) => JSON.stringify(map.path)));
  assert.ok(distinctPaths.size > 190, "la mayoría de semillas debe producir una forma distinta");
});

test("rechaza huecos fuera de la banda invisible del camino", () => {
  const map = generateSharedMap("12345678");
  const slotOutOfLane = structuredClone(map);
  const y = slotOutOfLane.towerSlots.player1[0].y;
  slotOutOfLane.towerSlots.player1[0] = { x: 0.91, y };
  slotOutOfLane.towerSlots.player2[0] = { x: 0.09, y: 1 - y };
  assert.ok(screenDistanceToPath(slotOutOfLane.towerSlots.player1[0], slotOutOfLane.path) > 0.1995);
  assert.equal(isValidSharedMap(slotOutOfLane), false);
});

test("hay cinco mapas de respaldo válidos y diferentes", () => {
  const fallbackMaps = getFallbackMaps();
  assert.equal(fallbackMaps.length, 5);
  assert.ok(fallbackMaps.every(isValidSharedMap));
  assert.equal(new Set(fallbackMaps.map((map) => JSON.stringify(map.path))).size, 5);
});

test("si no hay intentos, se selecciona uno de los cinco respaldos", () => {
  const fallbackPaths = new Set(getFallbackMaps().map((map) => JSON.stringify(map.path)));
  const map = generateSharedMap("deadbeef", { maxAttempts: 0 });
  assert.equal(map.seed, "deadbeef");
  assert.ok(fallbackPaths.has(JSON.stringify(map.path)));
  assert.ok(isValidSharedMap(map));
});

test("rechaza mapas con huecos incompletos o coordenadas fuera del tablero", () => {
  const map = generateSharedMap("12345678");
  const missingSlot = structuredClone(map);
  missingSlot.towerSlots.player2.pop();
  assert.equal(isValidSharedMap(missingSlot), false);
  const outsideBoard = structuredClone(map);
  outsideBoard.path[5].x = 1.1;
  assert.equal(isValidSharedMap(outsideBoard), false);
});
