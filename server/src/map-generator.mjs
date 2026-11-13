import { randomBytes } from "node:crypto";

const ROTATE = ({ x, y }) => ({ x: 1 - x, y: 1 - y });

function seededRandom(seed) {
  let state = seed >>> 0;
  return () => {
    state += 0x6D2B79F5;
    let value = state;
    value = Math.imul(value ^ (value >>> 15), value | 1);
    value ^= value + Math.imul(value ^ (value >>> 7), value | 61);
    return ((value ^ (value >>> 14)) >>> 0) / 4294967296;
  };
}

function fromSeed(seed) {
  const random = seededRandom(seed);
  const offset = () => Math.round((random() * 0.28 - 0.14) * 1000) / 1000;
  const lowerHalf = [
    { x: 0.5, y: 0.94 },
    { x: 0.5 + offset(), y: 0.78 },
    { x: 0.5 + offset(), y: 0.62 },
    { x: 0.5, y: 0.5 }
  ];
  const path = [...lowerHalf, ...lowerHalf.slice(0, -1).reverse().map(ROTATE)];
  const lowerSlots = [
    { x: 0.18, y: 0.78 }, { x: 0.82, y: 0.78 },
    { x: 0.18, y: 0.65 }, { x: 0.82, y: 0.65 }
  ];
  const upperSlots = lowerSlots.map(ROTATE);
  return {
    seed: seed.toString(16).padStart(8, "0"),
    width: 1,
    height: 1,
    path,
    towerSlots: { player1: lowerSlots, player2: upperSlots }
  };
}

export function generateSharedMap(seed = randomBytes(4).readUInt32BE(0)) {
  return fromSeed(seed);
}
