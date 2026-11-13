# FortRush — Documento de planificación

**Versión 0.8 · 12 de noviembre de 2026**\
**Estado: planificación. El proyecto Android está pendiente de revisión técnica. No se ha confirmado todavía que Firebase, Firestore ni Authentication funcionen correctamente.**

## 1. Objetivo y condiciones

**FortRush será una aplicación Android nativa de estrategia multijugador. Dos jugadores usarán cartas para desplegar tropas y construir torres en un mapa aleatorio compartido.**

**Condiciones confirmadas:**

- **Aplicación Android nativa desarrollada con Kotlin y Jetpack Compose.**
- **Orientación siempre vertical.**
- **Partidas por Internet entre jugadores que pueden estar en casas distintas.**
- **Presupuesto de servicios de 0 €.**
- **No se activará la facturación.**
- **No se dependerá de pruebas gratuitas que puedan generar cargos posteriormente.**
- **Desarrollo individual con ayuda de Codex.**
- **Disponibilidad aproximada de 16 horas repartidas en 10 sesiones durante un mes.**
- **Primera versión con cuatro cartas fijas.**
- **El PDF inicial proponía una aplicación web, pero esa decisión queda sustituida por Android.**

**La estética general será medieval, colorida y de estilo cartoon, inspirada en las imágenes proporcionadas:**

- **Fondo verde con patrón de rombos y símbolos medievales.**
- **Elementos de madera, piedra y metal.**
- **Botones grandes con bordes decorativos.**
- **Iconos grandes y fácilmente reconocibles.**
- **Contraste suficiente para facilitar la lectura.**
- **Navegación inferior con iconos grandes.**

**Ideas visuales futuras:**

- **Sistema de nivel y experiencia.**
- **Monedas y recompensas persistentes.**
- **Logros.**
- **Colección ampliada de cartas.**
- **Más animaciones y efectos.**
- **Personalización visual del perfil.**

## 2. Estado actual del proyecto

**La usuaria tiene un proyecto Android Studio iniciado e indica que está conectado con Firebase y Firestore.**

**Pendiente de comprobar cuando se revise el repositorio:**

- **Que el proyecto compile.**
- **Que la aplicación se ejecute.**
- **Que la configuración de Firebase sea correcta.**
- **Que Firebase Authentication esté correctamente configurado.**
- **Que Google Sign-In funcione.**
- **Que Firestore esté correctamente conectado.**
- **Que las reglas de acceso de Firestore sean seguras.**
- **Que las dependencias sean compatibles.**
- **Que las pantallas y la estructura existentes sean aprovechables.**
- **Que el proyecto mantenga la orientación vertical.**

**El login todavía no se considera implementado hasta comprobarlo. No se debe afirmar que una integración funciona antes de realizar una prueba real.**

**No se modificará el código del proyecto hasta que la usuaria lo indique explícitamente.**

## 3. Requisitos de la actividad

**La entrega debe demostrar:**

- **Juego multijugador en dispositivos diferentes.**
- **Sala de espera.**
- **Emparejamiento entre jugadores.**
- **Tablero generado aleatoriamente.**
- **Mapa idéntico para ambos jugadores.**
- **Objetivo de victoria.**
- **Comunicación asíncrona.**
- **Un servidor que gestione la partida.**
- **Validación de acciones en el servidor.**
- **Comunicación del resultado.**
- **Estética, usabilidad y accesibilidad.**
- **Documentación del trabajo realizado con ayuda de agentes de IA.**

**La presentación debe explicar el proceso y las decisiones tomadas, no limitarse a copiar respuestas de la IA.**

## 4. Identificación y perfiles

**Para jugar será obligatorio registrarse.**

**Método de acceso confirmado:**

- **Registro e inicio de sesión mediante Google.**
- **Firebase Authentication se utilizará para identificar al jugador.**

**Después del primer inicio de sesión, el jugador deberá escoger un apodo.**

**Reglas del apodo:**

- **Entre 3 y 16 caracteres.**
- **Solo letras, números y guiones bajos.**
- **No se permiten espacios ni otros símbolos.**
- **La comparación no distinguirá mayúsculas y minúsculas.**
- **“Guerrero” y “guerrero” se consideran el mismo nombre.**
- **No se podrá utilizar un apodo ya escogido por otra persona.**
- **El apodo podrá cambiarse una vez cada siete días.**
- **El plazo se contará desde el último cambio realizado.**
- **El apodo del rival se mostrará durante la partida.**

**Firestore podrá guardar:**

- **Identificador del usuario.**
- **Apodo.**
- **Fecha del último cambio de apodo.**
- **Resultados básicos.**
- **Estadísticas mínimas, si se implementan.**

## 5. Entrada a una partida

**Funcionamiento confirmado:**

1. **El jugador pulsa Jugar.**
2. **Entra en una pantalla de espera.**
3. **El servidor busca al primer jugador disponible.**
4. **Cuando encuentra una pareja, forma la partida.**
5. **Ambos jugadores reciben el mismo mapa.**
6. **Ambos comienzan a cargarlo.**
7. **Cuando los dos han cargado, empieza una cuenta atrás de tres segundos.**
8. **Durante la cuenta atrás todos los controles permanecen bloqueados.**
9. **Al finalizar la cuenta atrás empieza la partida.**

**No habrá en la primera versión:**

- **Selección por nivel.**
- **Ranking.**
- **Emparejamiento por habilidad.**
- **Códigos de sala.**
- **Salas privadas.**

### Cancelación durante la espera

- **El jugador podrá pulsar Cancelar mientras esté buscando partida.**
- **Al cancelar, saldrá de la cola.**
- **Volverá a la pantalla principal.**
- **No podrá cancelar después de haber sido emparejado.**
- **Si un jugador cancela después del emparejamiento pero antes de comenzar, la partida se cancela para él.**
- **El otro jugador volverá automáticamente a la cola.**
- **El jugador que no canceló recuperará su posición anterior en la cola.**
- **El jugador que canceló no conservará su posición.**

### Carga del mapa

- **El tiempo máximo para cargar el mapa será de 30 segundos.**
- **Si un jugador carga y el otro tarda demasiado, se mostrará un aviso.**
- **El jugador que ya cargó volverá a la cola.**
- **Recuperará su posición anterior.**
- **Podrá pulsar Jugar de nuevo si lo desea.**
- **Si ambos cargan correctamente, no habrá vuelta atrás.**
- **La cuenta atrás será visible y mostrará 3, 2 y 1.**
- **Durante la cuenta atrás no se podrá cancelar ni rendirse.**

## 6. Rendición

**Durante una partida normal habrá un menú situado arriba a la derecha.**

**El menú incluirá:**

- **Rendirse.**
- **Activar o desactivar el volumen.**

**La rendición:**

- **Requerirá confirmación.**
- **Dará la victoria inmediatamente al rival.**
- **No estará disponible durante la cuenta atrás.**
- **No estará disponible durante la muerte súbita.**
- **El botón y el menú aparecerán bloqueados cuando no puedan utilizarse.**

## 7. Arquitectura y servicios

**La conexión prevista será:**

**Aplicación Android ↔ servidor de partidas ↔ aplicación Android**

**Responsabilidades previstas:**

### Android

- **Pantallas.**
- **Navegación.**
- **Controles.**
- **Dibujo del mapa.**
- **Animaciones.**
- **Interfaz de cartas.**
- **Indicadores de vida, monedas y tiempo.**
- **Gestión visual de conexión y errores.**

### Servidor Node.js con WebSocket

- **Cola de espera.**
- **Emparejamiento.**
- **Creación de partidas.**
- **Generación del mapa.**
- **Validación de acciones.**
- **Control de monedas.**
- **Control de tropas y torres.**
- **Simulación del combate.**
- **Temporizadores.**
- **Muerte súbita.**
- **Rendición.**
- **Desconexiones.**
- **Determinación del resultado.**

### Firebase Authentication

- **Inicio de sesión con Google.**
- **Identificación de los jugadores.**
- **Validación de identidad frente al servidor.**

### Firestore

**Se utilizará para datos duraderos, como:**

- **Perfiles mínimos.**
- **Apodos.**
- **Fecha de cambio de apodo.**
- **Resultados de partidas.**
- **Historial básico, si queda tiempo.**

**No se guardará cada movimiento continuamente en Firestore.**

### Alojamiento

- **Se estudiará Render Free para alojar inicialmente el servidor.**
- **Se comprobarán sus condiciones oficiales antes de configurarlo.**
- **Se mantendrá Firebase en el plan Spark.**
- **No se utilizarán Cloud Functions ni Cloud Storage en la primera versión.**
- **No se activará facturación.**

**La arquitectura es todavía una propuesta pendiente de comprobar contra el repositorio y en dispositivos reales.**

## 8. Desconexiones y continuidad

**Si un jugador pierde la conexión:**

- **La partida continuará durante 30 segundos.**
- **El jugador podrá reconectarse durante ese periodo.**
- **Si vuelve a abrir la aplicación dentro de esos 30 segundos, intentará regresar automáticamente a la misma partida.**
- **Si reconecta correctamente, la partida continuará con normalidad.**
- **Si el jugador no reconecta antes de 30 segundos, ganará el jugador que permaneció conectado.**
- **Si la partida termina normalmente antes de cumplirse los 30 segundos, se respetará el resultado normal.**
- **Un jugador desconectado todavía podrá ganar si el resultado se decide a su favor antes de finalizar ese plazo.**
- **Si ambos jugadores se desconectan y ninguno regresa, la partida se cancelará sin ganador.**
- **Si el servidor se reinicia y no puede recuperar una partida activa, la partida se cancelará sin ganador.**

**La aplicación deberá mostrar:**

- **Estado de conexión.**
- **Aviso de desconexión.**
- **Tiempo restante para reconectar.**
- **Resultado de la partida.**
- **Opción de reintentar o cancelar cuando corresponda.**

**Pendiente de diseñar y probar:**

- **Recuperación exacta del estado.**
- **Identificación de acciones duplicadas.**
- **Reenvío seguro de acciones.**
- **Comportamiento durante un reinicio.**
- **Persistencia mínima de partidas activas.**

## 9. Mecánicas de la primera versión

**Confirmado:**

- **Juego de estrategia uno contra uno.**
- **Uso de cartas, tropas y torres.**
- **Cuatro cartas fijas.**
- **Mapa aleatorio compartido.**
- **Cada jugador tiene un castillo y un muro.**
- **El muro no se repara al ser destruido.**
- **Las tropas se colocan dentro del área de su jugador.**
- **Las tropas avanzan por el camino hacia la base rival.**
- **Cuando una tropa encuentra una tropa enemiga, se detiene para combatir.**
- **Si no hay tropas enemigas, las tropas atacan el muro.**
- **Si el muro está destruido, las tropas entran en el castillo.**
- **Las tropas que entran en el castillo reducen la vida de la base.**
- **Las torres no pueden ser destruidas por el enemigo en la primera versión.**
- **Las torres pueden venderse en cualquier momento de una partida normal.**
- **La venta siempre requiere confirmación.**
- **Vender una torre devuelve el 50 % de su coste, redondeado hacia abajo.**
- **El hueco de construcción queda libre al venderla.**

## 10. Victoria y final de partida

**La partida durará como máximo cuatro minutos.**

**Antes de la muerte súbita, una partida puede terminar por:**

- **Destruir el muro enemigo y reducir a cero la vida de su castillo.**
- **Rendición de un jugador.**
- **Desconexión definitiva de un jugador después de 30 segundos.**

**Al llegar a los cuatro minutos comenzará la muerte súbita.**

## 11. Muerte súbita

**Al comenzar la muerte súbita:**

- **Todo queda pausado.**
- **Las tropas dejan de moverse.**
- **Las tropas dejan de atacar.**
- **Las torres dejan de atacar.**
- **Las tropas y torres se destruyen al instante.**
- **Los botones y menús quedan bloqueados.**
- **No se podrá rendir.**
- **Solo se resolverá la reducción de vida pactada.**
- **La reducción será visible y rápida, aproximadamente durante tres segundos.**

### Resolución de la muerte súbita

1. **Si un jugador conserva el muro y el otro no, gana directamente quien conserva el muro.**
2. **Si ambos conservan el muro:**
   - **Se reduce la vida de ambos muros a la vez.**
   - **Se resta la misma cantidad a los dos.**
   - **El primer muro que llegue a cero pierde.**
   - **Si los dos llegan a cero dentro de la misma resolución, hay empate.**
3. **Si ambos jugadores comienzan la resolución sin muro:**
   - **Se reduce la vida de ambos castillos a la vez.**
   - **Se resta la misma cantidad a ambas bases.**
   - **El primer castillo que llegue a cero pierde.**
   - **Si los dos llegan a cero dentro de la misma resolución, hay empate.**
4. **Si los muros recibieron el mismo daño o no recibieron daño antes de la muerte súbita, las bases no recibirán daño adicional por ese motivo y el resultado será empate cuando corresponda.**
5. **Si los dos muros se destruyen exactamente al mismo tiempo durante la resolución, se considera empate.**
6. **Si los muros se destruyeron antes de la muerte súbita, se pasa directamente a comparar la vida de los castillos.**

**El servidor deberá definir una resolución determinista para los eventos simultáneos.**

## 12. Monedas

**Las monedas serán el recurso principal.**

**Su función será permitir:**

- **Construir torres.**
- **Desplegar tropas.**
- **Realizar posibles mejoras futuras.**

**Reglas acordadas:**

- **Cada jugador empezará con una cantidad suficiente para construir una torre y desplegar algunas tropas.**
- **Las monedas aumentarán de forma constante durante los tres primeros minutos.**
- **Durante el último minuto, cuando se aproxime la muerte súbita, se probará una cantidad de ingresos superior.**
- **Existirá un límite máximo de monedas.**
- **El límite evitará acumular cantidades excesivas.**
- **Las monedas obtenidas mediante ingresos periódicos que superen el límite se perderán.**
- **Las monedas obtenidas por romper el muro tendrán una excepción.**
- **La recompensa por romper el muro se conservará aunque haga superar el límite.**
- **Los valores definitivos se ajustarán durante las pruebas.**

**Ejemplo provisional:**

- **Jugador que rompe el muro: +50 monedas.**
- **Jugador cuyo muro es destruido: +30 monedas.**

**Estos valores son solamente una propuesta y todavía no están aprobados.**

**Pendiente de definir:**

- **Monedas iniciales exactas.**
- **Ritmo exacto de ingresos.**
- **Cantidad del último minuto.**
- **Límite máximo.**
- **Coste de cada carta.**
- **Recompensas definitivas por romper el muro.**
- **Si la recompensa del jugador que pierde el muro también puede superar el límite.**
- **Si habrá mejoras en la primera versión.**

## 13. Cartas

**La primera versión tendrá cuatro cartas:**

| **Carta**            | **Tipo**  | **Función**                    |
| -------------------- | --------- | ------------------------------ |
| **Espadachines**     | **Tropa** | **Ataque equilibrado**         |
| **Escuderos**        | **Tropa** | **Menos fuerza y más defensa** |
| **Torre de flechas** | **Torre** | **Ataque a distancia**         |
| **Torre de cañón**   | **Torre** | **Ataque en área corta**       |

### Despliegue

- **Las cartas estarán siempre visibles en una franja inferior.**
- **Para utilizar una carta se mantendrá pulsada.**
- **Al empezar a arrastrarla se mostrará un blueprint de la unidad o estructura.**
- **El blueprint ocupará menos espacio que la carta completa.**
- **El jugador arrastrará el blueprint hasta la posición deseada.**
- **Si la posición es válida, el blueprint se mostrará en color normal o verde.**
- **Si la posición no es válida, se mostrará en gris o rojo.**
- **En una posición inválida no se podrá soltar la carta.**
- **Al soltarla en una posición válida, el servidor validará la acción.**
- **Las cartas tendrán un tiempo de reutilización.**
- **No se podrá volver a utilizar una carta hasta que termine su cooldown.**

### Cantidad desplegada

- **Espadachines: aparecen de tres en tres.**
- **Escuderos: aparecen de dos en dos.**
- **Las torres se construyen individualmente.**

### Torres

- **Las torres atacan a la primera tropa enemiga que encuentran dentro de su alcance.**
- **La torre de flechas realiza ataques a distancia.**
- **La torre de cañón afecta también a tropas cercanas al objetivo.**
- **Las torres no pueden ser destruidas por el enemigo en la primera versión.**
- **Se pueden vender durante una partida normal.**
- **La venta libera el hueco de construcción.**
- **La venta devuelve el 50 % del coste, redondeado hacia abajo.**

**Pendiente de definir:**

- **Coste.**
- **Vida.**
- **Daño.**
- **Velocidad.**
- **Alcance.**
- **Frecuencia de ataque.**
- **Duración del cooldown.**
- **Límite de tropas simultáneas.**
- **Límite de torres simultáneas.**

## 14. Mapa aleatorio

**El mapa será generado principalmente por el servidor.**

**Decisiones confirmadas:**

- **Ambos jugadores recibirán exactamente la misma geometría.**
- **El camino será continuo y sinuoso.**
- **El camino conectará ambos castillos pasando por el centro.**
- **No habrá ramales.**
- **No será una línea recta.**
- **Habrá cuatro huecos de torre por jugador.**
- **Habrá ocho huecos en total.**
- **Los huecos estarán colocados en la mitad correspondiente de cada jugador.**
- **Dos huecos estarán cerca del castillo.**
- **Dos huecos estarán hacia el centro.**
- **Las mitades serán simétricas mediante un giro de 180 grados.**
- **Un jugador verá su base abajo y el otro arriba.**
- **La geometría podrá girarse, pero los textos y controles seguirán derechos.**

**Comprobaciones del generador:**

- **El camino debe estar conectado.**
- **El camino no puede salir del tablero.**
- **El camino no puede cruzarse consigo mismo.**
- **Los bordes no pueden solaparse.**
- **Los huecos deben ser válidos.**
- **Los huecos deben ser simétricos.**
- **Los huecos deben ser accesibles.**
- **La decoración no puede tapar elementos jugables.**
- **El camino debe dejar espacio suficiente para las tropas.**

**Si el generador principal falla, se elegirá aleatoriamente uno de cinco mapas de respaldo diferentes y válidos.**

**La geometría final se enviará desde el servidor para evitar diferencias entre Kotlin y JavaScript.**

## 15. Estilo visual del mapa

**El mapa no debe ser un fondo verde plano o apagado.**

**Se buscará un estilo cartoon medieval con:**

- **Césped verde luminoso.**
- **Variación de tonos de hierba.**
- **Sombras suaves.**
- **Bordes oscuros alrededor del camino.**
- **Camino de tierra marrón y sinuoso.**
- **Plataformas de piedra.**
- **Árboles y arbustos.**
- **Rocas pequeñas.**
- **Castillos opuestos.**
- **Muros de piedra.**
- **Detalles de madera.**
- **Banderas de colores para diferenciar a los jugadores.**
- **Sombras bajo edificios y elementos.**
- **Decoración sin cubrir zonas jugables.**

**Capas previstas:**

1. **Césped.**
2. **Decoración de fondo.**
3. **Borde oscuro del camino.**
4. **Tierra del camino.**
5. **Plataformas y huecos.**
6. **Muros y castillos.**
7. **Torres.**
8. **Tropas.**
9. **Proyectiles y efectos.**
10. **Interfaz, cartas, monedas y temporizador.**

**El camino se dibujará principalmente por código. Los edificios, árboles, rocas, torres y tropas podrán utilizar imágenes independientes con transparencia.**

## 16. Interfaz y pantallas

### Navegación general

**La aplicación será siempre vertical.**

**La navegación inferior tendrá iconos grandes para:**

- **Inicio.**
- **Cartas.**
- **Perfil.**

**Los botones utilizarán el estilo visual de las referencias:**

- **Marcos de piedra.**
- **Paneles de madera.**
- **Bordes metálicos.**
- **Sombras.**
- **Texto grande con contorno.**
- **Colores claros sobre fondos oscuros.**

### Pantalla de inicio

**Incluirá:**

- **Apodo del jugador.**
- **Avatar.**
- **Monedas.**
- **Nivel o progreso, si se implementa.**
- **Botón principal Jugar.**
- **Acceso a las cartas.**
- **Acceso al perfil.**
- **Botón de ajustes o volumen.**
- **Imagen decorativa de una pequeña fortaleza.**

### Pantalla sin sesión

**Incluirá:**

- **Mensaje de bienvenida.**
- **Botón Iniciar sesión con Google.**
- **Diseño medieval con marco de piedra y madera.**
- **Personaje del rey como elemento decorativo.**
- **Mensaje explicando que es necesario iniciar sesión para jugar.**

### Sala de espera

**La sala de espera tendrá:**

- **Fondo medieval verde.**
- **Panel central de madera y piedra.**
- **Texto indicando que el jugador está en cola.**
- **Apodo visible.**
- **Indicador animado de búsqueda.**
- **Escudo con espadas cruzadas como elemento visual.**
- **Botón inferior Cancelar.**
- **Al cancelar, retorno a la pantalla principal.**

### Carga y cuenta atrás

**Antes de empezar:**

- **Se mostrarán los dos apodos.**
- **Cada jugador tendrá un color y escudo identificativo.**
- **Se mostrará una banda medieval en el centro.**
- **La cuenta atrás aparecerá en grande.**
- **Se mostrarán 3, 2 y 1.**
- **Todos los botones estarán bloqueados.**
- **El menú también estará bloqueado.**
- **No se podrá cancelar ni rendirse.**

### Pantalla de partida

**La pantalla principal de partida tendrá:**

- **Mapa ocupando la mayor parte de la pantalla.**
- **Nombre de ambos jugadores.**
- **Colores diferenciados para cada jugador.**
- **Vida del muro.**
- **Vida del castillo.**
- **Tiempo restante en la parte superior.**
- **Monedas junto a las cartas.**
- **Cuatro cartas siempre visibles en la parte inferior.**
- **Botón de menú arriba a la derecha.**
- **Indicadores de cooldown.**
- **Animaciones de tropas y ataques.**
- **Feedback visual para acciones válidas e inválidas.**

### Menú de partida

**El menú desplegable tendrá:**

- **Rendirse.**
- **Activar o desactivar volumen.**

**Durante la muerte súbita:**

- **El menú aparecerá en gris o desactivado.**
- **Los botones no responderán.**
- **No se permitirá rendirse.**

## 17. Recursos gráficos existentes

**Los siguientes PNG ya se han preparado o guardado en:**

```
C:\Users\izarb\AndroidStudioProjects\FortRush\app\src\main\res\drawable-nodpi
```

### Mapa y decoración

-

```
cesped.png
```

1.

```
hierba_01.png
```

1.

```
hierba_02.png
```

1.

```
arbol_01.png
```

1.

```
arbol_02.png
```

1.

```
arbusto_01.png
```

1.

```
arbusto_02.png
```

1.

```
rocas_01.png
```

1.

```
rocas_02.png
```

1.

```
plataforma_torre.png
```

1.

```
castillo_azul.png
```

1.

```
castillo_rojo.png
```

1.

```
muro_tramo.png
```

1.

```
muro_remate.png
```

### Torres y tropas

-

```
torre_flechas_azul.png
```

1.

```
torre_flechas_roja.png
```

1.

```
torre_canon_azul.png
```

1.

```
torre_canon_roja.png
```

1.

```
espadachin_muestra.png
```

1.

```
escudero_muestra.png
```

1.

```
espadachin_animaciones.png
```

1.

```
escudero_animaciones.png
```

**Las hojas de animación contienen:**

- **Cuatro frames de caminar.**
- **Cuatro frames de atacar.**
- **Distribución en una cuadrícula de 4 × 2.**

### Cartas y efectos

-

```
marco_carta.png
```

1.

```
carta_espadachines.png
```

1.

```
carta_escuderos.png
```

1.

```
carta_torre_flechas.png
```

1.

```
carta_torre_canon.png
```

1.

```
moneda.png
```

1.

```
flecha.png
```

1.

```
bala_canon.png
```

### Interfaz general

-

```
nav_cartas.png
```

1.

```
nav_inicio.png
```

1.

```
nav_perfil.png
```

1.

```
barra_navegacion.png
```

1.

```
ajustes.png
```

1.

```
boton_principal.png
```

1.

```
boton_secundario.png
```

1.

```
panel_dialogo.png
```

1.

```
cartel_titulo.png
```

1.

```
estado_bloqueado.png
```

### Inicio y perfil

-

```
marco_bienvenida.png
```

1.

```
rey_bienvenida.png
```

1.

```
escena_inicio.png
```

1.

```
marco_apodo.png
```

1.

```
avatar_rey.png
```

## 18. PNG pendientes de crear

### Cuenta atrás

-

```
banda_cuenta_atras.png
```

- **Banda medieval horizontal.**
- **Fondo de madera, piedra y metal.**
- **Transparencia alrededor.**
- **Espacio central suficiente para mostrar 3, 2 y 1.**

### Placas de jugadores

-

```
placa_jugador_azul.png
```

1.

```
placa_jugador_roja.png
```

**Deben incluir espacio para:**

- **Apodo.**
- **Vida del muro.**
- **Vida del castillo.**
- **Iconos correspondientes.**

### Iconos de información

-

```
icono_muro.png
```

1.

```
icono_castillo.png
```

1. **`icono_tiempo.png`, opcional.**

### Menú de partida

-

```
icono_menu_partida.png
```

1.

```
icono_volumen_activado.png
```

1.

```
icono_volumen_desactivado.png
```

1. **`icono_rendirse.png`, opcional.**

### Blueprints

- **`efecto_blueprint_valido.png`, opcional.**
- **`efecto_blueprint_invalido.png`, opcional.**

**Los blueprints deben poder mostrarse con transparencia y tintarse en verde, gris o rojo según la validez de la posición.**

## 19. Plan estimado

| **Sesión** | **Tiempo** | **Meta**                                                 |
| ---------- | ---------- | -------------------------------------------------------- |
| **1**      | **1 h**    | **Revisar Android, Firebase, compilar y fijar alcance**  |
| **2**      | **2 h**    | **Servidor mínimo y conexión desde dos redes**           |
| **3**      | **1,5 h**  | **Google Sign-In, apodo, Jugar, espera y cancelación**   |
| **4**      | **1,5 h**  | **Emparejamiento, carga, cuenta atrás y mapa**           |
| **5**      | **2 h**    | **Una tropa, monedas, muro, castillo y resultado**       |
| **6**      | **1,5 h**  | **Cuatro cartas, construcción, cooldown y venta**        |
| **7**      | **1,5 h**  | **Temporizador, muerte súbita y consulta del resultado** |
| **8**      | **2 h**    | **Desconexiones, duplicados, abandono y reinicio**       |
| **9**      | **1 h**    | **Usabilidad, accesibilidad y acabado visual**           |
| **10**     | **2 h**    | **Correcciones, APK, demostración y presentación**       |

**Total estimado: 16 horas.**

**Si aparecen retrasos, se recortarán antes los extras, las mejoras y la decoración avanzada que las pruebas esenciales del multijugador.**

## 20. Organización del trabajo con Codex

**Cuando esté disponible el repositorio, se revisará antes de mover carpetas o reescribir código.**

**Se conservará la aplicación Android existente y se añadirán, cuando corresponda:**

-

```
server/
```

1.

```
docs/
```

1.

```
AGENTS.md
```

**Documentos previstos:**

-

```
docs/especificacion.md
```

1.

```
docs/arquitectura.md
```

1.

```
docs/protocolo.md
```

1.

```
docs/plan.md
```

1.

```
docs/diario-ia.md
```

**Codex deberá:**

- **Trabajar una tarea verificable cada vez.**
- **No inventar decisiones pendientes.**
- **No afirmar comprobaciones no realizadas.**
- **Registrar errores y pruebas.**
- **Mantener separadas las decisiones confirmadas y las propuestas.**
- **Validar identidad, recursos, propiedad y pertenencia a la partida desde el servidor.**
- **No incluir credenciales privadas en el APK ni en el repositorio.**

## 21. Documentación para la presentación

**En cada sesión se registrará:**

- **Objetivo.**
- **Herramienta de IA utilizada.**
- **Prompt relevante.**
- **Propuesta recibida.**
- **Decisión tomada.**
- **Motivo de la decisión.**
- **Prueba realizada.**
- **Resultado real.**
- **Errores encontrados.**
- **Reformulaciones.**
- **Documentación consultada.**
- **Aprendizaje propio.**

**También se guardarán:**

- **Capturas.**
- **Versiones relevantes del código.**
- **Resultados de pruebas.**
- **Evidencias de conexión entre dos dispositivos.**
- **Evidencias de desconexión y reconexión.**
- **Evidencias del resultado decidido por el servidor.**

**Se distinguirá claramente entre:**

- **Implementado.**
- **Comprobado.**
- **Pendiente.**
- **Propuesto.**
- **Idea futura.**

## 22. Ampliaciones futuras

**Estas ideas no son compromisos de la primera entrega:**

- **Añadir cuatro cartas más.**
- **Mazo de ocho cartas.**
- **Mano rotatoria de cuatro cartas.**
- **Mejoras de torres.**
- **Más familias de tropas.**
- **Ventajas estratégicas.**
- **Torres destruibles.**
- **Mejoras de murallas.**
- **Muerte súbita avanzada.**
- **Recuperación adicional de recursos.**
- **Desbloqueos sin pagos.**
- **Resumen del momento decisivo.**
- **Avisos de descanso.**
- **Arte y animaciones más elaborados.**
- **Salas privadas para amigos.**
- **Ranking.**
- **Partidas con amigos.**
- **Historial ampliado.**
- **Repeticiones de partidas.**

## 23. Pendientes principales

**Antes de comenzar la implementación habrá que:**

- **Revisar el repositorio Android.**
- **Comprobar la compilación.**
- **Comprobar Firebase.**
- **Comprobar Firestore.**
- **Comprobar Firebase Authentication.**
- **Comprobar Google Sign-In.**
- **Verificar las reglas de Firestore.**
- **Confirmar las condiciones de Render Free.**
- **Confirmar las condiciones de Firebase Spark.**
- **Definir las cifras de combate.**
- **Definir las monedas iniciales.**
- **Definir el ritmo exacto de monedas.**
- **Definir el límite máximo.**
- **Definir costes de cartas.**
- **Definir vida y daño de torres.**
- **Definir vida de muros y castillos.**
- **Definir cooldowns.**
- **Definir límites de tropas y torres.**
- **Concretar los cinco mapas de respaldo.**
- **Definir el protocolo WebSocket.**
- **Diseñar la reconexión.**
- **Diseñar la recuperación tras reinicio.**
- **Preparar los PNG pendientes.**
- **Revisar la interfaz final con los diseños definitivos.**
- **Ajustar el plan de 16 horas tras revisar el proyecto.**

## 24. Fuentes técnicas de referencia

- **Render Free:[ https://render.com/docs/free](https://render.com/docs/free)**
- **Firebase:[ https://firebase.google.com/pricing](https://firebase.google.com/pricing)**
- **Cuotas de Firestore:[ https://firebase.google.com/docs/firestore/quotas](https://firebase.google.com/docs/firestore/quotas)**

**Fin del documento.**



\
\
\
\
\
**Firebase Authentication**

- **Inicio de sesión con Google.**
- **Identificación única del jugador.**

**Cloud Firestore**

- **Perfil del jugador.**
- **Apodo.**
- **Fecha del último cambio de apodo.**
- **Resultados de partidas.**

**Servidor Node.js con WebSocket**

- **Cola de espera.**
- **Emparejamiento.**
- **Estado de la partida.**
- **Validación de acciones.**
- **Resultado final.**

**Render Free**

- **Alojamiento del servidor Node.js para que puedan conectarse dos móviles desde redes diferentes.**

**Google Cloud/Firebase Console**

- **Configuración de Google Sign-In.**
- **Huellas SHA del proyecto Android.**
- **Proveedores de Authentication.**
- **Reglas y colecciones de Firestore.**
