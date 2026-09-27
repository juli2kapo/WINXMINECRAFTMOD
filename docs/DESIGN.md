# Factory Ascent — design

A progression-focused tech mod for Minecraft 26.2 (NeoForge), built for co-op
survival and small servers. The one idea it is built around: **your first
machines are never dead weight.** Every machine climbs the tiers with you, so a
better miner never forces you to either live with a bottleneck or build ten
more furnaces.

This isn't new to Minecraft. It is how the classic tech mods already work, and
Factory Ascent borrows their conventions on purpose:

| Idea                              | Where it comes from                                     |
|-----------------------------------|---------------------------------------------------------|
| In-place tier upgrades            | Mekanism *Tier Installers*, Thermal upgrade kits        |
| Named tiers with colours          | Mekanism (Basic → Ultimate), Thermal machine frames     |
| Recipes locked to a minimum tier  | GregTech voltage tiers                                  |
| Ore yield rising with tier        | Mekanism 2×/3×/4×/5× ore processing                     |
| Upgrade slots unlocked by tier    | Thermal augment slots                                   |
| Speed / energy upgrade cards      | Mekanism & IC2 upgrades                                 |
| Moulds decide what a press makes  | Immersive Engineering metal press, Thermal press dies   |
| Hammer for plates before machines | Immersive Engineering engineer's hammer                 |
| Tiered cables and item pipes      | Mekanism universal cables / logistical transporters     |
| Area ore miner                    | Mekanism Digital Miner                                  |
| Machine frames + tiered circuits  | Mekanism circuits, Thermal frames                       |

## Tiers

| Tier       | Speed | Energy / op | Upgrade slots | Colour | Signature material |
|------------|-------|-------------|---------------|--------|--------------------|
| Basic      | ×1    | 100%        | 1             | iron   | iron, copper, tin  |
| Reinforced | ×2    | 90%         | 2             | steel  | bronze, steel      |
| Advanced   | ×4    | 80%         | 3             | copper | aluminium, silicon |
| Elite      | ×8    | 70%         | 3             | violet | titanium           |
| Ultimate   | ×16   | 60%         | 3             | cyan   | quantum alloy      |

- **Upgrade in place.** Right-click a machine with the next tier's *Upgrade Kit*.
  It keeps its items, energy and settings, like Mekanism's tier installer. One
  tier at a time.
- **Or craft it.** The previous-tier machine plus the tier's frame, circuit and
  material also works in a crafting table.
- **Work points.** Each tick a machine earns `speed × upgrades` points and
  finishes one operation per `recipe.time` points. High tiers can really do
  several operations per tick.
- **Recipe gates.** Some recipes need a minimum tier (`min_tier`). Titanium only
  smelts in an Advanced+ Electric Furnace, and quantum alloy needs an Elite+
  Alloy Smelter.

### Upgrades

Machines get upgrade slots by tier (1/2/3/3/3):

- **Speed Upgrade**: +50% speed; energy use grows faster (`∝ speed^1.6`).
- **Energy Upgrade**: −20% energy per operation (stacks multiplicatively).

## Ore processing: yield grows with tier

Mine ores normally, or with the Miner. Raw ore goes through the Crusher:

| Crusher tier     | Dust per raw ore |
|------------------|------------------|
| Basic/Reinforced | 2                |
| Advanced/Elite   | 3                |
| Ultimate         | 4                |

This is driven by recipe data. The Crusher uses the highest `min_tier` recipe
its tier allows, so upgrading the crusher directly raises ore output.

## Machines

| Machine          | Inputs            | Role                                               |
|------------------|-------------------|----------------------------------------------------|
| Electric Furnace | 1                 | every vanilla smelting recipe (5× faster at Basic) + dusts |
| Crusher          | 1                 | ore → dust (2×–4×), cobble → gravel → sand         |
| Metal Press      | 1 + mould         | plates, gears, rods, wires (mould is not consumed) |
| Alloy Smelter    | 2                 | bronze, steel, silicon, quantum alloy              |
| Assembler        | 4                 | circuits, motors, coils, machine frames            |
| Miner            | —                 | mines only ore blocks in an area below it          |

Recipes are data-driven: `data/factoryascent/recipe/*.json` with the types
`factoryascent:smelting|crushing|pressing|alloying|assembling`, each with
`ingredients` (item + count), `result`, optional `byproduct` (item + chance),
optional `mold`, `time` (Basic ticks) and `min_tier`.

**Miner:** place it anywhere. It scans the area below it and mines only blocks
tagged `#c:ores`, replacing them with stone or deepslate so caves and terrain
stay intact. Radius by tier: 5/8/12/16/24. It only mines loaded chunks and
never force-loads them.

## Energy

Forge Energy (FE) through NeoForge's `EnergyHandler` capability, so it works
with other tech mods.

- **Power Cable** (5 tiers): touching cables form a network. The network moves
  at most the *slowest* cable's rate per tick (512 / 2k / 8k / 32k / 128k FE/t),
  so upgrading your wiring matters.
- **Combustion Generator**: burns furnace fuel. 40 FE/t at Basic. Higher tiers
  burn faster *and* get 15% more energy per fuel item per tier.
- **Solar Panel**: 8 FE/t × tier speed in daylight under open sky; half in rain.
- **Geothermal Generator**: 24 FE/t × tier speed per touching lava source
  (like Mekanism's heat generator). The lava is not consumed.
- **Energy Cell**: 100k × 4^(tier−1) FE. Accepts on all faces and outputs only
  from its front, so cells never bounce energy back and forth.

## Items

- **Item Pipe** (5 tiers): touching pipes form a network that delivers items
  immediately (EnderIO style, nothing flies around to lag the server). Machines
  push their outputs into pipes. To pull from a chest, right-click the pipe face
  touching it with a Wrench. Pull rate per tier: 4 / 16 / 32 / 64 / 128 items
  every 10 ticks. Deliveries go round-robin to every other inventory on the
  network.
- Machines auto-eject outputs into neighbouring inventories and pipes (toggle
  in the GUI).

## Materials and progression

```
Basic       crafting table: Basic Machine Frame (iron + copper), Forge Hammer (plates by hand)
            → Electric Furnace, Crusher, Metal Press, Combustion Generator, cables, pipes
            tin ore → bronze (3 copper + 1 tin, Alloy Smelter)
Reinforced  kit = Reinforced Frame (steel) + Reinforced Circuit + bronze gear
            steel = iron + coal dust (Alloy Smelter); Solar, Miner, Energy Cell
Advanced    kit = Advanced Frame (aluminium) + Advanced Circuit (silicon wafer, gold wire)
            bauxite → aluminium; silicon = quartz dust + coal dust
Elite       kit = Elite Frame (titanium) + Elite Circuit
            deepslate titanium ore, smelts only in an Advanced+ furnace
Ultimate    kit = Ultimate Frame (quantum alloy) + Ultimate Circuit
            quantum alloy = titanium + ender pearl (Elite+ Alloy Smelter), needs netherite for the frame
```

New ores (normal ore generation): tin (common, y −16…96), bauxite (y 32…128),
titanium (deepslate only, y −64…0, rare).

## Multiplayer / server notes

- All logic is server-side; clients only render.
- The server config (`factoryascent-server.toml`) has multipliers for machine
  speed, energy use, generator output and miner speed, plus the miner radius.
- Nothing force-loads chunks. Cable and pipe networks rebuild lazily and keep
  their stored energy across saves and unloads.
