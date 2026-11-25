# Progreso de FortRush

Actualizado: 25/11/2026

## Repositorio y recuperación

- Proyecto de trabajo: C:\Users\izarb\AndroidStudioProjects\FortRush.
- Remoto: https://github.com/TheIza/FortRush.git.
- La usuaria pidió que los commits se hagan en `main`. El commit de combate `63ebc9c` está en `main`; la versión desplegada se verificó con `/health`.
- Se creó y verificó el bundle de recuperación C:\Users\izarb\Documents\ChatGPT\FortRush\staging\fortrush-main-before-balance-2026-11-24.bundle; contiene la referencia main anterior d612c9a. No se cambiaron local.properties, Firebase ni secretos.
- Antes del combate se creó y verificó `staging/fortrush-main-before-gameplay-v2-2026-11-24.bundle`; contiene la referencia `b0da81d` y el historial completo.
- El commit `63ebc9c` (`feat: implementar combate y sincronizar arena`) está en `main`. La consulta posterior a `/health` devolvió `status: ok`, `mapGeneratorVersion: v2` y `matchBalanceVersion: fort-rush-2026-11-24.1`; el Worker de combate está publicado.

## Tareas del plan

### 00 — Auditoría inicial
Estado: completada por revisión del repositorio y del Worker. El juego todavía no tiene simulación de combate.

### 01 — Copia recuperable
Estado: completada para la versión base d612c9a; bundle verificado antes de integrar balance en el proyecto AndroidStudio.

### 02 — Pruebas existentes y compilación Android
Estado: completada el 24/11/2026.
- npm test: 35/35 pasaron, incluida la suite de 1000 mapas y los dos tests de identidad del punto 06.
- :app:testDebugUnitTest: compilación y tests Kotlin completados correctamente después de añadir org.json solo a testImplementation.
- La biblioteca de tests es org.json:json:20260814; no forma parte de la app distribuida.

### 03 — Perfil, Google y apodos
Estado: parcial. Hay pruebas automatizadas del API, pero falta repetir en Android el flujo completo con cuenta nueva y existente.

### 04 — Viabilidad gratuita
Estado: parcial. Se consultó el plan Free y el Worker usa WebSocket hibernable; no hay aún simulación completa para medir el coste real de una partida de combate.

### 05 — Balance y protocolo
Estado: completada en código y despliegue inicial.
- El servidor conserva una versión de balance por partida y la entrega en game_start.
- `game_start` conserva el balance por partida. La consulta actual a `/health` confirma `fort-rush-2026-11-24.1` en el Worker desplegado.
- Node 35/35 y Android tests pasaron.
- Falta una prueba manual de una partida usando el APK actualizado.

### 06 — Dos identidades y creación de partida
Estado: completada mediante tests automatizados.
- Dos UID distintos crean una sola partida con dos jugadores numerados y ambos reciben el mismo matchId.
- Una segunda conexión autenticada con el UID ya conectado recibe ALREADY_CONNECTED y se cierra.
- El test sustituye el verificador de Firebase por una función controlada. No usa tokens reales ni reemplaza la validación JWT de producción.
- npm test: 35/35 pasaron.

### 07 — Mapa y huecos
Estado: completada por tests automatizados.
- La suite comprueba 1.000 semillas, siete huecos por lado, simetría, coberturas y mapas de respaldo.
- `npm test`: el test de 1.000 mapas pasó en la ejecución del 24/11/2026.

### 08 — Cola y cancelación
Estado: completada en código y tests automatizados.
- El emparejamiento toma a los dos primeros, conserva en cola a las personas siguientes mientras la partida está activa y actualiza sus posiciones.
- Una tercera conexión ya no se rechaza por superar el límite de dos; puede cancelar solo su propia espera.
- Test dirigido: un tercer jugador espera y cancela sin modificar la partida de los dos emparejados.

### 09 — Carga y cuenta atrás
Estado: completada en lógica del Worker y tests automatizados; falta prueba visual en dispositivos.
- La sala tiene un límite de carga de 30 segundos. Si solo una persona confirma carga, conserva su secuencia al volver a la cola; quien no cargó se retira.
- Dos confirmaciones inician la cuenta atrás compartida 3, 2, 1 y ambos reciben el mismo `game_start`.
- Los controles de juego todavía no aparecen durante carga/cuenta atrás. Las acciones de torre siguen rechazándose en fases distintas a `IN_MATCH`.
- `node --test matchmaking.test.mjs`: 5/5 pasaron; `npm test`: 38/38 pasaron el 24/11/2026.

## Estado actual — combate jugable en código (24/11/2026)

El punto 10–20 del plan tiene una primera implementación autoritativa en el Worker y una interfaz Android conectada. El commit `63ebc9c` está en `main` y `/health` confirma la versión `fort-rush-2026-11-24.1`. Esto confirma el despliegue del código, no una partida real de principio a fin.

- El servidor procesa despliegue de Espadachín y Escudero, movimiento sobre el camino, combate simultáneo, muros, castillos, torres de flechas y cañón, monedas, venta, rendición, temporizador y muerte súbita.
- Las acciones del jugador se identifican por la conexión autenticada; el servidor valida y guarda el estado del encuentro en el Durable Object. Las instantáneas llegan a ambos clientes.
- Android lee la instantánea inicial y las posteriores, muestra reloj, vidas, tropas y proyectiles; se puede seleccionar una tropa y tocar el camino en la mitad propia, construir/vender torres y rendirse con confirmación. La pantalla final permite volver a Inicio.
- El modo «prueba con bot» continúa siendo una vista local de mapa y construcción; no ejecuta esta nueva simulación. La prueba real es el emparejamiento online con dos UIDs.

### Comprobaciones de esta implementación

- `npm test`: 53/53 pasaron, incluida la generación de 1.000 mapas, economía, combate, resultados, venta, rendición, cola y balance versionado. No usa credenciales reales.
- `:app:compileDebugKotlin`: compilación correcta.
- `:app:testDebugUnitTest`: pruebas Kotlin correctas, incluidas lectura del snapshot, resultado empate y conversión de toques a distancia del camino.
- `:app:assembleDebug`: APK de depuración generada en `app/build/outputs/apk/debug/app-debug.apk`.
- La APK debug se compiló en `app/build/outputs/apk/debug/app-debug.apk` (112.271.030 bytes, 24/11/2026 20:51). `/health` confirma el Worker actualizado. Falta jugar una partida real con dos UIDs/dispositivos y revisar visualmente el flujo; no se ha probado la recuperación tras reinicio.
- El Worker no implementa recuperación de conexión de la app, abandono con plazo de 30 s, ni escritura persistente de estadísticas/recompensas de perfil. Si una conexión se corta, el cliente todavía no puede reanudar la partida automáticamente.

## Siguiente paso

Siguiente paso de beta: instalar la APK en dos dispositivos con dos cuentas distintas, iniciar emparejamiento, cargar la partida y comprobar despliegue de tropas, construcción/venta de torres, victoria/derrota y muerte súbita. La reconexión tras cortes y la recuperación tras reinicio siguen pendientes; no prometer continuidad ante esos fallos.

## Incidencia de emparejamiento — 25/11/2026

- La captura nueva muestra a la usuaria en la posición 2 de la cola, sin recibir partida. El perfil Android y el Worker usan el mismo proyecto Firebase y la ruta pública WebSocket acepta el protocolo desde una comprobación externa; el intento de prueba usó un token deliberadamente inválido, así que no valida la autenticación de una cuenta real.
- Revisión del código encontró una causa compatible con la sala atascada: la cola solo intentaba emparejar si no había una partida guardada. Un registro antiguo `IN_MATCH` de antes de la simulación, sin objeto de juego, podía quedar guardado y bloquear todas las parejas. También se detectó que la cuenta atrás se difundía a todos los sockets del Durable Object y que la desconexión durante la preparación podía borrar a jugadores que ya estaban esperando.
- Corrección local: reintentar el emparejamiento con colas de dos o más; retirar estados de partida antiguos irrecuperables; recuperar al jugador conectado cuando su rival desaparece durante carga/cuenta atrás y conservar el orden de la cola; enviar cuenta atrás solo a los dos sockets de esa partida; iniciar la siguiente pareja cuando termina la actual. La app conserva el error de autenticación/servidor recibido en vez de sustituirlo por un mensaje genérico de cierre.
- Pruebas del Worker: `npm test`, 58/58; pruebas dirigidas de cola y mapa, 25/25 y 10/10. Android: `:app:testDebugUnitTest` y `:app:assembleDebug` terminaron correctamente. La APK contiene el mensaje diagnóstico y se encuentra en la salida debug.
- Publicado en el Worker existente `round-king-86b7` mediante Wrangler; no se creó ningún servicio nuevo ni se cambió el plan. El GET público a `/health` confirmó `status: ok`, `matchmakingVersion: lifecycle-2026-11-25.1` y `matchBalanceVersion: fort-rush-2026-11-24.1`. ID de versión Cloudflare: `5e6491d1-4521-456e-be6f-1e669108e671`.
- El cambio quedó en `main` como commit `1cf8f02` (`fix: destrabar cola y aislar partidas`) y se empujó a GitHub. Tests: `npm test` 58/58; suite dirigida de cola y mapa 25/25; Android `:app:testDebugUnitTest` y `:app:assembleDebug` correctos.
- Pendiente de confirmación manual: dos cuentas reales deben cancelar la búsqueda atascada y volver a pulsar «Jugar». La prueba de handshake solo verificó el transporte con un token deliberadamente inválido; no se afirma todavía que dos jugadores reales hayan iniciado una partida.

## Cambios dentro de la partida — nombre, recompensas y fluidez (25/11/2026)

Estado: implementado en el árbol local de `main`; **todavía no desplegado en Cloudflare ni probado en dos móviles**.

- Se sustituyó el nombre de prueba «Rival de prueba» por el apodo leído por el servidor desde `Usuarios/{uid}.apodo`. Si Firestore no responde en la búsqueda, el juego puede continuar con el texto genérico «Jugador» y el Worker registra el error sin exponer credenciales.
- Al terminar una partida, el Worker acredita en Firestore 250 monedas persistentes al ganador, 100 a cada jugador en empate y 0 al derrotado. Actualiza también partidas completadas/victorias/derrotas/empates. La operación actualiza ambos perfiles y crea `ResultadosPartida/{matchId}` en una transacción; ese documento hace seguros los reintentos. Si Firestore falla, el Durable Object conserva un resultado pendiente y programa nuevos intentos.
- El panel de la arena muestra un avatar de castillo más grande y medidores separados para muro y castillo de ambos jugadores; los números pasan a 11 sp y las barras a 9 dp. La selección de avatar en el menú Usuario quedó anotada como trabajo futuro en `docs/mejoras-pendientes.txt`.
- La causa técnica del salto visual era la cadencia de un segundo de las instantáneas. El Worker ahora programa 250 ms durante combate y Android interpola tropas sobre la distancia real del camino y anima proyectiles entre instantáneas. No se modificó daño, movimiento, balance ni reglas de combate.
- El nuevo ritmo requiere aproximadamente 960 invocaciones de alarma por partida máxima de cuatro minutos. La cuota oficial vigente de Durable Objects Free es 100.000 solicitudes diarias e incluye alarmas; al superar el límite del plan gratis, Cloudflare rechaza operaciones en vez de cobrarlas. Esto reduce el margen teórico a unas 100 partidas máximas al día antes de contar las demás solicitudes. Fuentes: [límites de Workers](https://developers.cloudflare.com/workers/platform/limits/) y [precios de Durable Objects](https://developers.cloudflare.com/durable-objects/platform/pricing/).
- Copia recuperable previa a esta tarea verificada: `C:\Users\izarb\Documents\ChatGPT\FortRush\staging\fortrush-before-match-result-ui-2026-11-25.bundle`.
- Comprobado antes del retoque final de legibilidad: `node --check` en los tres módulos del servidor, `wrangler deploy --dry-run` (empaquetado correcto, sin desplegar), `git diff --check` y `:app:compileDebugKotlin --offline` (`BUILD SUCCESSFUL`). Tras aumentar las barras de 6 a 9 dp, `git diff --check` volvió a pasar; el intento de recompilar el checkout real se detuvo antes de compilar por `AccessDeniedException` al archivo `.gradle/9.6.0/fileHashes.lock`. No es un error Kotlin. No se ejecutó la suite de tests ni una prueba con cuentas reales.
- Pendiente: revisar transacciones con tests automatizados y credenciales simuladas; desplegar Worker; probar victoria por juego, victoria por rendición, empate, actualización del saldo del perfil, y comprobar la fluidez en dos dispositivos. El APK instalado no recibe el render interpolado hasta compilar e instalar esta versión Android.
