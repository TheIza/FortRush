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
  sampleSpacing: 14,
  minimumRadiusFactor: 1.6,
  minimumSegmentSeparationFactor: 1.12,
  minimumWidthUsage: 0.70,
  minimumLengthFactor: 1.25,
  maximumLengthFactor: 3.0,
  noiseFraction: 0.012,
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

function candidateMap(seed, archetype, random, config, noiseFraction) {
  const path = buildFullPath(archetype, random, config, noiseFraction);
  const inspected = inspectPath(path, config);
  return {
    map: {
      width: config.width,
      height: config.height,
      path,
      slots: [],
      meta: {
        seed: String(seed),
        archetype,
        score: scorePath(inspected.metrics ?? { totalLength: 0, widthUsage: 0, minimumRadius: 0, minimumSeparation: 0 }, config),
        metrics: inspected.metrics,
        attempts: 0,
        usedFallback: false
      }
    },
    inspected
  };
}

export function getFallbackMaps(configInput = {}) {
  const config = resolvedConfig(configInput);
  return FALLBACK_SEEDS.map((seed, index) => {
    const random = mulberry32(hashSeed(seed));
    const { map } = candidateMap(seed, MAP_ARCHETYPES[index], random, config, 0);
    map.meta.usedFallback = true;
    map.meta.attempts = 0;
    return map;
  });
}

export function generateMap(seed, configInput = {}) {
  const seedText = String(seed);
  const initialSeed = hashSeed(seed);
  const config = resolvedConfig(configInput);

  for (let attempt = 0; attempt < config.maxAttempts; attempt += 1) {
    const attemptSeed = mixSeed(initialSeed ^ Math.imul(attempt + 1, 0x9e3779b9));
    const random = mulberry32(attemptSeed);
    const archetype = MAP_ARCHETYPES[Math.floor(random() * MAP_ARCHETYPES.length)];
    const { map, inspected } = candidateMap(seedText, archetype, random, config, config.noiseFraction);
    if (inspected.valid) {
      map.meta.attempts = attempt + 1;
      return map;
    }
  }

  const fallbackMaps = getFallbackMaps(config).filter((map) => inspectPath(map.path, config).valid);
  if (fallbackMaps.length === 0) {
    throw new Error("No se pudo generar un camino válido ni encontrar un respaldo válido.");
  }
  const chosen = fallbackMaps[initialSeed % fallbackMaps.length];
  return {
    ...chosen,
    meta: { ...chosen.meta, seed: seedText, attempts: config.maxAttempts, usedFallback: true }
  };
}
