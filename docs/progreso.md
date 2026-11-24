# Progreso de FortRush

Actualizado: 24/11/2026

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
