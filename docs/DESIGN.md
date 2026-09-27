# Factory Ascent — design (v2)

A progression-focused tech mod for Minecraft 26.2 (NeoForge), built for co-op
survival and small servers.

Core principles:

1. **Ages, not recolours.** Progress through seven ages. Each age brings
   genuinely new machines (distinct blocks and shapes), a signature material,
   a reward that changes how you play, and one breakthrough that opens the
   next age. Your early machines stay useful until their better version shows
   up, and the better version visibly raises your output (for example ore
   yield 1.5× → 2× → 3× → 4×).
2. **Always know what to do next.** The advancement tab is the guide: each
   age is a branch, and each advancement says what to build next. Every item
   can tell you which machines use it (tooltip, GUI hint, JEI).
3. **Server-friendly.** All logic is server-side, nothing force-loads chunks,
   destructive things can be toggled in the server config, and machines fire
   normal break events so claim mods can protect bases.

Borrowed conventions: GregTech and Modern Industrialization ages, voltage
tiers and breakthrough goals; Mekanism rewards (jetpack, armour, miner,
teleporters, fusion); Immersive Engineering multiblocks and the hammer; Applied
Energistics / Refined Storage storage networks; Industrial Foregoing's
automation of chores.

## The seven ages

| # | Age | Material | Power | Breakthrough |
|---|-----|----------|-------|--------------|
| 1 | Stone | copper, tin | muscle / fuel | first bronze ingot |
| 2 | Bronze | bronze, coke | fuel (burner machines) | steel from the Blast Furnace multiblock |
| 3 | Electric | steel, copper wire | LV (copper cable) | first Basic Circuit |
| 4 | Automation | aluminium, silicon | MV (aluminium cable) | first Advanced Circuit |
| 5 | Industrial | titanium | HV (titanium cable) | first titanium ingot |
| 6 | Orbital | orbital alloy | HV+ | satellite launched / uplink online |
| 7 | Quantum | quantum alloy | EV (superconductor) | fusion reactor running |

### Ore processing (the backbone)

| Age | Machine | Yield per raw ore |
|-----|---------|-------------------|
| Stone | Quern (hand-cranked) | 1 dust + 50% chance of a second |
| Bronze | Burner Crusher | 2 |
| Electric | Crusher | 2 + byproduct |
| Automation | Ore Washer (needs an adjacent water source) | 3 |
| Industrial | Industrial Refinery (multiblock) | 4 |

### Roster by age

**1 — Stone.** Forge Hammer (plates by hand), Quern, Brick Kiln (fuel-burning
alloy kiln: bronze), Wooden Crate (27 slots, keeps its items when broken).
Reward: bronze tools, the crate.

**2 — Bronze.** Burner Crusher, Burner Press (moulds), Coke Oven (3×3×3
multiblock: coal → coke), Blast Furnace (3×3×4 multiblock: iron + coke →
steel), Bronze Item Pipe, Bronze Crate (54 slots). Burner machines accept
hoppers and pipes, which is the first taste of automation.

**3 — Electric (LV).** Combustion Generator, Solar Panel, Energy Cell, Copper
Cable, Electric Furnace, Crusher, Metal Press, Alloy Smelter, Assembler,
Steel Item Pipe, Steel Crate (81). **Storage Network:** Network Cable,
Storage Controller, Storage Drive + Storage Cells (1k/4k items), Storage
Terminal. Rewards: Electric Drill, Auto-Farmer.

**4 — Automation (MV).** Ore Washer, Tree Farm, Auto-Crafter, Miner (mines
only ores in an area), Geothermal Generator, MV cable, cell and pipe.
Storage: Import/Export Bus, Crafting Terminal, 16k cells. Reward: Jetpack.

**5 — Industrial (HV).** Induction Smelter (4 parallel slots), Industrial
Refinery multiblock, Teleporter Pads, Power Armour (flight, night vision,
speed, no fall damage). Storage: Wireless Terminal, 64k cells.

**6 — Orbital.** Launch Pad + Satellite (launch objective), Orbital Uplink,
Orbital Scanner (shows ores from orbit). Unlocks the Orbital Railgun recipe
when that mod is installed. Storage: uplink-range wireless.

**7 — Quantum (EV).** Fusion Reactor multiblock, Matter Replicator (energy →
ores), Quantum Armour, cross-dimension teleport, Quantum Storage Cell,
cross-dimension wireless terminal.

### What keeps tiers

Only infrastructure: cables (copper → aluminium → titanium →
superconductor), item pipes (bronze → steel → aluminium → titanium), energy
cells and crates. Tiers work well there. Machines don't use tiers; they get
replaced by better *different* machines. Speed and energy upgrade cards
fine-tune machines.

## Knowing what goes where

- **Tooltip:** hold Shift on any item to see "Used in: Crusher, Ore Washer…".
- **Machine GUI:** an input with no recipe in that machine gets a red outline
  and "Not used by this machine". A `?` button lists every accepted input.
- **JEI:** optional integration with a recipe category per machine.

Recipes are synced to clients (`OnDatapackSyncEvent.sendRecipes`) so this all
works on dedicated servers.

## Advancements

One tab, *Factory Ascent*, with a root and seven branches. Breakthroughs use
the "challenge" frame. Descriptions are written as the next instruction
("Grind raw ore in a Quern to get dust"). About 45 advancements in total.

## Ores

Tin (common, y −16…96), bauxite (y 32…128, stone only), titanium
(deepslate only, y −64…0, rare: about 3 per chunk).
