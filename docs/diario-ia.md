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
- Al inspeccionar `muro_tramo.png` se encontró una sombra gris semitransparente dibujada bajo los bloques. Se generó `muro_tramo_limpio.png`, conservando las piedras y eliminando esa sombra exterior; la versión original se mantiene.
- La primera comprobación detectó que dos de las cinco plantillas anteriores ya no cumplían la nueva zona libre. Se sustituyeron por cinco plantillas deterministas que sí pasan la validación completa.
- `node --check` y la comprobación de 50 semillas pasaron: 50 mapas válidos y distintos, con cuatro espacios por jugador y zonas/simetría correctas; las cinco plantillas de respaldo también pasaron.
- `:app:assembleDebug --offline --no-daemon` terminó correctamente; se generó `app/build/outputs/apk/debug/app-debug.apk`.
- Pendiente de validar visualmente la APK en un dispositivo. El Worker actualizado tampoco está desplegado aún.
