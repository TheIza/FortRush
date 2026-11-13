# FortRush — Emparejamiento y sala de espera

Estado: requisitos confirmados e implementación local preparada. La prueba con dos cuentas y dispositivos distintos sigue pendiente.

## Confirmado por el documento de planificación v0.8

- Al pulsar **Jugar**, el jugador entra en una cola de emparejamiento en Internet.
- Se empareja con la primera persona disponible; no hay ranking, habilidad, códigos ni salas privadas.
- Mientras busca, ve su apodo, el estado de búsqueda y puede pulsar **Cancelar**.
- Cancelar durante la búsqueda elimina al jugador de la cola y lo devuelve a Inicio. Si vuelve a pulsar Jugar, entra al final.
- La partida se identifica y coordina desde el servidor Node.js. La identidad se obtiene verificando el ID token de Firebase Auth; nunca se acepta un UID enviado sin verificar.
- Al encontrar pareja, los dos clientes reciben el ID de partida, los dos apodos y una única geometría de mapa generada por el servidor.
- Ambos clientes deben confirmar que cargaron el mapa. El límite de carga es 30 segundos. Si ambos confirman a tiempo, se muestra una cuenta atrás visible de 3, 2, 1 y luego comienza la partida. Los controles permanecen bloqueados durante la cuenta atrás.
- Si una persona tarda más de 30 segundos, se cancela la preparación y la persona que ya había cargado vuelve a la cola en su posición previa. Quien no completó la carga no conserva su turno.
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

- WebSocket sobre `wss://` en producción y `ws://` solo en desarrollo local.
- El móvil envía primero su ID token de Firebase; el servidor lo verifica y asocia el socket al UID.
- Mensajes previstos: `auth`, `heartbeat`/`heartbeat.ack`, `queue.join`, `queue.cancel`, `queue.waiting`, `queue.cancelled`, `match.found`, `match.loaded`, `match.countdown`, `match.started`, `match.cancelled` y `error`.
- Los comandos de cliente incluyen un `requestId`; `queue.join` y `match.loaded` son idempotentes por estado y las transiciones se validan en el servidor.
- La cola y las salas son memoria temporal. Un reinicio de Render cancela búsquedas/preparaciones; el móvil muestra error y permite volver a buscar. No se recupera una partida activa.
- Un mensaje de aplicación cada 25 segundos mantiene viva la conexión y permite detectar cierres.

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

- Render Free puede dormir o reiniciar el proceso; la cola en memoria se pierde en un reinicio. En esta primera fase, cliente y servidor deben informar y recuperar la búsqueda, no restaurar una partida ya iniciada.
- La pantalla muestra que la cuenta atrás terminó, pero el tablero de combate y sus controles siguen pendientes. Esta entrega no simula una partida jugable.
- La sincronización final con el servidor de Render requiere desplegar el commit que añade WebSocket y generar un APK nuevo con la URL pública ya configurada.
