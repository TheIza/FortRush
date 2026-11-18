const MAP_HEIGHT_PER_WIDTH = 16 / 9;
// Match the widest rendered road stroke (0.13 × 1.30 of the board width).
const ROAD_HALF_WIDTH = 0.0845;
const MIN_PATH_CLEARANCE = ROAD_HALF_WIDTH * 2;
// The 4:3 platform is wider than it is tall, so use its larger screen-space radius.
const TOWER_PAD_RADIUS = 0.09;
const TOWER_PAD_EDGE_RADIUS = 0.09;
const MIN_SLOT_PATH_CLEARANCE = ROAD_HALF_WIDTH + TOWER_PAD_RADIUS + 0.012;
const MIN_SLOT_SEPARATION = TOWER_PAD_RADIUS * 2 + 0.02;
const WALL_HALF_THICKNESS = 0.0375;
// Keep the entire platform outside the central and wall-to-edge no-build bands.
const MIDFIELD_NO_BUILD_HALF_HEIGHT = 0.035;
const WALL_NO_BUILD_MARGIN = 0.035;
const TOWER_PAD_HALF_HEIGHT_NORMALIZED = 0.04;
const MIN_LOCAL_PATH_ARC = 0.34;
const SAMPLE_STEP = 0.022;
const MAX_PATH_POINTS = 260;
const CASTLE_RADIUS = 0.11;
const DECORATION_RADIUS = { tree: 0.07, bush: 0.05, rock: 0.04 };

const WALL_GATES = {
  player1: { x: 0.5, y: 0.86 },
  player2: { x: 0.5, y: 0.14 }
};

const CASTLE_POSITIONS = {
  player1: { x: 0.5, y: 0.932 },
  player2: { x: 0.5, y: 0.068 }
};

const FALLBACK_SHAPE_SEEDS = [
  "00000000",
  "00000002",
  "00000006",
  "00000008",
  "0000000b"
];
const FALLBACK_SHAPES = FALLBACK_SHAPE_SEEDS.map((seed) =>
  randomShape(createSeededRandom(seed))
);

function makeRandomSeed() {
  return Array.from(crypto.getRandomValues(new Uint8Array(4)), (byte) =>
    byte.toString(16).padStart(2, "0")
  ).join("");
}

function normalizeSeed(seed) {
  return typeof seed === "string" && /^[0-9a-f]{8}$/i.test(seed)
    ? seed.toLowerCase()
    : makeRandomSeed();
}

function createSeededRandom(seed) {
  let state = Number.parseInt(seed, 16) >>> 0;
  if (state === 0) state = 0x6d2b79f5;
  return () => {
    state = (state + 0x6d2b79f5) >>> 0;
    let value = state;
    value = Math.imul(value ^ (value >>> 15), value | 1);
    value ^= value + Math.imul(value ^ (value >>> 7), value | 61);
    return ((value ^ (value >>> 14)) >>> 0) / 4294967296;
  };
}

function roundedPoint(x, y) {
  return {
    x: Math.round(x * 100000) / 100000,
    y: Math.round(y * 100000) / 100000
  };
}

function rotate180(point) {
  return roundedPoint(1 - point.x, 1 - point.y);
}

function playerOneSlotZone(gateY) {
  return {
    minimumY: 0.5 + MIDFIELD_NO_BUILD_HALF_HEIGHT + TOWER_PAD_HALF_HEIGHT_NORMALIZED,
    maximumY: gateY - WALL_NO_BUILD_MARGIN - TOWER_PAD_HALF_HEIGHT_NORMALIZED
  };
}

function screenDistance(a, b) {
  return Math.hypot(a.x - b.x, (a.y - b.y) * MAP_HEIGHT_PER_WIDTH);
}

function appendLine(points, start, end) {
  const length = screenDistance(start, end);
  const steps = Math.max(1, Math.ceil(length / SAMPLE_STEP));
  for (let step = 1; step <= steps; step += 1) {
    const t = step / steps;
    points.push(roundedPoint(
      start.x + (end.x - start.x) * t,
      start.y + (end.y - start.y) * t
    ));
  }
}

function appendQuadratic(points, start, control, end) {
  const estimate = screenDistance(start, control) + screenDistance(control, end);
  const steps = Math.max(2, Math.ceil(estimate / SAMPLE_STEP));
  for (let step = 1; step <= steps; step += 1) {
    const t = step / steps;
    const inverse = 1 - t;
    points.push(roundedPoint(
      inverse * inverse * start.x + 2 * inverse * t * control.x + t * t * end.x,
      inverse * inverse * start.y + 2 * inverse * t * control.y + t * t * end.y
    ));
  }
}

function makeSmoothPath(anchors, cornerFactor) {
  const path = [roundedPoint(anchors[0].x, anchors[0].y)];
  let cursor = path[0];

  for (let index = 1; index < anchors.length - 1; index += 1) {
    const previous = anchors[index - 1];
    const corner = anchors[index];
    const next = anchors[index + 1];
    const incomingLength = screenDistance(previous, corner);
    const outgoingLength = screenDistance(corner, next);
    if (incomingLength < 0.0001 || outgoingLength < 0.0001) continue;

    const radius = Math.min(incomingLength, outgoingLength) * cornerFactor;
    const incomingT = radius / incomingLength;
    const outgoingT = radius / outgoingLength;
    const approach = roundedPoint(
      corner.x - (corner.x - previous.x) * incomingT,
      corner.y - (corner.y - previous.y) * incomingT
    );
    const leave = roundedPoint(
      corner.x + (next.x - corner.x) * outgoingT,
      corner.y + (next.y - corner.y) * outgoingT
    );

    appendLine(path, cursor, approach);
    appendQuadratic(path, approach, corner, leave);
    cursor = leave;
  }

  appendLine(path, cursor, anchors[anchors.length - 1]);
  return path;
}

function makeLowerWaypoints(shape) {
  const firstX = shape.side > 0 ? shape.rightX : shape.leftX;
  const secondX = shape.side > 0 ? shape.leftX : shape.rightX;
  const gateExitY = shape.gateY - 0.035;
  return [
    roundedPoint(0.5, 0.925),
    roundedPoint(0.5, shape.gateY),
    roundedPoint(0.5, gateExitY),
    roundedPoint(firstX, shape.firstRowY),
    roundedPoint(firstX, shape.secondRowY),
    roundedPoint(secondX, shape.secondRowY),
    roundedPoint(secondX, shape.thirdRowY),
    roundedPoint(firstX, shape.thirdRowY),
    roundedPoint(0.5, shape.centerY),
    roundedPoint(0.5, 0.5)
  ];
}

function pointAtDistance(points, cumulative, distance) {
  const bounded = Math.max(0, Math.min(cumulative[cumulative.length - 1], distance));
  let index = 0;
  while (index < cumulative.length - 2 && cumulative[index + 1] < bounded) index += 1;
  const segmentLength = cumulative[index + 1] - cumulative[index];
  const progress = segmentLength > 0 ? (bounded - cumulative[index]) / segmentLength : 0;
  const start = points[index];
  const end = points[index + 1];
  const dx = end.x - start.x;
  const dy = (end.y - start.y) * MAP_HEIGHT_PER_WIDTH;
  const length = Math.hypot(dx, dy) || 1;
  return {
    point: roundedPoint(start.x + dx * progress, start.y + (end.y - start.y) * progress),
    tangent: { x: dx / length, y: dy / length }
  };
}

function makePlayerOneSlots(lowerPath, fullPath, gate, castle, random) {
  let gateIndex = 0;
  for (let index = 1; index < lowerPath.length; index += 1) {
    if (screenDistance(lowerPath[index], gate) < screenDistance(lowerPath[gateIndex], gate)) gateIndex = index;
  }
  const route = lowerPath.slice(gateIndex);
  if (route.length < 5) return null;

  const cumulative = [0];
  for (let index = 1; index < route.length; index += 1) {
    cumulative.push(cumulative[index - 1] + screenDistance(route[index - 1], route[index]));
  }
  const totalLength = cumulative[cumulative.length - 1];
  const allowedY = playerOneSlotZone(gate.y);
  if (allowedY.maximumY <= allowedY.minimumY) return null;
  const firstDistance = 0.04;
  const lastDistance = totalLength - MIN_SLOT_SEPARATION * 1.5;
  const availableLength = lastDistance - firstDistance;
  if (availableLength < 0.38) return null;

  const candidateCount = Math.max(30, Math.ceil(availableLength / 0.02));
  const candidates = [];
  for (let index = 0; index <= candidateCount; index += 1) {
    const distance = firstDistance + availableLength * index / candidateCount;
    const { point, tangent } = pointAtDistance(route, cumulative, distance);
    for (const side of [-1, 1]) {
      const offset = MIN_SLOT_PATH_CLEARANCE + 0.008 + random() * 0.018;
      const slot = roundedPoint(
        point.x + (-tangent.y) * offset * side,
        point.y + (tangent.x * offset * side) / MAP_HEIGHT_PER_WIDTH
      );
      if (
        slot.x < TOWER_PAD_EDGE_RADIUS || slot.x > 1 - TOWER_PAD_EDGE_RADIUS ||
        slot.y < allowedY.minimumY || slot.y > allowedY.maximumY ||
        distanceToPath(slot, fullPath) < MIN_SLOT_PATH_CLEARANCE ||
        Math.abs(slot.y - gate.y) * MAP_HEIGHT_PER_WIDTH <
          TOWER_PAD_RADIUS + WALL_HALF_THICKNESS + 0.018 ||
        screenDistance(slot, castle) < TOWER_PAD_RADIUS + CASTLE_RADIUS + 0.02
      ) continue;
      candidates.push(slot);
    }
  }
  if (candidates.length < 4) return null;

  for (let index = candidates.length - 1; index > 0; index -= 1) {
    const other = Math.floor(random() * (index + 1));
    [candidates[index], candidates[other]] = [candidates[other], candidates[index]];
  }
  let visited = 0;
  function chooseSlots(startIndex, selected) {
    if (selected.length === 4) return selected;
    if (visited >= 10000) return null;
    const existing = selected.flatMap((slot) => [slot, rotate180(slot)]);
    for (let index = startIndex; index < candidates.length; index += 1) {
      visited += 1;
      const candidate = candidates[index];
      const pair = [candidate, rotate180(candidate)];
      if (screenDistance(pair[0], pair[1]) < MIN_SLOT_SEPARATION) continue;
      if (pair.some((point) => existing.some((other) =>
        screenDistance(point, other) < MIN_SLOT_SEPARATION
      ))) continue;
      const result = chooseSlots(index + 1, [...selected, candidate]);
      if (result) return result;
      if (visited >= 10000) break;
    }
    return null;
  }
  return chooseSlots(0, []);
}

function makeDecorations(random, path, slots, castles, wallGates) {
  const decorations = [];
  const kinds = ["tree", "tree", "bush", "rock"];
  for (let attempt = 0; attempt < 120 && decorations.length < 12; attempt += 1) {
    const kind = kinds[Math.floor(random() * kinds.length)];
    const scale = Math.round((0.78 + random() * 0.35) * 100) / 100;
    const radius = DECORATION_RADIUS[kind] * scale;
    // Candidate art stays at the board edges; collisions decide whether it fits.
    const point = roundedPoint(
      random() < 0.5 ? 0.008 + random() * 0.018 : 0.974 + random() * 0.018,
      0.06 + random() * 0.88
    );
    if (distanceToPath(point, path) < radius + ROAD_HALF_WIDTH + 0.018) continue;
    if (wallGates.some((gate) =>
      Math.abs(point.y - gate.y) * MAP_HEIGHT_PER_WIDTH < radius + WALL_HALF_THICKNESS + 0.018
    )) continue;
    if (slots.some((slot) => screenDistance(point, slot) < radius + TOWER_PAD_RADIUS + 0.015)) continue;
    if (castles.some((castle) => screenDistance(point, castle) < radius + CASTLE_RADIUS + 0.015)) continue;
    if (decorations.some((decoration) =>
      screenDistance(point, decoration) < radius + DECORATION_RADIUS[decoration.kind] * decoration.scale + 0.02
    )) continue;
    decorations.push({ kind, ...point, scale });
  }
  return decorations;
}

function makeMap(seed, shape, random) {
  const lowerPath = makeSmoothPath(makeLowerWaypoints(shape), shape.cornerFactor);
  const path = [
    ...lowerPath,
    ...lowerPath.slice(0, -1).reverse().map(rotate180)
  ];
  const wallGate = roundedPoint(0.5, shape.gateY);
  const castle = roundedPoint(CASTLE_POSITIONS.player1.x, CASTLE_POSITIONS.player1.y);
  const player1Slots = makePlayerOneSlots(lowerPath, path, wallGate, castle, random);
  if (!player1Slots) return null;
  const player2Slots = player1Slots.map(rotate180);
  const wallGates = {
    player1: wallGate,
    player2: roundedPoint(0.5, 1 - shape.gateY)
  };
  const castlePositions = {
    player1: castle,
    player2: roundedPoint(CASTLE_POSITIONS.player2.x, CASTLE_POSITIONS.player2.y)
  };

  return {
    seed,
    width: 1,
    height: 1,
    aspectRatio: { width: 9, height: 16 },
    path,
    towerSlots: {
      player1: player1Slots,
      player2: player2Slots
    },
    wallGates,
    castlePositions,
    decorations: makeDecorations(
      random,
      path,
      [...player1Slots, ...player2Slots],
      [castlePositions.player1, castlePositions.player2],
      [wallGates.player1, wallGates.player2]
    )
  };
}

function pointSegmentDistance(point, start, end) {
  const pointY = point.y * MAP_HEIGHT_PER_WIDTH;
  const startY = start.y * MAP_HEIGHT_PER_WIDTH;
  const endY = end.y * MAP_HEIGHT_PER_WIDTH;
  const dx = end.x - start.x;
  const dy = endY - startY;
  const lengthSquared = dx * dx + dy * dy;
  if (lengthSquared === 0) return screenDistance(point, start);
  const projection = Math.max(0, Math.min(1,
    ((point.x - start.x) * dx + (pointY - startY) * dy) / lengthSquared
  ));
  return Math.hypot(
    point.x - (start.x + projection * dx),
    pointY - (startY + projection * dy)
  );
}

function distanceToPath(point, path) {
  let minimum = Number.POSITIVE_INFINITY;
  for (let index = 0; index < path.length - 1; index += 1) {
    minimum = Math.min(minimum, pointSegmentDistance(point, path[index], path[index + 1]));
  }
  return minimum;
}

export function isValidSharedMap(map) {
  const path = map?.path;
  if (!Array.isArray(path) || path.length < 40 || path.length > MAX_PATH_POINTS) return false;

  const accumulated = [0];
  for (let index = 0; index < path.length; index += 1) {
    const point = path[index];
    if (!Number.isFinite(point?.x) || !Number.isFinite(point?.y)) return false;
    if (point.x < ROAD_HALF_WIDTH || point.x > 1 - ROAD_HALF_WIDTH) return false;
    if (point.y < ROAD_HALF_WIDTH / MAP_HEIGHT_PER_WIDTH ||
        point.y > 1 - ROAD_HALF_WIDTH / MAP_HEIGHT_PER_WIDTH) return false;
    if (index > 0) {
      const segmentLength = screenDistance(path[index - 1], point);
      if (segmentLength < 0.00001 || segmentLength > SAMPLE_STEP * 1.8) return false;
      accumulated.push(accumulated[index - 1] + segmentLength);
    }
  }

  // Repeated portions must leave room for the full road width plus its border.
  for (let first = 0; first < path.length; first += 1) {
    for (let second = first + 1; second < path.length; second += 1) {
      if (accumulated[second] - accumulated[first] < MIN_LOCAL_PATH_ARC) continue;
      if (screenDistance(path[first], path[second]) < MIN_PATH_CLEARANCE) return false;
    }
  }

  const player1Slots = map.towerSlots?.player1;
  const player2Slots = map.towerSlots?.player2;
  const gates = map.wallGates;
  if (!gates?.player1 || !gates?.player2) return false;
  if (![gates.player1.x, gates.player1.y, gates.player2.x, gates.player2.y].every(Number.isFinite)) return false;
  if (!Array.isArray(player1Slots) || !Array.isArray(player2Slots)) return false;
  if (player1Slots.length !== 4 || player2Slots.length !== 4) return false;
  const allSlots = [...player1Slots, ...player2Slots];
  const allowedY = playerOneSlotZone(gates.player1.y);
  if (allowedY.maximumY <= allowedY.minimumY) return false;

  for (let index = 0; index < 4; index += 1) {
    const slot1 = player1Slots[index];
    const slot2 = player2Slots[index];
    if (![slot1?.x, slot1?.y, slot2?.x, slot2?.y].every(Number.isFinite)) return false;
    if (screenDistance(slot2, rotate180(slot1)) > 0.00002) return false;
    if (slot1.y < allowedY.minimumY || slot1.y > allowedY.maximumY) return false;
    if (slot2.y < 1 - allowedY.maximumY || slot2.y > 1 - allowedY.minimumY) return false;
    for (const slot of [slot1, slot2]) {
      if (slot.x < TOWER_PAD_EDGE_RADIUS || slot.x > 1 - TOWER_PAD_EDGE_RADIUS) return false;
      if (slot.y < 0.04 || slot.y > 0.96) return false;
      if (distanceToPath(slot, path) < MIN_SLOT_PATH_CLEARANCE) return false;
    }
  }

  for (let first = 0; first < allSlots.length; first += 1) {
    for (let second = first + 1; second < allSlots.length; second += 1) {
      if (screenDistance(allSlots[first], allSlots[second]) < MIN_SLOT_SEPARATION) return false;
      }
  }

  for (let index = 0; index < path.length; index += 1) {
    if (screenDistance(path[index], rotate180(path[path.length - index - 1])) > 0.00002) return false;
  }

  if (screenDistance(gates.player1, WALL_GATES.player1) > 0.02) return false;
  if (screenDistance(gates.player2, rotate180(gates.player1)) > 0.00002) return false;
  if (distanceToPath(gates.player1, path) > 0.015) return false;
  if (distanceToPath(gates.player2, path) > 0.015) return false;
  if (allSlots.some((slot) => [gates.player1, gates.player2].some((gate) =>
    Math.abs(slot.y - gate.y) * MAP_HEIGHT_PER_WIDTH <
      TOWER_PAD_RADIUS + WALL_HALF_THICKNESS + 0.018
  ))) return false;

  const castles = map.castlePositions;
  if (!castles?.player1 || !castles?.player2) return false;
  if (screenDistance(castles.player2, rotate180(castles.player1)) > 0.00002) return false;

  const decorations = map.decorations;
  if (!Array.isArray(decorations)) return false;
  for (let index = 0; index < decorations.length; index += 1) {
    const decoration = decorations[index];
    const radius = DECORATION_RADIUS[decoration?.kind] * decoration?.scale;
    if (!Number.isFinite(radius)) return false;
    const point = { x: decoration.x, y: decoration.y };
    if (!Number.isFinite(point.x) || !Number.isFinite(point.y)) return false;
    if (point.x < 0 || point.x > 1 || point.y < 0 || point.y > 1) return false;
    if (distanceToPath(point, path) < radius + ROAD_HALF_WIDTH + 0.018) return false;
    if ([gates.player1, gates.player2].some((gate) =>
      Math.abs(point.y - gate.y) * MAP_HEIGHT_PER_WIDTH < radius + WALL_HALF_THICKNESS + 0.018
    )) return false;
    if ([...player1Slots, ...player2Slots].some((slot) =>
      screenDistance(point, slot) < radius + TOWER_PAD_RADIUS + 0.015
    )) return false;
    if ([castles.player1, castles.player2].some((castle) =>
      screenDistance(point, castle) < radius + CASTLE_RADIUS + 0.015
    )) return false;
    for (let other = 0; other < index; other += 1) {
      const otherDecoration = decorations[other];
      const otherRadius = DECORATION_RADIUS[otherDecoration.kind] * otherDecoration.scale;
      if (screenDistance(point, otherDecoration) < radius + otherRadius + 0.02) return false;
    }
  }

  return true;
}

function randomShape(random) {
  const side = random() < 0.5 ? -1 : 1;
  return {
    side,
    leftX: 0.34 + random() * 0.02,
    rightX: 0.64 + random() * 0.02,
    gateY: 0.852 + random() * 0.018,
    firstRowY: 0.81 + random() * 0.025,
    secondRowY: 0.72 + random() * 0.005,
    thirdRowY: 0.60 + random() * 0.005,
    centerY: 0.5 + random() * 0.005,
    cornerFactor: 0.31 + random() * 0.11
  };
}

export function getFallbackMaps() {
  return FALLBACK_SHAPES.map((shape, index) => {
    const seed = FALLBACK_SHAPE_SEEDS[index];
    return makeMap(seed, shape, createSeededRandom(seed));
  });
}

export function generateSharedMap(inputSeed = makeRandomSeed(), { maxAttempts = 24 } = {}) {
  const seed = normalizeSeed(inputSeed);
  const random = createSeededRandom(seed);
  const attempts = Number.isFinite(maxAttempts) ? Math.max(0, Math.floor(maxAttempts)) : 24;

  for (let attempt = 0; attempt < attempts; attempt += 1) {
    const candidate = makeMap(seed, randomShape(random), random);
    if (candidate && isValidSharedMap(candidate)) return candidate;
  }

  const firstFallback = Math.floor(random() * FALLBACK_SHAPES.length);
  for (let offset = 0; offset < FALLBACK_SHAPES.length; offset += 1) {
    const index = (firstFallback + offset) % FALLBACK_SHAPES.length;
    const fallbackRandom = createSeededRandom(FALLBACK_SHAPE_SEEDS[index]);
    const candidate = makeMap(seed, FALLBACK_SHAPES[index], fallbackRandom);
    if (candidate && isValidSharedMap(candidate)) return candidate;
  }

  throw new Error("No se pudo construir un mapa válido.");
}
