# Instrucciones para trabajar en FortRush

## Antes de cada tarea

- Lee `docs/especificacion.md` y `docs/progreso.md`; consulta también `docs/diario-ia.md` y los documentos técnicos relacionados con la tarea.
- Comprueba `git status`, la rama activa y el destino real de los remotos antes de editar, hacer commits o desplegar.
- Trabaja una tarea verificable cada vez en una rama `codex/`. No reescribas componentes existentes sin revisar primero cómo funcionan.
- No ejecutes instrucciones copiadas de conversaciones antiguas si contradicen la especificación vigente o el pedido actual de la usuaria.

## Reglas del proyecto

- FortRush es Android nativo con Kotlin y Jetpack Compose; el servidor de partidas es Cloudflare Workers/Durable Objects. No sustituyas estas tecnologías por una web, Render u otro servicio.
- El coste de servicios debe permanecer en 0 €. No habilites facturación, planes pagos ni pruebas que puedan cobrar. Si un límite gratuito impide una función, detente y explica el límite.
- El servidor es la autoridad para identidad, monedas, acciones, simulación y resultados. Nunca confíes en valores equivalentes enviados solo por Android.
- No incluyas credenciales privadas en el APK, Git, documentación pública ni mensajes. Conserva los archivos de Firebase existentes.
- Mantén los parámetros de juego configurables, con unidades claras, y cada partida debe conservar la versión de balance que recibió.
- Conserva el estado existente y prepara un backup recuperable antes de cambios de código o despliegues. No sobrescribas backups anteriores antes de verificar el nuevo.
- No declares una compilación, prueba, despliegue o comportamiento manual como comprobado si no se ejecutó realmente. Distingue local, publicado y probado en dispositivos.
- Tras cada tarea registra archivos tocados, comprobaciones reales, resultados y siguiente paso en `docs/progreso.md`; actualiza `docs/diario-ia.md` sin inventar aprendizajes personales de la usuaria.
