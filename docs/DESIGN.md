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

**4 — Automation (MV).** Ore Washer, Tree Farm, Auto-Crafter, Ore Miner (digs
its own claim in The Deep, see below), Geothermal Generator, MV cable, cell and pipe.
Storage: Import/Export Bus, Crafting Terminal, 16k cells. Reward: Jetpack.

**5 — Industrial (HV).** Induction Smelter (4 parallel slots), Industrial
Refinery multiblock, Teleporter Pads, Power Armour (flight, night vision,
speed, no fall damage). Storage: Wireless Terminal, 64k cells.

**6 — Orbital.** Launch Pad + satellites (launch objective), Ground Station
(maps from orbit, marks ore-rich chunks), Orbital Targeting Core. Storage:
uplink-range Wireless Terminal.

- **Teams** (`/factoryascent team create|invite|join|leave|info|list`): everyone starts on a solo
  team keyed by their UUID; joining a named team needs an invite. Satellites belong to the
  launcher's team at launch time and stay with the team when members leave.
- **Satellites** cover the dimension they were launched from. An *Uplink Satellite* gives the team
  signal in that whole dimension; a *Survey Satellite* lets Ground Stations there make maps.
  `OrbitalSignal.hasCoverage(ServerPlayer)` is the API every signal device uses (the Wireless
  Terminal now, the phone and its apps later).
- **Launch Pad:** a Launch Controller surrounded by 8 Launch Pad plates. Right-click with a
  satellite to mount it (the rocket appears), add fuel (Blaze Powder = 1, Rocket Fuel = 4; a launch
  burns 4), then launch with redstone or sneak + flint and steel. A 5 s sequence: countdown, smoke
  and flame, liftoff, the rocket climbs out of sight, and the team is told it reached orbit.
  Rocket Fuel: 2 coal dust + 1 blaze powder in the Alloy Smelter.
- **Ground Station:** right-click lists the team's satellites over this dimension and the signal
  state. With a Survey Satellite up, an empty map becomes a scale-2 map centred on the station and
  painted in from orbit over a few ticks: loaded chunks from the real blocks, the rest from the
  world generator's terrain height and biome (nothing is loaded or generated). Loaded chunks
  nearby with the most ore get a red X.
- **Wireless Terminal:** sneak-use on a Storage Terminal to link; use it anywhere in the same
  dimension with uplink coverage while the terminal's chunk is loaded (an Ender Anchor helps).
- Satellites, the pad and the station need titanium plates and advanced circuits; satellites
  also need an Orbital Targeting Core.

When the Orbital Railgun mod is installed, Factory Ascent replaces its recipe with one needing:

- an Orbital Targeting Core (Assembler recipe, grade 6: titanium plates, advanced circuits, an end crystal and echo shards)
- titanium plates
- advanced circuits
- a beacon
- a spyglass

Factory Ascent declares an optional dependency with `ordering="AFTER"`, so its data pack loads
after the railgun's and its recipe wins.

**7 — Quantum (EV).** Fusion Reactor multiblock, Matter Replicator (energy →
ores), Quantum Armour, cross-dimension teleport, Quantum Storage Cell,
cross-dimension wireless terminal.

### What keeps tiers

Only infrastructure: cables (copper → aluminium → titanium →
superconductor), item pipes (bronze → steel → aluminium → titanium), energy
cells and crates. Tiers work well there. Machines don't use tiers; they get
replaced by better *different* machines. Speed and energy upgrade cards
fine-tune machines.

## Survival reachability

`python3 tools/check_progression.py` starts from vanilla items plus the mod's ore drops. It applies every recipe, respecting machine grades, and lists any mod item you can't obtain; it currently reports 119/119. `--why <item>` explains a gap.

The late machines that close the chain:

| Age | Machine | Kind, grade | Unlocks |
|-----|---------|-------------|---------|
| Automation | Ore Washer | crushing 4 | 3 dust per raw ore |
| Industrial | Induction Smelter | smelting 5 | titanium ingots (the breakthrough) |
| Industrial | Hydraulic Press | pressing 5 | titanium plates and gears |
| Orbital | Precision Assembler | assembling 6 | Orbital Targeting Core (the breakthrough, the railgun, satellites) |
| Orbital | Plasma Forge | alloying 7 | quantum alloy (the Quantum breakthrough) |

## Knowing what goes where

- **Machine GUI:** a panel docked to the left of every processing machine lists everything it
  accepts as a slot grid. Inputs that need a better machine of the same kind are shown greyed
  in their own section. Hovering an input shows what it turns into, the byproduct chance, other
  inputs or the mould, and the time. The `?` button toggles the panel.
- An input with no recipe in that machine gets a red outline and "Not used by this machine".
- **JEI:** optional integration with a recipe category per machine.

Recipes are synced to clients (`OnDatapackSyncEvent.sendRecipes`) so this all
works on dedicated servers.

## Advancements

One tab, *Factory Ascent*, with a root and seven branches. Breakthroughs use
the "challenge" frame. Descriptions are written as the next instruction
("Grind raw ore in a Quern to get dust"). About 45 advancements in total.

## Ender tech (utility branch)

- **Ender Anchor** (Electric age): a chunk loader built like a stasis chamber.
  - It's 2 blocks tall: glass all round, soul sand at the bottom, water inside.
  - Recipe: `GGG / GWG / GSG` (glass, water bucket, soul sand). The empty bucket comes back.
  - Right-click it with one ender pearl: from then on it keeps a 3×3 chunk area loaded until it's broken, and breaking it loses the pearl. There's no fuel timer.
  - The pearl bobs on the bubble column inside.
  - Chunk loading goes through a NeoForge `TicketController`, whose tickets are checked against a ledger after every restart.
  - Limit: 4 per player (server config).
- **Ender Beacon + Recall Charm** (Electric age): a teleporter back home, based on the vanilla stasis chamber. In vanilla, a thrown pearl hangs in a soul-sand bubble column, and triggering it brings its thrower to it.
  - The beacon is a one-block chamber: obsidian corners, glass, water, soul sand, a trapdoor lid.
    - Recipe: `OTO / GWG / OSO`.
    - Right-click it with an ender pearl to load it.
  - The charm (gold, Ender Dust, a pearl) is the remote trigger.
    - Sneak-use it on the beacon to link. Only the beacon's owner can.
    - Hold use for 5 s to channel. Taking real damage interrupts it.
    - You land on top of the beacon, the beacon's pearl is used up, and a 60 s cooldown starts.
  - It works within the same dimension until the Quantum age (config).
- **Ender Dust:** a Crusher turns 1 ender pearl into 2 dust. It's the shared ingredient.

## Mob tools (Automation age)

- **Mob Capsule:** hold it on a mob for 3 s to trap it (the mob is pinned while the trap forms), then use it on a block to release the mob. Bosses and players can't be captured.
- **Minimizer / Maximizer rays:** FE guns that halve or double any living thing's size (vanilla `scale` attribute, 0.25×–4×). Shrunk mobs fit through 1-block gaps. Recipe: an eye of ender as the lens, ender dust along the barrel, aluminium plates, an advanced circuit, and a fermented spider eye (Minimizer) or golden apple (Maximizer) as the catalyst.

## The Deep (where the Ore Miner's ore comes from)

The Ore Miner doesn't create ore from nothing, and it doesn't strip-mine your base either.
The Deep (`factoryascent:the_deep`) is a sealed dimension with:

- a bedrock floor at y 0 and a bedrock ceiling at y 255
- deepslate below y 80 and stone above
- about 1,200 ore blocks per chunk: coal, iron, copper, tin and bauxite up high; gold, redstone, lapis, diamond and titanium down low

Each Ore Miner claims its own chunk column there. Claims sit on a spiral grid with one empty
chunk between them. The Miner sweeps its claim top-down, digs out the real ore blocks, and skips
sections that hold none. When the claim is dug out, it moves on to the next free claim.

The claim is kept loaded only while the Miner works: a ticket expires 10 s after it stops. At
2 ores/s, a claim lasts about 10 minutes.

The GameTest server builds its world without data-pack dimensions, so the GameTest only checks
that the Miner leaves the world alone. Digging a claim is verified on a dedicated server.

A Rift Portal to visit The Deep is planned for the Industrial age.

## Ores

Tin (common, y −16…96), bauxite (y 32…128, stone only), titanium
(deepslate only, y −64…0, rare: about 3 per chunk).
