import { generateMap } from "./map-v2.mjs";

const rounded = (value) => Math.round(value * 1000) / 1000;

/** Adds fixed world objects to the complete deterministic geometry sent to clients. */
export function createSharedMap(seed, config = {}) {
  const generated = generateMap(seed, config);
  const centerX = rounded(generated.width / 2);
  return {
    ...generated,
    seed: generated.meta.seed,
    wallGates: {
      player1: { x: centerX, y: rounded(generated.height * 0.86) },
      player2: { x: centerX, y: rounded(generated.height * 0.14) }
    },
    castlePositions: {
      player1: { x: centerX, y: rounded(generated.height * 0.932) },
      player2: { x: centerX, y: rounded(generated.height * 0.068) }
    }
  };
}
