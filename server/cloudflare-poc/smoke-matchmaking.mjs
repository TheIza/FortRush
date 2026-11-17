import assert from "node:assert/strict";

const baseUrl = process.argv[2];
if (!baseUrl) {
  console.error("Uso: node smoke-matchmaking.mjs https://<worker>.workers.dev");
  process.exit(2);
}

const health = await fetch(`${baseUrl.replace(/\/$/, "")}/health`);
assert.equal(health.status, 200, "GET /health debe responder HTTP 200");
const healthBody = await health.json();
assert.equal(healthBody.status, "ok");
console.log(`OK: /health está disponible; perfil configurado: ${healthBody.profileApiConfigured === true}.`);

const unauthenticatedProfile = await fetch(`${baseUrl.replace(/\/$/, "")}/api/profile/me`);
assert.equal(unauthenticatedProfile.status, 401, "El perfil debe rechazar solicitudes sin sesión Firebase");
console.log("OK: /api/profile/me rechaza solicitudes sin autenticación.");

const socketUrl = `${baseUrl.replace(/^http/, "ws").replace(/\/$/, "")}/ws`;
const socket = new WebSocket(socketUrl);
await new Promise((resolve, reject) => {
  socket.addEventListener("open", resolve, { once: true });
  socket.addEventListener("error", reject, { once: true });
});

const rejection = await new Promise((resolve, reject) => {
  const timeout = setTimeout(() => reject(new Error("El Worker no rechazó el token de prueba")), 10_000);
  socket.addEventListener("message", (event) => {
    const message = JSON.parse(event.data);
    if (message.type === "error" && message.code === "AUTH_INVALID") {
      clearTimeout(timeout);
      resolve(message);
    }
  });
  socket.send(JSON.stringify({ type: "auth", token: "token-invalido-de-prueba" }));
});
assert.equal(rejection.type, "error");
console.log("OK: el Worker rechaza un token Firebase inválido.");
socket.close();
