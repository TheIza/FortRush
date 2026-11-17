import { createRemoteJWKSet, jwtVerify } from "jose";
import { handleProfileApi, ProfileApiError } from "./profile-api.mjs";
import { getNicknamePolicyMessage } from "../../src/nickname-policy.mjs";
import { generateSharedMap } from "./map-generator.mjs";

const FIREBASE_JWKS = createRemoteJWKSet(
  new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com")
);

async function verifyFirebaseIdentity(token, projectId) {
  const { payload } = await jwtVerify(token, FIREBASE_JWKS, {
    algorithms: ["RS256"],
    issuer: `https://securetoken.google.com/${projectId}`,
    audience: projectId
  });
  if (typeof payload.sub !== "string" || payload.sub.length === 0) {
    throw new Error("Firebase ID token has no UID");
  }
  return { uid: payload.sub, email: typeof payload.email === "string" ? payload.email : "" };
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === "/health") {
      return Response.json({
        status: "ok",
        service: "fortrush-cloudflare",
        profileApiConfigured: Boolean(env.FIREBASE_SERVICE_ACCOUNT_JSON)
      });
    }
    if (url.pathname.startsWith("/api/")) {
      const authorization = request.headers.get("Authorization") ?? "";
      const match = /^Bearer\s+(.+)$/i.exec(authorization);
      if (!match) return Response.json({ error: "AUTH_REQUIRED", message: "Inicia sesión para continuar." }, { status: 401 });
      let identity;
      try {
        identity = await verifyFirebaseIdentity(match[1], env.FIREBASE_PROJECT_ID);
      } catch {
        return Response.json({ error: "AUTH_INVALID", message: "La sesión no es válida." }, { status: 401 });
      }
      try {
        const result = await handleProfileApi(request, env, identity.uid, identity.email);
        console.log("profile-api", request.method, url.pathname, 200);
        return Response.json(result);
      } catch (error) {
        if (error instanceof ProfileApiError) {
          console.log("profile-api", request.method, url.pathname, error.status, error.code);
          return Response.json({
            error: error.code,
            message: error.message,
            ...error.details
          }, { status: error.status });
        }
        const policyMessage = getNicknamePolicyMessage(error?.code);
        if (policyMessage) {
          console.log("profile-api", request.method, url.pathname, 400, error.code);
          return Response.json({ error: error.code, message: policyMessage }, { status: 400 });
        }
        console.error("profile-api", request.method, url.pathname, 500, error?.name || "UnknownError");
        return Response.json({ error: "INTERNAL_ERROR", message: "No se pudo completar la operación. Inténtalo de nuevo." }, { status: 500 });
      }
    }
    if (url.pathname !== "/ws") return Response.json({ error: "NOT_FOUND" }, { status: 404 });
    if (request.headers.get("Upgrade")?.toLowerCase() !== "websocket") {
      return new Response("WebSocket upgrade required", { status: 426 });
    }
    const roomId = env.TEST_ROOM.idFromName("fort-rush-matchmaking-probe");
    return env.TEST_ROOM.get(roomId).fetch(request);
  }
};

function send(socket, type, data = {}) {
  if (socket.readyState === WebSocket.OPEN) socket.send(JSON.stringify({ type, ...data }));
}

export class WebSocketProbe {
  constructor(ctx, env) {
    this.ctx = ctx;
    this.env = env;
  }

  async fetch(request) {
    if (this.ctx.getWebSockets().length >= 2) {
      return Response.json({ error: "PROBE_FULL" }, { status: 429 });
    }
    const pair = new WebSocketPair();
    const client = pair[0];
    const server = pair[1];
    this.ctx.acceptWebSocket(server);
    server.serializeAttachment({ uid: null, queueSequence: null, matchId: null, playerNumber: null });
    return new Response(null, { status: 101, webSocket: client });
  }

  async webSocketMessage(socket, message) {
    if (typeof message !== "string" || new TextEncoder().encode(message).byteLength > 4096) {
      send(socket, "error", { code: "INVALID_MESSAGE", message: "Mensaje no válido." });
      return;
    }
    let payload;
    try { payload = JSON.parse(message); }
    catch {
      send(socket, "error", { code: "INVALID_MESSAGE", message: "Envía un mensaje JSON." });
      return;
    }

    const attachment = socket.deserializeAttachment() ?? {};
    if (!attachment.uid) {
      await this.authenticate(socket, attachment, payload);
      return;
    }
    if (payload.type === "queue.join") return this.joinQueue(socket, attachment, payload);
    if (payload.type === "queue.cancel") return this.cancelQueue(socket, attachment, payload);
    if (payload.type === "match.loaded") return this.markLoaded(socket, attachment, payload);
    send(socket, "error", { code: "UNKNOWN_MESSAGE", message: "Acción no reconocida." });
  }

  async authenticate(socket, attachment, payload) {
    if (payload.type !== "auth" || typeof payload.token !== "string") {
      send(socket, "error", { code: "AUTH_REQUIRED", message: "Inicia sesión para buscar partida." });
      socket.close(1008, "Authentication required");
      return;
    }
    try {
      const { uid } = await verifyFirebaseIdentity(payload.token, this.env.FIREBASE_PROJECT_ID);
      const duplicate = this.ctx.getWebSockets().some((other) => other !== socket && other.deserializeAttachment()?.uid === uid);
      if (duplicate) {
        send(socket, "error", { code: "ALREADY_CONNECTED", message: "Esta cuenta ya está conectada a la prueba." });
        socket.close(1008, "Duplicate account");
        return;
      }
      socket.serializeAttachment({ ...attachment, uid });
      send(socket, "auth.ok");
    } catch {
      send(socket, "error", { code: "AUTH_INVALID", message: "No se pudo validar tu sesión de Firebase." });
      socket.close(1008, "Invalid Firebase token");
    }
  }

  async joinQueue(socket, attachment, payload) {
    if (typeof payload.requestId !== "string" || payload.requestId.length > 80) {
      send(socket, "error", { code: "REQUEST_ID_REQUIRED", message: "Solicitud no válida." });
      return;
    }
    if (attachment.matchId) {
      send(socket, "error", { code: "ALREADY_MATCHED", requestId: payload.requestId, message: "Ya tienes una partida asignada." });
      return;
    }
    const queue = await this.ctx.storage.get("queue") ?? [];
    const existing = queue.find((entry) => entry.uid === attachment.uid);
    if (existing) {
      send(socket, "queue.waiting", { requestId: payload.requestId, position: queue.indexOf(existing) + 1 });
      return;
    }
    if (queue.length >= 2) {
      send(socket, "error", { code: "PROBE_FULL", requestId: payload.requestId, message: "La prueba está limitada a dos jugadores." });
      return;
    }
    const sequence = (await this.ctx.storage.get("queueSequence") ?? 0) + 1;
    await this.ctx.storage.put("queueSequence", sequence);
    queue.push({ uid: attachment.uid, sequence });
    queue.sort((a, b) => a.sequence - b.sequence);
    await this.ctx.storage.put("queue", queue);
    socket.serializeAttachment({ ...attachment, queueSequence: sequence });
    send(socket, "queue.waiting", { requestId: payload.requestId, position: queue.length });
    if (queue.length === 2) await this.pairQueue(queue);
  }

  async pairQueue(queue) {
    const socketsByUid = new Map(this.ctx.getWebSockets().map((socket) => [socket.deserializeAttachment()?.uid, socket]));
    const players = queue.map((entry) => ({ ...entry, socket: socketsByUid.get(entry.uid) }));
    if (players.some((player) => !player.socket)) {
      const active = players.filter((player) => player.socket);
      await this.ctx.storage.put("queue", active.map(({ uid, sequence }) => ({ uid, sequence })));
      return;
    }
    const match = {
      matchId: crypto.randomUUID(),
      phase: "LOADING",
      players: players.map(({ uid, sequence }, index) => ({ uid, sequence, playerNumber: index + 1, loaded: false })),
      map: generateSharedMap()
    };
    await this.ctx.storage.put("match", match);
    await this.ctx.storage.put("queue", []);
    players.forEach((player, index) => {
      const playerNumber = index + 1;
      player.socket.serializeAttachment({ ...player.socket.deserializeAttachment(), queueSequence: null, matchId: match.matchId, playerNumber });
      send(player.socket, "match.found", {
        matchId: match.matchId,
        playerNumber,
        opponentNickname: "Rival de prueba",
        map: match.map
      });
    });
  }

  async cancelQueue(socket, attachment, payload) {
    if (attachment.matchId) {
      send(socket, "error", { code: "CANCEL_NOT_ALLOWED", requestId: payload.requestId, message: "No puedes cancelar después de encontrar rival." });
      return;
    }
    const queue = await this.ctx.storage.get("queue") ?? [];
    const remaining = queue.filter((entry) => entry.uid !== attachment.uid);
    await this.ctx.storage.put("queue", remaining);
    socket.serializeAttachment({ ...attachment, queueSequence: null });
    send(socket, remaining.length < queue.length ? "queue.cancelled" : "queue.idle", { requestId: payload.requestId });
    this.notifyQueue(remaining);
  }

  async markLoaded(socket, attachment, payload) {
    const match = await this.ctx.storage.get("match");
    if (!match || match.matchId !== attachment.matchId || match.phase !== "LOADING") {
      send(socket, "error", { code: "MATCH_NOT_LOADING", requestId: payload.requestId, message: "La preparación ya no está activa." });
      return;
    }
    const player = match.players.find((entry) => entry.uid === attachment.uid);
    if (!player) return;
    player.loaded = true;
    await this.ctx.storage.put("match", match);
    send(socket, "match.loadAccepted", { requestId: payload.requestId, matchId: match.matchId });
    if (match.players.every((entry) => entry.loaded)) await this.startCountdown(match);
  }

  async startCountdown(match) {
    match.phase = "COUNTDOWN";
    match.countdown = 3;
    await this.ctx.storage.put("match", match);
    this.broadcast("match.countdown", { seconds: match.countdown });
    await this.ctx.storage.setAlarm(Date.now() + 1_000);
  }

  async alarm() {
    const match = await this.ctx.storage.get("match");
    if (!match || match.phase !== "COUNTDOWN") return;
    if (match.countdown > 1) {
      match.countdown -= 1;
      await this.ctx.storage.put("match", match);
      this.broadcast("match.countdown", { seconds: match.countdown });
      await this.ctx.storage.setAlarm(Date.now() + 1_000);
      return;
    }
    match.phase = "IN_MATCH";
    await this.ctx.storage.put("match", match);
    this.broadcast("match.started", { matchId: match.matchId });
  }

  async webSocketClose(socket) {
    const attachment = socket.deserializeAttachment() ?? {};
    if (attachment.queueSequence !== null && attachment.queueSequence !== undefined) {
      const queue = await this.ctx.storage.get("queue") ?? [];
      const remaining = queue.filter((entry) => entry.uid !== attachment.uid);
      await this.ctx.storage.put("queue", remaining);
      this.notifyQueue(remaining);
    }
    if (!attachment.matchId) return;
    const match = await this.ctx.storage.get("match");
    if (!match || match.matchId !== attachment.matchId || !["LOADING", "COUNTDOWN"].includes(match.phase)) return;
    const survivor = match.players.find((entry) => entry.uid !== attachment.uid);
    const survivorSocket = this.ctx.getWebSockets().find((candidate) => candidate.deserializeAttachment()?.uid === survivor?.uid);
    await this.ctx.storage.delete("match");
    await this.ctx.storage.deleteAlarm();
    if (survivor && survivorSocket) {
      survivorSocket.serializeAttachment({ ...survivorSocket.deserializeAttachment(), matchId: null, playerNumber: null, queueSequence: survivor.sequence });
      await this.ctx.storage.put("queue", [{ uid: survivor.uid, sequence: survivor.sequence }]);
      send(survivorSocket, "match.cancelled", { reason: "OPPONENT_DISCONNECTED", requeued: true });
      send(survivorSocket, "queue.waiting", { position: 1 });
    }
  }

  notifyQueue(queue) {
    const sockets = new Map(this.ctx.getWebSockets().map((socket) => [socket.deserializeAttachment()?.uid, socket]));
    queue.forEach((entry, index) => send(sockets.get(entry.uid), "queue.waiting", { position: index + 1 }));
  }

  broadcast(type, data = {}) {
    this.ctx.getWebSockets().forEach((socket) => send(socket, type, data));
  }
}
