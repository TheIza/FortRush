export const MIN_NICKNAME_CODE_POINTS = 3;
export const MAX_NICKNAME_CODE_POINTS = 16;

// Policy shared with Android: NFC, Latin basic + Spanish accented vowels/ñ/ü,
// ASCII digits and underscore. Other Unicode letters and combining marks are rejected.
const ALLOWED_NICKNAME = /^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ0-9_]+$/u;

export function validateNickname(input) {
  if (typeof input !== "string") return { ok: false, code: "NICKNAME_REQUIRED" };

  const nickname = input.normalize("NFC");
  const codePoints = Array.from(nickname).length;
  if (codePoints < MIN_NICKNAME_CODE_POINTS) {
    return { ok: false, code: "NICKNAME_TOO_SHORT" };
  }
  if (codePoints > MAX_NICKNAME_CODE_POINTS) {
    return { ok: false, code: "NICKNAME_TOO_LONG" };
  }
  if (!ALLOWED_NICKNAME.test(nickname)) {
    return { ok: false, code: "NICKNAME_INVALID_CHARACTERS" };
  }

  return {
    ok: true,
    nickname,
    normalized: nickname.toLowerCase()
  };
}

export function getNicknamePolicyMessage(code) {
  switch (code) {
    case "NICKNAME_REQUIRED": return "Escribe un apodo.";
    case "NICKNAME_TOO_SHORT": return "El apodo debe tener al menos 3 caracteres.";
    case "NICKNAME_TOO_LONG": return "El apodo no puede superar los 16 caracteres.";
    case "NICKNAME_INVALID_CHARACTERS":
      return "Usa letras latinas, tildes españolas, ñ, ü, números o guion bajo.";
    default: return null;
  }
}
