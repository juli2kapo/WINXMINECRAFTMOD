# Ride the Storm (Stormy, slot 3) — Design

Date: 2026-09-10
Status: Approved (pending spec review)

## Goal

Completar el kit de Stormy con su tercer poder (el "ultimate" del elemento,
como Dragon Punch / Geyser Field / Solar Flare). Hoy Storm es el único
elemento con un socket vacío en el HUD.

Los slots 1–2 de Storm son ambos *trampas estáticas*: se colocan y se dejan.
El slot 3 tiene que sentirse distinto — que la tormenta sea ELLA, no algo que
deja atrás. Es además el único poder de movilidad del mod entero.

## Concepto

Stormy monta una nube de tormenta y es arrastrada por el viento en la
dirección en la que mira. Mientras dura, caen rayos automáticamente sobre los
enemigos que sobrevuela.

## Decisión clave: NO se usa `mayfly`

El mod prohíbe volar deliberadamente. `ServerEvents.tickPlayer` limpia
`mayfly`/`flying` de todo jugador no-creativo CADA TICK, y
`TransformC2SPacket` lo documenta: "Las winx ya no vuelan: la transformación
no otorga mayfly."

Por eso este poder **nunca** toca `getAbilities()`. En su lugar fija la
velocidad (`setDeltaMovement`) tick a tick: la nube la *lleva*. Así:

- La limpieza anti-vuelo existente no se toca ni necesita excepciones.
- No hay estado de vuelo residual que pueda quedar colgado (que es
  exactamente el bug que esa limpieza previene).
- Se siente como ser arrastrada por una tormenta, no como volar en creativo
  — más fiel al personaje.

## Componentes

### 1. `StormPowers.rideTheStorm(Player)` — slot 3

Sigue el patrón ya establecido en la misma clase (`activeFields` +
`onServerTick`), con un segundo mapa `activeRides`.

- `Map<UUID, RideState> activeRides` (`ConcurrentHashMap`, igual que
  `activeFields`).
- Al lanzar: guardar estado, empujón inicial hacia arriba, sonido de trueno.
- Re-lanzar mientras está activo: refresca la duración, no apila (misma regla
  que Storm Field).

Escalado por stage:

| Stage | Duración | Velocidad | Radio de rayos | Intervalo medio |
|-------|----------|-----------|----------------|-----------------|
| 1     | 6 s      | 0.55      | 6              | 20 ticks        |
| 2     | 8 s      | 0.70      | 8              | 14 ticks        |
| 3     | 10 s     | 0.85      | 10             | 9 ticks         |

Cooldown: `40 * 10` (el más alto de Storm — es el ultimate).

### 2. Movimiento por tick (dentro de `onServerTick`)

Por cada `RideState` activo:

1. `Vec3 dir = player.getViewVector(1.0F)` — dirección de la mirada.
2. Velocidad = `dir.scale(speed)`, con la componente Y **clampeada** a
   `[-0.15, +0.35]`: puede subir y bajar mirando, pero no se dispara al cielo
   ni se clava en el piso.
3. **Techo de altura:** si está a más de 40 bloques sobre el terreno
   (`Heightmap.MOTION_BLOCKING` bajo ella), forzar la Y a negativa. Evita que
   se vaya al límite del mundo.
4. `player.setDeltaMovement(...)` + `player.hurtMarked = true` (necesario para
   que el servidor mande la velocidad al cliente).
5. `player.resetFallDistance()` cada tick — sin daño de caída durante el viaje.
6. `player.hasImpulse = true` para que la posición se sincronice.

### 3. Rayos automáticos

Reusa la lógica de strike que ya existe en `onServerTick` para Storm Field
(bolt visual + daño manual, sin incendios) — se extrae a un helper compartido
`strikeAt(ServerLevel, Vec3, Player, float damage)` para no duplicarla.

- Cada tick, con probabilidad `1/meanStrikeInterval`, buscar enemigos válidos
  en un cilindro de `strikeRadius` alrededor de su posición (usando
  `Targeting.isValidTarget`, que ya excluye aliados, plantas propias e
  ilusiones propias).
- Si hay enemigos: golpear a UNO al azar (no a todos — que se sienta como una
  tormenta, no como un botón de borrar la pantalla).
- Si no hay ninguno: nada. Sin rayos decorativos que prendan fuego al mundo.
- Daño 6/8/10 por stage.

### 4. Aterrizaje

Al expirar (o si muere / se desconecta / cambia de dimensión):

- Quitar del mapa.
- **No** se cancela la caída: cae, pero con `fallDistance` resetada en el
  último tick, así que el aterrizaje es seguro sin necesidad de un
  `LivingFallEvent` (el mod no tiene handler de caída hoy y no hace falta
  agregarlo).
- Sonido de trueno lejano + partículas de nube al disiparse.

### 5. Wiring

- `EnumPowers`: `RIDE_THE_STORM(Element.STORM, 3, 40 * 10, StormPowers::rideTheStorm)`.
- HUD: **no requiere cambios de código**. `PowerHudOverlay` ya resuelve el
  icono por nombre de poder (`textures/gui/powers/ride_the_storm.png`) y su
  comentario de socket vacío ("p. ej. Storm slot 3") deja de aplicar solo con
  que exista la entrada del enum.
- Icono: `ride_the_storm.png` 16x16 en el estilo de los demás
  (`solar_flare.png` como referencia) — nube gris con rayo amarillo.

## Riesgos y mitigaciones

- **Irse al vacío / muy alto:** techo de 40 bloques sobre el terreno + clamp
  de Y (punto 2).
- **Daño de caída al terminar:** `resetFallDistance()` cada tick, incluido el
  último.
- **Atravesar paredes:** NO se desactiva la colisión — choca contra bloques
  como cualquier entidad. Es intencional: montar la tormenta es para espacios
  abiertos.
- **Sincronización cliente/servidor:** `hurtMarked` es lo que hace que el
  servidor mande la velocidad; sin eso el cliente no se entera y se ve un
  rubber-band. Es el punto más probable de tener que ajustar en el smoke test.
- **Interacción con Storm Field:** son compatibles a propósito — volar dentro
  del propio campo es la fantasía completa del personaje, y la pasiva de
  inmunidad a rayos ya la protege.

## Testing

- Compila (`compileJava`).
- Manual en juego:
  - El socket 3 del HUD aparece con icono y cooldown.
  - Se mueve en la dirección de la mirada, sube y baja, no se dispara al cielo.
  - No recibe daño de caída al terminar.
  - Los rayos caen sobre enemigos sobrevolados y NO sobre aliados.
  - No quedan `mayfly`/`flying` colgados al terminar (comprobar que sigue sin
    poder volar después).
  - Re-lanzar refresca sin apilar.
