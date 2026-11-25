# FortRush — servidor de FortRush en Cloudflare

Este Worker atiende el API de perfiles y apodos y el juego 1v1 por WebSocket. Un Durable Object mantiene la cola, la partida, la simulación autoritativa y sus instantáneas. Verifica tokens de Firebase Authentication y comparte geometría de mapa idéntica entre los dos jugadores.

El código incluye tropas, movimiento y combate, muros/castillos, dos torres, monedas, venta, rendición, temporizador y muerte súbita. La versión activa de balance del código es `fort-rush-2026-11-24.1`. El despliegue y la partida en dos dispositivos deben comprobarse para cada versión antes de considerarlos publicados.

La reconexión de un cliente y la recuperación si se pierde el estado activo siguen pendientes. Las recompensas de perfil se guardan de forma transaccional e idempotente al terminar; el flujo necesita validación manual con dos cuentas reales. El generador v2 produce un camino curvo, continuo y simétrico mediante un giro de 180 grados, con siete huecos por jugador por defecto (configurable). El Worker envía geometría, no archivos de arte. El generador evalúa variantes y conserva cinco mapas de respaldo.

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

La suite de contrato simulada se ejecuta con `npm test` desde esta carpeta. Incluye reserva de apodos, matchmaking, mapas, economía, construcción, venta, tropas, combate, resultados y muerte súbita. No sustituye una prueba autenticada entre dos dispositivos ni prueba la persistencia ante reinicios.

## Despliegue

Desde esta carpeta, con Wrangler autorizado para la cuenta Cloudflare:

```powershell
npm install
npx wrangler deploy
```

La migración `v1` crea la clase Durable Object con almacenamiento SQLite. Comprueba `GET /health`; debe responder `status: "ok"`, `profileApiConfigured: true` una vez guardado el secreto del perfil y `matchBalanceVersion: "fort-rush-2026-11-24.1"` tras publicar esta versión.

## Prueba desde FortRush

1. Compila e instala la app con una sesión Firebase válida.
2. Abre la app en dos dispositivos o en un móvil y un emulador, con dos usuarios Firebase distintos.
3. Pulsa **Jugar** en ambos.
4. Comprueba que ambos ven el mismo mapa y que la cuenta atrás empieza cuando ambos terminan de cargar.
5. Toca una carta de tropa y luego una parte verde del camino de tu lado. La carta de torre se construye tocándola y después un hueco propio libre; toca una torre propia para seleccionarla y venderla con confirmación.
6. Confirma que las tropas y vidas se actualizan en ambos móviles. Deja que la partida llegue a cuatro minutos para probar muerte súbita o usa rendición para validar la pantalla de resultado.
7. Cancela mientras uno está esperando y confirma que vuelve a Inicio. Después de encontrar rival, el botón cancelar debe estar desactivado.

La prueba con un dispositivo en modo local es solo una vista visual y no reemplaza la simulación autoritativa. El ensayo de juego requiere dos UIDs Firebase.

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

## Generador de mapa v2 — pasos a, b, c y d

El módulo experimental `src/map-v2.mjs` ofrece la API determinista `generateMap(seed, config)`. Devuelve dimensiones lógicas, camino con longitudes acumuladas, slots de ambos jugadores, cobertura de torre y métricas. El arquetipo se elige a partir de la semilla y el módulo evalúa 20 variantes válidas para quedarse con la de mejor puntuación. El camino y los slots del rival son simétricos al rotar 180 grados; la diferencia de valor entre los lados es 0 %.

El score pondera longitud, cantidad de curvas, apertura lateral, seguridad geométrica, cobertura, equilibrio y dispersión de valor entre plataformas. Cada jugador tiene 7 slots normales; cada uno incluye su valor geométrico y los intervalos de camino dentro del alcance de torre.

Los slots dejan un margen lateral mínimo de 11 % del ancho lógico. En la prueba de un tablero de 320 dp, equivale a 35,2 dp como mínimo; el test exige 32 dp. La banda vertical disponible se calcula con una semialtura de slot del 2,7 % de la altura lógica, manteniendo la separación física mínima entre plataformas.

El Worker y el lector Android ya están conectados localmente al formato v2. La publicación de Cloudflare sigue pendiente; el Worker en producción no cambia hasta que se haga un despliegue explícito.

### Decoración cosmética del cliente — paso d

`MapDecorationGenerator.kt` crea árboles, arbustos, rocas y flores en Android usando una semilla y un stream pseudoaleatorio separado. Su muestreo mantiene separación entre elementos y descarta ubicaciones junto al camino, las plataformas, las murallas y los castillos. Se dibuja como decoración; no participa en las reglas de combate ni en la geometría que valida el servidor.

Las pruebas unitarias comprueban que una semilla repita el mismo resultado y que los objetos respeten las zonas libres.

Para revisar las formas y métricas en el navegador, desde esta carpeta ejecuta:

```powershell
node debug-map-server.mjs
```

Abre `http://127.0.0.1:4173/debug-map.html`. La rejilla compara doce semillas, dibuja los slots coloreados por jugador y muestra los círculos de alcance y métricas del score. El servidor de previsualización escucha solo en el propio dispositivo; no despliega ni contacta Cloudflare. Detenlo con `Ctrl+C`.

Para medir 1.000 semillas y revisar diversidad, formas, longitudes, curvas, respaldos, fallos y margen lateral de 32 dp:

```powershell
npm run map:stats
```

La prueba completa se ejecuta con:

```powershell
node --test map-v2.test.mjs
```

La suite recorre 1.000 semillas y comprueba determinismo, caminos distintos para semillas distintas, validez del camino y de los slots, cobertura, simetría, siete huecos por jugador, cinco respaldos válidos y selección de 20 candidatos. También valida el contrato del mapa completo enviado por el Worker, el registro de semilla y arquetipo al emparejar, y que `game_start` difunde el mismo mapa a los dos jugadores.

### Integración con Worker y Android — paso e

El Durable Object crea el mapa v2 cuando forma la pareja y guarda la geometría completa en la partida. `match.found` la envía para que los móviles preparen la vista y `game_start` vuelve a incluirla cuando empieza la partida. Se conservan las métricas y los intervalos de cobertura; no se envía decoración. Android valida el formato, normaliza las coordenadas, genera la decoración a partir de la semilla y muestra siete plataformas iguales por jugador. Solo los huecos propios responden al toque y pueden mostrar selección o alcance; los del rival son solo visuales. El cliente ignora campos desconocidos procedentes de formatos anteriores.

El servidor todavía no simula el combate. La construcción local guarda torre y gasto en el estado del Durable Object y difunde la confirmación; no ataca ni ejecuta reglas de combate. `GET /health` informa `mapGeneratorVersion: v2` cuando se despliegue este código. El código local no ha sido desplegado ni enviado con `git push`.

### Construcción de torres — pasos D y E

El prototipo local muestra las cuatro cartas actuales; tocar una carta de torre permite seleccionar huecos propios libres y mantenerla pulsada permite arrastrar una miniatura. El servidor obtiene costes y reglas numéricas de `src/match-balance.mjs`, guarda una copia con la versión `fort-rush-2026-11-23.1` en cada partida y la envía en `game_start`. La economía empieza con 150 monedas, tiene tope pasivo de 1000, produce 5 monedas/s durante 180 segundos y 10 monedas/s durante el último minuto. Las torres cuestan 100 y 130 monedas y su cooldown confirmado es 0. La app usa el balance recibido para presentar coste e ingreso; el servidor sigue validando el saldo/propiedad/ocupación/fase y evita repetir una construcción aceptada con el mismo `actionId`. Los campos de combate se documentan y versionan, pero aún no están activos porque la simulación sigue pendiente.

Comprobado localmente el 22/11/2026: `npm test` pasó 29/29; `:app:compileDebugKotlin` y los tests Android `ArenaSlotPickingTest` y `ArenaTowerBuildRulesTest` pasaron. No se ha desplegado el Worker ni se ha comprobado el arrastre en un dispositivo real.

Se añadió una prueba unitaria de lectura del payload v2. En esta sesión, `:app:compileDebugKotlin` pasó, pero la ejecución de tests Android no terminó: Javac recibió `AccessDeniedException` al leer un JAR de la caché Gradle, por lo que el test nuevo del parser sigue pendiente. Las pruebas de decoración del paso d sí pasaron anteriormente. La prueba visual con el Worker desplegado desde dos dispositivos y el despliegue siguen pendientes.


## Pruebas del generador de mapas

Estas son pruebas automáticas locales; no sustituyen la comprobación visual en Android ni la prueba con dos redes.
