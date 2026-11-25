# FortRush — Diario de desarrollo con IA

## 18/11/2026 — Correcciones visuales y geometría de la arena

### Objetivo

Responder a la revisión visual de la arena: muralla continua de borde a borde, castillos más grandes, camino visible con variaciones y cuatro plataformas por jugador colocadas junto al camino en posiciones reflejadas.

### Herramientas

- Codex para inspeccionar y modificar Kotlin/Jetpack Compose, el generador Node.js y la documentación.
- ImageGen integrado para crear sprites separados de puerta intacta y puerta derrumbada, usando como referencias los sprites de piedra y puerta ya existentes.
- Gradle para compilar la APK de depuración.
- Node.js para comprobar la generación y validación geométrica de mapas.

### Decisiones

- Se conserva el arte existente de piedra y se dibuja de manera continua en toda la anchura. La puerta pasa a ser un sprite central independiente; al destruirla se abre solo el ancho del camino.
- Los castillos aumentan de tamaño y se dibujan detrás de la muralla.
- El generador del servidor elige cuatro huecos junto al recorrido del jugador y calcula los del rival con un giro exacto de 180°.
- La prueba con bot genera una muestra local aleatoria al abrirse. No se conecta al Worker y no valida partidas online.
- La geometría online sigue siendo responsabilidad del Worker. El código está actualizado en el repositorio, pero esta versión no se ha desplegado.

### Investigación, errores y ajustes

- La captura del modo bot mostraba siempre el mismo camino porque la muestra Android utilizaba una geometría fija.
- El primer ajuste del generador rechazó demasiados mapas por colisiones y límites de separación demasiado estrictos. Se revisaron las causas y se ajustaron la ruta, las distancias y la selección de huecos.
- Se comprobó la generación con 50 semillas: 50 mapas válidos y diferentes, cuatro huecos por jugador. Las cinco plantillas de respaldo también pasaron la validación.

### Comprobaciones realizadas

- `node --check server/cloudflare-poc/src/map-generator.mjs`: correcto.
- Generación de 50 mapas y validación geométrica: correcto.
- Validación de cinco mapas de respaldo: correcto.
- `:app:assembleDebug --offline --no-daemon`: correcto.
- `git diff --check`: sin errores de espacios; Git mostró solo avisos de conversión LF/CRLF en archivos que ya estaban modificados.

### Pendiente

- Instalar esta APK y revisar visualmente el resultado en un dispositivo.
- Desplegar el Worker actualizado para que las partidas online reciban el nuevo generador.
- Comprobar desde dos móviles que ambos reciben exactamente el mismo mapa.
- Las pruebas automáticas completas de `npm test` no se ejecutaron en esta sesión.
### Ajuste de las zonas de construcción y el sprite del muro — 18/11/2026

- Se añadieron franjas no construibles junto a la mitad del mapa y desde cada muro hacia el borde de ese lado. La validación considera el tamaño completo de la plataforma, no solo su centro; los huecos del rival se obtienen por giro de 180°.
- Se alineó la arena local con bot a las mismas zonas y se sustituyó su ubicación de respaldo para que tampoco coloque plataformas en esas franjas.
- Se añadió un límite superior a la distancia de cada plataforma respecto al camino: ahora debe quedar dentro de la banda invisible ampliada del camino, sin invadir el camino visible. El servidor y la muestra local usan el mismo intervalo.
- Al inspeccionar `muro_tramo.png` se encontró una sombra gris semitransparente dibujada bajo los bloques. Se generó `muro_tramo_limpio.png`, conservando las piedras y eliminando esa sombra exterior; la versión original se mantiene.
- La primera comprobación detectó que dos de las cinco plantillas anteriores ya no cumplían la nueva zona libre. Se sustituyeron por cinco plantillas deterministas que sí pasan la validación completa.
- `node --check` y la comprobación de 50 semillas pasaron: 50 mapas válidos y distintos, con cuatro espacios por jugador y zonas/simetría correctas; las cinco plantillas de respaldo también pasaron.
- `:app:assembleDebug --offline --no-daemon` terminó correctamente; se generó `app/build/outputs/apk/debug/app-debug.apk`.
- Pendiente de validar visualmente la APK en un dispositivo. El Worker actualizado tampoco está desplegado aún.

### 19/11/2026 — Proximidad de las plataformas al camino

- Se definió una banda invisible alrededor del camino. Cada plataforma debe conservar una separación de `0.012` respecto al borde visible y su borde debe alcanzar la banda, que añade `0.025` al ancho del camino.
- La validación rechaza huecos fuera del intervalo de distancia permitido. Se aplica al generador online y a la muestra con bot.
- `node --test` pasó: 7 de 7 pruebas, incluida una que aleja un hueco del camino para comprobar que se rechaza. Las 200 semillas de variación y los cinco mapas de respaldo siguen siendo válidos.
- `:app:assembleDebug --offline --no-daemon` compiló correctamente el 19/11/2026.
- Pendiente: desplegar el Worker para partidas online e instalar la APK para revisar visualmente las plataformas y la muralla.

### 19/11/2026 — Corrección del cierre al abrir la partida con bot

- Logcat del emulador mostró que `makeDebugSlots` lanzaba `IllegalStateException` cuando la muestra aleatoria no encontraba cuatro candidatos.
- Se cambió el modo bot para usar cuatro huecos de respaldo junto al camino si el muestreo o el espaciado aleatorio no encuentran una combinación; el flujo ya no falla por esos `check`.
- `:app:assembleDebug --offline --no-daemon` compiló correctamente.
- `:app:installDebug --offline --no-daemon` instaló la versión corregida en el emulador conectado.
- Pendiente: pulsar **Partida con bot** en el emulador y confirmar que la arena abre sin cerrar la app.

## 24/11/2026 — Cierre del balance en el proyecto principal

### Objetivo y acciones
- Integrar el commit de balance en el repositorio AndroidStudioProjects\FortRush y en main, que es la rama conectada a Cloudflare.
- Centralizar la configuración por partida, alinear el radio de flechas y reflejarlo en game_start.
- Corregir el test JVM para que org.json use una implementación real, no el stub de Android.
- La usuaria confirmó con captura el despliegue Ready de main. GET /health devolvió status ok y matchBalanceVersion fort-rush-2026-11-23.1.

### Comprobaciones reales
- npm test: 33/33 pasaron.
- Al principio, seis tests Kotlin fallaron porque Android devuelve “not mocked” para org.json.JSONObject; esto también afectaba a los tests existentes del parser del mapa.
- Se añadió org.json:json:20260814 solo a testImplementation. :app:testDebugUnitTest terminó en BUILD SUCCESSFUL e incluyó compileDebugKotlin.
- No se probó todavía una partida manual con el APK actualizado ni un combate real.

### Resultado y siguiente paso
- El commit 901d2b0 se empujó a main y la usuaria confirmó que Cloudflare lo mostró Ready.
- El punto 05 queda verificado en contrato, servidor, parser Android y test JVM.
- Siguiente tarea: punto 06, pruebas explícitas de dos identidades y rechazo de identidad duplicada. No se han modificado todavía las reglas de combate.

## 24/11/2026 — Punto 06: dos identidades y creación de partida

### Objetivo y cambios
- Comprobar que dos UID diferentes producen una sola partida con un matchId común, y que una cuenta conectada no pueda abrir una segunda conexión.
- Se permitió inyectar el verificador de identidad en WebSocketProbe para los tests. La ejecución normal mantiene verifyFirebaseIdentity como valor por defecto.
- Se añadió matchmaking.test.mjs con un flujo de autenticación controlado, unión a cola, emparejamiento y rechazo de UID repetido.

### Comprobaciones reales
- node --check pasó en index.mjs y matchmaking.test.mjs.
- npm test: 35/35 pasaron, incluidas 1000 semillas, balance, perfil, torres e identidades.
- El test de identidad usa UIDs simulados; no valida un token real de Google ni sustituye la prueba manual con dos dispositivos.

### Siguiente paso
- El mapa del punto 07 ya tiene cobertura de 1.000 semillas; continuar con cola/cancelación, carga y cuenta atrás antes del combate.
- La primera mecánica de combate desplegable sigue siendo el punto 10.

## 24/11/2026 — Puntos 08 y 09: cola, cancelación y carga

### Objetivo y cambios
- Permitir que más de dos jugadores mantengan una espera ordenada mientras una partida está activa.
- Añadir límite de 30 segundos para confirmar la carga del mapa. Si solo un jugador llegó a cargar, regresa a la cola con su secuencia anterior; el otro queda fuera de esa búsqueda.
- Ampliar los tests de emparejamiento y preparación del Worker.

### Investigación
- Se revisaron las páginas oficiales vigentes de [límites de Durable Objects](https://developers.cloudflare.com/durable-objects/platform/limits/) y [precios del plan gratuito](https://developers.cloudflare.com/durable-objects/platform/pricing/). El Worker sigue configurado con una clase Durable Object SQLite; no se activó facturación ni se cambió de plan.

### Comprobaciones reales
- `node --check` pasó para `index.mjs` y `matchmaking.test.mjs`.
- `node --test matchmaking.test.mjs`: 5/5 pasaron, incluidos tercer jugador en espera, cancelación aislada, timeout, secuencia preservada y `game_start` compartido.
- Tras añadir estos casos, `npm test`: 38/38 pasaron; incluye los 1.000 mapas y las pruebas de perfil, economía, construcción y preparación de partida.
- No se han probado todavía estos flujos en móviles reales.

### Siguiente paso
- Repetir la suite completa, registrar el resultado y empezar la simulación autoritativa de tropas y combate.
- La medición de cuota/carga de una partida de cuatro minutos sigue pendiente; las cuotas del plan Free no equivalen a una garantía de disponibilidad.
## 24/11/2026 — Punto 10–20: primera partida jugable

### Objetivo y trabajo realizado
- Continuar desde `main` después de las tareas de balance, mapas, cola y carga.
- Implementar en el Worker la simulación determinista por paso fijo: despliegue de Espadachín/Escudero, movimiento por distancia del camino, enfrentamientos simultáneos, torres, proyectiles, muro/castillo, economía, venta, rendición, temporizador y muerte súbita.
- Conectar Android con las instantáneas de combate del Worker, el despliegue de tropas en el camino, los estados de muros/castillos y la pantalla de resultado.
- Documentar mensajes nuevos en `docs/protocolo.md` y registrar lo pendiente sin dar por probada una partida real.

### Decisiones y correcciones
- Se conservó el balance que ya estaba aprobado y versionado; no se inventaron costes o estadísticas nuevos.
- En una prueba falló la comparación literal de los mensajes `game_start`, porque `viewerPlayerNumber` debe ser distinto para cada cliente. Se corrigió el test para comparar el contenido compartido y verificar por separado el número local.
- Android usa spritesheets existentes para dibujar las tropas y elige el punto más cercano del camino dentro de la zona válida del jugador. La validación definitiva sigue en el Worker.

### Comprobaciones reales
- `npm test`: 53/53 tests pasaron; incluye 1000 mapas, combate, torres, monedas, muro, muerte súbita, cola, carga y API de perfil simulada.
- `:app:compileDebugKotlin`: correcto.
- `:app:testDebugUnitTest`: correcto, incluidos parseo de snapshot/resultado y conversión de toque a posición de camino.
- `:app:assembleDebug`: correcto; generó `app/build/outputs/apk/debug/app-debug.apk`.
- Tras el push a `main`, el GET público a `/health` respondió `status: ok`, `mapGeneratorVersion: v2` y `matchBalanceVersion: fort-rush-2026-11-24.1`; esto confirma que el Worker publicado sirve la versión de combate. No se ha probado una partida en dos móviles ni con credenciales Firebase auténticas.

### Pendiente
- Instalar la APK en dos dispositivos con UIDs distintos y probar una partida completa; el despliegue se verificó mediante `/health`.
- La reconexión tras corte, persistencia del resultado/recompensa del perfil y prueba de reinicio de Durable Object siguen pendientes.
- Aprendizaje personal: pendiente de que la usuaria lo redacte o confirme.

## 25/11/2026 — Emparejamiento detenido en posición 2

### Objetivo y hallazgos
- Resolver la captura en que la app mostraba posición 2 y no encontraba rival. La usuaria confirmó que dos dispositivos seguían en «Conectando» y luego fallaban; una segunda captura mostró que sí llegaban a la cola.
- La comprobación desde el entorno confirmó que la ruta `/ws` acepta el handshake público y que la APK apunta al mismo host Cloudflare y al mismo Firebase project ID que el Worker. La comprobación de handshake usó una credencial falsa y solo valida transporte, no identidad.
- La revisión del flujo encontró que el Worker no volvía a llamar a `pairQueue` con un Durable Object que guardaba un estado viejo de partida, y que no aislaba `match.countdown` por partida. También podía perder el orden de espera al cerrarse un socket durante la carga.

### Cambios y pruebas reales
- El Worker ahora intenta emparejar si ya hay dos entradas, limpia registros antiguos de partidas sin simulación, recupera al jugador conectado durante una cancelación previa al inicio y conserva el orden de quienes esperaban detrás. Cuenta atrás e inicio se envían solo a la pareja correspondiente; el emparejamiento continúa tras acabar una partida.
- Android mantiene el primer rechazo del servidor (por ejemplo, cuenta repetida o token inválido) en vez de reemplazarlo con un error genérico de cierre y muestra el código HTTP o tipo de excepción cuando corresponda.
- `npm test`: 58/58 pasaron. Suite dirigida de cola y mapa: 25/25; `node --test matchmaking.test.mjs`: 10/10 después del último ajuste. `:app:testDebugUnitTest` y `:app:assembleDebug` terminaron con `BUILD SUCCESSFUL`.
- Commit `1cf8f02` (`fix: destrabar cola y aislar partidas`) está en `main` y fue empujado a GitHub. El Worker existente `round-king-86b7` se desplegó con Wrangler, sin crear servicios nuevos ni cambiar el plan. `/health` confirmó `status: ok`, `matchmakingVersion: lifecycle-2026-11-25.1`, `matchBalanceVersion: fort-rush-2026-11-24.1`; versión Cloudflare `5e6491d1-4521-456e-be6f-1e669108e671`.
- La comprobación pública de WebSocket usó un token deliberadamente inválido: valida handshake y transporte, no inicio de sesión real ni emparejamiento entre dos cuentas. La prueba real con dos cuentas sigue pendiente; no se atribuye con certeza un estado concreto de producción sin observarlo.

### Pendiente
- En ambos móviles, cancelar la búsqueda atascada y volver a pulsar «Jugar» para validar emparejamiento real entre los dos UID. No hace falta reinstalar para recibir el arreglo del Worker. Si vuelve a fallar, registrar el mensaje que muestre la app y revisar los eventos nuevos del Worker.
## 25/11/2026 — Panel de partida, recompensa y movimiento

### Objetivo y cambios locales
- Mostrar el apodo real de la cuenta emparejada y resolver el saldo de perfil faltante al final de partida.
- Ampliar el avatar de la otra persona y hacer más legibles las vidas de muro y castillo de ambos lados; los números pasan a 11 sp y las barras a 9 dp.
- Reducir los saltos de movimiento sin cambiar las reglas: las instantáneas del servidor pasan de 1 s a 250 ms; Android interpola posiciones con la distancia recibida sobre el camino y anima proyectiles entre actualizaciones.
- Guardar resultado, contadores y monedas de perfil en una transacción Firestore. El ledger por `matchId` protege contra duplicados; el Durable Object conserva y reintenta operaciones pendientes.
- Añadir a la lista futura la selección de avatares desde Usuario.

### Decisiones
- Se usaron los valores ya documentados: 250 monedas por victoria, 100 por empate y 0 por derrota. El saldo del perfil sigue separado de las monedas temporales de batalla.
- No se aceptan apodos del payload Android como identidad: el Worker consulta los apodos asociados a los UID autenticados.
- El intervalo de 250 ms implica unas 960 alarmas en una partida máxima de cuatro minutos. Se conservará Cloudflare Workers Free, sin activar facturación. La documentación oficial consultada indica 100.000 solicitudes diarias de Durable Objects en Free, incluidas alarmas; superar el límite gratuito rechaza operaciones.

### Comprobaciones reales y límites
- `node --check` pasó para `index.mjs`, `profile-api.mjs` y `match-engine.mjs`.
- `wrangler deploy --dry-run` empaquetó correctamente el Worker y terminó sin desplegarlo.
- `:app:compileDebugKotlin --offline` había terminado con `BUILD SUCCESSFUL` antes del último ajuste visual de 6 a 9 dp. La compilación posterior se detuvo antes de compilar por `AccessDeniedException` en `.gradle/9.6.0/fileHashes.lock`; no da un resultado sobre Kotlin.
- `git diff --check` pasó. No se ejecutaron tests automáticos ni una prueba multijugador real. Los cambios siguen locales en `main`; el Worker publicado sigue en la versión anterior hasta ejecutar las pruebas y desplegar.
# 25/11/2026 — revisión del despliegue de tropas

- Petición: revisar por qué al elegir una tropa y tocar el camino no aparecía, resolverlo sin tocar lo demás y separar un poco más las barras de vida del borde.
- Revisión de código: el modo bot rechazaba el callback de despliegue y su mapa no incluía longitudes acumuladas; por eso el detector de camino no encontraba ninguna posición. La ruta online sí envía la acción al Worker.
- Cambio: completar los datos de longitud del mapa local; en modo bot, cobrar el coste local y mostrar una formación visual estática; fuera de la zona verde, mostrar una indicación. La simulación online y sus valores no cambian. El medidor de vida reserva 4 dp inferiores.
- Comprobación realizada: Worker 58/58; Android `testDebugUnitTest` y `assembleDebug` exitosos; 5/5 pruebas del destino de tropas. Queda pendiente la revisión visual manual con la APK y probar el despliegue online desde dos cuentas.
- Publicación: commit `524e613` empujado a `main`. Tras esperar el build, `/health` devolvió `matchmakingVersion: profile-rewards-2026-11-25.1`; no se afirma que el apodo real se haya comprobado manualmente en una partida.
