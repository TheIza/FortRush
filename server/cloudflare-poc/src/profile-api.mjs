import { importPKCS8, SignJWT } from "jose";
import { getNicknamePolicyMessage, validateNickname } from "../../src/nickname-policy.mjs";

const FIRESTORE_SCOPE = "https://www.googleapis.com/auth/datastore";
const TOKEN_URL = "https://oauth2.googleapis.com/token";
const FIRESTORE_URL = "https://firestore.googleapis.com/v1";
const COOLDOWN_MS = 7 * 24 * 60 * 60 * 1000;
let serviceTokenCache;

export class ProfileApiError extends Error {
  constructor(code, status, message, details = {}) {
    super(message);
    this.code = code;
    this.status = status;
    this.details = details;
  }
}

function docName(projectId, collection, id) {
  return `projects/${projectId}/databases/(default)/documents/${collection}/${id}`;
}

function readField(document, key) {
  const value = document?.fields?.[key];
  if (!value) return null;
  if (Object.hasOwn(value, "stringValue")) return value.stringValue;
  if (Object.hasOwn(value, "integerValue")) return Number(value.integerValue);
  if (Object.hasOwn(value, "doubleValue")) return value.doubleValue;
  if (Object.hasOwn(value, "booleanValue")) return value.booleanValue;
  if (Object.hasOwn(value, "timestampValue")) return value.timestampValue;
  if (Object.hasOwn(value, "nullValue")) return null;
  return null;
}

function encodeField(value) {
  if (value === null || value === undefined) return { nullValue: null };
  if (typeof value === "string") return { stringValue: value };
  if (typeof value === "boolean") return { booleanValue: value };
  if (Number.isInteger(value)) return { integerValue: String(value) };
  if (typeof value === "number") return { doubleValue: value };
  throw new TypeError("Unsupported Firestore field value");
}

function encodeFields(fields) {
  return Object.fromEntries(Object.entries(fields).map(([key, value]) => [key, encodeField(value)]));
}

function decodeProfile(document, now = Date.now()) {
  const rawNickname = readField(document, "apodo") ?? "";
  const checked = validateNickname(rawNickname);
  const nickname = checked.ok ? checked.nickname : null;
  const normalized = nickname ? checked.normalized : null;
  const lastChangedAt = readField(document, "fechaUltimoCambioApodo");
  const changedMillis = typeof lastChangedAt === "string" ? Date.parse(lastChangedAt) : null;
  const availableAt = Number.isFinite(changedMillis) ? changedMillis + COOLDOWN_MS : null;
  return {
    nickname,
    nicknameNormalized: normalized,
    lastNicknameChangeAt: Number.isFinite(changedMillis) ? new Date(changedMillis).toISOString() : null,
    nicknameChangeRemainingMs: availableAt === null ? 0 : Math.max(0, availableAt - now),
    nicknameChangeAvailableAt: availableAt === null ? null : new Date(availableAt).toISOString()
  };
}

function profileBase(uid, email) {
  return {
    id: uid,
    gmail: email ?? "",
    nombreUsuario: "",
    apodo: "",
    apodoNormalizado: "",
    fechaUltimoCambioApodo: null,
    monedas: 0,
    partidasCompletadas: 0,
    victorias: 0,
    derrotas: 0,
    empates: 0,
    experiencia: 0,
    nivel: 0
  };
}

function updateWrite(name, fields, { updateTime, exists, serverTimestamps = [] } = {}) {
  const write = {
    update: { name, fields: encodeFields(fields) },
    updateMask: { fieldPaths: Object.keys(fields) }
  };
  if (updateTime) write.currentDocument = { updateTime };
  else if (exists !== undefined) write.currentDocument = { exists };
  if (serverTimestamps.length) {
    write.updateTransforms = serverTimestamps.map((fieldPath) => ({
      fieldPath,
      setToServerValue: "REQUEST_TIME"
    }));
  }
  return write;
}

function timestampFrom(value) {
  if (typeof value !== "string") return null;
  const millis = Date.parse(value);
  return Number.isFinite(millis) ? millis : null;
}

async function serviceAccessToken(env) {
  if (!env.FIREBASE_SERVICE_ACCOUNT_JSON) {
    throw new ProfileApiError("PROFILE_BACKEND_NOT_CONFIGURED", 503, "El perfil aún no está configurado en el servidor.");
  }
  let account;
  try {
    account = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT_JSON);
  } catch {
    throw new ProfileApiError("PROFILE_BACKEND_NOT_CONFIGURED", 503, "La credencial del perfil no tiene un formato válido.");
  }
  if (account.project_id !== env.FIREBASE_PROJECT_ID || !account.client_email || !account.private_key) {
    throw new ProfileApiError("PROFILE_BACKEND_NOT_CONFIGURED", 503, "La credencial configurada no corresponde al proyecto Firebase.");
  }
  const nowSeconds = Math.floor(Date.now() / 1000);
  if (serviceTokenCache?.email === account.client_email && serviceTokenCache.expiry > nowSeconds + 60) {
    return serviceTokenCache.token;
  }

  const key = await importPKCS8(account.private_key, "RS256");
  const assertion = await new SignJWT({ scope: FIRESTORE_SCOPE })
    .setProtectedHeader({ alg: "RS256", typ: "JWT" })
    .setIssuer(account.client_email)
    .setAudience(account.token_uri || TOKEN_URL)
    .setIssuedAt(nowSeconds)
    .setExpirationTime(nowSeconds + 3600)
    .sign(key);
  const response = await fetch(account.token_uri || TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion
    })
  });
  const result = await response.json().catch(() => ({}));
  if (!response.ok || typeof result.access_token !== "string") {
    throw new ProfileApiError("PROFILE_BACKEND_UNAVAILABLE", 503, "No se pudo conectar con Firestore.");
  }
  serviceTokenCache = {
    email: account.client_email,
    token: result.access_token,
    expiry: nowSeconds + Number(result.expires_in || 3600)
  };
  return result.access_token;
}

async function firestoreRequest(env, path, accessToken, body) {
  const response = await fetch(`${FIRESTORE_URL}/${path}`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify(body)
  });
  const text = await response.text();
  let result = {};
  if (text) {
    try { result = JSON.parse(text); } catch { /* batchGet is handled separately */ }
  }
  if (!response.ok) {
    const status = result.error?.status;
    const operation = path.slice(path.lastIndexOf(":") + 1);
    console.error("firestore-rest", operation, response.status, status || "UNKNOWN");
    const transactionConflict = status === "ABORTED" || status === "ALREADY_EXISTS";
    throw new ProfileApiError(
      transactionConflict ? "TRANSACTION_RETRY" : "FIRESTORE_UNAVAILABLE",
      transactionConflict ? 409 : 503,
      "No se pudo guardar el perfil. Inténtalo de nuevo."
    );
  }
  return result;
}

async function beginTransaction(env, token) {
  const result = await firestoreRequest(env, `projects/${env.FIREBASE_PROJECT_ID}/databases/(default)/documents:beginTransaction`, token, {
    options: { readWrite: {} }
  });
  return result.transaction;
}

async function batchGet(env, token, transaction, names) {
  if (names.length === 0) return new Map();
  const response = await fetch(`${FIRESTORE_URL}/projects/${env.FIREBASE_PROJECT_ID}/databases/(default)/documents:batchGet`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({ documents: names, transaction })
  });
  const text = await response.text();
  if (!response.ok) {
    let errorStatus = "UNKNOWN";
    try { errorStatus = JSON.parse(text).error?.status || errorStatus; } catch { /* response may be plain text */ }
    console.error("firestore-rest-batch", response.status, errorStatus);
    throw new ProfileApiError("FIRESTORE_UNAVAILABLE", 503, "No se pudo leer el perfil.");
  }
  const docs = new Map();
  const addResult = (result) => {
    if (result.found?.name) {
      docs.set(result.found.name, result.found);
    } else if (result.missing) {
      docs.set(result.missing, null);
    }
  };
  let parsedBody;
  try { parsedBody = JSON.parse(text.trim()); } catch { /* Firestore may stream one JSON object per line. */ }
  if (Array.isArray(parsedBody)) {
    parsedBody.forEach(addResult);
  } else if (parsedBody && (parsedBody.found || parsedBody.missing)) {
    addResult(parsedBody);
  } else {
    for (const line of text.split(/\r?\n/).filter((value) => value.trim())) {
      let result;
      try { result = JSON.parse(line); } catch { continue; }
      if (Array.isArray(result)) result.forEach(addResult);
      else addResult(result);
    }
  }
  return docs;
}

async function commit(env, token, transaction, writes) {
  if (writes.length === 0) return;
  try {
    await firestoreRequest(env, `projects/${env.FIREBASE_PROJECT_ID}/databases/(default)/documents:commit`, token, {
      writes,
      transaction
    });
  } catch (error) {
    if (error instanceof ProfileApiError && error.code === "TRANSACTION_RETRY") {
      const targets = writes.map((write) => {
        const name = write.update?.name ?? write.delete ?? "";
        const relative = name.split("/documents/")[1] ?? "";
        const collection = relative.split("/")[0] || "UNKNOWN";
        const action = write.delete ? "delete" : write.currentDocument?.exists === false ? "create" : "update";
        return `${collection}:${action}`;
      });
      console.error("firestore-commit-conflict-targets", targets.join(","));
    }
    throw error;
  }
}

async function rollback(env, token, transaction) {
  try {
    await firestoreRequest(env, `projects/${env.FIREBASE_PROJECT_ID}/databases/(default)/documents:rollback`, token, { transaction });
  } catch { /* Firestore expires idle transactions automatically. */ }
}

function fieldExists(document, key) {
  return Object.hasOwn(document?.fields ?? {}, key);
}

function applyBaseDefaults(document, uid, email) {
  const base = profileBase(uid, email);
  const patch = {};
  for (const [key, value] of Object.entries(base)) {
    if (!fieldExists(document, key)) patch[key] = value;
  }
  return patch;
}

function reservationOwner(document) {
  return readField(document, "uid");
}

function nicknameError(code) {
  const messages = {
    NICKNAME_TAKEN: "Ese apodo ya está en uso.",
    NICKNAME_MIGRATION_CONFLICT: "No se pudo recuperar tu apodo actual. Contacta con soporte.",
    NICKNAME_COOLDOWN: "Aún no puedes cambiar tu apodo.",
    NICKNAME_RESERVATION_INCONSISTENT: "No se pudo actualizar la reserva. Inténtalo de nuevo."
  };
  return new ProfileApiError(code, code === "NICKNAME_COOLDOWN" ? 423 : 409, messages[code] || "El apodo no es válido.");
}

function profileFromDocument(document, now) {
  return decodeProfile(document, now);
}

async function handleProfileApiOnce(request, env, uid, email = "") {
  const url = new URL(request.url);
  const token = await serviceAccessToken(env);
  const userDocumentName = docName(env.FIREBASE_PROJECT_ID, "Usuarios", uid);

  if (request.method === "GET" && url.pathname === "/api/profile/me") {
    const transaction = await beginTransaction(env, token);
    try {
      const read = await batchGet(env, token, transaction, [userDocumentName]);
      const userDocument = read.get(userDocumentName) ?? null;
      if (!userDocument) {
        const base = profileBase(uid, email);
        await commit(env, token, transaction, [
          updateWrite(userDocumentName, base, { exists: false, serverTimestamps: ["creadoEn"] })
        ]);
        return profileFromDocument({ fields: encodeFields(base) }, Date.now());
      }

      const patch = applyBaseDefaults(userDocument, uid, email);
      if (!fieldExists(userDocument, "creadoEn")) {
        delete patch.creadoEn;
      }
      const checked = validateNickname(readField(userDocument, "apodo") ?? "");
      let reservationDocument = null;
      let reservationName = null;
      if (checked.ok) {
        reservationName = docName(env.FIREBASE_PROJECT_ID, "Apodos", checked.normalized);
        reservationDocument = (await batchGet(env, token, transaction, [reservationName])).get(reservationName) ?? null;
        if (reservationDocument && reservationOwner(reservationDocument) !== uid) {
          throw nicknameError("NICKNAME_MIGRATION_CONFLICT");
        }
        if (!reservationDocument) {
          const reserveFields = { uid, apodo: checked.nickname };
          const reservedWrite = updateWrite(reservationName, reserveFields, {
            exists: false,
            serverTimestamps: ["reservadoEn"]
          });
          await commit(env, token, transaction, [
            ...(userDocument.updateTime && Object.keys(patch).length ? [updateWrite(userDocumentName, patch, { updateTime: userDocument.updateTime })] : []),
            reservedWrite
          ]);
          return profileFromDocument({ ...userDocument, fields: { ...userDocument.fields, ...encodeFields(patch), apodoNormalizado: encodeField(checked.normalized) } }, Date.now());
        }
        if (readField(userDocument, "apodoNormalizado") !== checked.normalized) patch.apodoNormalizado = checked.normalized;
      }

      if (Object.keys(patch).length) {
        await commit(env, token, transaction, [updateWrite(userDocumentName, patch, { updateTime: userDocument.updateTime })]);
      } else {
        await rollback(env, token, transaction);
      }
      const refreshed = { ...userDocument, fields: { ...userDocument.fields, ...encodeFields(patch) } };
      if (checked.ok) refreshed.fields.apodoNormalizado = encodeField(checked.normalized);
      return profileFromDocument(refreshed, Date.now());
    } catch (error) {
      await rollback(env, token, transaction);
      throw error;
    }
  }

  if (request.method === "GET" && url.pathname === "/api/nicknames/availability") {
    const checked = validateNickname(url.searchParams.get("nickname"));
    if (!checked.ok) {
      throw new ProfileApiError(checked.code, 400, getNicknamePolicyMessage(checked.code) || "El apodo no es válido.");
    }
    const transaction = await beginTransaction(env, token);
    try {
      const reservationName = docName(env.FIREBASE_PROJECT_ID, "Apodos", checked.normalized);
      const document = (await batchGet(env, token, transaction, [reservationName])).get(reservationName) ?? null;
      await rollback(env, token, transaction);
      return { available: !document || reservationOwner(document) === uid };
    } catch (error) {
      await rollback(env, token, transaction);
      throw error;
    }
  }

  if (request.method === "PUT" && url.pathname === "/api/profile/nickname") {
    let body;
    try { body = await request.json(); } catch { throw new ProfileApiError("INVALID_JSON", 400, "La solicitud no tiene un formato válido."); }
    const checked = validateNickname(body?.nickname);
    if (!checked.ok) {
      throw new ProfileApiError(checked.code, 400, getNicknamePolicyMessage(checked.code) || "El apodo no es válido.");
    }

    const transaction = await beginTransaction(env, token);
    try {
      const userRead = await batchGet(env, token, transaction, [userDocumentName]);
      const userDocument = userRead.get(userDocumentName) ?? null;
      const existingFields = userDocument?.fields ?? {};
      const oldRawNickname = readField(userDocument, "apodo") ?? "";
      const oldCheck = validateNickname(oldRawNickname);
      const oldNormalized = oldCheck.ok ? oldCheck.normalized : null;
      const lastChangedAt = timestampFrom(readField(userDocument, "fechaUltimoCambioApodo"));
      const targetName = docName(env.FIREBASE_PROJECT_ID, "Apodos", checked.normalized);
      const oldName = oldNormalized ? docName(env.FIREBASE_PROJECT_ID, "Apodos", oldNormalized) : null;
      const names = [...new Set([targetName, ...(oldName && oldName !== targetName ? [oldName] : [])])];
      const reservations = await batchGet(env, token, transaction, names);
      const targetReservation = reservations.get(targetName) ?? null;
      const oldReservation = oldName && oldName !== targetName ? (reservations.get(oldName) ?? null) : null;

      if (targetReservation && reservationOwner(targetReservation) !== uid) throw nicknameError("NICKNAME_TAKEN");

      if (oldNormalized === checked.normalized) {
        const patch = {};
        if (!userDocument) Object.assign(patch, profileBase(uid, email));
        if (oldRawNickname !== oldCheck.nickname) patch.apodo = oldCheck.nickname;
        if (readField(userDocument, "apodoNormalizado") !== checked.normalized) patch.apodoNormalizado = checked.normalized;
        const writes = [];
        if (!targetReservation) writes.push(updateWrite(targetName, { uid, apodo: oldCheck.nickname }, { exists: false, serverTimestamps: ["reservadoEn"] }));
        if (Object.keys(patch).length) writes.push(updateWrite(userDocumentName, patch, { ...(userDocument ? { updateTime: userDocument.updateTime } : { exists: false }) }));
        await commit(env, token, transaction, writes);
        if (!writes.length) await rollback(env, token, transaction);
        const current = { ...(userDocument ?? {}), fields: { ...(userDocument?.fields ?? {}), ...encodeFields(patch) } };
        return profileFromDocument(current, Date.now());
      }

      const initialRegistration = oldNormalized === null && lastChangedAt === null;
      const now = Date.now();
      if (!initialRegistration && lastChangedAt !== null) {
        const availableAt = lastChangedAt + COOLDOWN_MS;
        if (now < availableAt) {
          throw new ProfileApiError("NICKNAME_COOLDOWN", 423, "Aún no puedes cambiar tu apodo.", {
            remainingMs: availableAt - now,
            availableAt: new Date(availableAt).toISOString()
          });
        }
      }
      if (oldReservation && reservationOwner(oldReservation) !== uid) throw nicknameError("NICKNAME_RESERVATION_INCONSISTENT");

      const patch = {
        ...(!userDocument ? profileBase(uid, email) : {}),
        id: uid,
        gmail: readField(userDocument, "gmail") ?? email ?? "",
        nombreUsuario: readField(userDocument, "nombreUsuario") ?? "",
        apodo: checked.nickname,
        apodoNormalizado: checked.normalized,
        fechaUltimoCambioApodo: initialRegistration ? null : null,
        monedas: readField(userDocument, "monedas") ?? 0,
        experiencia: readField(userDocument, "experiencia") ?? 0,
        nivel: readField(userDocument, "nivel") ?? 0
      };
      const writes = [
        updateWrite(targetName, { uid, apodo: checked.nickname }, {
          ...(targetReservation ? { updateTime: targetReservation.updateTime } : { exists: false }),
          serverTimestamps: ["reservadoEn"]
        }),
        updateWrite(userDocumentName, patch, {
          ...(userDocument ? { updateTime: userDocument.updateTime } : { exists: false }),
          serverTimestamps: initialRegistration ? [] : ["fechaUltimoCambioApodo"]
        })
      ];
      if (oldReservation && oldName && oldName !== targetName) writes.push({ delete: oldName });
      await commit(env, token, transaction, writes);

      const responseDocument = {
        fields: {
          ...existingFields,
          ...encodeFields(patch),
          ...(initialRegistration ? {} : { fechaUltimoCambioApodo: encodeField(new Date(now).toISOString()) })
        }
      };
      return decodeProfile(responseDocument, now);
    } catch (error) {
      await rollback(env, token, transaction);
      throw error;
    }
  }

  throw new ProfileApiError("NOT_FOUND", 404, "Ruta no encontrada.");
}

export async function handleProfileApi(request, env, uid, email = "") {
  const maxAttempts = 3;
  for (let attempt = 0; ; attempt += 1) {
    try {
      return await handleProfileApiOnce(request, env, uid, email);
    } catch (error) {
      if (!(error instanceof ProfileApiError) || error.code !== "TRANSACTION_RETRY" || attempt + 1 >= maxAttempts) {
        throw error;
      }
      await new Promise((resolve) => setTimeout(resolve, 30 * (attempt + 1)));
    }
  }
}

const PROFILE_MATCH_REWARD = Object.freeze({ victory: 250, draw: 100, defeat: 0 });

function nonNegativeCounter(value) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? Math.max(0, Math.floor(parsed)) : 0;
}

function matchRewardLedgerName(env, matchId) {
  if (typeof matchId !== "string" || !/^[A-Za-z0-9_-]{1,80}$/.test(matchId)) {
    throw new ProfileApiError("INVALID_MATCH_ID", 400, "El resultado de partida no es válido.");
  }
  return docName(env.FIREBASE_PROJECT_ID, "ResultadosPartida", matchId);
}

function readExistingMatchSettlement(document, players) {
  if (readField(document, "jugador1Uid") !== players[0].uid ||
      readField(document, "jugador2Uid") !== players[1].uid) {
    throw new ProfileApiError("MATCH_RESULT_CONFLICT", 409, "El resultado de esta partida ya está registrado.");
  }
  return {
    status: "SETTLED",
    awardsByUid: {
      [players[0].uid]: nonNegativeCounter(readField(document, "jugador1Recompensa")),
      [players[1].uid]: nonNegativeCounter(readField(document, "jugador2Recompensa"))
    },
    balancesByUid: {
      [players[0].uid]: nonNegativeCounter(readField(document, "jugador1Saldo")),
      [players[1].uid]: nonNegativeCounter(readField(document, "jugador2Saldo"))
    }
  };
}

/**
 * Reads public in-game names from profiles using the server credential. Names
 * supplied by Android are intentionally not accepted as authoritative here.
 */
export async function loadMatchNicknames(env, uids) {
  const uniqueUids = [...new Set(uids.filter((uid) => typeof uid === "string" && uid.length > 0))];
  const token = await serviceAccessToken(env);
  const transaction = await beginTransaction(env, token);
  try {
    const names = uniqueUids.map((uid) => docName(env.FIREBASE_PROJECT_ID, "Usuarios", uid));
    const documents = await batchGet(env, token, transaction, names);
    const result = Object.fromEntries(uniqueUids.map((uid, index) => {
      const document = documents.get(names[index]);
      const nickname = validateNickname(readField(document, "apodo") ?? "");
      return [uid, nickname.ok ? nickname.nickname : "Jugador"];
    }));
    await rollback(env, token, transaction);
    return result;
  } catch (error) {
    await rollback(env, token, transaction);
    throw error;
  }
}

async function settleMatchProfilesOnce(env, match) {
  const players = [...(match?.players ?? [])].sort((a, b) => a.playerNumber - b.playerNumber);
  const result = match?.game?.result;
  if (players.length !== 2 || players.some((player, index) => player.playerNumber !== index + 1 || !player.uid) ||
      players[0].uid === players[1].uid || !result ||
      (result.winnerPlayerNumber !== null && ![1, 2].includes(result.winnerPlayerNumber))) {
    throw new ProfileApiError("INVALID_MATCH_RESULT", 400, "No se pudo guardar el resultado de la partida.");
  }

  const token = await serviceAccessToken(env);
  const transaction = await beginTransaction(env, token);
  const ledgerName = matchRewardLedgerName(env, match.matchId);
  const userNames = players.map((player) => docName(env.FIREBASE_PROJECT_ID, "Usuarios", player.uid));
  try {
    const documents = await batchGet(env, token, transaction, [ledgerName, ...userNames]);
    const existingLedger = documents.get(ledgerName);
    if (existingLedger) {
      const settlement = readExistingMatchSettlement(existingLedger, players);
      await rollback(env, token, transaction);
      return settlement;
    }

    const rewards = players.map((player) => result.winnerPlayerNumber === null
      ? PROFILE_MATCH_REWARD.draw
      : result.winnerPlayerNumber === player.playerNumber
        ? PROFILE_MATCH_REWARD.victory
        : PROFILE_MATCH_REWARD.defeat);
    const balances = [];
    const writes = [];
    for (let index = 0; index < players.length; index += 1) {
      const player = players[index];
      const userDocument = documents.get(userNames[index]) ?? null;
      const fields = {
        ...(!userDocument ? profileBase(player.uid, "") : {}),
        monedas: nonNegativeCounter(readField(userDocument, "monedas")) + rewards[index],
        partidasCompletadas: nonNegativeCounter(readField(userDocument, "partidasCompletadas")) + 1,
        victorias: nonNegativeCounter(readField(userDocument, "victorias")) + Number(result.winnerPlayerNumber === player.playerNumber),
        derrotas: nonNegativeCounter(readField(userDocument, "derrotas")) + Number(result.winnerPlayerNumber !== null && result.winnerPlayerNumber !== player.playerNumber),
        empates: nonNegativeCounter(readField(userDocument, "empates")) + Number(result.winnerPlayerNumber === null)
      };
      balances.push(fields.monedas);
      writes.push(updateWrite(userNames[index], fields, userDocument
        ? { updateTime: userDocument.updateTime }
        : { exists: false, serverTimestamps: ["creadoEn"] }));
    }

    writes.push(updateWrite(ledgerName, {
      matchId: match.matchId,
      jugador1Uid: players[0].uid,
      jugador2Uid: players[1].uid,
      ganadorNumero: result.winnerPlayerNumber,
      motivo: result.reason,
      duracionMs: result.elapsedMatchMs,
      jugador1Recompensa: rewards[0],
      jugador2Recompensa: rewards[1],
      jugador1Saldo: balances[0],
      jugador2Saldo: balances[1]
    }, { exists: false, serverTimestamps: ["creadoEn"] }));
    await commit(env, token, transaction, writes);
    return {
      status: "SETTLED",
      awardsByUid: { [players[0].uid]: rewards[0], [players[1].uid]: rewards[1] },
      balancesByUid: { [players[0].uid]: balances[0], [players[1].uid]: balances[1] }
    };
  } catch (error) {
    await rollback(env, token, transaction);
    throw error;
  }
}

/**
 * Atomically updates both profiles and creates a per-match ledger document.
 * The ledger makes retries safe if the Worker is interrupted after Firestore
 * commits but before the Durable Object stores its settlement state.
 */
export async function settleMatchProfiles(env, match) {
  for (let attempt = 0; ; attempt += 1) {
    try {
      return await settleMatchProfilesOnce(env, match);
    } catch (error) {
      if (!(error instanceof ProfileApiError) || error.code !== "TRANSACTION_RETRY" || attempt >= 2) throw error;
      await new Promise((resolve) => setTimeout(resolve, 30 * (attempt + 1)));
    }
  }
}
