# Muralla: resumen del proyecto

Juego multijugador 1v1 para Android (tower defense + cartas). Nombre provisional.

## 1. Concepto

Juego 1v1 en tiempo real que mezcla tower defense y cartas estilo Clash Royale. Los dos jugadores comparten un único camino, cada uno con su base en un extremo, y gana quien destruya la base del rival. Cumple el enunciado: varios jugadores desde máquinas distintas, mapa generado aleatoriamente, sala de espera y un servidor que gestiona la partida y decide el ganador.

## 2. Cómo hemos llegado a la idea (material para la presentación)

- **Punto de partida:** ideas previas (tower defense 1v1, incremental cooperativo, Pokémon) que no convencían. Objetivo de diseño: no adictivo, pero que incite a mejorar (maestría, como Clash Royale).
- La IA propuso varias familias de ideas (territorio, recolector con power-ups, duelo de cartas, Snake.io, Bomberman-lite, Among Us-lite, artillería). Ninguna terminaba de encajar.
- **Cambio de método:** la IA hizo preguntas guiadas. Resultado del perfil: 1v1, estrategia (mazos y contadores), partidas de 3-5 minutos.
- **Decisión:** tower defense + cartas. Se descartó la información parcial (el rival no se oculta).
- **Decisión:** huecos de construcción fijos en lugar de colocación libre (más profundidad por decisión, mejor en móvil, más fácil de equilibrar).
- **Decisión:** el mapa es un tower defense clásico con camino aleatorio y huecos fijos junto al camino.
- **Decisión:** un solo camino compartido donde las tropas de ambos bandos se enfrentan.
- **Tecnología:** se investigó qué es realmente gratis en Firebase y en hosting (ver sección 6), y se descartó Godot a favor de Kotlin + Compose.

## 3. Mecánicas principales

- **Mazo de 8 cartas** elegido antes de la partida, con una mano de 4 que va rotando. Hay cartas de torre (se colocan en huecos junto al camino) y de tropa (avanzan por el camino hacia la base rival).
- **Maná único** que se recarga solo. La decisión clave: gastar en defensa, en ataque o ahorrar.
- **Contadores:** 3 familias de tropas que se ganan entre sí (por ejemplo, rápidas, blindadas y voladoras) y torres especializadas contra cada una. Perder debe sentirse como "me leyó mejor", no como mala suerte.
- **Combate en el centro:** las tropas enemigas se paran al encontrarse y pelean. Quien gana sigue avanzando, lo que forma una línea de frente móvil.
- **Muralla** al final de cada lado, con vida visible. Si se rompe, las tropas entran en la base y le bajan la vida.
- **Victoria:** dejar la base rival a 0, o tener más vida a los 4 minutos (con muerte súbita).
- **Comeback suave:** quien va perdiendo recarga maná algo más rápido.
- **Información abierta:** se ve lo que construye el rival.

## 4. El mapa

Vista desde arriba en formato vertical. Un camino de tierra sinuoso (curvas en S, tramos rectos y alguna curva cerrada) recorre el mapa de arriba abajo. En el extremo superior está la base del rival y en el inferior la del jugador, cada una con un pequeño castillo y una muralla de piedra que cruza el camino delante, con puertas. El resto es césped y bosque estilizado, con aire de dibujo animado. Junto al camino hay unos 16-20 huecos de construcción (plataformas de piedra), la mitad para cada jugador, con la zona central más disputada. Algunos están en curvas, otros en rectas y otros cerca de las murallas, de modo que su valor depende de la forma del camino. Las tropas de cada bando (azul y rojo) se enfrentan en el medio.

- El **servidor genera el camino** con una semilla que envía a los dos jugadores: distinto en cada partida pero idéntico para ambos (justo).
- El generador fija **longitud mínima y máxima** y descarta caminos casi rectos o con huecos amontonados. Los huecos se calculan a partir del camino.
- Cada jugador ve **su base abajo** (el cliente da la vuelta al mapa para el otro).
- En el móvil, la mano de cartas y la barra de maná ocupan el 20-25 % inferior; el mapa debe caber en el resto.
- Cada hueco tiene un dueño claro (borde de color).

## 5. Anti-adicción y accesibilidad

- Sin cofres con temporizador, energía, recompensas diarias ni pagos. Las cartas se desbloquean **jugando bien**.
- Resumen al final de cada partida con el momento que decidió el resultado.
- Aviso de descanso tras varias partidas seguidas.
- Unidades distinguibles por **forma y no solo por color** (daltonismo), botones grandes, texto escalable, vibración como feedback y compatibilidad con TalkBack.

## 6. Arquitectura técnica (todo gratis, sin tarjeta)

Hallazgo importante (comprobado en noviembre de 2026): **Firebase gratis (plan Spark) no permite Cloud Functions**; para usarlas hay que pasar al plan Blaze, que exige tarjeta. Además, las Functions no sirven para una simulación continua. Por eso usamos Firebase para lo que es bueno y ponemos el servidor de partida aparte.

| Pieza | Tecnología | Para qué |
|---|---|---|
| Cliente | Kotlin + Jetpack Compose (Canvas) | Dibujar mapa y mano de cartas; enviar comandos |
| Login | Firebase Auth (anónimo o Google) | Identificar jugadores |
| Perfil y estadísticas | Firestore | Cartas desbloqueadas, historial, resumen de partidas |
| Servidor de partida | Cloudflare Worker + Durable Object | API de perfiles y prototipo de emparejamiento; el combate aún está pendiente |
| Servidor Node.js | Uso local de desarrollo | Pruebas y lógica anterior; no es la ruta activa de la app instalada |

**Flujo:** login con Firebase, conexión al servidor con el token, sala de espera, el servidor genera el camino y empieza la partida, los clientes envían comandos ("carta X en hueco Y") y reciben el estado, y al terminar el servidor guarda el resultado en Firestore y avisa del ganador. El servidor es la autoridad: simula a pasos fijos (unos 10 por segundo) y los clientes solo dibujan.

**Simplificación clave:** como todas las unidades van por un único camino, la posición de cada una es un solo número (distancia desde su base). Colisiones, alcance de torres y combates son comparaciones de números.

### Limitaciones a tener en cuenta

- **Cloudflare** es el alojamiento elegido. Perfil y emparejamiento apuntan al Worker; las pruebas de reconexión, cambio de red y reinicio siguen pendientes.
- **Cuotas de Firestore en Spark:** unas 20.000 escrituras y 50.000 lecturas al día. Solo se guardan perfiles y resultados, nunca el estado en tiempo real.
- Las cuotas y condiciones del plan gratuito de Cloudflare deben revisarse antes de ampliar el uso. No activar un plan de pago.

## 7. Por qué Kotlin + Compose y no Godot

- Es un "proyecto de Android" de verdad, como pide el enunciado.
- Firebase tiene SDK oficial para Android (en Godot habría que usar la API REST a mano).
- Mejor accesibilidad (TalkBack, tamaños de texto, contraste).
- El juego es 2D sobre un único camino: un Canvas basta, no hace falta motor.
- Lo que se pierde: animaciones muy vistosas (más costosas de hacer). Para el MVP sobra con animación simple por código.
- Pendiente de confirmar con el profesor si Android nativo es obligatorio.

## 8. Aspecto visual y assets

La imagen generada con Gemini sirve como referencia de estilo, no como mapa del juego (el camino es aleatorio). Se podrá conseguir un 85-90 % de ese aspecto:

- Pedir a Gemini sprites sueltos con fondo transparente: castillos, murallas, torres, tropas, piedras de hueco, árboles y rocas, con el mismo prompt de estilo.
- Dibujar el camino por código con 2-3 capas (borde oscuro, tierra, luz).
- Añadir profundidad barata: sombras bajo torres y tropas, degradado suave en el césped.
- Dejar las animaciones detalladas para el final. Para el MVP, balanceo, rebote al andar y parpadeo al recibir daño, hechos por código.
- Detalles a corregir de la imagen: tramo muerto en el bucle inferior, puertas que deben empezar cerradas y dueño claro de cada hueco.

## 9. MVP y plan de trabajo

**MVP:** 8 cartas (4 tropas + 4 torres), generador de camino, muralla, sala de espera, login y pantalla de resultado.

1. Servidor mínimo: dos clientes se conectan y se mandan mensajes.
2. Generador de camino y huecos, con tests de validez.
3. Simulación: tropas que se mueven, pelean y rompen la muralla.
4. Cliente: dibujar mapa y estado.
5. Cartas, maná y contadores.
6. Login, sala de espera y resultado final.
7. Pulido: accesibilidad, aviso de descanso, resumen de partida.

Cada fase sirve para documentar el ciclo prompt, resultado, fallo, reformulación, que es lo que pide el profesorado.

## 10. Pendiente de decidir

- Confirmar con el profesor si es obligatorio Android nativo.
- Definir las 8 cartas del MVP y sus contadores.
- Si las tropas pelean de una en una o pueden juntarse varias (propuesta: empezar de una en una).
- Lista de assets y prompts para Gemini.
- Estructura de carpetas del proyecto (cliente y servidor).
