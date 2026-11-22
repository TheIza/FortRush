# FortRush — Protocolo de emparejamiento y mapa

Este documento describe el protocolo implementado en el prototipo actual. Incluye economía de partida y construcción de torres; todavía no define acciones de combate.

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
  "economy": {
    "startingCoins": 150,
    "incomePerSecond": 5,
    "startedAtServerMs": 1795380000000,
    "serverTimeMs": 1795380000000,
    "coinsSpent": 0
  }
}
```

El fragmento muestra la estructura y tipos; los arrays y objetos reales contienen la geometría completa del mapa.

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
- El servidor suma 5 monedas por cada segundo completo desde `startedAtServerMs`.
- El límite máximo de monedas todavía está pendiente de decidir. El prototipo actual no aplica un tope.
- El ingreso y el saldo se calculan en el servidor; el cliente solo aproxima el contador visual entre eventos.
- No hay cooldown de construcción: `TOWER_BUILD_COOLDOWN_MS` no está configurado en Wrangler, por lo que su valor efectivo es 0.

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

Las cartas de torre y costes aceptados por el servidor son `arrow_tower` por 100 monedas y `cannon_tower` por 130. El servidor identifica al jugador con el UID y número de jugador asociados a la conexión autenticada; no utiliza un número de jugador del payload. Valida la fase, el hueco y su propietario, ocupación, tipo de carta, cooldown y saldo antes de escribir el estado.

Si acepta, guarda la torre y el gasto en el Durable Object y envía `tower_built` a ambos jugadores. El evento contiene `actionId`, `slotId`, `ownerPlayerNumber`, `card` y una instantánea de economía. Si rechaza, envía `action_rejected` al solicitante con `actionId`, `slotId` y uno de estos motivos: `UNKNOWN_SLOT`, `NOT_YOUR_SLOT`, `SLOT_OCCUPIED`, `NOT_A_TOWER_CARD`, `NOT_ENOUGH_COINS`, `ON_COOLDOWN` o `INVALID_STATE`. Un identificador ya aceptado se ignora sin volver a gastar monedas ni crear otra torre.

Las solicitudes durante `COUNTDOWN` o `SUDDEN_DEATH` se rechazan como `INVALID_STATE`. En la implementación actual el cooldown efectivo es cero; el caso `ON_COOLDOWN` se prueba configurando un cooldown solo en el test. La construcción no inicia ni modifica la simulación de combate. La venta de torres todavía no está conectada al protocolo.

La pantalla de arena permite seleccionar una carta de torre y tocar un hueco propio libre, o mantener la carta pulsada y arrastrarla. El cliente solo muestra huecos propios como destinos interactivos y deja los huecos rivales como elementos visuales. El círculo usa `meta.towerRange` recibido en el mapa y aparece al apuntar a un hueco propio o seleccionar una torre propia; es una visualización geométrica y todavía no representa un combate simulado. Una construcción se muestra como pendiente hasta que el servidor la confirma; ante rechazo, el cliente retira el estado pendiente y presenta un mensaje. La prueba visual en dispositivos todavía está pendiente.
