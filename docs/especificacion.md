# FortRush · Especificación y decisiones para la IA

Especificación de trabajo consolidada el 23 de noviembre de 2026 para la entrega prevista el 30 de noviembre de 2026. Recoge las decisiones de la usuaria y el plan incremental para la IA. Sustituye al plan anterior por sesiones y a las propuestas incompatibles del documento de Claude. Leer el documento completo antes de trabajar.

## Base del proyecto

- Android nativo con Kotlin y Jetpack Compose, orientación vertical.
- Estrategia multijugador 1v1 por Internet, con cuatro cartas fijas.
- Cloudflare en plan gratuito para el servidor. Firebase en plan gratuito para identidad y datos duraderos. Sin activar facturación.
- Servidor autoritativo que valida acciones, simula combate y decide el resultado.
- Mapa compartido con camino continuo, sinuoso y simétrico mediante giro de 180 grados. Cada jugador ve su base abajo.
- Siete huecos de torre por jugador. Balance ajustable tras las pruebas.
- Torres indestructibles por el enemigo en la primera versión.
- Duración normal de cuatro minutos, seguida de resolución de muerte súbita según las reglas que se terminen de confirmar.
- La planificación original v0.8 conserva los requisitos de entrega, estilo medieval cartoon y documentación del uso de IA. Las decisiones explícitas de este documento prevalecen sobre los valores anteriores.

## Economía confirmada

| Parámetro | Valor |
|---|---:|
| Monedas iniciales | 150 |
| Límite de monedas | 1000 |
| Ingreso durante los primeros 180 segundos | 5 por segundo |
| Ingreso durante el último minuto | 10 por segundo |
| Espadachines, grupo de tres | 45 |
| Escuderos, grupo de dos | 55 |
| Torre de flechas | 100 |
| Torre de cañón | 130 |

Los valores sirven como balance inicial y podrán ajustarse tras jugar.

## Tropas confirmadas

| Parámetro por unidad | Espadachín | Escudero |
|---|---:|---:|
| Vida | 60 | 110 |
| Daño de ataque | 10 | 6 |
| Frecuencia contra el muro | Un golpe por segundo | Un golpe por segundo |
| Tiempo para recorrer todo el camino sin combatir | 25 segundos | 30 segundos |
| Unidades por despliegue | 3 | 2 |
| Espera para reutilizar la carta | 3 segundos | 3 segundos |

Cada carta tiene su propio temporizador de reutilización.

Al llegar al castillo, la tropa aplica su daño una vez y desaparece. Cada espadachín quita 10 y cada escudero 6.

Espadachines y Escuderos atacan una vez por segundo también contra otras tropas, con 10 y 6 de daño por golpe respectivamente. Cada tropa ataca al enemigo más cercano. Varias tropas pueden atacar al mismo enemigo. Los golpes simultáneos se aplican juntos y pueden causar muertes mutuas.

### Defensa del escudero

- Cada impacto de flecha tiene un 10 % de probabilidad de reducir su daño un 30 %.
- Cada impacto de espada tiene un 3 % de probabilidad de reducir su daño un 30 %.
- Los cañonazos hacen daño completo.
- Ejemplo confirmado, un impacto de 10 causa 7 cuando se activa la reducción.
- El servidor resuelve la probabilidad. El daño y la vida conservan decimales internamente y la vida se muestra redondeada en pantalla. Por ejemplo, una flecha de 8 causa 5,6 si se activa la reducción. Para permitir pruebas reproducibles, la simulación recibirá una fuente aleatoria controlada por el servidor.

### Despliegue de tropas

Se sueltan sobre el camino dentro de la mitad propia. Al arrastrar se marca la zona válida. La posición elegida afecta al tiempo restante de viaje. Se reserva una franja central sin despliegue equivalente al 5 % de la longitud total del camino, centrada en su punto medio. Al arrastrar una carta de tropa se resaltan las zonas prohibidas, incluida la mitad rival, la franja central y el exterior del camino. La indicación incluirá color y una señal visual adicional, como sombreado o patrón. Las unidades del grupo aparecen juntas alrededor del punto donde se suelta la carta, con una pequeña separación para distinguir sus figuras y siempre dentro de la zona válida. La separación será configurable. Se permite soltar la carta sobre todo el ancho visible del camino dentro de la zona válida. El grupo se coloca en el punto del recorrido más cercano.

## Torres confirmadas

| Parámetro | Flechas | Cañón |
|---|---:|---:|
| Daño | 8 | 20 |
| Intervalo de disparo | 0,7 segundos | 2 segundos |
| Espera de construcción por carta | 0 segundos | 0 segundos |
| Alcance relativo | 100 % | 80 % del alcance de flechas |

El cañón tiene un radio de explosión igual al 10 % de su propio alcance. Cada enemigo dentro recibe los 20 de daño completos.

En cada disparo, la torre elige al enemigo más avanzado hacia su castillo dentro del alcance. Cambia de objetivo cuando otro enemigo pasa a ser el más avanzado.

### Controles de construcción

Las tres formas estarán disponibles.

- Tocar un hueco propio libre y después la carta de torre.
- Tocar la carta de torre y después un hueco propio libre.
- Arrastrar la carta de torre hasta un hueco propio libre.

Las flechas y balas aplican el daño al impactar. Si el objetivo muere durante el vuelo, la flecha se pierde y la bala de cañón explota en el punto al que iba dirigida, dañando a los enemigos que estén allí. Si varios enemigos están igual de avanzados, se elige al de menor vida restante. Si también empatan en vida, el servidor utiliza un orden fijo.

## Estructuras confirmadas

- Muro con 500 de vida inicial.
- Castillo con 400 de vida inicial.
- El muro destruido no se repara.

## Registro de decisiones cerradas

Los 22 puntos se han revisado con la usuaria. Los parámetros técnicos de ajuste visual se implementarán como configuración editable y se documentarán.

1. Resuelta. Daño al impactar y comportamiento ante muerte del objetivo confirmados.
2. Resuelta. Flechas con alcance del 25 % del ancho del tablero, cañón del 20 %. Desempate por menor vida restante y después orden fijo.
3. Resuelta. Ambas tropas atacan una vez por segundo. Empiezan al estar prácticamente en contacto, con un pequeño margen para las armas. La distancia será configurable y se calibrará visualmente con las figuras.
4. Resuelta. Franja central del 5 % sin despliegue, selección sobre todo el ancho visible del camino, grupo alrededor del punto más cercano con separación configurable y resaltado de zonas prohibidas.
5. Resuelta. Se conservan decimales internamente y se redondea la vida solo al mostrarla. La implementación utilizará aleatoriedad controlada por el servidor para permitir pruebas reproducibles.
6. Resuelta. Máximo de 20 tropas vivas por jugador. Si el grupo completo excede el límite, se rechaza el despliegue entero sin cobrar monedas ni activar el cooldown.
7. Resuelta. Al destruir un muro se conceden 50 monedas al atacante y 30 al defensor, una sola vez por muro.
8. Resuelta. Las recompensas por muro y la venta de torres pueden superar las 1000 monedas. El ingreso automático se detiene con un saldo de 1000 o más y conserva todo el saldo existente. Si un ingreso pasivo cruza el límite, se acredita solo hasta 1000.
9. Resuelta. Venta de torres con devolución del 50 % del coste redondeada hacia abajo, confirmación previa y hueco liberado. Disponible durante juego normal y bloqueada durante cuenta atrás y muerte súbita.
10. Resuelta. Al cumplir 240 segundos se detiene el combate, se eliminan tropas y torres y se bloquean los controles. Si solo un jugador conserva muro, gana. Si ambos lo conservan, gana el de más vida de muro. Si ninguno conserva muro, gana el de más vida de castillo. Igualdad en las vidas comparadas produce empate. El servidor decide el resultado y se representa una reducción de vida durante unos 3 segundos.
11. Resuelta. Cancelar solo mientras se busca rival. Tras emparejarse ambos tienen un máximo de 30 segundos para cargar. Si uno falla o abandona antes del inicio, el otro vuelve a la cola conservando su posición anterior. La cuenta atrás empieza cuando ambos han cargado y mantiene los controles bloqueados.
12. Resuelta. Cada jugador dispone de 30 segundos para reconectarse y la simulación continúa. Si vence su plazo y el rival está conectado, gana el rival. Si ambos se desconectan, mantienen plazos individuales y la partida se cancela sin ganador si ninguno regresa. Un resultado normal de combate anterior al vencimiento se respeta, incluso si favorece al desconectado. El servidor deberá especificar y probar el caso en que vence un plazo mientras el otro jugador sigue desconectado.
13. Resuelta. Si el servidor pierde la partida y no puede recuperarla, se cancela sin ganador ni derrota. Al entrar de nuevo con la misma cuenta, la conexión nueva sustituye a la anterior y recupera la partida si sigue activa.
14. Resuelta. Rendirse requiere confirmación, da la victoria al rival y solo está disponible durante el juego normal. Se bloquea durante la cuenta atrás y la muerte súbita.
15. Resuelta. Perfil con partidas completadas, victorias, derrotas, empates y monedas persistentes. Canceladas excluidas de contadores y recompensas. Pantalla final con victoria, derrota o empate y monedas ganadas, sin mostrar el motivo. Un único botón Volver a Inicio. Para buscar otra partida se vuelve primero a Inicio. El servidor conserva el motivo técnico y guarda resultados y recompensas con protección frente a duplicados y recuperación de escrituras fallidas.
16. Resuelta. El entrenamiento contra la máquina se implementará después de completar y probar el multijugador. Será una partida con un rival controlado por el servidor, usando las mismas reglas y validaciones. El algoritmo del bot se diseñará en esa etapa. Quedan por decidir entonces dificultad, recompensas y efecto sobre estadísticas.
17. Resuelta. Toda la interfaz y la documentación estarán en español.
18. Resuelta. Sonido aplazado para una etapa posterior. Reutilizar imágenes existentes y completar provisionalmente los gráficos que falten con elementos de Compose, como paneles, iconos y cuenta atrás, sustituibles después por imágenes definitivas.
19. Resuelta. Apodo de 3 a 16 caracteres, con letras, números y guion bajo. Único sin distinguir mayúsculas y minúsculas. Se elige al entrar por primera vez y puede cambiarse una vez cada siete días desde el último cambio.
20. Resuelta. La IA puede modificar el proyecto, guardar commits, subir a una rama de trabajo y desplegar en Cloudflare cuando pasen las pruebas, siempre dentro de los planes gratuitos y sin activar facturación. Debe conservar los datos existentes y mantener backups recuperables.
21. Plazo confirmado de una semana como máximo desde el 23 de noviembre de 2026, con objetivo de entrega el 30 de noviembre de 2026. Priorizar una partida multijugador completa y comprobable. La disponibilidad de horas de la usuaria queda sin concretar y el plan deberá adaptarse al estado real del repositorio.
22. Resuelta. Enunciado docente recibido íntegramente. Se exige multijugador, mapa aleatorio, objetivo, comunicación entre máquinas, gestión de partida y resultado, estética, usabilidad, accesibilidad y documentación del proceso con agentes de IA. Las FAQ permiten herramientas existentes y hacen opcionales el login y guardar partidas. FortRush mantiene ambos por decisión de producto.

## Preparación del plan definitivo

Ejecutar las tareas del plan siguiente según sus dependencias. Inspeccionar el repositorio para distinguir código existente de funciones realmente comprobadas. El estado descrito por Claude requiere esa verificación.

La IA deberá registrar avance y pruebas, conservar las decisiones de juego, mantener credenciales privadas fuera del APK y Git, y comprobar las condiciones gratuitas vigentes antes de desplegar.

## Alcances y edición de balance

Confirmado como punto de partida para las pruebas.

- Alcance de flechas igual al 25 % del ancho del tablero.
- Alcance del cañón igual al 20 % del ancho del tablero.
- Mostrar el círculo de alcance al seleccionar una torre o un hueco.
- Mantener áreas, alcances, daños y demás valores de balance juntos, con nombres claros y unidades documentadas, para que la usuaria pueda editarlos fácilmente.

### Editor visual de administración futuro

La usuaria solicita una opción de editor para su cuenta TheIza. Permitirá editar visualmente áreas y valores del juego con una vista previa simultánea del resultado. El alcance detallado, las opciones de publicación y la prioridad del editor se concretarán antes de implementarlo.

La cuenta se identificará por su UID autenticado y una autorización de administrador validada en el servidor. El apodo TheIza sirve como referencia de la cuenta, y por sí solo no concede permisos. La identificación del UID queda pendiente.

Para conservar partidas consistentes, el diseño previsto guardará una versión de configuración al crear cada partida. Los detalles de borradores, validación, publicación y recuperación de versiones se definirán al preparar el editor.

Decisión de interfaz confirmada para el despliegue: resaltar expresamente dónde NO se pueden colocar tropas mientras se arrastra la carta.

Límite confirmado de tropas: 20 vivas simultáneamente por jugador. El despliegue de un grupo es íntegro o se rechaza sin coste ni cooldown.

Recompensa confirmada por muro destruido: 50 monedas al atacante y 30 al defensor, concedidas una sola vez por muro.

Excepción confirmada al límite de monedas: recompensas de muro y ventas pueden superar 1000. El ingreso pasivo respeta el límite sin reducir el saldo acumulado.

## Monedas persistentes del perfil

Cada victoria concede 250 monedas al usuario. Son un saldo persistente del perfil, separado del recurso temporal usado durante las batallas. El límite de 1000 y los ingresos automáticos definidos para batalla corresponden únicamente al recurso de batalla.

El servidor concede la recompensa una sola vez por partida y ganador, incluso si se reintenta guardar el resultado. Recompensas confirmadas del perfil: 250 monedas por victoria, 100 por empate y 0 por derrota. Las cuentas nuevas empiezan con 0 monedas de perfil. Por ahora, las monedas del perfil se acumulan y se muestra su saldo. La usuaria decidirá sus usos en una ampliación futura. Confirmado: las partidas canceladas sin ganador conceden 0 monedas de perfil.

## Ideas futuras para revisar tras completar el juego

- Regalos diarios. Solicitud de la usuaria para revisar cuando el juego esté terminado. Quedan pendientes las recompensas, la frecuencia, las condiciones y la interfaz.

### Contadores del perfil

Confirmado: guardar y mostrar partidas completadas, victorias, derrotas y empates. Las partidas canceladas quedan excluidas de todos estos contadores. Los resultados y recompensas se actualizarán de forma consistente, con protección frente a reintentos y duplicados.

### Entrenamiento contra la máquina

Confirmado para una etapa posterior a completar y probar el multijugador. Se jugará una partida contra un rival controlado por la máquina. Se reutilizarán las reglas y validaciones de las partidas normales. El diseño del algoritmo, dificultad, recompensas y contadores del entrenamiento se concretará al preparar esa etapa.

Sonido solicitado como mejora futura. Su implementación se abordará más adelante.

## Autorización y recuperación

Antes de modificar el proyecto, inspeccionar el estado de Git y preservar los cambios existentes de la usuaria. Mantener una copia inicial recuperable del proyecto, incluidos archivos sin seguimiento necesarios para restaurarlo. Guardar los secretos y configuraciones privadas únicamente en copias locales protegidas y excluidas de Git.

Trabajar en una rama de desarrollo y crear puntos de recuperación verificables antes de cada tarea y despliegue. Registrar el último estado comprobado y cómo restaurarlo. Conservar los backups anteriores hasta comprobar el nuevo estado. Antes de desplegar, identificar la versión anterior y el procedimiento de reversión. La copia del código y la reversión del servidor deben distinguirse de la recuperación de datos persistentes. Evitar cambios destructivos en los datos existentes.

Estos requisitos describen el trabajo futuro. No se ha creado un backup durante la preparación de este documento.

## Requisitos docentes y evidencia de entrega

| Requisito | Implementación prevista | Evidencia que debe guardarse |
|---|---|---|
| Varios jugadores | Dos personas con sus propias cuentas y dispositivos | Partida completa entre dos móviles, uno con Wi-Fi y otro con datos |
| Espera e identificación | Cola, apodos, emparejamiento y cuenta atrás | Capturas y prueba de cancelación |
| Tablero aleatorio y objetivo | Mapa generado y compartido, bases opuestas, destruir el castillo | Pruebas del generador y capturas de ambos clientes |
| Comunicación entre máquinas | WebSocket y servidor autoritativo en Cloudflare | Prueba real remota y protocolo documentado |
| Gestión y resultado de partida | Servidor valida acciones y decide resultado | Pruebas de rechazo de acciones y resultado coincidente |
| Estética y accesibilidad | Recursos medievales, interfaz vertical, controles legibles | Revisión de interacción, letra grande y TalkBack |
| Desarrollo con IA | Diario, decisiones, errores y reformulaciones | Exportaciones de conversaciones y esquema de presentación |
| Recuperación | Git, backups y versiones desplegadas | Punto de restauración y procedimiento verificado |

El profesor considera el funcionamiento y la explicación del proceso igualmente importantes. El historial de partidas es opcional para la actividad. El proyecto mantiene perfiles y recompensas como requisitos propios. La referencia a Among Us es un ejemplo del enunciado.

## Reglas de trabajo para la IA

1. Auditar antes de reestructurar. Reutilizar la aplicación, servidor, autenticación, mapa y recursos existentes que cumplan los requisitos.
2. Consultar las instrucciones locales aplicables. Crear o actualizar AGENTS.md conservando sus instrucciones pertinentes.
3. Preservar el trabajo existente y crear el backup inicial antes de editar código. Usar una rama de trabajo, por defecto codex/gameplay, compatible con la organización del repositorio.
4. Ejecutar una tarea verificable cada vez. Repetir una tarea ya implementada solo cuando falte comprobarla o corregirla.
5. Tras cada tarea, registrar archivos cambiados, prueba ejecutada, resultado real y siguiente paso en docs/progreso.md. Guardar un punto de recuperación.
6. Mantener docs/diario-ia.md durante el desarrollo. Registrar herramienta y modelo conocido, objetivo, prompt relevante, respuesta, decisión propia de la usuaria, errores, reformulación, investigación y pruebas. Dejar el aprendizaje personal para que la usuaria lo escriba o confirme.
7. Comprobar documentación oficial vigente y viabilidad del bucle de simulación en Cloudflare Free antes de ampliar la integración. Registrar CPU, mensajes, duración, cuota diaria y limitaciones observadas. Verificar Firebase gratuito y sus cuotas. Mantener desactivada la facturación.
8. Consolidar parámetros en una configuración clara con unidades y comentarios o documentación. Cada partida conserva su versión de balance.
9. Validar identidad con Firebase y autorización en servidor. Nunca confiar en UID, monedas, daño, ganador ni privilegios enviados por el cliente. Mantener secretos fuera de Git y APK. La configuración pública de Firebase Android puede permanecer donde requiera su integración.
10. Acciones con ID único, validación y confirmación. Reintentar una acción devuelve su resultado anterior sin aplicar de nuevo sus efectos. Instantáneas con partida, versión y secuencia para descartar mensajes antiguos.
11. Mantener la simulación comprobable, con paso fijo y aleatoriedad sembrada controlada por el servidor. Elegir la frecuencia de cálculo y envío según mediciones y cuotas.
12. Guardar resultado, recompensa y contadores con operación transaccional o mecanismo equivalente idempotente por partida. Reintentar fallos de persistencia conservando una referencia duradera al resultado pendiente.
13. Probar regresiones pertinentes, compilación Android y escenarios multijugador. Informar por separado lo implementado y lo comprobado, con pruebas manuales pendientes.
14. Continuar por tareas independientes ante un bloqueo. Pedir ayuda cuando haga falta acceso de cuenta, secretos o resolver un conflicto material de reglas. Conservar el progreso y explicar el bloqueo concreto.
15. Desplegar tras las pruebas pertinentes, verificar el servicio y conservar la versión anterior recuperable. Un endpoint de salud debe reflejarse como comprobación de servicio y complementarse con una prueba de partida.
16. Tratar los plazos como objetivo de planificación. Informar pronto si la auditoría muestra que el alcance supera el plazo, proponiendo prioridades concretas sin cambiar reglas confirmadas por cuenta propia.

## Plan por tareas con prueba de cierre

Completar y comprobar cada fila antes de continuar con sus dependencias. Las tareas existentes y verificadas se marcan completadas con evidencia.

| ID | Acción específica | Prueba de cierre |
|---|---|---|
| 00 | Auditar repositorio, Git, servidor, Android, documentos y despliegue conocido | Inventario de funciones comprobadas, fallos y diferencias frente a esta especificación |
| 01 | Crear backup recuperable y punto de partida de Git | Comprobar contenido y documentar cómo restaurar proyecto y versión de servidor |
| 02 | Ejecutar pruebas existentes y compilar Android | Resultado real registrado, con errores de base corregidos cuando sea posible |
| 03 | Probar Google, primer apodo, regreso y perfil | Cuenta nueva y existente, panel de apodo cerrado tras guardarlo, exclusión de duplicados |
| 04 | Comprobar viabilidad gratuita de servidor y simulación | Prueba mínima de WebSocket, temporizadores y coste medido o estimado con hipótesis explícitas |
| 05 | Consolidar balance y contrato de mensajes | Servidor entrega configuración y versión a clientes, parámetros sin duplicación de autoridad |
| 06 | Verificar creación de partida con dos identidades distintas | Una partida con ID y dos jugadores, rechazo de identidad duplicada |
| 07 | Adaptar y validar mapa y siete huecos por lado | Camino conectado, sin cruces ni salida del tablero, huecos válidos y simétricos, respaldo válido |
| 08 | Verificar cola y cancelación | Dos jugadores se emparejan por llegada, tercero espera, cancelar elimina solo al solicitante |
| 09 | Sincronizar carga y cuenta atrás | Ambos comparten geometría y comienzo, controles bloqueados, fallo de carga devuelve rival a su posición |
| 10 | Desplegar Espadachines desde cliente con validación | Cobro de 45 una vez, tres unidades, cooldown de 3 segundos, posiciones inválidas rechazadas |
| 11 | Implementar movimiento y combate entre tropas | Recorrido completo en 25 segundos sin oposición, contacto y golpes simultáneos comprobados |
| 12 | Implementar muro, castillo y victoria normal | Muro con 500, castillo con 400, recompensa de muro una vez, entrada consume tropa y aplica daño |
| 13 | Completar ingresos y primera partida de extremo a extremo | Dos clientes ven combate y resultado coincidente con economía correcta durante una partida completa |
| 14 | Añadir Escuderos y máximo de tropas | Dos unidades, coste 55, vida 110, viaje 30 segundos, probabilidades reproducibles y límite 20 |
| 15 | Construir torre de flechas por los tres controles | Coste, hueco propio, ocupación y alcance correctos, objetivo avanzado y desempates |
| 16 | Resolver proyectiles e impactos | Flechas causan daño al llegar y se pierden si murió su objetivo, dos clientes ven el mismo impacto |
| 17 | Añadir cañón con explosión | Coste 130, daño 20 cada 2 segundos, alcance y radio correctos, enemigos del área dañados una vez |
| 18 | Añadir venta con confirmación | Devuelve 50 % redondeado abajo y libera hueco, cancelación del diálogo conserva la torre |
| 19 | Añadir temporizador y muerte súbita | Casos de ambos muros, uno solo y ninguno, empate, animación y bloqueo comprobados |
| 20 | Añadir rendición | Confirmación decide victoria rival una vez, rechazo en fases bloqueadas |
| 21 | Implementar reconexión y sustitución de conexión | Regreso antes de 30 segundos restaura estado completo sin duplicar acciones |
| 22 | Resolver desconexiones dobles y pérdida de servidor | Casos con plazos distintos, resultado de combate previo y cancelación irrecuperable |
| 23 | Guardar resultados, perfil y recompensa | Victoria 250, empate 100, derrota y cancelación 0, sin duplicación tras reintentos |
| 24 | Implementar pantalla final y retorno | Resultado y monedas ganadas, único botón Volver a Inicio, saldo persistente actualizado |
| 25 | Completar apodo editable cada siete días | Reserva única atómica y tiempo del servidor, cliente sin permiso para eludir la regla |
| 26 | Revisar estética, interacción y accesibilidad | Zonas prohibidas claras, círculo de alcance, letra grande, TalkBack y controles de al menos 48 dp |
| 27 | Jugar dos partidas reales desde redes distintas | Partida normal y prueba de reconexión con capturas o vídeo y resultados coincidentes |
| 28 | Corregir regresiones, generar APK y verificar despliegue | APK instalable, servicio disponible y prueba de partida con versión final |
| 29 | Preparar entrega y exposición | Matriz de requisitos, evidencias, diario, decisiones y esquema de presentación completos |

## Distribución orientativa de la semana

- Día 1. Auditoría, backup y comprobación del recorrido existente, tareas 00 a 09 según lo que ya funcione.
- Día 2. Espadachines y primera partida completa, tareas 10 a 13.
- Día 3. Escuderos, torres y proyectiles, tareas 14 a 18.
- Día 4. Finales de partida y rendición, tareas 19 y 20.
- Día 5. Reconexión, resultados y perfil, tareas 21 a 25.
- Día 6. Accesibilidad y pruebas reales con dos móviles, tareas 26 y 27.
- Día 7. Correcciones finales, APK y exposición, tareas 28 y 29.

La distribución es una estimación del asistente. Ajustarla tras la auditoría al código existente y las horas disponibles. Registrar evidencias cada día y reservar el último día para cerrar la entrega.

## Detalles técnicos que deberá cerrar la implementación

Estas decisiones técnicas quedan propuestas para verificar durante la tarea correspondiente. Mantenerlas configurables y registrar lo elegido.

- Tiempo de vuelo de proyectiles y trayectoria. Elegir valores legibles y coherentes entre servidor y animación. El seguimiento de flechas con objetivo vivo debe conservar el daño al impacto.
- Distancia de contacto y separación de figuras. Calibrar con los recursos reales para evitar superposiciones. Resolver empates por ID estable.
- Si ambos castillos llegan a cero en el mismo paso, proponer empate por destrucción simultánea y documentarlo.
- Si expira un plazo mientras ambos siguen desconectados, conservar la opción del rival de volver dentro de su propio plazo. Si vuelve después de expirar el del otro, gana por abandono. Si ninguno regresa dentro de sus plazos, cancelar. Verificar que esta interpretación cumple la regla confirmada.
- Mostrar una cancelación como Partida cancelada, con 0 monedas y el mismo único botón de retorno.
- Establecer límites de mensajes y entradas inválidas, invalidar conexiones sustituidas y enviar resultado al reconectar a una partida finalizada.
- Letras permitidas del apodo y normalización. Conservar la compatibilidad con perfiles existentes y documentar el conjunto admitido.
- Crear reservas de apodos y cambios con tiempo del servidor y exclusión mutua para impedir duplicados.
- Reutilizar los cinco mapas de respaldo si existen. Completar y validar los que falten.
- El editor visual de TheIza, entrenamiento, regalos diarios y sonido se mantienen en la lista futura. La configuración centralizada sí forma parte del trabajo actual.

## Diario y exposición

Cada tarea deja una entrada breve con objetivo, herramienta usada, instrucción relevante, propuesta, decisión tomada, error encontrado, reformulación y resultado real. Mantener exportaciones de conversaciones disponibles y elegir fragmentos que expliquen cambios de dirección.

Preparar un esquema con idea inicial, herramientas de IA, decisiones de arquitectura, tecnologías investigadas por la usuaria, ejemplos de prompts y reformulaciones, errores y correcciones, demostración y requisitos comprobados. La usuaria aporta su aprendizaje y valoración personal.

Evitar que el agente atribuya a la usuaria investigaciones o pruebas que no ha realizado. Guardar evidencias de las pruebas realmente ejecutadas y pedir la prueba manual concreta cuando sea necesaria.

## Mensaje inicial para la IA

Lee este documento completo y las instrucciones locales del proyecto. Empieza por la tarea 00, comprueba el estado real y prepara el backup de la tarea 01 antes de editar código. Reutiliza lo que exista y continúa por las tareas pendientes en orden de dependencias. Trabaja una entrega verificable cada vez, actualiza docs/progreso.md y docs/diario-ia.md y conserva puntos recuperables. Puedes modificar, hacer commits, subir una rama y desplegar según la autorización de este documento, siempre en planes gratuitos. Ante un bloqueo sigue con trabajo independiente y comunica la necesidad concreta. Al retomar otra sesión, lee el progreso y verifica el estado antes de continuar.
