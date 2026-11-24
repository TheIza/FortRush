# FortRush — Protocolo de partida

Este documento describe los mensajes de emparejamiento, economía y combate del protocolo actual. El Worker es la autoridad: valida las acciones, simula la partida y decide el resultado.

## Evento `game_start`

Cuando termina la cuenta atrás, el servidor envía a cada jugador el mismo evento:

```json
{
  "type": "game_start",
  "matchId": "id-de-partida",
  "map": {
    "seed": "semilla-generada-por-el-servidor",
    "width": 1000,
    "height": 1800,
    "path": {
      "points": [{ "x": 500, "y": 1740 }],
      "cumLen": [0],
      "totalLength": 1700,
      "width": 80
    },
    "slots": [],
    "meta": {},
    "wallGates": { "player1": { "x": 500, "y": 1548 }, "player2": { "x": 500, "y": 252 } },
    "castlePositions": { "player1": { "x": 500, "y": 1677.6 }, "player2": { "x": 500, "y": 122.4 } }
  },
  "balance": {
    "version": "fort-rush-2026-11-24.1",
    "match": { "durationMs": 240000, "suddenDeathDurationMs": 3000, "maxLivingTroopsPerPlayer": 20 },
    "economy": {
      "startingCoins": 150,
      "maximumCoins": 1000,
      "incomeBands": [
        { "fromElapsedMs": 0, "untilElapsedMs": 180000, "coinsPerSecond": 5 },
        { "fromElapsedMs": 180000, "untilElapsedMs": 240000, "coinsPerSecond": 10 }
      ],
      "wallBreakReward": { "attacker": 50, "defender": 30 }
    },
    "cards": { "troops": {}, "towers": {} },
    "structures": { "wallHealth": 500, "castleHealth": 400 }
  },
  "economy": {
    "startingCoins": 150,
    "incomePerSecond": 5,
    "startedAtServerMs": 1795380000000,
    "serverTimeMs": 1795380000000,
    "elapsedMatchMs": 0,
    "coinsSpent": 0,
    "coins": 150,
    "maximumCoins": 1000,
    "balanceVersion": "fort-rush-2026-11-24.1"
  },
  "state": {
    "sequence": 0,
    "phase": "IN_MATCH",
    "elapsedMatchMs": 0,
    "remainingMs": 240000,
    "player1": { "wallHealth": 500, "castleHealth": 400 },
    "player2": { "wallHealth": 500, "castleHealth": 400 },
    "troops": [], "towers": [], "projectiles": [], "result": null,
    "viewerPlayerNumber": 1
  }
}
```

El fragmento omite el detalle de las cartas y la geometría del mapa. El evento real incluye ambos completos. `viewerPlayerNumber` cambia entre destinatarios para que cada móvil sepa cuál es su lado; el resto de la geometría y estado compartido es el mismo.

## Instantáneas de combate

`match.state` envía la instantánea autoritativa a ambos jugadores aproximadamente una vez por segundo y también después de acciones aceptadas o rechazadas. Incluye `sequence`, fase, tiempo, vida de muros/castillos, tropas, torres, proyectiles y resultado. El cliente descarta una instantánea con una secuencia anterior y dibuja las posiciones recibidas; no simula daño ni movimiento.

Las fases principales son `IN_MATCH`, `SUDDEN_DEATH` y `FINISHED`. `remainingMs` llega a cero al comenzar la muerte súbita. `result` es `null` durante el juego y, al finalizar, incluye el ganador o `null` en caso de empate, motivo técnico, tiempo y vidas finales.

## Versión de balance

- Al emparejar, el servidor copia la configuración vigente dentro del estado de esa partida.
- `balance.version` identifica ese conjunto de reglas. Cambiar un valor de juego requiere actualizar la versión.
- `balance` contiene duración, muerte súbita, economía, valores aprobados de las cuatro cartas, vida de muro/castillo y recompensas por destruir el muro.
- `game_start` envía la misma configuración a los dos jugadores. `tower_built.economy.balanceVersion` debe coincidir con `balance.version`.
- Android usa los costes y el ingreso recibidos para presentar el estado. Solo el servidor decide saldo, coste, validación y resultado; valores enviados por el cliente no son autoridad.
- El generador toma del balance central el alcance de la Torre de flechas para puntuar `coverage` y publicar `map.meta.towerRange`; con el mapa lógico actual de 1000 unidades, el valor es 250. El alcance de la Torre de cañón permanece en su propio campo de balance y no se sustituye por este radio común del generador.
- El Worker ejecuta movimiento, combate de tropas, disparos de torres, daño a muro/castillo, monedas, temporizador y muerte súbita usando esta configuración versionada.

## Mapa

- `seed`: semilla generada en el servidor para esa partida.
- `width`, `height`: dimensiones lógicas; no son píxeles del dispositivo.
- `path.points`: puntos del camino en coordenadas lógicas.
- `path.cumLen` y `path.totalLength`: distancias acumuladas y longitud total del recorrido.
- `path.width`: ancho lógico del camino.
- `slots`: huecos de construcción vacíos, siete por jugador por defecto. Se dibujan todos del mismo tamaño.
- `wallGates`, `castlePositions`: ubicación lógica de las puertas de muro y castillos.
- `meta`: métricas del generador, como arquetipo, puntuación y alcance geométrico.

Cada slot tiene esta forma:

```json
{
  "id": "p1-01",
  "x": 200,
  "y": 1300,
  "owner": "player1",
  "value": 5.5,
  "coverage": [[100, 240]]
}
```

`owner` es `player1` o `player2`. `coverage` contiene intervalos de distancia sobre el camino que caen dentro del alcance geométrico de una torre. `value` y `coverage` son datos del generador; no establecen daño ni otros valores de combate.

El Worker crea la semilla con `crypto.randomUUID()` al emparejar, no a partir de la identidad ni del lugar en la cola. El registro de servidor `match-map` incluye el ID de partida, esa semilla, el arquetipo elegido y si se usó un mapa de respaldo.

## Compatibilidad

El cliente ignora campos desconocidos en el objeto de slot para aceptar formatos anteriores. La selección del generador, la puntuación y el renderizado no distinguen unos huecos de otros.

## Economía de partida

Las monedas de la partida son independientes del saldo cosmético del perfil Firestore.

- Al comenzar (`game_start`), cada jugador recibe 150 monedas.
- El saldo máximo es 1000 monedas. Las recompensas por muro y la venta de torres podrán dejar el saldo por encima de ese máximo; el tope solo limita el ingreso pasivo.
- Desde 0 hasta 180 segundos se reciben 5 monedas por cada segundo completo. Desde 180 hasta 240 segundos se reciben 10 por segundo.
- El Worker aplica el ingreso de forma perezosa al validar una acción y conserva el saldo al último momento aplicado. Si se alcanza el máximo y después se gastan monedas, el ingreso puede continuar en segundos posteriores.
- `economy.coins` es el saldo autoritativo en `serverTimeMs`; `elapsedMatchMs` permite al cliente aproximar los segundos posteriores. `incomePerSecond` es la tasa vigente en esa instantánea y `maximumCoins` el límite del ingreso pasivo.
- El cliente comprueba que la versión de la instantánea coincide con la configuración de `game_start`. La interfaz aproxima el contador; las acciones siempre vuelven a validarse en el servidor.
- La construcción de torre tiene cooldown 0 según `balance.cards.towers`. El Worker ya no depende de una variable de entorno para ese valor.

## Construcción de torres

El cliente solicita una construcción con un identificador único por acción:

```json
{
  "type": "build_tower",
  "card": "arrow_tower",
  "slotId": "p1-01",
  "actionId": "uuid-de-la-accion"
}
```

Las cartas de torre y costes aceptados por el servidor salen de la versión guardada en la partida: `arrow_tower` cuesta 100 monedas y `cannon_tower` cuesta 130. El servidor identifica al jugador con el UID y número de jugador asociados a la conexión autenticada; no utiliza un número de jugador del payload. Valida la fase, el hueco y su propietario, ocupación, tipo de carta, cooldown y saldo antes de escribir el estado.

Si acepta, guarda la torre y el gasto en el Durable Object y envía `tower_built` a ambos jugadores. El evento contiene `actionId`, `slotId`, `ownerPlayerNumber`, `card` y una instantánea de economía. Si rechaza, envía `action_rejected` al solicitante con `actionId`, `slotId` y uno de estos motivos: `UNKNOWN_SLOT`, `NOT_YOUR_SLOT`, `SLOT_OCCUPIED`, `NOT_A_TOWER_CARD`, `NOT_ENOUGH_COINS`, `ON_COOLDOWN` o `INVALID_STATE`. Un identificador ya aceptado se ignora sin volver a gastar monedas ni crear otra torre.

Las solicitudes durante `COUNTDOWN` o `SUDDEN_DEATH` se rechazan como `INVALID_STATE`. El cooldown sale de la configuración de la carta y ahora vale cero. El test de cooldown cambia una copia de configuración para comprobar el rechazo; no modifica el balance desplegable.

La pantalla de arena permite seleccionar una carta de torre y tocar un hueco propio libre, o mantener la carta pulsada y arrastrarla. Al seleccionar una torre propia o arrastrar una carta se dibuja su alcance usando `rangeAsBoardWidth` de la versión de balance. `map.meta.towerRange` describe el alcance de flechas usado para puntuar el mapa. El servidor decide la selección del objetivo y el daño. La prueba visual en dispositivos todavía está pendiente.

## Tropas, venta y rendición

Despliegue de tropa:

```json
{ "type": "deploy_troop", "card": "espadachin", "position": 1000, "actionId": "uuid-de-la-accion" }
```

`position` es una distancia sobre el camino lógico. El servidor obtiene al jugador de la conexión autenticada y rechaza zonas inválidas, cartas incorrectas, falta de monedas, cooldown o exceso del límite sin cobrar. `troops_deployed` confirma la acción; `action_rejected` devuelve su motivo. Los golpes se aplican de forma simultánea. Al alcanzar el muro las tropas lo atacan; tras destruirlo, avanzan al castillo.

Venta de torre:

```json
{ "type": "sell_tower", "slotId": "p1-01", "actionId": "uuid-de-la-accion" }
```

El servidor solo permite vender una torre propia durante `IN_MATCH`, devuelve el 50 % del coste redondeado hacia abajo y libera el hueco. Al aceptar envía `tower_sold` y después `match.state`.

Rendición:

```json
{ "type": "match.surrender", "actionId": "uuid-de-la-accion" }
```

La acción se limita al juego normal. El Worker termina la partida y comunica el mismo resultado a los dos jugadores mediante `match.state` y `match.result`. La pantalla final muestra victoria, derrota o empate. La persistencia de resultados/recompensas y la reconexión ante una interrupción siguen pendientes.
