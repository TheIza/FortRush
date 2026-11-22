import { createHash } from "node:crypto";
import { generateMap, inspectPath, inspectSlots } from "./src/map-v2.mjs";

const sampleSize = 1_000;
const pathHashes = new Set();
const archetypeCounts = new Map();
const lengths = [];
const curveCounts = [];
const edgeMarginsDpAt320 = [];
let fallbackCount = 0;
let failureCount = 0;

for (let index = 0; index < sampleSize; index += 1) {
  const seed = `map-stats-${index + 1}`;
  try {
    const map = generateMap(seed);
    const pathValidation = inspectPath(map.path);
    const slotValidation = inspectSlots(map.slots, map.path);
    if (!pathValidation.valid || !slotValidation.valid) {
      throw new Error([...pathValidation.issues, ...slotValidation.issues].join("; "));
    }

    const geometry = JSON.stringify({
      width: map.width,
      height: map.height,
      path: map.path
    });
    pathHashes.add(createHash("sha256").update(geometry).digest("hex"));
    archetypeCounts.set(map.meta.archetype, (archetypeCounts.get(map.meta.archetype) ?? 0) + 1);
    lengths.push(map.path.totalLength);
    curveCounts.push(pathValidation.metrics.curveCount);
    edgeMarginsDpAt320.push(slotValidation.metrics.minimumLateralEdgeClearance / map.width * 320);
    if (map.meta.usedFallback) fallbackCount += 1;
  } catch (error) {
    failureCount += 1;
    console.error(`Fallo con ${seed}: ${error.message}`);
  }
}

const mean = (values) => values.length
  ? values.reduce((sum, value) => sum + value, 0) / values.length
  : Number.NaN;
const format = (value) => Number.isFinite(value) ? value.toFixed(2) : "n/d";
const distinctPercent = pathHashes.size / sampleSize * 100;
const fallbackPercent = fallbackCount / sampleSize * 100;
const minimumEdgeMarginDpAt320 = edgeMarginsDpAt320.length ? Math.min(...edgeMarginsDpAt320) : 0;
const commonShapes = [...archetypeCounts.entries()]
  .filter(([, count]) => count / sampleSize * 100 > 5).length;
const criteria = [
  ["Caminos distintos >= 99 %", distinctPercent >= 99],
  ["Al menos 4 formas con > 5 %", commonShapes >= 4],
  ["Mapas de respaldo < 2 %", fallbackPercent < 2],
  ["Margen lateral >= 32 dp en un tablero de 320 dp", minimumEdgeMarginDpAt320 >= 32]
];

console.log(`Estadísticas de mapa (${sampleSize} semillas)`);
console.log(`Caminos distintos: ${pathHashes.size}/${sampleSize} (${format(distinctPercent)} %)`);
console.log("Distribución de formas:");
for (const [archetype, count] of [...archetypeCounts.entries()].sort(([a], [b]) => a.localeCompare(b))) {
  console.log(`  ${archetype}: ${count} (${format(count / sampleSize * 100)} %)`);
}
console.log(`Longitud del camino (unidades): min ${format(Math.min(...lengths))}, media ${format(mean(lengths))}, max ${format(Math.max(...lengths))}`);
console.log(`Número de curvas: min ${Math.min(...curveCounts)}, media ${format(mean(curveCounts))}, max ${Math.max(...curveCounts)}`);
console.log(`Margen lateral mínimo de los slots: ${format(minimumEdgeMarginDpAt320)} dp (tablero de 320 dp)`);
console.log(`Mapas de respaldo: ${fallbackCount}/${sampleSize} (${format(fallbackPercent)} %)`);
console.log(`Fallos: ${failureCount}`);
console.log("Criterios:");
for (const [description, passed] of criteria) console.log(`  ${passed ? "OK" : "NO CUMPLE"} — ${description}`);

if (criteria.some(([, passed]) => !passed)) process.exitCode = 1;
