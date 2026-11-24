# Progreso de FortRush

Actualizado: 24/11/2026

## Repositorio y recuperación

- Proyecto de trabajo: C:\Users\izarb\AndroidStudioProjects\FortRush.
- Remoto: https://github.com/TheIza/FortRush.git.
- La usuaria pidió que los commits se hagan en main. El commit 901d2b0, “feat: centralizar balance y economia de partida”, está en main y se subió a GitHub.
- Se creó y verificó el bundle de recuperación C:\Users\izarb\Documents\ChatGPT\FortRush\staging\fortrush-main-before-balance-2026-11-24.bundle; contiene la referencia main anterior d612c9a. No se cambiaron local.properties, Firebase ni secretos.
- Cloudflare muestra Ready para el commit 901d2b0 en main. GET /health respondió status ok, profileApiConfigured true, mapGeneratorVersion v2 y matchBalanceVersion fort-rush-2026-11-23.1.

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
- /health confirmó fort-rush-2026-11-23.1.
- Node 35/35 y Android tests pasaron.
- Falta una prueba manual de una partida usando el APK actualizado.

### 06 — Dos identidades y creación de partida
Estado: completada mediante tests automatizados.
- Dos UID distintos crean una sola partida con dos jugadores numerados y ambos reciben el mismo matchId.
- Una segunda conexión autenticada con el UID ya conectado recibe ALREADY_CONNECTED y se cierra.
- El test sustituye el verificador de Firebase por una función controlada. No usa tokens reales ni reemplaza la validación JWT de producción.
- npm test: 35/35 pasaron.

## Siguiente paso

Auditar y cerrar el punto 07 con las pruebas existentes de geometría, siete huecos por jugador, simetría y mapas de respaldo. Luego verificar cola/cancelación (08) y carga/cuenta atrás (09). El primer combate jugable empieza en el punto 10.
