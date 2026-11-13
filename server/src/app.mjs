import express from "express";
import { getNicknamePolicyMessage } from "./nickname-policy.mjs";
import { createNicknameService, NicknameServiceError } from "./nickname-service.mjs";

function sendServiceError(res, error) {
  if (!(error instanceof NicknameServiceError)) return false;
  const status = error.code === "NICKNAME_TAKEN" || error.code === "NICKNAME_MIGRATION_CONFLICT" ? 409
    : error.code === "NICKNAME_COOLDOWN" ? 423
    : error.code === "NICKNAME_RESERVATION_INCONSISTENT" ? 503
    : 400;
  const message = getNicknamePolicyMessage(error.code)
    || ({
      NICKNAME_TAKEN: "Ese apodo ya está en uso.",
      NICKNAME_MIGRATION_CONFLICT: "No se pudo recuperar tu apodo actual. Contacta con soporte.",
      NICKNAME_COOLDOWN: "Aún no puedes cambiar tu apodo.",
      NICKNAME_RESERVATION_INCONSISTENT: "No se pudo actualizar la reserva. Inténtalo de nuevo."
    }[error.code] ?? "No se pudo procesar el apodo.");
  res.status(status).json({ error: error.code, message, ...error.details });
  return true;
}

export function createApp({ auth, db, serverTimestamp, now = () => Date.now() }) {
  const app = express();
  const nicknames = createNicknameService({ db, serverTimestamp, now });
  app.disable("x-powered-by");
  app.use(express.json({ limit: "8kb" }));

  app.get("/health", (_req, res) => res.status(200).json({ status: "ok" }));

  app.use("/api", async (req, res, next) => {
    const header = req.get("authorization") ?? "";
    const match = /^Bearer\s+(.+)$/i.exec(header);
    if (!match) return res.status(401).json({ error: "AUTH_REQUIRED", message: "Inicia sesión para continuar." });
    try {
      req.authUser = await auth.verifyIdToken(match[1]);
      if (!req.authUser?.uid) return res.status(401).json({ error: "AUTH_INVALID", message: "La sesión no es válida." });
      next();
    } catch {
      res.status(401).json({ error: "AUTH_INVALID", message: "La sesión ha caducado. Vuelve a iniciar sesión." });
    }
  });

  app.get("/api/profile/me", async (req, res, next) => {
    try {
      res.json(await nicknames.getProfile(req.authUser.uid, req.authUser));
    } catch (error) {
      if (sendServiceError(res, error)) return;
      next(error);
    }
  });

  app.get("/api/nicknames/availability", async (req, res, next) => {
    try {
      const available = await nicknames.isAvailable(req.authUser.uid, req.query.nickname);
      res.json({ available });
    } catch (error) {
      if (sendServiceError(res, error)) return;
      next(error);
    }
  });

  app.put("/api/profile/nickname", async (req, res, next) => {
    try {
      const profile = await nicknames.setNickname(req.authUser.uid, req.authUser, req.body?.nickname);
      res.json(profile);
    } catch (error) {
      if (sendServiceError(res, error)) return;
      next(error);
    }
  });

  app.use((error, _req, res, _next) => {
    if (error instanceof SyntaxError && "body" in error) {
      return res.status(400).json({ error: "INVALID_JSON", message: "La solicitud no tiene un formato válido." });
    }
    console.error("[FortRush API] Internal request error", error?.message ?? "unknown");
    res.status(500).json({ error: "INTERNAL_ERROR", message: "No se pudo completar la operación. Inténtalo de nuevo." });
  });

  return app;
}
