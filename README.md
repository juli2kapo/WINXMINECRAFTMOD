# Factory Ascent

A progression-focused tech mod for **Minecraft 26.2 (NeoForge 26.2.0.88)**, made for
co-op survival and small servers.

You progress through seven ages, **Stone → Bronze → Electric → Automation → Industrial →
Orbital → Quantum**. Each age brings new machines rather than recoloured tiers: a hand-cranked
Quern gives way to a Burner Crusher, then an electric Crusher. Your ore yield grows with them
(1.5× → 2× → 2×+byproduct → …). The advancement tab tells you what to build next.

See [docs/DESIGN.md](docs/DESIGN.md) for the full design and numbers.

## What's in it so far

- **Stone / Bronze:**
  - Forge Hammer, Quern, Brick Kiln, Burner Crusher, Burner Press
  - Coke Oven and Blast Furnace multiblocks
  - Wooden and Bronze Crates, bronze tools
  - Sieve (gravel/sand/dirt/soul sand into flint, clay, seeds, nuggets), Drying Rack (rotten flesh into leather)
  - Water Wheel and Windmill: turn the Quern or Sieve next to them for you
  - Bronze armour, Bronze Backpack (27 slots), Grappling Hook
- **Electric:**
  - Electric Furnace, Crusher, Metal Press, Alloy Smelter, Assembler
  - Combustion Generator, Solar Panel, Energy Cells
  - Cables, item pipes, Electric Drill, Auto-Farmer
  - Charger, Floodlight, Item Magnet, Night-Vision Goggles
  - **Storage Network:** controller, drives, 1k/4k cells, terminal with search, and an interface for pipes and hoppers
- **Automation:**
  - Geothermal Generator
  - Block Breaker, Block Placer, Vacuum Hopper, Tree Farm
  - **Ore Miner**, which digs its own chunk claim in **The Deep**, a sealed mining dimension. It never touches the world around it.
- **Industrial:**
  - Industrial Grinder (4 dust per raw ore), Recycler (junk into scrap, worn gear into materials), Mob Farm Controller (a captured mob's drops, no mob)
- **Knowing what goes where:**
  - Hold Shift on an item for "Used in: …".
  - Machine GUIs outline inputs they can't use.
  - A `?` button lists a machine's recipes.
  - Optional **JEI** support: a category per machine kind, with the weakest machine a recipe needs.
- Uses Forge Energy and `c:` tags, so it works alongside other tech mods.
- English and Spanish.

## Playing it

1. Install NeoForge **26.2.0.88** (or a newer 26.2.x) on the server and every client.
2. Build the jar with `./gradlew build`. It is written to `build/libs/factoryascent-0.1.0.jar`.
3. Put the jar in `mods/` on the server and on every client. JEI is optional.

Server owners can tune speeds, energy use, generator output and whether Miners need power in
`config/factoryascent-server.toml`.

## Developing

| Command | What it does |
|---|---|
| `./gradlew build` | Compile and package the mod |
| `./gradlew runClient` / `runServer` | Launch a dev client (with JEI) or a dedicated server |
| `./gradlew runGameTestServer` | Run the in-game tests (all must pass) |
| `python3 tools/gen_resources.py` | Regenerate models, blockstates, recipes, loot, tags, worldgen, dimensions, advancements and lang |
| `python3 tools/gen_textures.py` | Regenerate textures |
| `python3 tools/storage_resources.py --textures` | Regenerate the storage network textures |

Recipes and balance live in `tools/gen_resources.py`. Edit them there and rerun it rather than
editing the JSON by hand.

`compat/orbital-railgun-renewed-26.2.patch` ports the Orbital Railgun mod to 26.2. It adds
bedrock protection, End portal breaking, an optimised crater, and no leftover items.
