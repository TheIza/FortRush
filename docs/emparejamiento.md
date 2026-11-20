# FortRush — Emparejamiento y sala de espera

Estado: prototipo de emparejamiento desplegado en Cloudflare. La usuaria confirmó que dos cuentas pueden llegar a la cuenta atrás. El generador v2 y su integración local con Android están implementados; la suite Node pasó. El test Android completo y la prueba desde dos dispositivos aún no se han completado, y esta versión no se ha publicado en Cloudflare.

## Confirmado por el documento de planificación v0.8

- Al pulsar **Jugar**, el jugador entra en una cola de emparejamiento en Internet.
- Se empareja con la primera persona disponible; no hay ranking, habilidad, códigos ni salas privadas.
- Mientras busca, ve su apodo, el estado de búsqueda y puede pulsar **Cancelar**.
- Cancelar durante la búsqueda elimina al jugador de la cola y lo devuelve a Inicio. Si vuelve a pulsar Jugar, entra al final.
- La partida se identifica y coordina desde un Cloudflare Worker con Durable Object. La identidad se obtiene verificando el ID token de Firebase Auth; nunca se acepta un UID enviado sin verificar.
- Al encontrar pareja, los dos clientes reciben el ID de partida, el apodo provisional `Rival de prueba` y una única geometría de mapa generada por el servidor para previsualizarla y confirmar la carga. La consulta del apodo real sigue pendiente.
- Ambos clientes deben confirmar que cargaron el mapa. Cuando ambos confirman, se muestra una cuenta atrás de 3, 2, 1 y luego comienza la partida. El timeout de carga de 30 segundos está pendiente de implementar; ahora mismo no se debe considerar disponible.
- La recuperación de posición de cola si falla la carga está descrita como comportamiento previsto, pero sus timeout y casos de error requieren pruebas de integración.
- El control **Cancelar** desaparece al emparejarse y no hay controles de partida durante la cuenta atrás.
- La sala de espera usa la estética medieval cartoon existente, muestra el apodo y ofrece un control accesible para cancelar.

## Regla confirmada para cancelar

- El botón **Cancelar** solo está disponible mientras el estado sea `QUEUED`.
- Al confirmar `match.found`, se desactiva; no se puede salir manualmente durante la carga del mapa ni la cuenta atrás.
- Si el rival se desconecta o la carga falla antes del inicio, se cancela esa preparación. El jugador que continúa conectado vuelve a la cola con su secuencia de turno original; quien se desconectó no conserva su turno.
- Si un mensaje de cancelación llega justo después del emparejamiento, el servidor lo rechaza con `CANCEL_NOT_ALLOWED`.

## Estados de sesión

`IDLE → QUEUED → MATCHED/LOADING → COUNTDOWN → IN_MATCH`

- `IDLE`: no está buscando.
- `QUEUED`: está en cola y puede cancelar.
- `MATCHED/LOADING`: pareja formada, datos/mapa recibidos y espera confirmación de carga. Cancelar manualmente está bloqueado.
- `COUNTDOWN`: ambos cargaron; cuenta atrás de tres segundos, controles bloqueados.
- `IN_MATCH`: termina la preparación y se navega a la partida.
- Un error de conexión devuelve un estado visible con opción de reintentar o volver a Inicio; el servidor evita que el mismo UID ocupe varias posiciones.

## Protocolo implementado en código

- WebSocket sobre `wss://` en el Worker de Cloudflare; el servidor Node.js que queda en `server/` es solo para pruebas locales.
- El móvil envía primero su ID token de Firebase; el servidor lo verifica y asocia el socket al UID.
- Mensajes implementados en el prototipo: `auth`, `queue.join`, `queue.cancel`, `queue.waiting`, `queue.cancelled`, `match.found`, `match.loaded`, `match.countdown`, `game_start`, `match.started` (del Worker anterior), `match.cancelled` y `error`.
- Los comandos de cliente incluyen un `requestId`; `queue.join` y `match.loaded` son idempotentes por estado y las transiciones se validan en el servidor.
- El Durable Object guarda el estado de cola y preparación. Aun así, una conexión WebSocket puede cortarse y no se ha implementado reconexión ni recuperación del jugador tras un reinicio.
- Al empezar, el evento `game_start` incluye el objeto de mapa completo. Android lo valida y usa esa geometría al entrar en la arena; el servidor no envía solo la semilla. El cliente también acepta `match.started` del Worker anterior mientras se despliega la actualización.

## Comprobaciones pendientes

- Un cliente puede entrar en cola, ver que busca y cancelar; tras cancelar vuelve a Inicio y no queda emparejable.
- Dos UID distintos conectados desde redes distintas forman una sola pareja.
- Un UID no puede entrar varias veces en la cola ni formar dos partidas simultáneas.
- Ambos reciben el mismo `matchId`, apodos y geometría de mapa.
- Solo tras dos confirmaciones de carga empieza la cuenta atrás 3, 2, 1.
- Un timeout de carga a los 30 segundos reencola solo al jugador que completó la carga y conserva su orden.
- Reintentar mensajes no crea parejas duplicadas.
- Desconectar/reiniciar servidor no deja al cliente en una sala ficticia; se informa y se puede buscar de nuevo.
- El botón **Cancelar** no aparece durante `MATCHED/LOADING` ni `COUNTDOWN`; queda pendiente comprobarlo en un dispositivo.

## Limitaciones conocidas

- El backend activo es Cloudflare. No se ha demostrado recuperación automática de una conexión WebSocket después de un reinicio o cambio de red.
- La pantalla muestra que la cuenta atrás terminó, pero el tablero de combate y sus controles siguen pendientes. Esta entrega no simula una partida jugable.
- Los dos servidores configurados localmente apuntan a Cloudflare. El cambio de URL requiere reconstruir el APK; los cambios solo del Worker no requieren reinstalarlo.


## Generación del mapa

- El Worker genera la geometría una sola vez al emparejar y envía el mismo objeto a ambos móviles. Kotlin no vuelve a generar el mapa.
- El generador v2 selecciona por semilla uno de cinco arquetipos y produce un camino sinuoso continuo con simetría de 180 grados. Coloca siete huecos de torre normales por jugador por defecto (configurable), su valor geométrico y segmentos de cobertura.
- Se valida que el camino permanezca dentro del tablero, no se cruce ni se acerque a sí mismo de forma incompatible con su ancho, y que los huecos queden fuera del camino.
- Se evalúan 20 variantes del arquetipo elegido; si no se obtiene un mapa válido tras los intentos configurados, se elige una de cinco geometrías de respaldo.
- Los objetos no jugables se generan determinísticamente en cada cliente a partir de la semilla y no forman parte de la geometría del servidor.
- Es una geometría compartida ya conectada al flujo local de emparejamiento, pero todavía no es un mapa de combate terminado.
