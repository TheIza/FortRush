import assert from "node:assert/strict";
import test from "node:test";
import { generateSharedMap, getFallbackMaps, isValidSharedMap } from "./src/map-generator.mjs";

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
