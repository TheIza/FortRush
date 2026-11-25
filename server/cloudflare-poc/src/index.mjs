import { createRemoteJWKSet, jwtVerify } from "jose";
import { handleProfileApi, loadMatchNicknames, ProfileApiError, settleMatchProfiles } from "./profile-api.mjs";
import { getNicknamePolicyMessage } from "../../src/nickname-policy.mjs";
import { createSharedMap } from "./shared-map.mjs";
import {
  accruePassiveCoins,
  cloneMatchBalance,
  incomeRateAtElapsedMs,
  MATCH_BALANCE
} from "./match-balance.mjs";
import {
  advanceCombat,
  createGameSnapshot,
  deployTroops as deployTroopAction,
  initializeCombatState,
  sellTower as sellTowerAction,
  surrenderMatch as surrenderMatchAction
} from "./match-engine.mjs";

const FIREBASE_JWKS = createRemoteJWKSet(
  new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com")
);
const MATCHMAKING_VERSION = "profile-rewards-2026-11-25.1";
const MATCH_STATE_INTERVAL_MS = 250;
const PENDING_MATCH_REWARDS_KEY = "pendingMatchRewards";

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
        profileApiConfigured: Boolean(env.FIREBASE_SERVICE_ACCOUNT_JSON),
        mapGeneratorVersion: "v2",
        matchmakingVersion: MATCHMAKING_VERSION,
        matchBalanceVersion: MATCH_BALANCE.version
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
    console.log("matchmaking", { event: "websocket-upgrade" });
    const roomId = env.TEST_ROOM.idFromName("fort-rush-matchmaking-probe");
    return env.TEST_ROOM.get(roomId).fetch(request);
  }
};

function send(socket, type, data = {}) {
  if (socket?.readyState !== WebSocket.OPEN) return false;
  try {
    socket.send(JSON.stringify({ type, ...data }));
    return true;
  } catch {
    console.warn("matchmaking", { event: "send-failed", messageType: type });
    return false;
  }
}

function economySnapshot(match, player, nowMs) {
  const balance = match.balance ?? MATCH_BALANCE;
  const elapsedMatchMs = Math.max(0, nowMs - match.startedAtServerMs);
  return {
    balanceVersion: balance.version,
    startingCoins: balance.economy.startingCoins,
    incomePerSecond: incomeRateAtElapsedMs(elapsedMatchMs, balance.economy),
    startedAtServerMs: match.startedAtServerMs,
    serverTimeMs: nowMs,
    elapsedMatchMs,
    coinsSpent: player?.coinsSpent ?? 0,
    coins: player ? coinsAvailable(match, player, nowMs) : balance.economy.startingCoins,
    maximumCoins: balance.economy.maximumCoins
  };
}

function coinsAvailable(match, player, nowMs) {
  const balance = match.balance ?? MATCH_BALANCE;
  const elapsedWholeSeconds = Math.floor(Math.max(0, nowMs - match.startedAtServerMs) / 1_000);
  const currentCoins = player.coinsAtLastUpdate ?? Math.max(0, balance.economy.startingCoins - (player.coinsSpent ?? 0));
  return accruePassiveCoins(
    currentCoins,
    player.incomeAppliedThroughSecond ?? 0,
    elapsedWholeSeconds,
    balance.economy
  );
}

function rejectBuild(socket, actionId, slotId, reason) {
  send(socket, "action_rejected", { actionId, slotId, reason });
}

function publishGameState(ctx, match, nowMs = Date.now()) {
  if (!match?.game) return;
  match.game.nextSnapshotSequence += 1;
  const socketsByUid = new Map(ctx.getWebSockets().map((socket) => [socket.deserializeAttachment()?.uid, socket]));
  for (const player of match.players) {
    const socket = socketsByUid.get(player.uid);
    const state = createGameSnapshot(match, player.playerNumber, nowMs);
    if (state.result) state.result = resultForPlayer(match, player);
    send(socket, "match.state", {
      matchId: match.matchId,
      state,
      economy: economySnapshot(match, player, nowMs)
    });
  }
  if (match.game.result) {
    const settlementStatus = match.rewardSettlement?.status ?? "PENDING";
    if (match.game.resultBroadcastedStatus === settlementStatus) return;
    match.game.resultBroadcastedStatus = settlementStatus;
    for (const player of match.players) {
      send(socketsByUid.get(player.uid), "match.result", {
        matchId: match.matchId,
        balanceVersion: match.balance.version,
        ...resultForPlayer(match, player)
      });
    }
  }
}

function resultForPlayer(match, player) {
  const settled = match.rewardSettlement?.status === "SETTLED";
  return {
    ...match.game.result,
    profileRewardCoins: settled ? (match.rewardSettlement.awardsByUid?.[player.uid] ?? 0) : null,
    profileRewardSettled: settled
  };
}

function matchRewardTask(match) {
  return {
    matchId: match.matchId,
    balance: match.balance,
    players: match.players.map(({ uid, playerNumber }) => ({ uid, playerNumber })),
    game: { result: match.game.result }
  };
}

export class WebSocketProbe {
  constructor(ctx, env, identityVerifier = verifyFirebaseIdentity) {
    this.ctx = ctx;
    this.env = env;
    this.verifyIdentity = identityVerifier;
  }

  async fetch(request) {
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
    if (payload.type === "deploy_troop") return this.deployTroop(socket, attachment, payload);
    if (payload.type === "build_tower") return this.buildTower(socket, attachment, payload);
    if (payload.type === "sell_tower") return this.sellTower(socket, attachment, payload);
    if (payload.type === "match.surrender") return this.surrenderMatch(socket, attachment, payload);
    send(socket, "error", { code: "UNKNOWN_MESSAGE", message: "Acción no reconocida." });
  }

  async authenticate(socket, attachment, payload) {
    if (payload.type !== "auth" || typeof payload.token !== "string") {
      send(socket, "error", { code: "AUTH_REQUIRED", message: "Inicia sesión para buscar partida." });
      socket.close(1008, "Authentication required");
      return;
    }
    try {
      const { uid } = await this.verifyIdentity(payload.token, this.env.FIREBASE_PROJECT_ID);
      const duplicate = this.ctx.getWebSockets().some((other) => other !== socket &&
        other.readyState === WebSocket.OPEN && other.deserializeAttachment()?.uid === uid);
      if (duplicate) {
        console.warn("matchmaking", { event: "duplicate-account" });
        send(socket, "error", { code: "ALREADY_CONNECTED", message: "Esta cuenta ya está conectada a la prueba." });
        socket.close(1008, "Duplicate account");
        return;
      }
      socket.serializeAttachment({ ...attachment, uid });
      console.log("matchmaking", { event: "authenticated" });
      send(socket, "auth.ok");
    } catch (error) {
      console.error("matchmaking", { event: "authentication-rejected", name: error?.name ?? "Error" });
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
      if (queue.length >= 2) await this.pairQueue(queue);
      return;
    }
    const sequence = (await this.ctx.storage.get("queueSequence") ?? 0) + 1;
    await this.ctx.storage.put("queueSequence", sequence);
    queue.push({ uid: attachment.uid, sequence });
    queue.sort((a, b) => a.sequence - b.sequence);
    await this.ctx.storage.put("queue", queue);
    socket.serializeAttachment({ ...attachment, queueSequence: sequence });
    send(socket, "queue.waiting", { requestId: payload.requestId, position: queue.length });
    console.log("matchmaking", { event: "queue-joined", queuedPlayers: queue.length });
    if (queue.length >= 2) await this.pairQueue(queue);
  }

  async pairQueue(queue) {
    const activeMatch = await this.ctx.storage.get("match");
    if (activeMatch?.phase === "FINISHED" && activeMatch.game?.result) {
      await this.settleFinishedMatch(activeMatch);
      publishGameState(this.ctx, activeMatch);
      await this.clearAlarmPreservingPendingRewards();
      for (const socket of this.ctx.getWebSockets()) {
        const attachment = socket.deserializeAttachment() ?? {};
        if (attachment.matchId === activeMatch.matchId) {
          socket.serializeAttachment({ ...attachment, matchId: null, playerNumber: null, queueSequence: null });
        }
      }
    }
    if (activeMatch && !["FINISHED", "CANCELLED"].includes(activeMatch.phase)) {
      if ((["IN_MATCH", "SUDDEN_DEATH"].includes(activeMatch.phase)) && !activeMatch.game) {
        activeMatch.phase = "CANCELLED";
        activeMatch.cancellationReason = "SERVER_STATE_LOST";
        await this.ctx.storage.put("match", activeMatch);
        for (const socket of this.ctx.getWebSockets()) {
          const attachment = socket.deserializeAttachment() ?? {};
          if (attachment.matchId !== activeMatch.matchId) continue;
          socket.serializeAttachment({ ...attachment, matchId: null, playerNumber: null, queueSequence: null });
          send(socket, "match.cancelled", { reason: "SERVER_STATE_LOST", requeued: false });
        }
        await this.clearAlarmPreservingPendingRewards();
      } else if (["LOADING", "COUNTDOWN"].includes(activeMatch.phase)) {
        const liveSockets = new Map(this.ctx.getWebSockets().filter(socket => socket.readyState === WebSocket.OPEN)
          .map(socket => [socket.deserializeAttachment()?.uid, socket]));
        const survivors = activeMatch.players.filter(player => liveSockets.has(player.uid));
        if (survivors.length === activeMatch.players.length) return;
        queue = await this.ctx.storage.get("queue") ?? queue;
        for (const survivor of survivors) {
          const survivorSocket = liveSockets.get(survivor.uid);
          survivorSocket.serializeAttachment({ ...survivorSocket.deserializeAttachment(), matchId: null,
            playerNumber: null, queueSequence: survivor.sequence });
          queue = [...queue.filter(entry => entry.uid !== survivor.uid), { uid: survivor.uid, sequence: survivor.sequence }];
          send(survivorSocket, "match.cancelled", { reason: "OPPONENT_DISCONNECTED", requeued: true });
        }
        queue.sort((a, b) => a.sequence - b.sequence);
        await this.ctx.storage.put("queue", queue);
        await this.ctx.storage.put("match", null);
        await this.clearAlarmPreservingPendingRewards();
        this.notifyQueue(queue);
      } else {
        console.warn("matchmaking", {
          event: "queue-held-by-active-match",
          phase: activeMatch.phase,
          queuedPlayers: queue.length
        });
        return;
      }
    }
    const orderedQueue = [...queue].sort((a, b) => a.sequence - b.sequence);
    const selected = orderedQueue.slice(0, 2);
    if (selected.length < 2) return;
    const socketsByUid = new Map(this.ctx.getWebSockets().filter(socket => socket.readyState === WebSocket.OPEN)
      .map((socket) => [socket.deserializeAttachment()?.uid, socket]));
    const players = selected.map((entry) => ({ ...entry, socket: socketsByUid.get(entry.uid) }));
    if (players.some((player) => !player.socket)) {
      const activeUids = new Set(socketsByUid.keys());
      const active = orderedQueue.filter((entry) => activeUids.has(entry.uid));
      await this.ctx.storage.put("queue", active);
      this.notifyQueue(active);
      if (active.length >= 2) await this.pairQueue(active);
      return;
    }
    let nicknames = {};
    try {
      nicknames = await loadMatchNicknames(this.env, selected.map((entry) => entry.uid));
    } catch (error) {
      console.error("matchmaking", { event: "nickname-lookup-failed", name: error?.name ?? "Error" });
    }
    const mapSeed = crypto.randomUUID();
    const map = createSharedMap(mapSeed);
    const match = {
      matchId: crypto.randomUUID(),
      balance: cloneMatchBalance(),
      phase: "LOADING",
      loadingDeadlineMs: Date.now() + 30_000,
      players: players.map(({ uid, sequence }, index) => ({
        uid,
        nickname: nicknames[uid] ?? "Jugador",
        sequence,
        playerNumber: index + 1,
        loaded: false,
        coinsAtLastUpdate: MATCH_BALANCE.economy.startingCoins,
        incomeAppliedThroughSecond: 0,
        coinsSpent: 0,
        lastTowerBuiltAtMs: null,
        processedActionIds: []
      })),
      map,
      towers: [],
      startedAtServerMs: null
    };
    console.log("match-map", {
      matchId: match.matchId,
      seed: mapSeed,
      archetype: map.meta.archetype,
      usedFallback: map.meta.usedFallback
    });
    await this.ctx.storage.put("match", match);
    const waiting = orderedQueue.slice(2);
    await this.ctx.storage.put("queue", waiting);
    await this.scheduleAlarmAt(match.loadingDeadlineMs);
    players.forEach((player, index) => {
      const playerNumber = index + 1;
      const opponent = match.players[1 - index];
      player.socket.serializeAttachment({ ...player.socket.deserializeAttachment(), queueSequence: null, matchId: match.matchId, playerNumber });
      send(player.socket, "match.found", {
        matchId: match.matchId,
        playerNumber,
        opponentNickname: opponent.nickname,
        map: match.map
      });
    });
    this.notifyQueue(waiting);
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
    this.broadcast(match, "match.countdown", { seconds: match.countdown });
    await this.scheduleAlarmAt(Date.now() + 1_000);
  }

  async publishMatchState(match, nowMs = Date.now()) {
    if (match.phase === "FINISHED" && match.game?.result) await this.settleFinishedMatch(match);
    publishGameState(this.ctx, match, nowMs);
    if (match.phase !== "FINISHED") return;
    await this.clearAlarmPreservingPendingRewards();
    for (const socket of this.ctx.getWebSockets()) {
      const attachment = socket.deserializeAttachment() ?? {};
      if (attachment.matchId === match.matchId) {
        socket.serializeAttachment({ ...attachment, matchId: null, playerNumber: null, queueSequence: null });
      }
    }
    const waiting = await this.ctx.storage.get("queue") ?? [];
    if (waiting.length >= 2) await this.pairQueue(waiting);
  }

  async settleFinishedMatch(match) {
    if (!match?.game?.result || ["SETTLED", "PENDING"].includes(match.rewardSettlement?.status)) return;
    const task = matchRewardTask(match);
    let pending = await this.ctx.storage.get(PENDING_MATCH_REWARDS_KEY) ?? [];
    if (!pending.some((entry) => entry.matchId === task.matchId)) {
      pending = [...pending, task];
      await this.ctx.storage.put(PENDING_MATCH_REWARDS_KEY, pending);
    }
    await this.scheduleAlarmAt(Date.now() + 5_000);
    try {
      match.rewardSettlement = await settleMatchProfiles(this.env, match);
      match.rewardSettlement.settledAtServerMs = Date.now();
      pending = pending.filter((entry) => entry.matchId !== match.matchId);
      await this.ctx.storage.put(PENDING_MATCH_REWARDS_KEY, pending);
    } catch (error) {
      match.rewardSettlement = { status: "PENDING" };
      console.error("match-reward-settlement", {
        event: "retry-scheduled",
        matchId: match.matchId,
        name: error?.name ?? "Error",
        code: error?.code ?? "UNKNOWN"
      });
    }
    const storedMatch = await this.ctx.storage.get("match");
    if (storedMatch?.matchId === match.matchId) await this.ctx.storage.put("match", match);
  }

  async retryPendingMatchRewards() {
    const pending = await this.ctx.storage.get(PENDING_MATCH_REWARDS_KEY) ?? [];
    if (pending.length === 0) return;
    const unresolved = [];
    const completed = [];
    for (const task of pending) {
      try {
        completed.push([task, await settleMatchProfiles(this.env, task)]);
      } catch (error) {
        unresolved.push(task);
        console.error("match-reward-settlement", {
          event: "retry-failed",
          matchId: task.matchId,
          name: error?.name ?? "Error",
          code: error?.code ?? "UNKNOWN"
        });
      }
    }
    await this.ctx.storage.put(PENDING_MATCH_REWARDS_KEY, unresolved);
    for (const [task, settlement] of completed) {
      const current = await this.ctx.storage.get("match");
      if (current?.matchId !== task.matchId || current.phase !== "FINISHED") continue;
      current.rewardSettlement = { ...settlement, settledAtServerMs: Date.now() };
      await this.ctx.storage.put("match", current);
      await this.publishMatchState(current);
    }
    if (unresolved.length > 0) await this.scheduleAlarmAt(Date.now() + 5_000);
  }

  async scheduleAlarmAt(timestamp) {
    const current = await this.ctx.storage.getAlarm?.();
    if (!Number.isFinite(current) || current > timestamp) await this.ctx.storage.setAlarm?.(timestamp);
  }

  async clearAlarmPreservingPendingRewards() {
    await this.ctx.storage.deleteAlarm?.();
    const pending = await this.ctx.storage.get(PENDING_MATCH_REWARDS_KEY) ?? [];
    if (pending.length > 0) await this.scheduleAlarmAt(Date.now() + 5_000);
  }

  async alarm() {
    await this.retryPendingMatchRewards();
    const match = await this.ctx.storage.get("match");
    if (!match) return;
    if (match.phase === "FINISHED" && match.game?.result) {
      await this.publishMatchState(match);
      return;
    }
    if (match.phase === "LOADING") {
      if (Date.now() < match.loadingDeadlineMs) {
        await this.scheduleAlarmAt(match.loadingDeadlineMs);
        return;
      }
      const loadedPlayers = match.players.filter((player) => player.loaded);
      const requeued = loadedPlayers.length === 1 ? loadedPlayers[0] : null;
      const socketsByUid = new Map(this.ctx.getWebSockets().filter(socket => socket.readyState === WebSocket.OPEN)
        .map((socket) => [socket.deserializeAttachment()?.uid, socket]));
      let queue = await this.ctx.storage.get("queue") ?? [];
      for (const player of match.players) {
        const playerSocket = socketsByUid.get(player.uid);
        if (!playerSocket) continue;
        if (player === requeued) {
          queue = [...queue.filter((entry) => entry.uid !== player.uid), { uid: player.uid, sequence: player.sequence }]
            .sort((a, b) => a.sequence - b.sequence);
          playerSocket.serializeAttachment({ ...playerSocket.deserializeAttachment(), matchId: null, playerNumber: null, queueSequence: player.sequence });
          send(playerSocket, "match.cancelled", { reason: "LOAD_TIMEOUT", requeued: true });
        } else {
          playerSocket.serializeAttachment({ ...playerSocket.deserializeAttachment(), matchId: null, playerNumber: null, queueSequence: null });
          send(playerSocket, "match.cancelled", { reason: "LOAD_TIMEOUT", requeued: false });
        }
      }
      await this.ctx.storage.put("match", null);
      await this.ctx.storage.put("queue", queue);
      this.notifyQueue(queue);
      if (queue.length >= 2) await this.pairQueue(queue);
      return;
    }
    if (["IN_MATCH", "SUDDEN_DEATH"].includes(match.phase)) {
      advanceCombat(match, Date.now());
      await this.ctx.storage.put("match", match);
      await this.publishMatchState(match);
      if (match.phase !== "FINISHED") await this.scheduleAlarmAt(Date.now() + MATCH_STATE_INTERVAL_MS);
      return;
    }
    if (match.phase !== "COUNTDOWN") return;
    if (match.countdown > 1) {
      match.countdown -= 1;
      await this.ctx.storage.put("match", match);
      this.broadcast(match, "match.countdown", { seconds: match.countdown });
      await this.scheduleAlarmAt(Date.now() + 1_000);
      return;
    }
    match.balance ??= cloneMatchBalance();
    initializeCombatState(match, Date.now());
    await this.ctx.storage.put("match", match);
    this.ctx.getWebSockets().filter(socket => socket.readyState === WebSocket.OPEN &&
      socket.deserializeAttachment()?.matchId === match.matchId).forEach((socket) => {
      const attachment = socket.deserializeAttachment?.() ?? {};
      const player = match.players.find((entry) => entry.uid === attachment.uid);
      send(socket, "game_start", {
        matchId: match.matchId,
        map: match.map,
        balance: match.balance ?? cloneMatchBalance(),
        economy: economySnapshot(match, player, match.startedAtServerMs),
        state: createGameSnapshot(match, player?.playerNumber ?? 0, match.startedAtServerMs)
      });
    });
    await this.scheduleAlarmAt(Date.now() + MATCH_STATE_INTERVAL_MS);
  }

  async deployTroop(socket, attachment, payload) {
    const actionId = typeof payload.actionId === "string" ? payload.actionId : "";
    const card = typeof payload.card === "string" ? payload.card : "";
    const position = typeof payload.position === "number" ? payload.position : NaN;
    const nowMs = Date.now();
    const match = await this.ctx.storage.get("match");
    const player = match?.players?.find((entry) =>
      entry.uid === attachment.uid && entry.playerNumber === attachment.playerNumber
    );
    if (!match || match.matchId !== attachment.matchId || !player || !match.game) {
      send(socket, "action_rejected", { actionId, reason: "INVALID_STATE" });
      return;
    }
    advanceCombat(match, nowMs);
    const result = deployTroopAction(match, player.playerNumber, card, position, actionId, nowMs);
    if (!result.ok) {
      await this.ctx.storage.put("match", match);
      send(socket, "action_rejected", { actionId, reason: result.reason });
      await this.publishMatchState(match, nowMs);
      return;
    }
    await this.ctx.storage.put("match", match);
    const socketsByUid = new Map(this.ctx.getWebSockets().map((candidate) => [candidate.deserializeAttachment()?.uid, candidate]));
    for (const recipient of match.players) {
      send(socketsByUid.get(recipient.uid), "troops_deployed", {
        actionId,
        ownerPlayerNumber: player.playerNumber,
        card,
        troopIds: result.troopIds,
        economy: economySnapshot(match, recipient, nowMs)
      });
    }
    await this.publishMatchState(match, nowMs);
  }

  async buildTower(socket, attachment, payload) {
    const actionId = typeof payload.actionId === "string" ? payload.actionId : "";
    const slotId = typeof payload.slotId === "string" ? payload.slotId : "";
    if (!actionId || actionId.length > 80) {
      rejectBuild(socket, actionId, slotId, "INVALID_ACTION_ID");
      return;
    }

    const match = await this.ctx.storage.get("match");
    const player = match?.players?.find((entry) =>
      entry.uid === attachment.uid && entry.playerNumber === attachment.playerNumber
    );
    if (!match || match.matchId !== attachment.matchId || !player || !Number.isFinite(match.startedAtServerMs)) {
      rejectBuild(socket, actionId, slotId, "INVALID_STATE");
      return;
    }
    if (match.game) {
      advanceCombat(match, Date.now());
      await this.ctx.storage.put("match", match);
    }
    if (match.phase !== "IN_MATCH") {
      await this.ctx.storage.put("match", match);
      rejectBuild(socket, actionId, slotId, "INVALID_STATE");
      await this.publishMatchState(match);
      return;
    }

    // Successful action IDs are stored with the accepted state. A duplicate never spends again.
    if ((player.processedActionIds ?? []).includes(actionId)) return;

    const slot = match.map?.slots?.find((entry) => entry.id === slotId);
    if (!slot) {
      rejectBuild(socket, actionId, slotId, "UNKNOWN_SLOT");
      return;
    }
    const ownSlotOwner = player.playerNumber === 1 ? "player1" : "player2";
    if (slot.owner !== ownSlotOwner) {
      rejectBuild(socket, actionId, slotId, "NOT_YOUR_SLOT");
      return;
    }
    if ((match.towers ?? []).some((tower) => tower.slotId === slotId)) {
      rejectBuild(socket, actionId, slotId, "SLOT_OCCUPIED");
      return;
    }
    const card = typeof payload.card === "string" ? payload.card : "";
    const balance = match.balance ?? MATCH_BALANCE;
    const towerDefinition = Object.hasOwn(balance.cards.towers, card) ? balance.cards.towers[card] : null;
    if (!towerDefinition) {
      rejectBuild(socket, actionId, slotId, "NOT_A_TOWER_CARD");
      return;
    }

    const nowMs = Date.now();
    const cooldownMs = Math.max(0, Math.floor(towerDefinition.constructionCooldownMs ?? 0));
    if (cooldownMs > 0 && Number.isFinite(player.lastTowerBuiltAtMs) &&
        nowMs - player.lastTowerBuiltAtMs < cooldownMs) {
      rejectBuild(socket, actionId, slotId, "ON_COOLDOWN");
      return;
    }
    const availableCoins = coinsAvailable(match, player, nowMs);
    if (availableCoins < towerDefinition.cost) {
      rejectBuild(socket, actionId, slotId, "NOT_ENOUGH_COINS");
      return;
    }

    const tower = {
      slotId,
      ownerPlayerNumber: player.playerNumber,
      card,
      cost: towerDefinition.cost,
      placedAtServerMs: nowMs,
      nextShotAtElapsedMs: Math.max(0, nowMs - match.startedAtServerMs) + towerDefinition.attackIntervalMs
    };
    player.coinsAtLastUpdate = availableCoins - towerDefinition.cost;
    player.incomeAppliedThroughSecond = Math.floor(Math.max(0, nowMs - match.startedAtServerMs) / 1_000);
    player.coinsSpent += towerDefinition.cost;
    player.lastTowerBuiltAtMs = nowMs;
    player.processedActionIds = [...(player.processedActionIds ?? []), actionId].slice(-32);
    match.towers = [...(match.towers ?? []), tower];
    await this.ctx.storage.put("match", match);

    const socketsByUid = new Map(this.ctx.getWebSockets().map((candidate) => [candidate.deserializeAttachment()?.uid, candidate]));
    for (const recipient of match.players) {
      const recipientSocket = socketsByUid.get(recipient.uid);
      send(recipientSocket, "tower_built", {
        actionId,
        slotId,
        ownerPlayerNumber: player.playerNumber,
        card,
        economy: economySnapshot(match, recipient, nowMs)
      });
    }
    await this.publishMatchState(match, nowMs);
  }

  async sellTower(socket, attachment, payload) {
    const actionId = typeof payload.actionId === "string" ? payload.actionId : "";
    const slotId = typeof payload.slotId === "string" ? payload.slotId : "";
    const match = await this.ctx.storage.get("match");
    const player = match?.players?.find((entry) =>
      entry.uid === attachment.uid && entry.playerNumber === attachment.playerNumber
    );
    if (!match || match.matchId !== attachment.matchId || !player || !match.game) {
      send(socket, "action_rejected", { actionId, slotId, reason: "INVALID_STATE" });
      return;
    }
    const nowMs = Date.now();
    advanceCombat(match, nowMs);
    const result = sellTowerAction(match, player.playerNumber, slotId, actionId, nowMs);
    if (!result.ok) {
      await this.ctx.storage.put("match", match);
      send(socket, "action_rejected", { actionId, slotId, reason: result.reason });
      await this.publishMatchState(match, nowMs);
      return;
    }
    await this.ctx.storage.put("match", match);
    const socketsByUid = new Map(this.ctx.getWebSockets().map((candidate) => [candidate.deserializeAttachment()?.uid, candidate]));
    for (const recipient of match.players) {
      send(socketsByUid.get(recipient.uid), "tower_sold", {
        actionId,
        slotId,
        ownerPlayerNumber: player.playerNumber,
        refund: result.refund,
        economy: economySnapshot(match, recipient, nowMs)
      });
    }
    await this.publishMatchState(match, nowMs);
  }

  async surrenderMatch(socket, attachment, payload) {
    const match = await this.ctx.storage.get("match");
    const player = match?.players?.find((entry) =>
      entry.uid === attachment.uid && entry.playerNumber === attachment.playerNumber
    );
    if (!match || match.matchId !== attachment.matchId || !player || !match.game) {
      send(socket, "action_rejected", { actionId: payload.actionId ?? "", reason: "INVALID_STATE" });
      return;
    }
    const nowMs = Date.now();
    advanceCombat(match, nowMs);
    const result = surrenderMatchAction(match, player.playerNumber, nowMs);
    if (!result.ok) {
      await this.ctx.storage.put("match", match);
      send(socket, "action_rejected", { actionId: payload.actionId ?? "", reason: result.reason });
      await this.publishMatchState(match, nowMs);
      return;
    }
    await this.ctx.storage.put("match", match);
    await this.publishMatchState(match, nowMs);
  }

  async webSocketClose(socket) {
    const attachment = socket.deserializeAttachment() ?? {};
    const otherSockets = this.ctx.getWebSockets().filter(candidate => candidate !== socket && candidate.readyState === WebSocket.OPEN);
    if (otherSockets.some(candidate => candidate.deserializeAttachment()?.uid === attachment.uid)) return;
    let queue = await this.ctx.storage.get("queue") ?? [];
    queue = queue.filter(entry => entry.uid !== attachment.uid);
    const match = await this.ctx.storage.get("match");
    if (match?.matchId === attachment.matchId && ["LOADING", "COUNTDOWN"].includes(match.phase)) {
      const survivor = match.players.find((entry) => entry.uid !== attachment.uid);
      const survivorSocket = otherSockets.find((candidate) => candidate.deserializeAttachment()?.uid === survivor?.uid);
      await this.ctx.storage.delete("match");
      await this.clearAlarmPreservingPendingRewards();
      if (survivor && survivorSocket) {
        survivorSocket.serializeAttachment({ ...survivorSocket.deserializeAttachment(), matchId: null,
          playerNumber: null, queueSequence: survivor.sequence });
        queue = [...queue.filter(entry => entry.uid !== survivor.uid), { uid: survivor.uid, sequence: survivor.sequence }]
          .sort((a, b) => a.sequence - b.sequence);
        await this.ctx.storage.put("queue", queue);
        send(survivorSocket, "match.cancelled", { reason: "OPPONENT_DISCONNECTED", requeued: true });
      }
    }
    await this.ctx.storage.put("queue", queue);
    this.notifyQueue(queue);
    if (queue.length >= 2) await this.pairQueue(queue);
  }

  async webSocketError(socket) {
    console.warn("matchmaking", { event: "socket-error" });
    await this.webSocketClose(socket);
  }

  notifyQueue(queue) {
    const sockets = new Map(this.ctx.getWebSockets().filter(socket => socket.readyState === WebSocket.OPEN)
      .map((socket) => [socket.deserializeAttachment()?.uid, socket]));
    queue.forEach((entry, index) => send(sockets.get(entry.uid), "queue.waiting", { position: index + 1 }));
  }

  broadcast(match, type, data = {}) {
    this.ctx.getWebSockets().filter(socket => socket.readyState === WebSocket.OPEN &&
      socket.deserializeAttachment()?.matchId === match.matchId)
      .forEach((socket) => send(socket, type, data));
  }
}
