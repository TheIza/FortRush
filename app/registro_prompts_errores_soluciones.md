# Registro de prompts, errores y soluciones

Este documento recoge las peticiones, los problemas encontrados, sus soluciones y las comprobaciones del trabajo en FortRush. Se irá ampliando con las siguientes tareas.

## Criterio de documentación

- Registrar los prompts del usuario, las decisiones relevantes y los errores reales, incluidos los del asistente o del entorno.
- Distinguir entre una solución comprobada, un comportamiento provisional y un problema pendiente.
- Leer de nuevo `resumen_muralla.md` al comenzar cada nueva tarea: el usuario lo actualiza y su versión vigente prevalece sobre las ideas anteriores del resumen. El registro conserva el historial, aunque cambien las decisiones del proyecto.
- No se puede borrar selectivamente el historial de la conversación; sí dejar de usar como referencia las decisiones que el resumen actualizado sustituya.

## 2026-11-10 — Primera pantalla principal

### Prompt del usuario

> tienes un resumen_muralla.md en app/, vamos a tantear el terreno creando 1 pantalla principal donde podemos ver el nombre del juego y 1 boton iniciar, las screens estaran en /fortrush/ui/Screen

### Trabajo realizado

- Lectura de `app/resumen_muralla.md` y revisión de la estructura existente de Kotlin y Jetpack Compose.
- Creación de `app/src/main/java/org/TheIza/fortrush/ui/Screen/MainScreen.kt`.
- Pantalla con el título «FortRush» y un botón «Iniciar», centrados, con una vista previa de Compose.
- Sustitución del saludo de ejemplo en `MainActivity.kt` por la nueva pantalla.
- Textos añadidos a `app/src/main/res/values/strings.xml`.
- El botón recibe un callback. De momento, la actividad muestra el aviso «La partida estará disponible próximamente»; todavía no inicia una partida ni abre otra pantalla.

### Errores y soluciones

1. **Bloqueo de ejecución local.** Los primeros comandos fallaron con `approval required by policy, but AskForApproval::Granular.sandbox_approval is false`. No se modificaron archivos durante ese bloqueo. Más adelante cambió la configuración de permisos de la sesión y la lectura y ejecución funcionaron. No se ha confirmado qué acción concreta produjo el cambio.
2. **Instrucción incorrecta para la interfaz utilizada.** El asistente indicó buscar un control de permisos debajo del cuadro de escritura, suponiendo una aplicación gráfica. El usuario aclaró que estaba en la terminal. Se corrigió la indicación y, al comprobar el acceso de nuevo, ya funcionaba; no fue necesario indicar un comando de configuración adicional.
3. **Consulta de Git fallida.** `git status --short` devolvió `fatal: not a git repository (or any of the parent directories): .git`. No impidió editar ni compilar. No se creó un repositorio ni se resolvió este punto.
4. **Advertencia de compilación sobre bibliotecas nativas.** Gradle indicó que no podía eliminar símbolos de `libandroidx.graphics.path.so` y `libdatastore_shared_counter.so` y que las empaquetaría tal cual. No requirió cambios para completar esta compilación.

### Comprobación y resultado

- Comando: `.\gradlew.bat :app:assembleDebug`.
- Resultado: `BUILD SUCCESSFUL`; APK de depuración generado.
- No se ha probado visualmente en un emulador ni en un dispositivo.

## 2026-11-10 — Solicitud de registro del trabajo

### Prompt del usuario

> te queria comentar una cosa, me podrias hacer un registro de los prompts, errores y soluciones que vamos encontrando en el trabajo, quiero que lo guardes en el mismo lugar donde se enceutnra el .md que te di (este .md lo estoy actualizando asu que en el siguiente prompt seguramente tengas que leertelo de nuevo y borrar memoria de lo anterior)

### Solución

- Creación de este archivo en `app/`, junto a `resumen_muralla.md`.
- Incorporación del historial disponible de la primera pantalla y sus incidencias.
- Se establece releer el resumen en cada nueva tarea y dar prioridad a su contenido actualizado.
- Durante la búsqueda de documentación, `rg` informó de que `.agents` y `.codex` no existían en el proyecto. Sí encontró `app/resumen_muralla.md`; no fue necesario crear esas carpetas.

### Resultado

Registro inicial guardado. Esta tarea solo añade documentación y no requiere recompilar la aplicación.
