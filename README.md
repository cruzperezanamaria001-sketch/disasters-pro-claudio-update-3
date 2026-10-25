# 🌪️ Natural Disasters — Forge 1.20.1

Mod de **desastres naturales** que se activan con un **control** (panel con botones) o con comandos.

## Desastres incluidos

| Desastre | Qué hace |
|---|---|
| ⚫ **Agujero negro** | **Modelo 3D**: esfera negra con halo, disco de acreción giratorio y anillo de luz. Atrae entidades, devora bloques y mata lo que cae al núcleo |
| 🌊 **Tsunami** | **Ola 3D** curva con cresta de espuma que avanza por el terreno empujando y dañando entidades. **No coloca agua real ni modifica el mundo** |
| 🌪️ **Tornado** | **Embudo 3D** giratorio de 3 capas con **nubarrones 3D** encima. Vaga por el mapa, levanta mobs/jugadores y arranca bloques (que salen volando como bloques reales) |
| ☄️ **Meteorito** | **Roca 3D** incandescente girando, con estela de fuego. Explota y deja un cráter con el núcleo |
| 🌠 **Lluvia de meteoritos** | Muchos meteoritos sobre una zona |
| 🌋 **Volcán** | Cono de bloques reales que crece del suelo, cráter de lava, **columna de ceniza 3D** y **bombas de lava 3D** que vuelan en arco y caen hasta ~100 bloques a la redonda, explotando y dejando charcos de magma |
| ❄️ **Ventisca** | **Nube 3D** blanca sobre la zona, nieve horizontal, lentitud, congelación y nieve/hielo en el mundo |
| 💧 **Inundación** | El agua sube por capas en toda la zona, se mantiene y luego baja |
| 🫨 **Terremoto** | Sacude entidades, agrieta el suelo, derrumba estructuras débiles y abre fisuras profundas |
| 🌨️ **Granizada** | Nube 3D grande que lanza **bolas de hielo 3D** sobre la zona (dañan al caer) |
| 🔥 **Lluvia de fuego** | Nubarrón rojo que lanza **rocas ardientes 3D** que explotan e incendian |
| 🕳️ **Sumidero** | El suelo se hunde en un embudo profundo, tragándose árboles y casas, con nube de polvo 3D |
| ☢️ **Explosión nuclear** | Destello, **onda expansiva 3D**, cráter enorme, escombros volando y **hongo de humo 3D**. Muy destructivo |
| ☄️ **Cometa Halley** | Cometa 3D (núcleo helado, coma y dos colas) que cruza el cielo en una curva controlada y se estrella: explosión, cráter, **hielo controlado** alrededor y, **20 s (400 ticks) después, el hielo se convierte en agua real** |
| 🌀 **Agujero de gusano** | Túnel 3D con **entrada** (azul-violeta, absorbe) y **salida** (naranja-dorada, expulsa) a ~100 bloques. Transporta jugadores, criaturas, objetos y orbes de forma segura |
| ⚫⚪ **Agujero negro hipotético + agujero blanco** | Sistema independiente del agujero negro original: el negro atrae y retiene lo que cruza su horizonte, el **agujero blanco** aparece en otro lugar seguro y lo expulsa |
| 🔴 **Colisión con Marte** | Marte (esfera 3D con textura) aparece en el cielo y se acerca acelerando mientras crece. Hay brasas, retumbos y **vibración de cámara**. El impacto abre un cráter **solo dentro del radio configurable** (`marsImpactRadius`) |
| 🌪️ **Huracán gigante** | Embudo enorme con nubarrones que **se desplaza** con velocidad y rumbo variables; levanta y arrastra entidades, arranca bloques débiles, trae lluvia, viento, truenos y rayos |
| 🔥 **Ola de calor extrema** | Domo anaranjado y sol extremo 3D, humo de calor. La exposición se acumula por entidad (debilidad → lentitud → daño → ignición). Incendios limitados y configurables |
| 🥶 **Ola de congelación extrema** | Frente azul y blanco que se expande: congela y ralentiza a mobs/jugadores (duración configurable) y cubre el terreno con **hielo temporal registrado** que se restaura solo |
| 🛸 **Invasión OVNI** | OVNI 3D que cruza el mundo, **haz de abducción**, rayos de energía en un radio configurable y **alienígenas** hostiles |
| ⛈️ **Tormenta eléctrica** | **Nubarrón 3D** oscuro sobre la zona y rayos constantes (prefieren jugadores y mobs) |

> Los desastres 3D son **entidades visuales** (`DisasterVisualEntity`) dibujadas con geometría propia
> (esferas, discos, embudos, olas...). Las texturas están en `assets/disasters/textures/entity/`:
> puedes reemplazarlas por las tuyas manteniendo el nombre. La lógica y la geometría están en
> `entity/DisasterVisualEntity.java` y `client/DisasterVisualRenderer.java`.

## Cómo se usa

1. Consigue el **Control de Desastres** (pestaña *Herramientas y utilidades* del creativo, o crafteo abajo, o `/give @s disasters:disaster_controller`).
2. **Clic derecho** → se abre el panel.
3. Elige **Intensidad** (Baja / Media / EXTREMA) y **Objetivo** (*Donde miro* / *Sobre mí*).
4. Pulsa el desastre que quieras (o **Desastre aleatorio**). El botón **Detener todos los desastres** los frena (y limpia el agua).

**Comandos** (requieren ser OP):
```
/disaster spawn <tipo> [intensidad 1-3]     # tipos: black_hole tsunami tornado meteor meteor_shower volcano blizzard flood earthquake thunderstorm hailstorm fire_rain sinkhole nuke halley_comet wormhole hypothetical_black_hole mars_collision hurricane heat_wave freeze_wave ufo_invasion
/disaster stop
```

**Crafteo** (opcional):
```
R D R        R = Redstone
D E D        D = Diamante
R D R        E = Ojo de Ender
```

## Permisos y configuración

Por defecto solo **OPs o jugadores en creativo** pueden usar el control. Se cambia en
`config/disasters-common.toml` (se crea al abrir el mundo):

| Opción | Por defecto | Para qué sirve |
|---|---|---|
| `blockDamage` | `true` | Si es `false`, los desastres **no rompen ni cambian bloques** (solo efectos y daño a entidades) |
| `requirePermission` | `true` | `false` = cualquiera con el item puede usarlo (útil en supervivencia) |
| `durationMultiplier` | `1.0` | Multiplica la duración de los desastres |
| `maxActiveDisasters` | `6` | Límite de desastres a la vez |

> ⚠️ **Haz copia de seguridad del mundo** antes de probar con intensidad EXTREMA. Estos desastres destruyen terreno y construcciones de verdad, y los de intensidad alta pueden bajar los TPS.
> Los desastres activos **no se guardan**: si cierras el mundo, se cancelan.

## Compilar el mod con GitHub (sin instalar nada)

1. Crea un repositorio nuevo en GitHub.
2. Sube **todo el contenido de esta carpeta** (incluida la carpeta oculta `.github`).
   La forma más fácil, desde una terminal dentro de la carpeta:
   ```bash
   git init
   git add .
   git commit -m "Natural Disasters mod"
   git branch -M main
   git remote add origin https://github.com/TU_USUARIO/TU_REPO.git
   git push -u origin main
   ```
   (con *GitHub Desktop* también funciona: "Add local repository" → Publish).
3. Ve a la pestaña **Actions** del repo: se ejecuta solo el workflow **Build mod** (tarda 3-6 min la primera vez).
4. Cuando termine en verde, entra a esa ejecución y descarga el artefacto **desastres-naturales-forge-1.20.1** (dentro está el `.jar`).
5. Mete el `.jar` en la carpeta `mods` de tu Minecraft **1.20.1 con Forge 47.x**.

**Releases automáticos:** si creas un tag `v1.0.0` (`git tag v1.0.0 && git push --tags`), el workflow publica el `.jar` en la sección *Releases*.

### Compilar en tu PC (opcional)
Necesitas JDK 17. Luego: `./gradlew build` → el jar queda en `build/libs/`.
Para abrirlo en IntelliJ: *Open* → selecciona la carpeta → espera a que Gradle sincronice → `./gradlew genIntellijRuns`.

## Estructura

```
src/main/java/com/desastres/
├─ DisastersMod.java            # clase principal
├─ config/DisasterConfig.java   # opciones
├─ item/ , registry/            # el item "Control de Desastres"
├─ entity/                      # entidad visual 3D (sincroniza forma/tamaño al cliente)
├─ network/                     # paquete cliente→servidor del panel
├─ client/                      # pantalla (GUI) del control
└─ disaster/
   ├─ Disaster.java             # clase base
   ├─ DisasterType.java         # lista de desastres (añade aquí los tuyos)
   ├─ DisasterManager.java      # tick global y spawn
   ├─ DisasterCommands.java     # /disaster
   └─ impl/                     # un archivo por desastre
```

### Añadir tu propio desastre
1. Crea `impl/MiDesastre.java` que extienda `Disaster` y sobreescriba `tick()` (llama a `finish()` cuando acabe; `onEnd()` para limpiar).
2. Añade una línea en el enum `DisasterType`.
3. Añade las claves de traducción `disasters.disaster.<id>` y `.desc` en `assets/disasters/lang/*.json`.
   El botón aparece solo en el panel.

## Licencia
MIT — úsalo y modifícalo como quieras.


---

## Actualización 1 (versión 1.1.0)

**Correcciones**
- **Granizo duplicado:** había dos generadores; uno de ellos creaba granizo sobre *cualquier jugador del mundo* sin comprobar si estaba dentro de la tormenta. Ahora hay **un solo punto de generación**, limitado a la huella de la nube, con tope de bolas vivas por tormenta y sin generar nada si no hay jugadores cerca. Se conservan el daño y los efectos.
- **Tsunami:** ya **no coloca bloques de agua**. Conserva el modelo 3D (ahora se inclina para seguir el relieve), el empuje, el daño y la duración configurable; no deja nada en el mundo.

**Desastres nuevos:** Cometa Halley, Agujero de gusano y Agujero negro hipotético + agujero blanco.

**Detalles del cometa y el hielo**
- Cada posición de hielo que genera el impacto se registra (con su tipo). El conteo de 20 s usa el *game time* del nivel (ticks del servidor).
- Los datos se guardan con el mundo: si lo cierras durante la cuenta, al reabrirlo continúa sin adelantarse.
- Al derretir solo se convierte el bloque que siga siendo el hielo del evento: no se toca hielo anterior ni lo que alguien haya cambiado. El hielo cubierto por una estructura se evapora sin generar agua. Los eventos terminados se eliminan del registro.
- El hielo solo sustituye terreno natural (tierra, piedra, arena, grava…); nunca construcciones. Las púas solo se colocan sobre aire.

**Seguridad del transporte** (gusano y agujero negro/blanco)
- Un destino solo es válido si el chunk ya está cargado, está dentro del borde del mundo, la caja de colisión de *esa* entidad cabe, no hay líquido, lava, fuego ni otros peligros, y hay suelo firme.
- Se transporta la **misma entidad** (sin copias). Si no hay destino seguro, la entidad **no se toca**. Los jugadores conservan inventario y salud.
- Las entidades en tránsito se registran para que otros desastres las ignoren y no reciban daño mientras están en tránsito (salvo `/kill`). Al terminar o al detener el desastre, lo pendiente se libera de forma segura.

## Cómo volver a la versión anterior (ruta de recuperación)

1. **El jar anterior** (`disasters-1.0.0.jar`) y el zip anterior (`desastresnaturales forge 1.20.1 update.zip`) no se han tocado. La versión nueva es `disasters-1.1.0.jar`: **no pongas los dos a la vez en `mods`**.
2. **En GitHub**, antes de subir esta actualización marca la versión estable:
   ```bash
   git tag v1.0.0-estable
   git push --tags
   ```
   Para volver: `git checkout v1.0.0-estable` (o descarga ese tag desde *Releases/Tags*).
3. Los mundos creados con la 1.1.0 funcionan al volver a la 1.0.0: lo único nuevo es el archivo `data/disasters_comet_ice.dat`, que la versión anterior simplemente ignora. Aun así, haz copia del mundo.


---

## Actualización 2 (versión 1.2.0)

**5 desastres nuevos** (ver la tabla de arriba) y, con el OVNI, nuevos objetos y una entidad:

| Elemento | Detalles |
|---|---|
| **Pistola Láser** | Clic derecho dispara un rayo instantáneo (verde o azul; **agachado + clic derecho** cambia el color), con haz 3D, chispas y sonido. La energía es la durabilidad (120 disparos); se recarga **sola** consumiendo una Celda de Energía del inventario. En creativo no gasta. Daño y alcance configurables. |
| **Celda de Energía** | Receta sin forma: 2 redstone + 2 polvo de piedra luminosa + 1 lingote de hierro → 2 celdas. También las sueltan los alienígenas. |
| **Alienígena** | Mob hostil (30 de vida) con cabeza grande. Dispara rayos magenta a distancia, ataca jugadores, se defiende de cualquiera que lo dañe (también mobs hostiles) y no ataca a otros alienígenas. Suelta celdas de energía y, rara vez, una pistola. Solo aparece con el OVNI (o con `/summon disasters:alien`). |

**Receta de la pistola láser**
```
I G I      I = lingote de hierro
I D R      G = panel de cristal
  I        D = diamante,  R = redstone
```

**Configuración nueva** (`config/disasters-common.toml`): `marsImpactRadius`, `ufoEffectRadius`, `ufoRayDamage`, `alienDamage`, `laserPistolDamage`, `laserPistolRange`, `heatWaveSeconds`, `heatWaveFires`, `freezeEffectSeconds`, `freezeTerrainSeconds`.
**Config de cliente** (`config/disasters-client.toml`): `cameraShake` y `cameraShakeStrength` (puedes desactivar la vibración de cámara).

**Limitaciones conocidas**
- Todos los modelos 3D son *entidades*: solo se ven dentro de la distancia de seguimiento del servidor (limitada por la distancia de renderizado). Marte, por ejemplo, aparece de golpe a unos 170 bloques en vez de verse desde el horizonte.
- La ola de calor y el domo naranja se hacen con un modelo 3D translúcido y partículas; **no hay distorsión real de la pantalla** (eso requeriría un shader).
- El hielo de la ola de congelación se restaura al terminar su tiempo; si el chunk estaba descargado, se restaura cuando vuelve a cargarse.
- Los alienígenas del evento se retiran al terminar el OVNI.
