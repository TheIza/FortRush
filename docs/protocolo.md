# FortRush — Protocolo de emparejamiento y mapa

Este documento describe el protocolo que existe en el prototipo actual. No define todavía acciones de combate o construcción.

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

`owner` es `player1` o `player2`. `coverage` contiene intervalos de distancia sobre el camino que caen dentro del alcance geométrico de una torre. `value` y `coverage` son datos del generador; no establecen coste, daño ni otros valores de combate.

## Compatibilidad

El cliente ignora campos desconocidos en el objeto de slot para aceptar formatos anteriores. La selección del generador, la puntuación y el renderizado no distinguen unos huecos de otros.
