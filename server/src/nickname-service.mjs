import {
  MAX_NICKNAME_CODE_POINTS,
  MIN_NICKNAME_CODE_POINTS,
  validateNickname
} from "./nickname-policy.mjs";

export const NICKNAME_COOLDOWN_MS = 7 * 24 * 60 * 60 * 1000;
const PROFILE_COLLECTION = "Usuarios";
const NICKNAME_COLLECTION = "Apodos";

export class NicknameServiceError extends Error {
  constructor(code, details = {}) {
    super(code);
    this.name = "NicknameServiceError";
    this.code = code;
    this.details = details;
  }
}

function asMillis(value) {
  if (!value) return null;
  if (typeof value.toMillis === "function") return value.toMillis();
  if (value instanceof Date) return value.getTime();
  if (typeof value === "number") return value;
  return null;
}

function baseProfile(uid, authUser, serverTimestamp) {
  return {
    id: uid,
    gmail: authUser.email ?? "",
    nombreUsuario: "",
    apodo: "",
    apodoNormalizado: "",
    fechaUltimoCambioApodo: null,
    monedas: 0,
    experiencia: 0,
    nivel: 0,
    creadoEn: serverTimestamp()
  };
}

function publicProfile(data, now) {
  const checked = validateNickname(data.apodo ?? "");
  const nickname = checked.ok ? checked.nickname : null;
  const normalized = nickname ? checked.normalized : null;
  const lastChangedAt = asMillis(data.fechaUltimoCambioApodo);
  const cooldownEndsAt = lastChangedAt === null ? null : lastChangedAt + NICKNAME_COOLDOWN_MS;
  return {
    nickname,
    nicknameNormalized: normalized,
    lastNicknameChangeAt: lastChangedAt === null ? null : new Date(lastChangedAt).toISOString(),
    nicknameChangeRemainingMs: cooldownEndsAt === null ? 0 : Math.max(0, cooldownEndsAt - now()),
    nicknameChangeAvailableAt: cooldownEndsAt === null ? null : new Date(cooldownEndsAt).toISOString()
  };
}

export function createNicknameService({ db, serverTimestamp, now = () => Date.now() }) {
  const users = db.collection(PROFILE_COLLECTION);
  const nicknames = db.collection(NICKNAME_COLLECTION);

  async function getProfile(uid, authUser = {}) {
    const profileRef = users.doc(uid);
    return db.runTransaction(async (transaction) => {
      const profileSnapshot = await transaction.get(profileRef);
      if (!profileSnapshot.exists) {
        const data = baseProfile(uid, authUser, serverTimestamp);
        transaction.set(profileRef, data);
        return publicProfile(data, now);
      }

      const data = profileSnapshot.data() ?? {};
      const patch = {};
      if (!Object.hasOwn(data, "id")) patch.id = uid;
      if (!Object.hasOwn(data, "gmail")) patch.gmail = authUser.email ?? "";
      if (!Object.hasOwn(data, "nombreUsuario")) patch.nombreUsuario = "";
      if (!Object.hasOwn(data, "apodo")) patch.apodo = "";
      if (!Object.hasOwn(data, "apodoNormalizado")) patch.apodoNormalizado = "";
      if (!Object.hasOwn(data, "fechaUltimoCambioApodo")) patch.fechaUltimoCambioApodo = null;
      if (!Object.hasOwn(data, "monedas")) patch.monedas = 0;
      if (!Object.hasOwn(data, "experiencia")) patch.experiencia = 0;
      if (!Object.hasOwn(data, "nivel")) patch.nivel = 0;

      const checked = validateNickname(data.apodo ?? "");
      if (checked.ok) {
        const nicknameRef = nicknames.doc(checked.normalized);
        const nicknameSnapshot = await transaction.get(nicknameRef);
        if (nicknameSnapshot.exists && nicknameSnapshot.get("uid") !== uid) {
          throw new NicknameServiceError("NICKNAME_MIGRATION_CONFLICT");
        }
        if (!nicknameSnapshot.exists) {
          transaction.set(nicknameRef, {
            uid,
            apodo: checked.nickname,
            reservadoEn: serverTimestamp()
          });
        }
        if (data.apodoNormalizado !== checked.normalized) {
          patch.apodoNormalizado = checked.normalized;
        }
      }

      if (Object.keys(patch).length > 0) transaction.set(profileRef, patch, { merge: true });
      return publicProfile({ ...data, ...patch }, now);
    });
  }

  async function isAvailable(uid, input) {
    const checked = validateNickname(input);
    if (!checked.ok) throw new NicknameServiceError(checked.code);
    const reservation = await nicknames.doc(checked.normalized).get();
    return !reservation.exists || reservation.get("uid") === uid;
  }

  async function setNickname(uid, authUser = {}, input) {
    const checked = validateNickname(input);
    if (!checked.ok) throw new NicknameServiceError(checked.code);

    const profileRef = users.doc(uid);
    const nextNicknameRef = nicknames.doc(checked.normalized);

    return db.runTransaction(async (transaction) => {
      const profileSnapshot = await transaction.get(profileRef);
      const existing = profileSnapshot.exists ? profileSnapshot.data() : baseProfile(uid, authUser, serverTimestamp);
      const oldCheck = validateNickname(existing.apodo ?? "");
      const oldNormalized = oldCheck.ok ? oldCheck.normalized : null;
      const lastChangedAt = asMillis(existing.fechaUltimoCambioApodo);

      if (oldNormalized === checked.normalized) {
        const oldReservation = await transaction.get(nextNicknameRef);
        if (oldReservation.exists && oldReservation.get("uid") !== uid) {
          throw new NicknameServiceError("NICKNAME_TAKEN");
        }
        const patch = {};
        if (!profileSnapshot.exists) Object.assign(patch, baseProfile(uid, authUser, serverTimestamp));
        if (existing.apodo !== oldCheck.nickname) patch.apodo = oldCheck.nickname;
        if (existing.apodoNormalizado !== checked.normalized) patch.apodoNormalizado = checked.normalized;
        if (!oldReservation.exists) {
          transaction.set(nextNicknameRef, { uid, apodo: oldCheck.nickname, reservadoEn: serverTimestamp() });
        }
        if (Object.keys(patch).length > 0) transaction.set(profileRef, patch, { merge: true });
        return publicProfile({ ...existing, ...patch }, now);
      }

      const oldNicknameRef = oldNormalized ? nicknames.doc(oldNormalized) : null;
      const [nextSnapshot, oldSnapshot] = await Promise.all([
        transaction.get(nextNicknameRef),
        oldNicknameRef && oldNormalized !== checked.normalized
          ? transaction.get(oldNicknameRef)
          : Promise.resolve(null)
      ]);

      if (nextSnapshot.exists && nextSnapshot.get("uid") !== uid) {
        throw new NicknameServiceError("NICKNAME_TAKEN");
      }

      const isInitialRegistration = oldNormalized === null && lastChangedAt === null;
      const serverNow = now();
      if (!isInitialRegistration && lastChangedAt !== null) {
        const eligibleAt = lastChangedAt + NICKNAME_COOLDOWN_MS;
        if (serverNow < eligibleAt) {
          throw new NicknameServiceError("NICKNAME_COOLDOWN", {
            remainingMs: eligibleAt - serverNow,
            availableAt: new Date(eligibleAt).toISOString()
          });
        }
      }

      if (oldSnapshot?.exists && oldSnapshot.get("uid") !== uid) {
        throw new NicknameServiceError("NICKNAME_RESERVATION_INCONSISTENT");
      }

      // Preserve an invalid legacy value instead of silently discarding user data.
      const legacyNickname = existing.apodo && !oldCheck.ok
        ? (existing.apodoLegacy ?? existing.apodo)
        : existing.apodoLegacy;
      const patch = {
        id: uid,
        gmail: existing.gmail ?? authUser.email ?? "",
        nombreUsuario: existing.nombreUsuario ?? "",
        apodo: checked.nickname,
        apodoNormalizado: checked.normalized,
        fechaUltimoCambioApodo: isInitialRegistration ? null : serverTimestamp(),
        monedas: existing.monedas ?? 0,
        experiencia: existing.experiencia ?? 0,
        nivel: existing.nivel ?? 0
      };
      if (legacyNickname) patch.apodoLegacy = legacyNickname;

      transaction.set(nextNicknameRef, {
        uid,
        apodo: checked.nickname,
        reservadoEn: serverTimestamp()
      });
      if (oldSnapshot?.exists && oldNormalized !== checked.normalized) {
        transaction.delete(oldNicknameRef);
      }
      transaction.set(profileRef, patch, { merge: true });
      return publicProfile({
        ...existing,
        ...patch,
        // Return the server's commit-time approximation to the caller; Firestore stores
        // the authoritative server timestamp in the same atomic transaction.
        fechaUltimoCambioApodo: isInitialRegistration ? null : new Date(serverNow)
      }, () => serverNow);
    });
  }

  return { getProfile, isAvailable, setNickname };
}
