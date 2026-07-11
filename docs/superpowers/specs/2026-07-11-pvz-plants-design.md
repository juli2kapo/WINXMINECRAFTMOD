# Flora PvZ Plants (Nature slots 2–3) — Design

Date: 2026-07-11
Status: Approved

## Goal

Give Flora (Nature) her missing powers as a Plants-vs-Zombies summoning kit:
slot 2 cycles the selected plant, slot 3 plants it where she aims. Plants are
autonomous turret/ally entities using models and textures extracted from the
"Plants Vs Zombies Replanted" mod JAR (GeckoLib geo JSON → vanilla
LayerDefinition conversion). Private, non-distributed gift mod — asset reuse OK.

## Roster (per stage; upgrade-replacement, not cumulative)

- Stage 1: Peashooter, Chomper, Cherry Bomb
- Stage 2: Repeater, Snow Pea, Chomper, Doom-shroom
- Stage 3: Gatling Pea, Snow Pea, Torchwood, Doom-shroom (extra damage), Chomper, Cob Cannon

Bomb line upgrades: Cherry Bomb (1) → Doom-shroom (2, stronger at 3).
Cob Cannon (3 only): artillery — every ~8 s lobs a ballistic cob at the
farthest hostile within 24 blocks; explodes with radius-4 damage, NO block
destruction; shows the "no cob" texture while reloading.

## Behaviors

- **Shooters** (Peashooter/Repeater/Gatling/Snow Pea): one entity type with a
  synced variant. Every N ticks, target nearest hostile Monster within 16
  blocks with line of sight and fire pea projectiles at it (burst: 1/2/4 peas;
  Snow Pea: 1 frozen pea). Body rotates toward target.
- **Pea projectile**: thrown projectile with variants — NORMAL (4 dmg),
  FROZEN (4 dmg + Slowness ~4 s), FIRE (7 dmg + ignite). Uses the mod's pea
  model; frozen/fire tinted.
- **Torchwood**: passive. A NORMAL or FROZEN pea passing within ~1.2 blocks
  becomes a FIRE pea (frozen loses its slow — PvZ classic rule). One upgrade
  per pea.
- **Chomper**: bites the nearest hostile within 2.5 blocks: heavy damage
  (12/16/20 by owner stage), then a ~5 s "chewing" cooldown. Available at all
  stages.
- **Bombs** (one entity type, variant): after planting, a short fuse
  (Cherry ~1.5 s, Doom ~2 s) then explode and disappear.
  - Cherry Bomb: radius 4, big damage, `ExplosionInteraction.NONE` — NO block
    destruction.
  - Doom-shroom: radius 8, huge damage, `ExplosionInteraction.BLOCK` — DOES
    destroy blocks.
  - Explosions never hurt the owner.

## Framework

- `PlantEntity` (abstract, extends Mob, no movement, no vanilla goals): owner
  UUID, marked with tag "Plant"; killable (HP: shooters 12, chomper 30,
  torchwood 20, bombs 8); silent-ish; no loot. Never targets/hurts its owner.
- Plant cap per owner: 3/5/7 by stage. Planting past the cap withers (poof +
  discard) the oldest plant.
- Selection state: `player.getPersistentData()` key with the selected plant id;
  reset-on-death acceptable. Slot 2 advances through the stage roster and shows
  the selection on the action bar.
- `EnumPowers`: `CYCLE_PLANT(NATURE, 2)`, `PLANT_SEED(NATURE, 3)` →
  NaturePowers methods.

## Assets pipeline

1. Extract from pvz_replanted JAR (already downloaded): geo JSONs + entity
   textures for peashooter, repeater, gatling_pea, snowpea, chomper,
   cherry_bomb, doomshroom, torchwood, pea.
2. Converter script: bedrock/GeckoLib geo JSON → Java `LayerDefinition`
   classes (bones → PartDefinitions; standard Blockbench mapping: X negated,
   Y flipped around pivot, rotations -x/-y/+z, per-cube rotations become
   sub-parts). Generated classes live in `entity/client/model/plants/`.
3. Textures copied to `assets/minewinx/textures/entity/plants/`.
4. Renderers: one per entity type; shooter renderer picks baked model +
   texture by variant. Idle sway in setupAnim; chomper bite anim via synced
   bite timestamp.

## Wiring

- ModEntities: shooter_plant, chomper_plant, bomb_plant, torchwood_plant,
  pea_projectile.
- ClientEvents: layer definitions + renderers.
- en_us.json entity names (Spanish).
- No new items/recipes — plants are powers, not items.

## Testing

- compileJava clean; runData unaffected (no datagen assets).
- In-game: cycle shows roster per stage; planting spawns on aimed block; cap
  enforcement withers oldest; peashooter line fires and hurts mobs; snow pea
  slows; torchwood upgrades peas crossing it (fire pea ignites); chomper bites;
  cherry bomb leaves terrain intact; doom-shroom craters terrain; plants never
  hurt Flora.
