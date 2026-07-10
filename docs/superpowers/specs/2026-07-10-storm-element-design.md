# Storm Element (Stormy) — Design

Date: 2026-07-10
Status: Approved (pending spec review)

## Goal

Complete the Trix trio by adding Stormy's storm element: two active powers
(Storm Field, Tornado), a lightning-immunity passive, and the standard
element progression items. Remove the dead `ShadowPowers` stub.

## Scope

- New element `STORM(9, "storm")` in `EnumPowers.Element`.
- Two active powers (slots 1–2, matching Fire/Water which also have two):
  Storm Field and Tornado. No slot 3 for now.
- Passive: immunity to lightning damage for Storm players.
- Progression: `storm_stage_1..3` ElementSeed items with sprites, recipes,
  creative tab, models, lang.
- Delete `powers/ShadowPowers.java` (empty class; Darcy's darkness already
  lives in DarkPowers).

Out of scope: chain lightning / wind gust (dropped), other elements' missing
powers, illusion mob fixes.

## Components

### 1. `StormPowers` (new class, `powers` package)

**`summonStormField(Player)`** — slot 1.
- Registers the caster in a `Map<UUID, FieldState>` (pattern copied from
  `SunAndMoonPowers.activeCasters`).
- Processed from `ServerEvents.onServerTick` via a `StormPowers.onServerTick`
  hook (same wiring as SunAndMoonPowers).
- Each tick, while active: with per-stage probability, spawn a vanilla
  `LightningBolt` at a random point within the field radius around the
  caster's CURRENT position (the field follows her).
- Stage scaling: radius 8/12/16 blocks; average strike interval
  ~30/20/12 ticks; duration 15/20/25 seconds.
- Re-cast while active: refresh duration (no stacking).

**`summonTornado(Player)`** — slot 2.
- Raytrace to targeted point (same combined block/entity clip as
  `SunAndMoonPowers.castSunRay`), spawn `TornadoEntity` there.
- Stage scaling: pull radius 6/8/10, damage on fling 4/6/8, lifetime ~6/8/10 s.

### 2. `TornadoEntity` (new entity)

- Server logic per tick: entities (not the caster, not other players' choice —
  any living entity except the caster) within pull radius get velocity toward
  a spiral: tangential + inward + upward components based on their angle to
  the tornado axis; entities reaching the core (< 1.5 blocks) are flung
  outward/upward with damage.
- Stores caster UUID for damage attribution.
- Discards after lifetime.
- Registered in `ModEntities`, synced via default spawn packets like existing
  entities.

### 3. Tornado visuals (hybrid: model + particles)

- `TornadoModel` — hand-coded `HierarchicalModel` like LightRayModel:
  6 stacked hollow ring segments, narrow base widening to top (funnel).
  In `setupAnim`, each segment rotates around Y at a different speed/phase
  driven by `pAgeInTicks`, with slight sway.
- `TornadoRenderer` — renders the model with `RenderType.entityTranslucent`
  and a grey cloud texture (drawn as part of this work, ~64x64) at partial
  alpha. Registered in ClientEvents/renderer registration alongside existing
  entities.
- Particles: ~5–10/tick server-side `sendParticles` — dust/cloud puffs at the
  base, occasional debris outward. No pure-particle funnel.

### 4. Passive: lightning immunity (`ServerEvents.onLivingDamage`)

- Existing handler extended: if entity is a `Player` whose element is Storm
  (any stage >= 1) and the damage source is `DamageTypes.LIGHTNING_BOLT`,
  cancel the event. This lets her stand inside her own Storm Field.

### 5. Progression items and wiring

- `ModItems`: `STORMSTAGE1..3` = `ElementSeed(durability 1, "Storm", n)`.
- `EnumPowers`: `STORM_FIELD(Element.STORM, 1, StormPowers::summonStormField)`,
  `TORNADO(Element.STORM, 2, StormPowers::summonTornado)`.
- Recipes (`ModRecipeProvider`, element convention — 8 crystals around center):
  stage 1 = low-quality crystals + lightning rod;
  stage 2 = medium-quality crystals + phantom membrane;
  stage 3 = high-quality crystals + trident.
- Sprites: `storm_stage_1..3.png` in the existing seed style, storm-grey
  palette with yellow lightning motif (drawn as part of this work).
- Creative tab entries, `ModItemModelProvider` simpleItems, `en_us.json`
  names (Spanish, e.g. "Semilla de tormenta").

## Error handling

- Storm Field ticks guard against the caster logging off / dying (remove from
  map), mirroring SunAndMoonPowers cleanup.
- TornadoEntity discards itself if level unloads it; no dangling references.

## Testing

- Build compiles; `runData` generates the three recipes + item models.
- Manual in-game: field follows player and strikes scale with stage; caster
  immune to lightning; tornado pulls/flings mobs, funnel renders and spins,
  particles at base; ShadowPowers removal breaks nothing.
