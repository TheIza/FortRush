# FortRush — prueba de emparejamiento en Cloudflare

Este Worker es el backend activo de FortRush. Atiende el API de perfiles y apodos y el emparejamiento de prueba por WebSocket, usando un Durable Object para mantener la cola y la preparación de una partida de hasta dos jugadores. Verifica tokens de Firebase Authentication y comparte una geometría aleatoria generada por el Worker, idéntica para ambos jugadores.

El generador curvo descrito aquí ya está implementado localmente, pero todavía no está desplegado: hasta publicar el cambio, el Worker activo seguirá enviando el mapa de prueba anterior.

Todavía no es el servidor completo del juego: no ejecuta combate ni permite reanudar una partida tras perder la conexión. El generador local produce un camino curvo, continuo y simétrico mediante un giro de 180 grados, con cuatro huecos por jugador y la posición de las dos puertas centrada sobre el camino. La app dibuja dos muros de lado a lado con piezas repetidas y solapadas; el Worker envía geometría, no archivos de arte. El generador valida límites, cruces, separación del camino, huecos, muros y decoración; tras 24 intentos, elige entre cinco mapas de respaldo. La lógica de combate sigue pendiente.

## Configuración local

La app obtiene la dirección del API de `fortRushApiBaseUrl` y la del emparejamiento de `fortRushMatchmakingBaseUrl` en `local.properties`. Ambas apuntan al Worker de Cloudflare en la configuración local actual.

Configuración local usada para la prueba:

```properties
fortRushMatchmakingBaseUrl=https://round-king-86b7.izarbelanuza06.workers.dev
```

No se sube `local.properties` a Git. El ID del proyecto Firebase configurado en `wrangler.jsonc` es público; el Worker no contiene credenciales de administrador.

## Perfil y apodos

El Worker replica los endpoints `GET /api/profile/me`, `GET /api/nicknames/availability?nickname=...` y `PUT /api/profile/nickname`. Verifica el token Firebase y usa Firestore REST con una credencial de servidor guardada como secreto de Cloudflare. La reserva y el cambio de apodo usan una transacción; las escrituras parciales preservan los demás campos del perfil.

El secreto se llama `FIREBASE_SERVICE_ACCOUNT_JSON`. Se configurará en Cloudflare como **Secret**, nunca como variable normal, archivo del repositorio, `local.properties` o valor del APK. No compartas su contenido en el chat. `/health` incluye `profileApiConfigured` para indicar si está disponible.

La credencial de servidor ya está configurada en Cloudflare. La prueba autenticada desde Android confirmó que el perfil carga y que un jugador puede entrar en la app. Render ya no forma parte de la ruta activa; el servicio alojado en Render y su Blueprint todavía deben eliminarse desde sus paneles.

El test de contrato simulado se ejecuta con `npm test` desde esta carpeta. Comprueba reserva inicial, duplicados, el plazo de siete días y conservación de campos; no sustituye una prueba contra Firestore real.

## Despliegue

Desde esta carpeta, con Wrangler autorizado para la cuenta Cloudflare:

```powershell
npm install
npx wrangler deploy
```

La migración `v1` crea la clase Durable Object con almacenamiento SQLite. Comprueba `GET /health`; debe responder `status: "ok"` y `profileApiConfigured: true` una vez guardado el secreto del perfil.

## Prueba desde FortRush

1. Compila e instala la app con una sesión Firebase válida.
2. Abre la app en dos dispositivos o en un móvil y un emulador, con dos usuarios Firebase distintos.
3. Pulsa **Jugar** en ambos.
4. Comprueba que ambos ven el mismo `matchId` y el mismo mapa, que aparecen cuatro huecos por jugador y que la cuenta atrás empieza cuando ambos terminan de cargar.
5. Cancela mientras uno está esperando y confirma que vuelve a Inicio. Después de encontrar rival, el botón cancelar debe estar desactivado.

El rival aparece como **Rival de prueba**: la prueba aún no consulta el apodo de Firestore. No compartas tokens Firebase. Si no se empareja, anota el mensaje que aparece y si ambos dispositivos siguen en la misma conexión de red.

## Comprobaciones realizadas — 17 de noviembre de 2026

- Wrangler desplegó la versión `d02e284e-29f8-4c74-b9b2-2ef790bff2cd` en `https://round-king-86b7.izarbelanuza06.workers.dev`.
- `GET /health`: HTTP 200; `profileApiConfigured` es `true`.
- Sin credenciales, `GET /api/profile/me` responde HTTP 401.
- Un token Firebase inventado fue rechazado con `AUTH_INVALID`.
- `:app:assembleDebug`: compilación correcta; APK generado en `app/build/outputs/apk/debug/app-debug.apk`.
- Antes de añadir Auth, dos clientes de consola recibieron el mismo ID de partida y estado; tras 15 segundos inactivos, un movimiento se guardó en Durable Object y se difundió a ambos.
- `npm test`: correcto para registro, duplicados, enfriamiento, conservación de campos y respuesta de `batchGet` como lista JSON.
- El Worker detectó una colisión 409 porque el lector no procesaba la lista JSON de Firestore; se corrigió el parser y se añadieron reintentos acotados para conflictos transaccionales.
- Tras desplegar la corrección, Android recibió HTTP 200 al cargar el perfil y la usuaria confirmó que pudo entrar.
- Versión publicada tras retirar los registros temporales: `2ab950b4-9ee6-44aa-bafa-468474e3f8c6`.

Pendiente: probar emparejamiento desde dos redes distintas y comprobar desconexiones y reinicios. La carga del perfil desde Android sí se confirmó.

## Pendiente antes de uso real

- Probar el flujo anterior desde dispositivos y redes diferentes.
- Leer y mostrar apodos reales sin confiar en datos editables por el cliente.
- Definir la cancelación y la desconexión en cada fase con reingreso justo en cola.
- Añadir límites, reconexión, recuperación y pruebas de carga dentro de las cuotas gratis.
- Implementar reglas de partida y resultado en el servidor.
- Mantener Firebase Spark y Workers Free; no seleccionar Workers Paid ni recursos de pago.

## Generador de mapa v2 — pasos a, b y c

El módulo experimental `src/map-v2.mjs` ofrece la API determinista `generateMap(seed, config)`. Devuelve dimensiones lógicas, camino con longitudes acumuladas, slots de ambos jugadores, cobertura de torre y métricas. El arquetipo se elige a partir de la semilla y el módulo evalúa 20 variantes válidas para quedarse con la de mejor puntuación. El camino y los slots del rival son simétricos al rotar 180 grados; la diferencia de valor entre los lados es 0 %.

El score pondera longitud, cantidad de curvas, apertura lateral, seguridad geométrica, cobertura, equilibrio y dispersión de valor entre plataformas. Los 7 slots de cada jugador incluyen 2 premium, el valor y los intervalos de camino que quedan dentro del alcance de torre.

El módulo todavía no sustituye a `generateSharedMap()` en `src/index.mjs`. Por eso, este cambio no modifica el mapa que reciben los jugadores online ni la simulación, y todavía no se ha desplegado en Cloudflare.

Para revisar las formas y métricas en el navegador, desde esta carpeta ejecuta:

```powershell
node debug-map-server.mjs
```

Abre `http://127.0.0.1:4173/debug-map.html`. La rejilla compara doce semillas, dibuja slots por jugador y nivel, y muestra los círculos de alcance y métricas del score. El servidor de previsualización escucha solo en el propio dispositivo; no despliega ni contacta Cloudflare. Detenlo con `Ctrl+C`.

La prueba del módulo se ejecuta con:

```powershell
node --test map-v2.test.mjs
```

La suite recorre 1.000 semillas y comprueba determinismo, validez del camino y de los slots, cobertura, simetría, siete huecos por jugador, cinco respaldos válidos y selección de 20 candidatos. La decoración y la integración con Worker y Android siguen pendientes.


## Pruebas del generador de mapas

Estas son pruebas automáticas locales; no sustituyen la comprobación visual en Android ni la prueba con dos redes.
