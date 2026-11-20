// Generador v2 aislado: no sustituye al formato que hoy consume el Worker.
// Coordenadas lógicas en unidades de mapa; la pantalla las escala al dibujar.

export const MAP_ARCHETYPES = Object.freeze([
  "s-suave",
  "doble-s",
  "zigzag-diagonal",
  "horquilla-central",
  "gran-c"
]);

const ARCHETYPE_SHAPES = Object.freeze({
  "s-suave": { horizontalRun: 0.060, middleLane: 0.00 },
  "doble-s": { horizontalRun: 0.085, middleLane: 0.12 },
  "zigzag-diagonal": { horizontalRun: 0.045, middleLane: 0.22 },
  "horquilla-central": { horizontalRun: 0.115, middleLane: 0.08 },
  "gran-c": { horizontalRun: 0.120, middleLane: 0.04 }
});

const FALLBACK_SEEDS = Object.freeze([
  "fallback-s-suave",
  "fallback-doble-s",
  "fallback-zigzag",
  "fallback-horquilla",
  "fallback-gran-c"
]);

const DEFAULTS = Object.freeze({
  width: 1000,
  height: 1800,
  pathWidth: 80,
  slotsPerPlayer: 7,
  slotRadius: 40,
  slotHalfHeightFraction: 0.04,
  slotGap: 12,
  invisibleRoadExtra: 25,
  towerRange: 300,
  premiumSlotsPerPlayer: 2,
  sampleSpacing: 14,
  minimumRadiusFactor: 1.6,
  minimumSegmentSeparationFactor: 1.12,
  minimumWidthUsage: 0.70,
  minimumLengthFactor: 1.25,
  maximumLengthFactor: 3.0,
  noiseFraction: 0.012,
  candidatesPerSeed: 20,
  maxAttempts: 50
});

const clamp = (value, minimum, maximum) => Math.max(minimum, Math.min(maximum, value));
const distance = (a, b) => Math.hypot(b.x - a.x, b.y - a.y);
const rounded = (value) => Math.round(value * 1000) / 1000;

function resolvedConfig(config = {}) {
  const width = Number.isFinite(config.width) && config.width > 0 ? config.width : DEFAULTS.width;
  const height = Number.isFinite(config.height) && config.height > 0 ? config.height : DEFAULTS.height;
  const pathWidth = Number.isFinite(config.pathWidth) && config.pathWidth > 0
    ? config.pathWidth
    : DEFAULTS.pathWidth;
  const sampleSpacing = Number.isFinite(config.sampleSpacing) && config.sampleSpacing > 0
    ? config.sampleSpacing
    : DEFAULTS.sampleSpacing;
  const maxAttempts = Number.isFinite(config.maxAttempts)
    ? clamp(Math.floor(config.maxAttempts), 0, 50)
    : DEFAULTS.maxAttempts;

  return {
    width,
    height,
    pathWidth,
    slotsPerPlayer: Number.isFinite(config.slotsPerPlayer)
      ? clamp(Math.floor(config.slotsPerPlayer), 1, 12)
      : DEFAULTS.slotsPerPlayer,
    slotRadius: Number.isFinite(config.slotRadius) && config.slotRadius > 0
      ? config.slotRadius
      : DEFAULTS.slotRadius * width / DEFAULTS.width,
    slotHalfHeight: Number.isFinite(config.slotHalfHeight) && config.slotHalfHeight > 0
      ? config.slotHalfHeight
      : height * DEFAULTS.slotHalfHeightFraction,
    slotGap: Number.isFinite(config.slotGap) && config.slotGap >= 0 ? config.slotGap : DEFAULTS.slotGap,
    invisibleRoadExtra: Number.isFinite(config.invisibleRoadExtra) && config.invisibleRoadExtra >= 0
      ? config.invisibleRoadExtra
      : DEFAULTS.invisibleRoadExtra * width / DEFAULTS.width,
    towerRange: Number.isFinite(config.towerRange) && config.towerRange > 0
      ? config.towerRange
      : DEFAULTS.towerRange * width / DEFAULTS.width,
    premiumSlotsPerPlayer: Number.isFinite(config.premiumSlotsPerPlayer)
      ? clamp(Math.floor(config.premiumSlotsPerPlayer), 1, 2)
      : DEFAULTS.premiumSlotsPerPlayer,
    minimumSlotSeparation: Number.isFinite(config.minimumSlotSeparation) && config.minimumSlotSeparation > 0
      ? config.minimumSlotSeparation
      : (Number.isFinite(config.slotRadius) && config.slotRadius > 0 ? config.slotRadius : DEFAULTS.slotRadius * width / DEFAULTS.width) * 2 + 16,
    slotCandidateSpacing: Number.isFinite(config.slotCandidateSpacing) && config.slotCandidateSpacing > 0
      ? config.slotCandidateSpacing
      : Math.max(18, (Number.isFinite(config.slotRadius) && config.slotRadius > 0 ? config.slotRadius : DEFAULTS.slotRadius * width / DEFAULTS.width) * 0.38),
    minimumAlongPathDistance: Number.isFinite(config.minimumAlongPathDistance) && config.minimumAlongPathDistance >= 0
      ? config.minimumAlongPathDistance
      : 64 * width / DEFAULTS.width,
    forbiddenZones: Array.isArray(config.forbiddenZones) ? config.forbiddenZones : [],
    sampleSpacing,
    minimumRadius: pathWidth * DEFAULTS.minimumRadiusFactor,
    minimumSegmentSeparation: pathWidth * DEFAULTS.minimumSegmentSeparationFactor,
    minimumWidthUsage: Number.isFinite(config.minimumWidthUsage)
      ? clamp(config.minimumWidthUsage, 0.5, 0.95)
      : DEFAULTS.minimumWidthUsage,
    minLength: Number.isFinite(config.minLength)
      ? config.minLength
      : height * DEFAULTS.minimumLengthFactor,
    maxLength: Number.isFinite(config.maxLength)
      ? config.maxLength
      : height * DEFAULTS.maximumLengthFactor,
    noiseFraction: Number.isFinite(config.noiseFraction)
      ? clamp(config.noiseFraction, 0, 0.04)
      : DEFAULTS.noiseFraction,
    candidatesPerSeed: Number.isFinite(config.candidatesPerSeed)
      ? clamp(Math.floor(config.candidatesPerSeed), 1, 50)
      : DEFAULTS.candidatesPerSeed,
    maxAttempts
  };
}

function hashSeed(seed) {
  if (typeof seed !== "string" && typeof seed !== "number") {
    throw new TypeError("La semilla debe ser texto o un número.");
  }
  const text = String(seed);
  if (text.length === 0) throw new TypeError("La semilla no puede estar vacía.");
  let hash = 0x811c9dc5;
  for (const character of text) {
    hash ^= character.codePointAt(0);
    hash = Math.imul(hash, 0x01000193);
  }
  return hash >>> 0;
}

function mixSeed(value) {
  let mixed = value >>> 0;
  mixed ^= mixed >>> 16;
  mixed = Math.imul(mixed, 0x7feb352d);
  mixed ^= mixed >>> 15;
  mixed = Math.imul(mixed, 0x846ca68b);
  mixed ^= mixed >>> 16;
  return mixed >>> 0;
}

// Mulberry32: cada stream comienza desde una semilla derivada; no usa Math.random.
function mulberry32(seed) {
  let state = seed >>> 0;
  return () => {
    state = (state + 0x6d2b79f5) >>> 0;
    let value = state;
    value = Math.imul(value ^ (value >>> 15), value | 1);
    value ^= value + Math.imul(value ^ (value >>> 7), value | 61);
    return ((value ^ (value >>> 14)) >>> 0) / 4294967296;
  };
}

function lerpPoint(a, b, amount) {
  return { x: a.x + (b.x - a.x) * amount, y: a.y + (b.y - a.y) * amount };
}

function centripetalBlend(a, b, ta, tb, t) {
  const span = Math.max(tb - ta, 1e-8);
  return lerpPoint(a, b, (t - ta) / span);
}

function evaluateCentripetalCatmullRom(p0, p1, p2, p3, amount) {
  const knotStep = (a, b) => Math.max(Math.sqrt(distance(a, b)), 1e-5);
  const t0 = 0;
  const t1 = t0 + knotStep(p0, p1);
  const t2 = t1 + knotStep(p1, p2);
  const t3 = t2 + knotStep(p2, p3);
  const t = t1 + (t2 - t1) * amount;

  const a1 = centripetalBlend(p0, p1, t0, t1, t);
  const a2 = centripetalBlend(p1, p2, t1, t2, t);
  const a3 = centripetalBlend(p2, p3, t2, t3, t);
  const b1 = centripetalBlend(a1, a2, t0, t2, t);
  const b2 = centripetalBlend(a2, a3, t1, t3, t);
  return centripetalBlend(b1, b2, t1, t2, t);
}

function appendStraight(points, end, spacing) {
  const start = points.at(-1);
  const length = distance(start, end);
  const steps = Math.max(1, Math.ceil(length / spacing));
  for (let step = 1; step <= steps; step += 1) {
    points.push(lerpPoint(start, end, step / steps));
  }
}

function appendQuarterArc(points, center, radius, startAngle, endAngle, spacing) {
  const arcLength = Math.abs(endAngle - startAngle) * radius;
  const steps = Math.max(12, Math.ceil(arcLength / spacing));
  for (let step = 1; step <= steps; step += 1) {
    const angle = startAngle + (endAngle - startAngle) * step / steps;
    points.push({
      x: center.x + radius * Math.cos(angle),
      y: center.y + radius * Math.sin(angle)
    });
  }
}

function makeAnalyticLowerPath(archetype, random, config, noiseFraction = config.noiseFraction) {
  const centerX = config.width / 2;
  const centerY = config.height / 2;
  const gateY = config.height * 0.86;
  const startY = config.height * 0.925;
  const shape = ARCHETYPE_SHAPES[archetype];
  const spacing = config.pathWidth * 0.16;
  const middleStraight = config.pathWidth * shape.middleLane;
  const centerTail = config.pathWidth * 0.18;
  const verticalGap = gateY - centerY;
  const radiusBase = (verticalGap - middleStraight - centerTail) / 4;
  const radius = radiusBase + (random() * 2 - 1) * config.pathWidth * noiseFraction;
  const side = random() < 0.5 ? -1 : 1;
  const horizontalRun = config.width * shape.horizontalRun +
    (random() * 2 - 1) * config.width * noiseFraction * 0.35;
  const points = [{ x: centerX, y: startY }];
  const landmarks = [0];
  const mark = () => landmarks.push(points.length - 1);
  const reflectSide = (point) => ({
    x: side < 0 ? point.x : centerX * 2 - point.x,
    y: point.y
  });

  appendStraight(points, { x: centerX, y: gateY }, spacing);
  mark();

  // Cuatro arcos de un cuarto de círculo trasladan la ruta al lateral y la devuelven
  // al centro. El radio se calcula para respetar la altura disponible entre puerta y centro.
  appendQuarterArc(points, { x: centerX - radius, y: gateY }, radius, 0, -Math.PI / 2, spacing);
  mark();
  appendStraight(points, { x: centerX - radius - horizontalRun, y: gateY - radius }, spacing);
  mark();
  appendQuarterArc(
    points,
    { x: centerX - radius - horizontalRun, y: gateY - radius * 2 },
    radius,
    Math.PI / 2,
    Math.PI,
    spacing
  );
  mark();

  const sideLaneY = gateY - radius * 2 - middleStraight;
  appendStraight(points, { x: centerX - radius * 2 - horizontalRun, y: sideLaneY }, spacing);
  mark();
  appendQuarterArc(
    points,
    { x: centerX - radius - horizontalRun, y: sideLaneY },
    radius,
    Math.PI,
    Math.PI * 1.5,
    spacing
  );
  mark();
  appendStraight(points, { x: centerX - radius, y: sideLaneY - radius }, spacing);
  mark();
  appendQuarterArc(
    points,
    { x: centerX - radius, y: sideLaneY - radius * 2 },
    radius,
    Math.PI / 2,
    0,
    spacing
  );
  mark();
  appendStraight(points, { x: centerX, y: centerY }, spacing);
  mark();

  return {
    points: points.map(reflectSide),
    landmarks: [...new Set(landmarks)].sort((a, b) => a - b)
  };
}

function selectIrregularControlPoints(points, landmarks, random, config) {
  const lengths = cumulativeLengths(points);
  const controls = [];
  const appendAt = (target) => {
    const point = pointAtDistance(points, lengths, target);
    const previous = controls.at(-1);
    if (!previous || distance(previous, point) > 1e-3) controls.push(point);
  };

  for (let landmarkIndex = 0; landmarkIndex < landmarks.length - 1; landmarkIndex += 1) {
    const startIndex = landmarks[landmarkIndex];
    const endIndex = landmarks[landmarkIndex + 1];
    const startDistance = lengths[startIndex];
    const endDistance = lengths[endIndex];
    if (landmarkIndex === 0) appendAt(startDistance);

    let target = startDistance;
    while (target < endDistance) {
      const irregularStep = config.pathWidth * (0.34 + random() * 0.30);
      if (target + irregularStep >= endDistance - config.pathWidth * 0.12) break;
      target += irregularStep;
      appendAt(target);
    }
    appendAt(endDistance);
  }
  return controls;
}

function interpolateControlPoints(controlPoints) {
  const dense = [controlPoints[0]];
  for (let index = 0; index < controlPoints.length - 1; index += 1) {
    const p1 = controlPoints[index];
    const p2 = controlPoints[index + 1];
    const p0 = index === 0
      ? { x: p1.x * 2 - p2.x, y: p1.y * 2 - p2.y }
      : controlPoints[index - 1];
    const p3 = index + 2 < controlPoints.length
      ? controlPoints[index + 2]
      : { x: p2.x * 2 - p1.x, y: p2.y * 2 - p1.y };
    const segmentSteps = Math.max(12, Math.ceil(distance(p1, p2) / 12));
    for (let step = 1; step <= segmentSteps; step += 1) {
      dense.push(evaluateCentripetalCatmullRom(p0, p1, p2, p3, step / segmentSteps));
    }
  }
  return dense;
}

function cumulativeLengths(points) {
  const lengths = [0];
  for (let index = 1; index < points.length; index += 1) {
    lengths.push(lengths[index - 1] + distance(points[index - 1], points[index]));
  }
  return lengths;
}

function pointAtDistance(points, lengths, target) {
  let low = 0;
  let high = lengths.length - 1;
  while (low < high) {
    const middle = Math.floor((low + high) / 2);
    if (lengths[middle] < target) low = middle + 1;
    else high = middle;
  }
  const endIndex = Math.max(1, low);
  const startIndex = endIndex - 1;
  const segmentLength = lengths[endIndex] - lengths[startIndex];
  const progress = segmentLength > 0 ? (target - lengths[startIndex]) / segmentLength : 0;
  return lerpPoint(points[startIndex], points[endIndex], clamp(progress, 0, 1));
}

function pathFrameAt(path, target) {
  const bounded = clamp(target, 0, path.totalLength);
  let low = 0;
  let high = path.cumLen.length - 1;
  while (low < high) {
    const middle = Math.floor((low + high) / 2);
    if (path.cumLen[middle] < bounded) low = middle + 1;
    else high = middle;
  }
  const endIndex = Math.max(1, low);
  const startIndex = endIndex - 1;
  const start = path.points[startIndex];
  const end = path.points[endIndex];
  const segmentLength = path.cumLen[endIndex] - path.cumLen[startIndex];
  const progress = segmentLength > 0 ? (bounded - path.cumLen[startIndex]) / segmentLength : 0;
  const dx = end.x - start.x;
  const dy = end.y - start.y;
  const tangentLength = Math.hypot(dx, dy) || 1;
  return {
    point: lerpPoint(start, end, clamp(progress, 0, 1)),
    tangent: { x: dx / tangentLength, y: dy / tangentLength }
  };
}

function resampleByArcLength(points, spacing) {
  const lengths = cumulativeLengths(points);
  const total = lengths.at(-1);
  const output = [];
  for (let target = 0; target < total; target += spacing) {
    output.push(pointAtDistance(points, lengths, target));
  }
  output.push(points.at(-1));
  return output.map((point) => ({ x: rounded(point.x), y: rounded(point.y) }));
}

function buildFullPath(archetype, random, config, noiseFraction = config.noiseFraction) {
  const analytic = makeAnalyticLowerPath(archetype, random, config, noiseFraction);
  const controls = selectIrregularControlPoints(analytic.points, analytic.landmarks, random, config);
  const lowerDense = interpolateControlPoints(controls);
  const lower = resampleByArcLength(lowerDense, config.sampleSpacing);
  const upper = lower.slice(0, -1).reverse().map((point) => ({
    x: rounded(config.width - point.x),
    y: rounded(config.height - point.y)
  }));
  const points = [...lower, ...upper];
  const cumLen = cumulativeLengths(points).map(rounded);
  return {
    points,
    cumLen,
    totalLength: cumLen.at(-1),
    width: rounded(config.pathWidth)
  };
}

function pointSegmentDistance(point, start, end) {
  const dx = end.x - start.x;
  const dy = end.y - start.y;
  const lengthSquared = dx * dx + dy * dy;
  if (lengthSquared === 0) return distance(point, start);
  const projection = clamp(((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared, 0, 1);
  return Math.hypot(point.x - (start.x + projection * dx), point.y - (start.y + projection * dy));
}

function distanceToPath(point, path) {
  let minimum = Number.POSITIVE_INFINITY;
  for (let index = 0; index < path.points.length - 1; index += 1) {
    minimum = Math.min(minimum, pointSegmentDistance(point, path.points[index], path.points[index + 1]));
  }
  return minimum;
}

function circleCoverageOnSegment(center, radius, start, end, startDistance, endDistance) {
  const dx = end.x - start.x;
  const dy = end.y - start.y;
  const fx = start.x - center.x;
  const fy = start.y - center.y;
  const a = dx * dx + dy * dy;
  if (a < 1e-10) return fx * fx + fy * fy <= radius * radius ? [startDistance, endDistance] : null;
  const b = 2 * (fx * dx + fy * dy);
  const c = fx * fx + fy * fy - radius * radius;
  const discriminant = b * b - 4 * a * c;
  if (discriminant < 0) return c <= 0 ? [startDistance, endDistance] : null;
  const root = Math.sqrt(Math.max(0, discriminant));
  const first = (-b - root) / (2 * a);
  const second = (-b + root) / (2 * a);
  const low = Math.max(0, Math.min(first, second));
  const high = Math.min(1, Math.max(first, second));
  if (low > high) return null;
  const segmentLength = endDistance - startDistance;
  return [startDistance + low * segmentLength, startDistance + high * segmentLength];
}

function mergeIntervals(intervals) {
  const sorted = intervals.filter((interval) => interval[1] - interval[0] > 1e-4)
    .sort((a, b) => a[0] - b[0]);
  const merged = [];
  for (const interval of sorted) {
    const previous = merged.at(-1);
    if (previous && interval[0] <= previous[1] + 0.01) previous[1] = Math.max(previous[1], interval[1]);
    else merged.push([...interval]);
  }
  return merged;
}

function turnAngleCoveredByIntervals(path, intervals) {
  let total = 0;
  for (let index = 1; index < path.points.length - 1; index += 1) {
    const along = path.cumLen[index];
    if (!intervals.some(([start, end]) => along >= start && along <= end)) continue;
    const previous = path.points[index - 1];
    const current = path.points[index];
    const next = path.points[index + 1];
    const before = Math.atan2(current.y - previous.y, current.x - previous.x);
    const after = Math.atan2(next.y - current.y, next.x - current.x);
    let delta = after - before;
    while (delta > Math.PI) delta -= Math.PI * 2;
    while (delta < -Math.PI) delta += Math.PI * 2;
    total += Math.abs(delta);
  }
  return total;
}

function coverageForSlot(slot, path, config) {
  const intervals = [];
  for (let index = 0; index < path.points.length - 1; index += 1) {
    const interval = circleCoverageOnSegment(
      slot,
      config.towerRange,
      path.points[index],
      path.points[index + 1],
      path.cumLen[index],
      path.cumLen[index + 1]
    );
    if (interval) intervals.push(interval);
  }
  const merged = mergeIntervals(intervals);
  const coveredLength = merged.reduce((sum, [start, end]) => sum + end - start, 0);
  const curveBonus = turnAngleCoveredByIntervals(path, merged) * config.towerRange * 0.045;
  const multiLaneBonus = Math.min(config.towerRange * 0.45, Math.max(0, merged.length - 1) * config.towerRange * 0.18);
  const value = rounded(coveredLength + curveBonus + multiLaneBonus);
  return {
    intervals: merged.map(([start, end]) => [rounded(start), rounded(end)]),
    value
  };
}

function rotatedPoint(point, config) {
  return { x: rounded(config.width - point.x), y: rounded(config.height - point.y) };
}

function intersectsForbiddenZone(point, config) {
  return config.forbiddenZones.some((zone) => {
    if (![zone?.x, zone?.y, zone?.width, zone?.height].every(Number.isFinite)) return false;
    const closestX = clamp(point.x, zone.x, zone.x + zone.width);
    const closestY = clamp(point.y, zone.y, zone.y + zone.height);
    return distance(point, { x: closestX, y: closestY }) < config.slotRadius;
  });
}

function isValidSlotPoint(point, owner, path, config) {
  const radius = config.slotRadius;
  if (point.x < radius || point.x > config.width - radius ||
      point.y < config.slotHalfHeight || point.y > config.height - config.slotHalfHeight) return false;
  if (intersectsForbiddenZone(point, config)) return false;

  const centerY = config.height / 2;
  const midfieldBand = config.height * 0.035;
  const wallMargin = config.height * 0.035;
  const wallY = owner === "player1" ? config.height * 0.86 : config.height * 0.14;
  const castleY = owner === "player1" ? config.height * 0.932 : config.height * 0.068;
  if (owner === "player1") {
    if (point.y - config.slotHalfHeight < centerY + midfieldBand) return false;
    if (point.y + config.slotHalfHeight > wallY - wallMargin) return false;
  } else {
    if (point.y + config.slotHalfHeight > centerY - midfieldBand) return false;
    if (point.y - config.slotHalfHeight < wallY + wallMargin) return false;
  }
  if (Math.abs(point.y - wallY) < config.slotHalfHeight + path.width * 0.5) return false;
  if (distance(point, { x: config.width / 2, y: castleY }) < radius + config.width * 0.11) return false;

  const roadDistance = distanceToPath(point, path);
  const minimumRoadDistance = path.width * 0.5 + radius + config.slotGap;
  const maximumRoadDistance = minimumRoadDistance + config.invisibleRoadExtra;
  return roadDistance >= minimumRoadDistance - 0.75 && roadDistance <= maximumRoadDistance + 0.75;
}

function slotsForPlayerOne(path, random, config) {
  const candidates = [];
  const halfLength = path.totalLength / 2;
  const requiredDistance = path.width * 0.5 + config.slotRadius + config.slotGap;
  const maximumOffset = requiredDistance + config.invisibleRoadExtra;
  const minimumY = config.height / 2 + config.height * 0.035 + config.slotHalfHeight;
  const maximumY = config.height * 0.86 - config.height * 0.035 - config.slotHalfHeight;

  for (let along = 0; along < halfLength; along += config.slotCandidateSpacing) {
    const { point, tangent } = pathFrameAt(path, along);
    if (point.y < minimumY || point.y > maximumY) continue;
    for (const side of [-1, 1]) {
      const offset = requiredDistance + random() * config.invisibleRoadExtra;
      const candidate = {
        x: rounded(point.x - tangent.y * offset * side),
        y: rounded(point.y + tangent.x * offset * side),
        along,
        side,
        tieBreak: random()
      };
      if (!isValidSlotPoint(candidate, "player1", path, config)) continue;
      const coverage = coverageForSlot(candidate, path, config);
      if (coverage.intervals.length === 0 || coverage.value <= 0) continue;
      candidates.push({ ...candidate, ...coverage });
    }
  }

  if (candidates.length < config.slotsPerPlayer) return null;
  const selected = [];
  const minAlong = Math.min(config.minimumAlongPathDistance, halfLength / (config.slotsPerPlayer + 1));
  const eligibleStart = Math.min(...candidates.map((candidate) => candidate.along));
  const eligibleEnd = Math.max(...candidates.map((candidate) => candidate.along));
  const eligibleLength = Math.max(1, eligibleEnd - eligibleStart);
  const sectorCounts = Array(config.slotsPerPlayer).fill(0);
  const bestValue = Math.max(...candidates.map((candidate) => candidate.value));

  while (selected.length < config.slotsPerPlayer) {
    let best = null;
    let bestUtility = Number.NEGATIVE_INFINITY;
    for (const candidate of candidates) {
      if (selected.some((other) => Math.abs(candidate.along - other.along) < minAlong)) continue;
      const pair = [candidate, rotatedPoint(candidate, config)];
      const existing = selected.flatMap((other) => [other, rotatedPoint(other, config)]);
      if (pair.some((point) => existing.some((other) => distance(point, other) < config.minimumSlotSeparation))) continue;
      if (distance(pair[0], pair[1]) < config.minimumSlotSeparation) continue;

      const progress = (candidate.along - eligibleStart) / eligibleLength;
      const sector = Math.min(config.slotsPerPlayer - 1, Math.floor(progress * config.slotsPerPlayer));
      const sectorBonus = sectorCounts[sector] === 0 ? bestValue * 0.18 : -bestValue * 0.32 * sectorCounts[sector];
      const nearestProgressGap = selected.length === 0
        ? 1
        : Math.min(...selected.map((other) => Math.abs(candidate.along - other.along))) / Math.max(eligibleLength, 1);
      const utility = candidate.value + sectorBonus + nearestProgressGap * bestValue * 0.2 + candidate.tieBreak * 1e-4;
      if (utility > bestUtility) {
        best = { ...candidate, sector };
        bestUtility = utility;
      }
    }
    if (!best) return null;
    selected.push(best);
    sectorCounts[best.sector] += 1;
  }

  selected.sort((a, b) => a.along - b.along || a.x - b.x);
  const premiumIndexes = new Set(selected.map((slot, index) => ({ index, value: slot.value }))
    .sort((a, b) => b.value - a.value || a.index - b.index)
    .slice(0, config.premiumSlotsPerPlayer)
    .map((entry) => entry.index));

  return selected.map((slot, index) => ({
    id: `p1-${String(index + 1).padStart(2, "0")}`,
    x: slot.x,
    y: slot.y,
    owner: "player1",
    tier: premiumIndexes.has(index) ? "premium" : "normal",
    value: slot.value,
    coverage: slot.intervals
  }));
}

function mirrorPlayerSlots(playerOneSlots, path, config) {
  return playerOneSlots.map((slot, index) => ({
    id: `p2-${String(index + 1).padStart(2, "0")}`,
    ...rotatedPoint(slot, config),
    owner: "player2",
    tier: slot.tier,
    value: slot.value,
    coverage: slot.coverage
      .map(([start, end]) => [rounded(path.totalLength - end), rounded(path.totalLength - start)])
      .sort((a, b) => a[0] - b[0])
  }));
}

function generateTowerSlots(path, random, config) {
  const playerOne = slotsForPlayerOne(path, random, config);
  if (!playerOne) return null;
  const playerTwo = mirrorPlayerSlots(playerOne, path, config);
  return [...playerOne, ...playerTwo];
}

export function inspectSlots(slots, path, configInput = {}) {
  const config = resolvedConfig(configInput);
  const issues = [];
  if (!Array.isArray(slots) || slots.length !== config.slotsPerPlayer * 2) {
    return { valid: false, issues: ["número de slots incorrecto"], metrics: null };
  }
  const playerOne = slots.filter((slot) => slot.owner === "player1");
  const playerTwo = slots.filter((slot) => slot.owner === "player2");
  if (playerOne.length !== config.slotsPerPlayer || playerTwo.length !== config.slotsPerPlayer) {
    issues.push("los slots no están repartidos entre los dos jugadores");
  }
  for (const slot of slots) {
    if (![slot.x, slot.y, slot.value].every(Number.isFinite) || slot.value <= 0) {
      issues.push(`slot ${slot.id ?? "sin id"} con valores inválidos`);
      continue;
    }
    if (!Array.isArray(slot.coverage) || slot.coverage.length === 0) {
      issues.push(`slot ${slot.id} sin cobertura`);
      continue;
    }
    if (!['normal', 'premium'].includes(slot.tier)) issues.push(`slot ${slot.id} con tier inválido`);
    if (slot.owner !== 'player1' && slot.owner !== 'player2') issues.push(`slot ${slot.id} sin propietario válido`);
    if (slot.owner && !isValidSlotPoint(slot, slot.owner, path, config)) {
      issues.push(`slot ${slot.id} está fuera de una zona de construcción válida`);
    }
    for (const [start, end] of slot.coverage) {
      if (!Number.isFinite(start) || !Number.isFinite(end) || start < 0 || end > path.totalLength || end <= start) {
        issues.push(`slot ${slot.id} con intervalo de cobertura inválido`);
      }
    }
    const expectedCoverage = coverageForSlot(slot, path, config);
    const sameIntervals = slot.coverage.length === expectedCoverage.intervals.length &&
      slot.coverage.every((interval, intervalIndex) =>
        Math.abs(interval[0] - expectedCoverage.intervals[intervalIndex][0]) <= 0.01 &&
        Math.abs(interval[1] - expectedCoverage.intervals[intervalIndex][1]) <= 0.01
      );
    if (Math.abs(slot.value - expectedCoverage.value) > 0.02 || !sameIntervals) {
      issues.push(`slot ${slot.id} no coincide con su cobertura calculada`);
    }
  }

  for (let index = 0; index < Math.min(playerOne.length, playerTwo.length); index += 1) {
    const first = playerOne[index];
    const second = playerTwo[index];
    const mirrored = rotatedPoint(first, config);
    if (distance(second, mirrored) > 0.002 || first.value !== second.value || first.tier !== second.tier) {
      issues.push(`slot ${first.id} y su reflejo no coinciden`);
    }
    const mirroredCoverage = first.coverage
      .map(([start, end]) => [rounded(path.totalLength - end), rounded(path.totalLength - start)])
      .sort((a, b) => a[0] - b[0]);
    if (JSON.stringify(second.coverage) !== JSON.stringify(mirroredCoverage)) {
      issues.push(`cobertura de ${first.id} y su reflejo no coincide`);
    }
  }

  for (let first = 0; first < slots.length; first += 1) {
    for (let second = first + 1; second < slots.length; second += 1) {
      if (distance(slots[first], slots[second]) < config.minimumSlotSeparation) {
        issues.push(`los slots ${slots[first].id} y ${slots[second].id} se solapan`);
      }
    }
  }

  const premiumCounts = ["player1", "player2"].map((owner) =>
    slots.filter((slot) => slot.owner === owner && slot.tier === "premium").length
  );
  if (premiumCounts.some((count) => count < 1 || count > 2)) issues.push("cada jugador debe tener 1 o 2 slots premium");
  return {
    valid: issues.length === 0,
    issues,
    metrics: {
      countPerPlayer: [playerOne.length, playerTwo.length],
      premiumPerPlayer: premiumCounts,
      minimumSeparation: slots.length > 1
        ? rounded(Math.min(...slots.flatMap((slot, index) => slots.slice(index + 1).map((other) => distance(slot, other)))))
        : null
    }
  };
}

function orientation(a, b, c) {
  return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
}

function segmentsIntersect(a, b, c, d) {
  const epsilon = 1e-7;
  const abC = orientation(a, b, c);
  const abD = orientation(a, b, d);
  const cdA = orientation(c, d, a);
  const cdB = orientation(c, d, b);
  if ((abC > epsilon && abD < -epsilon || abC < -epsilon && abD > epsilon) &&
      (cdA > epsilon && cdB < -epsilon || cdA < -epsilon && cdB > epsilon)) return true;
  return Math.abs(abC) <= epsilon && pointSegmentDistance(c, a, b) <= epsilon ||
    Math.abs(abD) <= epsilon && pointSegmentDistance(d, a, b) <= epsilon ||
    Math.abs(cdA) <= epsilon && pointSegmentDistance(a, c, d) <= epsilon ||
    Math.abs(cdB) <= epsilon && pointSegmentDistance(b, c, d) <= epsilon;
}

function segmentDistance(a, b, c, d) {
  if (segmentsIntersect(a, b, c, d)) return 0;
  return Math.min(
    pointSegmentDistance(a, c, d), pointSegmentDistance(b, c, d),
    pointSegmentDistance(c, a, b), pointSegmentDistance(d, a, b)
  );
}

function boxesDistance(a, b, c, d) {
  const gapX = Math.max(0, Math.max(Math.min(a.x, b.x), Math.min(c.x, d.x)) - Math.min(Math.max(a.x, b.x), Math.max(c.x, d.x)));
  const gapY = Math.max(0, Math.max(Math.min(a.y, b.y), Math.min(c.y, d.y)) - Math.min(Math.max(a.y, b.y), Math.max(c.y, d.y)));
  return Math.hypot(gapX, gapY);
}

function circumradius(a, b, c) {
  const ab = distance(a, b);
  const bc = distance(b, c);
  const ca = distance(c, a);
  const twiceArea = Math.abs(orientation(a, b, c));
  return twiceArea < 1e-7 ? Number.POSITIVE_INFINITY : (ab * bc * ca) / (2 * twiceArea);
}

function countBroadCurves(points) {
  let previousHeading = null;
  let totalAbsoluteTurn = 0;
  for (let index = 1; index < points.length; index += 1) {
    const heading = Math.atan2(points[index].y - points[index - 1].y, points[index].x - points[index - 1].x);
    if (previousHeading !== null) {
      let change = heading - previousHeading;
      while (change > Math.PI) change -= Math.PI * 2;
      while (change < -Math.PI) change += Math.PI * 2;
      totalAbsoluteTurn += Math.abs(change);
    }
    previousHeading = heading;
  }
  return Math.round(totalAbsoluteTurn / Math.PI);
}

function crossesGateVertically(points, gateY, centerX, tolerance) {
  for (let index = 0; index < points.length - 1; index += 1) {
    const start = points[index];
    const end = points[index + 1];
    if (gateY < Math.min(start.y, end.y) - 1e-6 || gateY > Math.max(start.y, end.y) + 1e-6) continue;
    const verticalSpan = Math.abs(end.y - start.y);
    if (verticalSpan < 1e-6) continue;
    const progress = clamp((gateY - start.y) / (end.y - start.y), 0, 1);
    const crossingX = start.x + (end.x - start.x) * progress;
    if (Math.abs(crossingX - centerX) <= tolerance &&
        Math.abs(end.x - start.x) <= verticalSpan * 0.15) return true;
  }
  return false;
}

function sampledRadius(path, config) {
  const window = Math.max(path.width * 0.34, config.sampleSpacing * 2.5);
  const step = Math.max(config.sampleSpacing * 2, window);
  let minimum = Number.POSITIVE_INFINITY;
  for (let along = window; along < path.totalLength - window; along += step) {
    const a = pointAtDistance(path.points, path.cumLen, along - window);
    const b = pointAtDistance(path.points, path.cumLen, along);
    const c = pointAtDistance(path.points, path.cumLen, along + window);
    minimum = Math.min(minimum, circumradius(a, b, c));
  }
  return minimum;
}

function sampledClearance(path, config) {
  const points = path.points;
  const lengths = path.cumLen;
  const requiredArc = path.width * 0.75;
  let minimum = Number.POSITIVE_INFINITY;
  let intersects = false;

  for (let first = 0; first < points.length - 1; first += 1) {
    const a = points[first];
    const b = points[first + 1];
    let second = first + 2;
    while (second < points.length - 1 && lengths[second] - lengths[first + 1] < requiredArc) second += 1;
    for (; second < points.length - 1; second += 1) {
      const c = points[second];
      const d = points[second + 1];
      if (boxesDistance(a, b, c, d) > path.width * 1.25) continue;
      const separation = segmentDistance(a, b, c, d);
      if (separation < path.width * 0.025) intersects = true;
      if (lengths[second] - lengths[first + 1] >= config.minimumSegmentSeparation * 1.1) {
        minimum = Math.min(minimum, separation);
      }
    }
  }
  return { minimum, intersects };
}

export function inspectPath(path, configInput = {}) {
  const config = resolvedConfig(configInput);
  const issues = [];
  if (!path || !Array.isArray(path.points) || path.points.length < 3) {
    return { valid: false, issues: ["faltan puntos"], metrics: null };
  }
  const points = path.points;
  if (points.some((point) => !Number.isFinite(point?.x) || !Number.isFinite(point?.y))) {
    return { valid: false, issues: ["hay coordenadas no finitas"], metrics: null };
  }
  const cumLen = cumulativeLengths(points).map(rounded);
  const totalLength = cumLen.at(-1);
  const xValues = points.map((point) => point.x);
  const yValues = points.map((point) => point.y);
  const widthUsage = (Math.max(...xValues) - Math.min(...xValues)) / config.width;
  const edgeMargin = path.width / 2 + config.width * 0.01;
  const inBounds = points.every((point) =>
    point.x >= edgeMargin && point.x <= config.width - edgeMargin &&
    point.y >= edgeMargin && point.y <= config.height - edgeMargin
  );
  const radius = sampledRadius(path, config);
  const separation = sampledClearance(path, config);
  const verticalTolerance = config.width * 0.002;
  const bottomExit = points.slice(0, Math.max(3, Math.ceil(config.height * 0.04 / config.sampleSpacing)))
    .every((point) => Math.abs(point.x - config.width / 2) <= verticalTolerance);
  const topExit = points.slice(-Math.max(3, Math.ceil(config.height * 0.04 / config.sampleSpacing)))
    .every((point) => Math.abs(point.x - config.width / 2) <= verticalTolerance);
  const bottomGate = crossesGateVertically(points, config.height * 0.86, config.width / 2, config.pathWidth * 0.02);
  const topGate = crossesGateVertically(points, config.height * 0.14, config.width / 2, config.pathWidth * 0.02);
  let rotationallySymmetric = points.length % 2 === 1;
  for (let index = 0; rotationallySymmetric && index < points.length; index += 1) {
    const mirrored = points[points.length - 1 - index];
    rotationallySymmetric = Math.abs(points[index].x + mirrored.x - config.width) <= 0.002 &&
      Math.abs(points[index].y + mirrored.y - config.height) <= 0.002;
  }

  if (!inBounds) issues.push("el camino sale del tablero o se acerca demasiado al borde");
  if (widthUsage < config.minimumWidthUsage) issues.push("el camino usa menos del ancho mínimo");
  if (totalLength < config.minLength || totalLength > config.maxLength) issues.push("longitud fuera del intervalo permitido");
  if (radius < config.minimumRadius) issues.push("radio de curvatura inferior al mínimo");
  if (separation.intersects) issues.push("el camino se autointersecta");
  if (separation.minimum < config.minimumSegmentSeparation) issues.push("tramos no contiguos demasiado próximos");
  if (!bottomExit || !topExit || !bottomGate || !topGate) issues.push("la entrada o salida no es vertical por la puerta");
  if (!rotationallySymmetric) issues.push("el camino no conserva la simetría de 180°");

  const metrics = {
    totalLength: rounded(totalLength),
    widthUsage: rounded(widthUsage),
    curveCount: countBroadCurves(points),
    minimumRadius: Number.isFinite(radius) ? rounded(radius) : null,
    requiredRadius: rounded(config.minimumRadius),
    minimumSeparation: Number.isFinite(separation.minimum) ? rounded(separation.minimum) : null,
    requiredSeparation: rounded(config.minimumSegmentSeparation),
    pointCount: points.length,
    bounds: {
      minX: rounded(Math.min(...xValues)), maxX: rounded(Math.max(...xValues)),
      minY: rounded(Math.min(...yValues)), maxY: rounded(Math.max(...yValues))
    }
  };
  return { valid: issues.length === 0, issues, metrics };
}

function scorePath(metrics, config) {
  const lengthMidpoint = (config.minLength + config.maxLength) / 2;
  const lengthRange = Math.max((config.maxLength - config.minLength) / 2, 1);
  const lengthFit = clamp(1 - Math.abs(metrics.totalLength - lengthMidpoint) / lengthRange, 0, 1);
  const widthFit = clamp(metrics.widthUsage / config.minimumWidthUsage, 0, 1);
  const radiusFit = metrics.minimumRadius === null
    ? 1
    : clamp(metrics.minimumRadius / config.minimumRadius, 0, 1);
  const separationFit = metrics.minimumSeparation === null
    ? 1
    : clamp(metrics.minimumSeparation / config.minimumSegmentSeparation, 0, 1);
  const curveFit = metrics.curveCount >= 3 && metrics.curveCount <= 6 ? 1 : 0.5;
  return Math.round((lengthFit * 0.35 + widthFit * 0.20 + radiusFit * 0.20 + separationFit * 0.15 + curveFit * 0.10) * 100);
}

function scoreCandidate(metrics, slots, config) {
  const pathScore = scorePath(metrics, config) / 100;
  const values = slots.map((slot) => slot.value);
  const meanValue = values.reduce((sum, value) => sum + value, 0) / Math.max(1, values.length);
  const variance = values.reduce((sum, value) => sum + (value - meanValue) ** 2, 0) / Math.max(1, values.length);
  const coefficientOfVariation = meanValue > 0 ? Math.sqrt(variance) / meanValue : 1;
  const valuesByOwner = ["player1", "player2"].map((owner) =>
    slots.filter((slot) => slot.owner === owner).reduce((sum, slot) => sum + slot.value, 0)
  );
  const equityDifference = Math.abs(valuesByOwner[0] - valuesByOwner[1]) /
    Math.max(valuesByOwner[0], valuesByOwner[1], 1);
  const lengthMidpoint = (config.minLength + config.maxLength) / 2;
  const lengthFit = clamp(1 - Math.abs(metrics.totalLength - lengthMidpoint) /
    Math.max((config.maxLength - config.minLength) / 2, 1), 0, 1);
  const curveFit = metrics.curveCount >= 3 && metrics.curveCount <= 6
    ? 1
    : clamp(1 - Math.min(Math.abs(metrics.curveCount - 3), Math.abs(metrics.curveCount - 6)) / 3, 0, 1);
  const openingFit = clamp(metrics.widthUsage / config.minimumWidthUsage, 0, 1);
  const clearanceFit = Math.min(
    metrics.minimumRadius === null ? 1 : clamp(metrics.minimumRadius / config.minimumRadius, 0, 1),
    metrics.minimumSeparation === null ? 1 : clamp(metrics.minimumSeparation / config.minimumSegmentSeparation, 0, 1)
  );
  const coverageFit = clamp(meanValue / (config.towerRange * 2), 0, 1);
  const equityFit = clamp(1 - equityDifference / 0.08, 0, 1);
  const variationFit = clamp(1 - Math.abs(coefficientOfVariation - 0.18) / 0.3, 0, 1);
  const breakdown = {
    length: rounded(lengthFit * 100),
    curves: rounded(curveFit * 100),
    opening: rounded(openingFit * 100),
    clearance: rounded(clearanceFit * 100),
    coverage: rounded(coverageFit * 100),
    equity: rounded(equityFit * 100),
    slotValueVariation: rounded(variationFit * 100),
    slotValueVariance: rounded(variance),
    slotValueCoefficientOfVariation: rounded(coefficientOfVariation),
    ownerValueDifferencePercent: rounded(equityDifference * 100)
  };
  const score = lengthFit * 0.25 + curveFit * 0.20 + openingFit * 0.20 + clearanceFit * 0.15 +
    coverageFit * 0.10 + equityFit * 0.05 + variationFit * 0.05;
  return { score: Math.round(score * 100), breakdown };
}

function candidateMap(seed, archetype, random, config, noiseFraction, slotSeed = hashSeed(`${seed}|${archetype}|slots`)) {
  const path = buildFullPath(archetype, random, config, noiseFraction);
  const inspected = inspectPath(path, config);
  const slots = inspected.valid
    ? generateTowerSlots(path, mulberry32(mixSeed(slotSeed ^ 0xa511e9b3)), config)
    : null;
  const inspectedSlots = slots ? inspectSlots(slots, path, config) : {
    valid: false,
    issues: ["no se pudieron colocar todos los slots"],
    metrics: null
  };
  const score = inspected.metrics && slots
    ? scoreCandidate(inspected.metrics, slots, config)
    : { score: 0, breakdown: null };
  return {
    map: {
      width: config.width,
      height: config.height,
      path,
      slots: slots ?? [],
      meta: {
        seed: String(seed),
        archetype,
        score: score.score,
        scoreBreakdown: score.breakdown,
        pathScore: scorePath(inspected.metrics ?? { totalLength: 0, widthUsage: 0, minimumRadius: 0, minimumSeparation: 0 }, config),
        metrics: inspected.metrics,
        slotMetrics: inspectedSlots.metrics,
        towerRange: rounded(config.towerRange),
        slotRadius: rounded(config.slotRadius),
        attempts: 0,
        usedFallback: false
      }
    },
    inspected: { ...inspected, valid: inspected.valid && inspectedSlots.valid, slotIssues: inspectedSlots.issues }
  };
}

export function getFallbackMaps(configInput = {}) {
  const config = resolvedConfig(configInput);
  return FALLBACK_SEEDS.map((seed, index) => {
    const random = mulberry32(hashSeed(seed));
    const { map } = candidateMap(seed, MAP_ARCHETYPES[index], random, config, 0, hashSeed(`${seed}|slot-stream`));
    for (let attempt = 0; attempt < 50 && !inspectSlots(map.slots, map.path, config).valid; attempt += 1) {
      const slotSeed = mixSeed(hashSeed(`${seed}|slot-retry|${attempt}`));
      const slots = generateTowerSlots(map.path, mulberry32(slotSeed), config);
      if (!slots || !inspectSlots(slots, map.path, config).valid) continue;
      map.slots = slots;
      map.meta.slotMetrics = inspectSlots(slots, map.path, config).metrics;
    }
    map.meta.usedFallback = true;
    map.meta.attempts = 0;
    return map;
  });
}

export function generateMap(seed, configInput = {}) {
  const seedText = String(seed);
  const initialSeed = hashSeed(seed);
  const config = resolvedConfig(configInput);
  const selectedArchetype = MAP_ARCHETYPES[mixSeed(initialSeed ^ 0x6d2b79f5) % MAP_ARCHETYPES.length];

  for (let attempt = 0; attempt < config.maxAttempts; attempt += 1) {
    let bestCandidate = null;
    let bestCandidateIndex = -1;
    let candidatesEvaluated = 0;

    for (let candidateIndex = 0; candidateIndex < config.candidatesPerSeed; candidateIndex += 1) {
      const ordinal = attempt * config.candidatesPerSeed + candidateIndex + 1;
      const candidateSeed = mixSeed(initialSeed ^ Math.imul(ordinal, 0x9e3779b9));
      const random = mulberry32(candidateSeed);
      const { map, inspected } = candidateMap(seedText, selectedArchetype, random, config, config.noiseFraction, candidateSeed);
      candidatesEvaluated += 1;
      if (!inspected.valid) continue;
      if (!bestCandidate || map.meta.score > bestCandidate.meta.score) {
        bestCandidate = map;
        bestCandidateIndex = candidateIndex + 1;
      }
    }

    if (bestCandidate) {
      bestCandidate.meta.attempts = attempt + 1;
      bestCandidate.meta.candidatesEvaluated = candidatesEvaluated;
      bestCandidate.meta.candidateIndex = bestCandidateIndex;
      return bestCandidate;
    }
  }

  const fallbackMaps = getFallbackMaps(config).filter((map) =>
    inspectPath(map.path, config).valid && inspectSlots(map.slots, map.path, config).valid
  );
  if (fallbackMaps.length === 0) {
    throw new Error("No se pudo generar un camino válido ni encontrar un respaldo válido.");
  }
  const chosen = fallbackMaps[initialSeed % fallbackMaps.length];
  return {
    ...chosen,
    meta: { ...chosen.meta, seed: seedText, attempts: config.maxAttempts, usedFallback: true }
  };
}
