# FORT RUSH — INSTRUCCIONES DE INTEGRACIÓN VISUAL v3

## LECTURA OBLIGATORIA
Lee `LEEME.md`, `manifest_assets.csv`, la planificación en `referencias/planificacion_original.md` y las capturas de referencia antes de modificar código. Este paquete es un **kit de integración con assets provisionales**; NO garantiza que todos los recortes sean definitivos ni que todos los sprites sean válidos. No afirmes que está terminado sin pruebas visuales reales.

## REGLAS INNEGOCIABLES
1. No cambies el servidor, protocolo, emparejamiento, reglas del mapa, autenticación ni lógica de combate por motivos estéticos. Haz cambios de UI de forma incremental y verificable.
2. Android nativo Kotlin + Jetpack Compose, siempre vertical. Tres pestañas: **Cartas | Inicio | Usuario**. La pantalla Inicio es la pestaña central.
3. Usa la estética de `referencias/mapa_referencia.png` para el mapa y `referencias/home_objetivo.webp` para la jerarquía visual del Inicio, sin copiar interfaces de terceros literalmente.
4. **Las cuatro cartas son:** Escudero (50 monedas), Guerrero (70), Torre de flechas (110), Torre de cañón (120). Prohibido mago, elixir, gemas y costes distintos. El coste debe aparecer con un icono de moneda dorada, no una gota.
5. No inventes estadísticas de jugador ni niveles: si no existen en datos reales, omítelos o marca explícitamente como demostración.
6. Texto SIEMPRE dibujado por Compose, no incrustado en assets. Evita el botón con texto `boton_principal.png`; usa `boton_atacar_sin_texto.png` y superpone `¡ATACAR!`.
7. No utilices la lámina de conceptos como fondo de pantalla. Usa assets individuales; si un recorte tiene fondo oscuro, restos de etiquetas o está roto, NO lo integres: conserva el original existente y repórtalo.

## INSPECCIÓN Y MAPEO
- Primero inventaría todos los `drawable-nodpi` existentes y compáralos con `manifest_assets.csv`.
- Revisa visualmente cada asset que quieras reemplazar, especialmente las imágenes con estado `RECORTE DE CONCEPTO - REVISAR`. Algunos recortes automáticos pueden contener halos, partes ausentes o bordes incompletos.
- NO sobrescribas un asset existente de mayor calidad con uno provisional inferior.
- Conserva la nomenclatura y ajusta los recursos de manera segura. No cambies referencias R.drawable sin revisar su uso.
- Muestra el plan de sustituciones antes de realizar cambios masivos.

## PANTALLA INICIO
- Fondo `assets/fondos/fondo_inicio.png` (1080×2400), `ContentScale.Crop` centrado; no estirar y no dibujar elementos esenciales en los extremos recortables.
- HUD superior con nivel/experiencia SOLO si existe; monedas reales; ajustes accesibles. No superponer status bar.
- Banner del jugador con avatar, apodo de 3–16 caracteres, contenido dentro del panel.
- Castillo central de gran tamaño, con plataforma y sombra, sin deformar la ilustración.
- Botón `boton_atacar_sin_texto.png` con texto `¡ATACAR!` encima, centrado, tacto mínimo 48 dp, estados pulsado/deshabilitado.
- Barra inferior de madera/piedra, pestañas Cartas, Inicio, Usuario con seleccionado visible. Respetar barra de gestos y navegación del sistema.

## PANTALLA CARTAS
- Fondo `fondo_cartas.png` con recorte proporcional. Cuatro cartas y solo cuatro; orden: Escudero, Guerrero, Torre de flechas, Torre de cañón.
- Marco limpio `marco_carta_vacio.png`, o `marco_carta_seleccionado.png` para selección; imagen de unidad centrada; nombre y coste en Compose, siempre dentro.
- Los iconos de coste son monedas. Usa coste de 50,70,110,120 desde un único modelo compartido con la lógica de partida (no duplicar valores inconsistentes).
- Si el layout es 2×2 en móvil, garantiza que todas las cartas sean legibles sin desbordamiento. Evita scroll horizontal accidental.

## PANTALLA USUARIO
- Fondo `fondo_usuario.png`, avatar y apodo, panel de perfil. Mostrar únicamente estadísticas realmente disponibles en el modelo o backend.
- Botón de cambio de apodo con las restricciones del documento de planificación y cooldown real, sin inventar temporizadores.
- No mostrar gemas, logros, trofeos ni recompensas como funcionalidad si no están implementados.

## MAPA DE COMBATE
- El servidor Node.js genera y envía geometría final compartida; ambos clientes ven mismo mapa rotado 180° según jugador, pero los textos/HUD permanecen derechos.
- Camino sinuoso continuo, sin ramificaciones ni autointersecciones, de castillo a castillo pasando por el centro; dibujar por código (Canvas/Path) con sombra, borde oscuro y tierra. NO usar tiles de T/cruces como mecánica: son solo referencias de estilo.
- 4 plataformas por jugador (8 total), dos cerca del castillo y dos hacia el centro, simetría rotacional. Decoración no puede cubrir unidades ni plataformas.
- Castillos, torres, murallas, árboles, rocas y tropas deben renderizarse como sprites independientes, respetando proporción y orden de profundidad.
- Las hojas de sprites del concepto NO equivalen a spritesheets utilizables; conserva las animaciones reales existentes hasta tener hojas verificadas con frames alineados.

## TIPOGRAFÍA Y DESBORDAMIENTO (CRÍTICO)
- Usa **Nunito Sans ExtraBold/Black** u otra tipografía redondeada con licencia OFL verificada; incorpora el archivo de fuente legalmente en `res/font`. No copies una fuente propietaria de Supercell. Si no hay fuente disponible, usa una fuente Android redondeada pesada como fallback y documenta cuál.
- Crea estilos centralizados para título, botón, encabezado, nombre de carta, coste, contadores y texto secundario; colores claros, sombra/contorno sutil cuando haga falta.
- NINGUNA palabra puede sobresalir de recuadros: usa `BoxWithConstraints`, padding horizontal >= 12 dp, ancho máximo, `maxLines`, `TextOverflow.Ellipsis` para nombres y labels, `softWrap` para descripciones, y tamaño adaptativo solo si es necesario. NO recortes el texto con `clip` como parche.
- En apodos de 16 caracteres, valida que el banner soporte el texto. Prueba textos como `GUERRERO_1234567` y nombres de carta largos.
- Evita superposiciones con botones, iconos y badges. Los valores deben caber también con escalado de fuente del sistema a 1.3× y 1.5×.

## RESPONSIVE Y ACCESIBILIDAD
- Probar al menos 360×800 dp, 393×873 dp, 412×915 dp y 360×740 dp, con navegación por gestos y botones; status/navigation insets mediante `WindowInsets.safeDrawing`.
- Fondos en 1080×2400 con `Crop`; recursos de primer plano con `ContentScale.Fit`. No uses tamaños dp absolutos para distribuir verticalmente toda la pantalla.
- `Modifier.weight`, `BoxWithConstraints`, `aspectRatio` y `LazyVerticalGrid` cuando corresponda. Mantén objetivos táctiles >=48dp, contraste suficiente y `contentDescription` en iconos interactivos.

## PROCEDIMIENTO DE TRABAJO
Fase A: auditar proyecto y capturas actuales. Fase B: instalar fondos y sistema tipográfico, sin alterar mecánicas. Fase C: rehacer Inicio y verificar en emulador. Fase D: Cartas y costes. Fase E: Usuario y navegación. Fase F: combate y renderizado modular. Fase G: capturas de todas las pantallas y correcciones.

## CRITERIOS DE ACEPTACIÓN
- Compilación Gradle correcta y aplicación ejecutable.
- Capturas reales en los 4 tamaños y con fuente 1.5×; comparar visualmente con las referencias.
- No hay texto cortado ni fuera de paneles, ni imágenes estiradas, ni fondos con franjas vacías.
- Las 4 cartas tienen nombre y coste correctos con moneda, sin mago ni elixir.
- Navegación Cartas/Inicio/Usuario operativa y con estado seleccionado visible.
- No se modifican reglas de juego ni se introducen datos ficticios.
- Informar qué assets provisionales se descartaron y cuáles faltan. Si no puedes tomar capturas o probar en emulador, dilo claramente: no declares verificación visual realizada.
