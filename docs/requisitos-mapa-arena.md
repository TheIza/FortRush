# FortRush — Requisitos del mapa y la arena

Este documento reúne las decisiones del documento de planificación y las decisiones confirmadas durante la conversación. Describe la primera versión visual de la arena; no significa que el combate o el estado de la partida ya estén implementados.

## Requisitos confirmados

- La arena se juega en vertical y ocupa el espacio disponible entre la información del rival y la mano de cartas.
- Cada partida usa un mapa aleatorio. Un mapa fijo no sirve como mapa habitual del juego.
- El servidor genera una sola geometría y envía el mismo mapa a ambos móviles. Los móviles no regeneran el mapa por separado.
- El camino es una sola ruta continua, con curvas y cambios de dirección; no es una línea recta, no tiene ramales y conecta ambos lados.
- El recorrido se refleja mediante una rotación de 180° para que los lados tengan una disposición equivalente.
- La vista del jugador coloca su castillo en la parte inferior. Se gira el mundo de la arena para el segundo jugador; las cartas, textos y controles permanecen derechos.
- Hay cuatro plataformas de torre por jugador, ocho en total. Al comenzar la partida están vacías.
- Cada jugador tiene un muro con una puerta cerrada, colocado donde el camino cruza la línea del muro.
- Cada muro recorre la pantalla de izquierda a derecha, hasta ambos bordes. Los tramos deben solaparse lo suficiente para que no queden huecos ni costuras visibles.
- El aspecto destruido del muro mostrará la puerta derrumbada y un paso abierto por los restos; no será simplemente una puerta intacta abierta.
- Las cuatro plataformas de cada jugador se sitúan junto a distintos tramos de su lado del camino; no aparecen en filas horizontales fijas.
- La disposición de las plataformas es aleatoria para la partida y la del rival es la misma reflejada 180°.
- Hay zonas prohibidas para construir: una franja central alrededor de la mitad del mapa y una franja que empieza un poco antes de cada muro y continúa hasta el borde inferior/superior correspondiente. Se rechaza cualquier hueco cuyo círculo de construcción invada esas franjas.
- Cada plataforma debe tocar una banda invisible que rodea el camino: se mantiene separada del borde visible del camino, pero no puede quedar más lejos que el ancho de esa banda más el radio de la plataforma.
- Los castillos deben ser suficientemente grandes para leerse como las bases principales del mapa, sin quedar pegados a los bordes ni tapar la interfaz.
- El bosque, las rocas y los arbustos son decoración. No deben tapar el camino, los castillos, los muros ni las plataformas construibles.
- El mapa se dibuja por capas: césped, decoración de fondo, camino y bordes, castillos, plataformas, muros y puertas, unidades y, por último, interfaz. La muralla queda delante del castillo donde se cruzan.
- La distancia de ataque de una torre se calcula en coordenadas visuales del tablero; avanzar cierta distancia por el camino no equivale necesariamente a estar cerca de una torre.
- Si el generador no obtiene una geometría válida tras sus intentos, se elegirá al azar una de cinco plantillas de respaldo diferentes.

## Validaciones mínimas del generador

1. El camino contiene puntos finitos dentro de los límites del tablero y tiene continuidad entre puntos.
2. El camino no se cruza consigo mismo y mantiene espacio suficiente entre curvas para el ancho del camino y sus bordes.
3. Los dos lados son simétricos mediante giro de 180° y ambos jugadores comparten la misma secuencia de puntos.
4. Las ocho plataformas se mantienen fuera del camino y de las zonas prohibidas, separadas entre sí y divididas en cuatro por jugador. Las del segundo jugador son un giro exacto de 180° de las del primero.
5. Las decoraciones respetan el espacio de seguridad de caminos, plataformas y castillos.
6. Las puertas quedan centradas sobre el camino; los tramos de muro cubren todo el ancho visible.
7. El tamaño y la disposición se adaptan a móviles de diferentes proporciones, dejando los controles fuera del área del mapa.
8. La generación está acotada: tiene un máximo de intentos y usa solo plantillas de respaldo previamente consideradas válidas.

## Decisiones técnicas para evitar costuras

- El fondo se dibuja en el lienzo de la arena; el camino se traza como una ruta vectorial suavizada con borde y relleno.
- El muro se compone con una banda de respaldo continua y tramos del mismo sprite de piedra repetidos con un pequeño solapamiento hasta los dos bordes de la pantalla.
- La puerta es una pieza central independiente que conserva el estilo y la escala de la piedra. En estado intacto, el muro continúa por detrás; en estado destruido, se deja libre únicamente el ancho del camino y se muestra el arco con la puerta derrumbada.
- Los elementos jugables usan las mismas coordenadas normalizadas que envía el servidor. El ancho del muro se extiende al lienzo completo aunque el tablero vertical tenga márgenes laterales.
- Las plataformas conservan la proporción de su imagen y usan posiciones enviadas por el servidor.
- La franja central usa una semialtura normalizada de `0.035`; la plataforma conserva un margen de `0.04` por arriba y por abajo. Cerca del muro se deja un margen de `0.035` desde su línea, también ampliado por la mitad de alto de la plataforma. El generador aplica estas reglas antes de aceptar posiciones, y la muestra local con bot sigue las mismas cotas.
- La banda invisible se modela con la semianchura del camino visible (`0.0845`) más `0.025`; la plataforma se aproxima con radio `0.09`. El centro de la plataforma debe quedar entre `0.1865` y `0.1995` unidades de pantalla del camino: conserva al menos `0.012` de separación del camino visible y su borde toca la banda ampliada. El validador rechaza huecos dentro o fuera de ese intervalo.

## Estado de esta entrega

- **Diseño acordado:** arena vertical, camino aleatorio y compartido, cuatro espacios por jugador, muro completo de lado a lado y giro de la vista por jugador.
- **Preparación local:** generador del servidor, lectura de la geometría recibida, arte separado de puerta intacta/derrumbada y pantalla visual de arena.
- **Comprobado localmente (18/11/2026):** compilación `:app:assembleDebug`; 50 semillas generaron 50 mapas válidos y distintos; las cinco plantillas de respaldo también resultaron válidas.
- **Pendiente de comprobar:** aspecto final en dispositivos reales, despliegue de esta versión del Worker y mapa compartido entre dos móviles.
- **Todavía no implementado como juego:** tropas moviéndose, construcción interactiva, daño, vida del muro/castillo, sincronización del combate y victoria.

Este listado es el criterio para revisar la primera arena. Las pruebas deben documentarse como realizadas solo después de ejecutarlas realmente.

## Modo local de prueba con bot

- El botón **Probar arena con bot** abre una partida de muestra usando una sola cuenta.
- La muestra crea una geometría local distinta al entrar; no entra en la cola, no contacta el Worker y no consume solicitudes de red. Es solo una ayuda visual para comprobar caminos y plataformas, no el generador oficial de las partidas online.
- El nombre del rival indica que es una prueba visual. El bot todavía no despliega tropas ni combate, porque esas mecánicas no están implementadas.
- Este modo sirve para revisar variaciones visuales de la arena, los muros, las plataformas y las cartas. No verifica el mapa aleatorio del servidor, el emparejamiento ni el combate.
- El botón **Jugar** mantiene el flujo de emparejamiento real entre dos jugadores.
