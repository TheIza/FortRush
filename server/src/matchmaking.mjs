import { randomUUID } from "node:crypto";
import { WebSocketServer, WebSocket } from "ws";
import { validateNickname } from "./nickname-policy.mjs";
import { generateSharedMap } from "./map-generator.mjs";

const AUTH_TIMEOUT_MS = 8_000;
const MAP_LOAD_TIMEOUT_MS = 30_000;
const COUNTDOWN_SECONDS = 3;
const HEARTBEAT_MS = 25_000;
const OPEN = WebSocket.OPEN;

function send(socket, type, payload = {}) {
  if (socket.readyState === OPEN) socket.send(JSON.stringify({ type, ...payload }));
}

export function attachMatchmaking(httpServer, { auth, db, logger = console }) {
  const webSockets = new WebSocketServer({ noServer: true, maxPayload: 8 * 1024 });
  const queue = [];
  const sessions = new Map();
  const matches = new Map();
  let nextSequence = 0;
  const heartbeatTimer = setInterval(() => {
    for (const session of sessions.values()) send(session.socket, "heartbeat");
  }, HEARTBEAT_MS);

  function queuePosition(uid) {
    return queue.findIndex((entry) => entry.uid === uid) + 1;
  }

  function notifyQueue() {
    queue.forEach((entry, index) => send(entry.socket, "queue.waiting", { position: index + 1 }));
  }

  function insertAtOriginalPosition(entry) {
    if (!entry.socket || entry.socket.readyState !== OPEN || sessions.get(entry.uid)?.socket !== entry.socket) return;
    if (queue.some((candidate) => candidate.uid === entry.uid)) return;
    queue.push(entry);
    queue.sort((a, b) => a.sequence - b.sequence);
    entry.matchId = null;
    entry.loaded = false;
    const session = sessions.get(entry.uid);
    if (session) session.matchId = null;
    notifyQueue();
    pairNext();
  }

  function clearMatch(match) {
    clearTimeout(match.loadTimer);
    clearInterval(match.countdownTimer);
    matches.delete(match.id);
    for (const entry of match.players) {
      if (sessions.get(entry.uid)?.socket === entry.socket) {
        entry.matchId = null;
        entry.loaded = false;
        sessions.get(entry.uid).matchId = null;
      }
    }
  }

  function cancelPreparation(match, reason, missingUid = null, preferredSurvivorUid = null) {
    if (!matches.has(match.id) || match.phase !== "LOADING") return;
    const survivor = preferredSurvivorUid
      ? match.players.find((entry) => entry.uid === preferredSurvivorUid)
      : match.players.find((entry) => entry.uid !== missingUid);
    for (const entry of match.players) {
      send(entry.socket, "match.cancelled", { reason, requeued: entry === survivor });
    }
    clearMatch(match);
    if (survivor) insertAtOriginalPosition(survivor);
    notifyQueue();
  }

  function startCountdown(match) {
    if (!matches.has(match.id) || match.phase !== "LOADING" || match.players.some((entry) => !entry.loaded)) return;
    clearTimeout(match.loadTimer);
    match.phase = "COUNTDOWN";
    let remaining = COUNTDOWN_SECONDS;
    for (const entry of match.players) send(entry.socket, "match.countdown", { seconds: remaining });
    match.countdownTimer = setInterval(() => {
      remaining -= 1;
      if (remaining > 0) {
        for (const entry of match.players) send(entry.socket, "match.countdown", { seconds: remaining });
      } else {
        clearInterval(match.countdownTimer);
        match.phase = "IN_MATCH";
        for (const entry of match.players) send(entry.socket, "match.started", { matchId: match.id });
      }
    }, 1_000);
  }

  function pairNext() {
    while (queue.length >= 2) {
      const first = queue.shift();
      const second = queue.shift();
      if (first.socket.readyState !== OPEN || second.socket.readyState !== OPEN) continue;
      const id = randomUUID();
      const map = generateSharedMap();
      const match = { id, phase: "LOADING", players: [first, second], map };
      for (const entry of match.players) {
        entry.matchId = id;
        entry.loaded = false;
        const session = sessions.get(entry.uid);
        if (session) session.matchId = id;
      }
      matches.set(id, match);
      first.socket.send(JSON.stringify({ type: "match.found", matchId: id, playerNumber: 1, opponentNickname: second.nickname, map }));
      second.socket.send(JSON.stringify({ type: "match.found", matchId: id, playerNumber: 2, opponentNickname: first.nickname, map }));
      match.loadTimer = setTimeout(() => {
        const notLoaded = match.players.find((entry) => !entry.loaded);
        const loaded = match.players.find((entry) => entry.loaded);
        cancelPreparation(match, "MAP_LOAD_TIMEOUT", notLoaded?.uid ?? null, loaded?.uid ?? null);
      }, MAP_LOAD_TIMEOUT_MS);
    }
    notifyQueue();
  }

  function removeQueued(session) {
    const index = queue.findIndex((entry) => entry.uid === session.uid);
    if (index < 0) return false;
    queue.splice(index, 1);
    notifyQueue();
    return true;
  }

  async function onMessage(session, raw) {
    let message;
    try { message = JSON.parse(raw.toString()); }
    catch { send(session.socket, "error", { code: "INVALID_MESSAGE", message: "Mensaje no válido." }); return; }
    if (message.type === "heartbeat.ack") return;
    if (!session.uid) {
      if (message.type !== "auth" || typeof message.token !== "string") {
        send(session.socket, "error", { code: "AUTH_REQUIRED", message: "Inicia sesión para buscar partida." });
        session.socket.close(1008, "Authentication required");
        return;
      }
      try {
        const decoded = await auth.verifyIdToken(message.token);
        const uid = decoded?.uid;
        if (!uid) throw new Error("No UID");
        const profile = await db.collection("Usuarios").doc(uid).get();
        const validNickname = validateNickname(profile.get("apodo") ?? "");
        if (!validNickname.ok) {
          send(session.socket, "error", { code: "NICKNAME_REQUIRED", message: "Completa tu apodo antes de buscar partida." });
          session.socket.close(1008, "Nickname required");
          return;
        }
        if (sessions.has(uid)) {
          send(session.socket, "error", { code: "ALREADY_CONNECTED", message: "Ya hay una conexión buscando con esta cuenta." });
          session.socket.close(1008, "Already connected");
          return;
        }
        session.uid = uid;
        session.nickname = validNickname.nickname;
        clearTimeout(session.authTimer);
        sessions.set(uid, session);
        send(session.socket, "auth.ok", { nickname: session.nickname });
      } catch {
        send(session.socket, "error", { code: "AUTH_INVALID", message: "No se pudo validar tu sesión. Vuelve a iniciar sesión." });
        session.socket.close(1008, "Invalid token");
      }
      return;
    }

    if (typeof message.requestId !== "string" || message.requestId.length > 80) {
      send(session.socket, "error", { code: "REQUEST_ID_REQUIRED", message: "Solicitud no válida." });
      return;
    }
    if (message.type === "queue.join") {
      if (session.matchId) {
        send(session.socket, "error", { code: "ALREADY_MATCHED", requestId: message.requestId, message: "Ya tienes una partida asignada." });
        return;
      }
      const existing = queue.find((entry) => entry.uid === session.uid);
      if (existing) {
        send(session.socket, "queue.waiting", { requestId: message.requestId, position: queuePosition(session.uid) });
        return;
      }
      const entry = { uid: session.uid, nickname: session.nickname, socket: session.socket, sequence: nextSequence++, loaded: false, matchId: null };
      session.matchId = null;
      queue.push(entry);
      send(session.socket, "queue.waiting", { requestId: message.requestId, position: queue.length });
      pairNext();
      return;
    }
    if (message.type === "queue.cancel") {
      if (session.matchId) {
        send(session.socket, "error", { code: "CANCEL_NOT_ALLOWED", requestId: message.requestId, message: "No se puede cancelar después de encontrar pareja." });
        return;
      }
      const removed = removeQueued(session);
      send(session.socket, removed ? "queue.cancelled" : "queue.idle", { requestId: message.requestId });
      return;
    }
    if (message.type === "match.loaded") {
      const match = matches.get(session.matchId);
      if (!match || match.phase !== "LOADING") {
        send(session.socket, "error", { code: "MATCH_NOT_LOADING", requestId: message.requestId, message: "La preparación ya no está activa." });
        return;
      }
      const player = match.players.find((entry) => entry.uid === session.uid);
      if (!player) return;
      player.loaded = true;
      send(session.socket, "match.loadAccepted", { requestId: message.requestId, matchId: match.id });
      startCountdown(match);
      return;
    }
    send(session.socket, "error", { code: "UNKNOWN_MESSAGE", requestId: message.requestId, message: "Acción no reconocida." });
  }

  httpServer.on("upgrade", (request, socket, head) => {
    if (new URL(request.url, "http://localhost").pathname !== "/ws") {
      socket.destroy();
      return;
    }
    webSockets.handleUpgrade(request, socket, head, (ws) => webSockets.emit("connection", ws, request));
  });

  webSockets.on("connection", (socket) => {
    const session = { socket, uid: null, nickname: null, matchId: null, authTimer: null };
    session.authTimer = setTimeout(() => socket.close(1008, "Authentication timeout"), AUTH_TIMEOUT_MS);
    socket.on("message", (raw) => { void onMessage(session, raw); });
    socket.on("close", () => {
      clearTimeout(session.authTimer);
      if (!session.uid || sessions.get(session.uid) !== session) return;
      sessions.delete(session.uid);
      if (removeQueued(session)) return;
      const match = matches.get(session.matchId);
      if (match?.phase === "LOADING") cancelPreparation(match, "OPPONENT_DISCONNECTED", session.uid);
      else if (match?.phase === "COUNTDOWN") {
        clearMatch(match);
        for (const player of match.players) send(player.socket, "match.cancelled", { reason: "CONNECTION_LOST", requeued: false });
      }
    });
    socket.on("error", (error) => logger.warn?.("[FortRush WS] socket error", error.message));
  });

  return {
    close() {
      clearInterval(heartbeatTimer);
      for (const match of matches.values()) clearMatch(match);
      webSockets.close();
    },
    get queueSize() { return queue.length; },
    get matchCount() { return matches.size; }
  };
}
