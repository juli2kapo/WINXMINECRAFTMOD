#!/usr/bin/env node
/**
 * minebench2schem.js — One command: MineBench JSON in, .schem out.
 *
 * Accepts EITHER:
 *   - a voxel.exec tool-call payload: { tool: "voxel.exec", input: { code, gridSize, seed } }
 *     (the code is executed in a sandbox with block/box/line + seeded rng)
 *   - an expanded build: { blocks: [{x,y,z,type}, ...] } or a top-level array,
 *     including [x,y,z,"type"] tuple form.
 *
 * Output: Sponge schematic next to the input, same basename (.json -> .schem).
 *
 * Usage:
 *   node minebench2schem.js alfea_voxel.json
 *   node minebench2schem.js build.json --format v3
 *   node minebench2schem.js build.json --out other.schem --data-version 3700
 *   node minebench2schem.js build.json --map crystal=amethyst_block
 *
 * Flags:
 *   --format v2|v3     Sponge schematic version (default v2; try v3 if a
 *                      viewer/tool refuses the file — both hold identical data)
 *   --out <path>       Output path (default: input basename + .schem)
 *   --data-version <n> Minecraft DataVersion (default 3700 = 1.20.4)
 *   --default-block <id>  Fallback for unknown names (default minecraft:stone)
 *   --map NAME=ID      Extra block-name mapping (repeatable)
 */
"use strict";
const fs = require("fs");
const path = require("path");
const vm = require("vm");
const zlib = require("zlib");

// ---------------------------------------------------------------- args
const argv = process.argv.slice(2);
const flagVal = (f) => {
  const i = argv.indexOf(f);
  return i >= 0 ? argv[i + 1] : null;
};
const inputPath = argv.find((a) => !a.startsWith("--") && a !== flagVal("--format") && a !== flagVal("--out") && a !== flagVal("--data-version") && a !== flagVal("--default-block") && !argv[argv.indexOf(a) - 1]?.startsWith("--"));
if (!inputPath) {
  console.error("Usage: node minebench2schem.js <input.json> [--format v2|v3] [--out file.schem]");
  process.exit(1);
}
const format = (flagVal("--format") || "v2").toLowerCase();
if (format !== "v2" && format !== "v3") {
  console.error(`Unknown --format "${format}" (expected v2 or v3)`);
  process.exit(1);
}
const outPath = flagVal("--out") || inputPath.replace(/\.json$/i, "") + ".schem";
const dataVersion = Number(flagVal("--data-version") || 3700);
const defaultBlock = (flagVal("--default-block") || "minecraft:stone");
const userMap = {};
for (let i = 0; i < argv.length; i++) {
  if (argv[i] === "--map" && argv[i + 1]) {
    const [k, v] = argv[i + 1].split("=");
    if (!v) { console.error(`Bad --map value: ${argv[i + 1]}`); process.exit(1); }
    userMap[k.trim().toLowerCase()] = v.trim().toLowerCase().replace(/^minecraft:/, "");
  }
}

// ---------------------------------------------------------------- block names
const ALIASES = {
  grass: "grass_block", wood: "oak_planks", plank: "oak_planks",
  planks: "oak_planks", log: "oak_log", logs: "oak_log", leaf: "oak_leaves",
  leaves: "oak_leaves", brick: "bricks", stone_brick: "stone_bricks",
  stonebrick: "stone_bricks", mossy_stone_brick: "mossy_stone_bricks",
  cobble: "cobblestone", snow: "snow_block", wool: "white_wool",
  concrete: "white_concrete", glass_block: "glass", iron: "iron_block",
  gold: "gold_block", diamond: "diamond_block", emerald: "emerald_block",
  lapis: "lapis_block", redstone: "redstone_block", copper: "copper_block",
  coal: "coal_block", quartz: "quartz_block", netherite: "netherite_block",
  hay: "hay_block", slime: "slime_block", honey: "honey_block",
  magma: "magma_block", endstone: "end_stone",
  end_stone_brick: "end_stone_bricks", nether_brick: "nether_bricks",
  red_nether_brick: "red_nether_bricks", purpur: "purpur_block",
  bookshelves: "bookshelf", path: "dirt_path", grass_path: "dirt_path",
  water_block: "water", lava_block: "lava", dirt_block: "dirt",
  sandstone_block: "sandstone", sea_lantern_block: "sea_lantern",
  terracotta_block: "terracotta",
};
const KNOWN = new Set(`air stone granite polished_granite diorite
polished_diorite andesite polished_andesite deepslate cobbled_deepslate
polished_deepslate deepslate_bricks deepslate_tiles tuff calcite grass_block
dirt coarse_dirt podzol rooted_dirt mud dirt_path cobblestone
mossy_cobblestone oak_planks spruce_planks birch_planks jungle_planks
acacia_planks dark_oak_planks mangrove_planks cherry_planks bamboo_planks
crimson_planks warped_planks oak_log spruce_log birch_log jungle_log
acacia_log dark_oak_log mangrove_log cherry_log stripped_oak_log
stripped_spruce_log stripped_birch_log stripped_jungle_log
stripped_acacia_log stripped_dark_oak_log oak_wood spruce_wood birch_wood
jungle_wood acacia_wood dark_oak_wood oak_leaves spruce_leaves birch_leaves
jungle_leaves acacia_leaves dark_oak_leaves mangrove_leaves cherry_leaves
azalea_leaves flowering_azalea_leaves azalea flowering_azalea sand red_sand
gravel sandstone smooth_sandstone cut_sandstone chiseled_sandstone
red_sandstone smooth_red_sandstone gold_block iron_block coal_block
diamond_block emerald_block lapis_block redstone_block copper_block
netherite_block quartz_block smooth_quartz chiseled_quartz_block
quartz_bricks quartz_pillar bricks stone_bricks mossy_stone_bricks
cracked_stone_bricks chiseled_stone_bricks mud_bricks bookshelf obsidian
crying_obsidian glass tinted_glass white_stained_glass orange_stained_glass
magenta_stained_glass light_blue_stained_glass yellow_stained_glass
lime_stained_glass pink_stained_glass gray_stained_glass
light_gray_stained_glass cyan_stained_glass purple_stained_glass
blue_stained_glass brown_stained_glass green_stained_glass red_stained_glass
black_stained_glass water lava ice packed_ice blue_ice snow_block clay
terracotta white_terracotta orange_terracotta magenta_terracotta
light_blue_terracotta yellow_terracotta lime_terracotta pink_terracotta
gray_terracotta light_gray_terracotta cyan_terracotta purple_terracotta
blue_terracotta brown_terracotta green_terracotta red_terracotta
black_terracotta white_wool orange_wool magenta_wool light_blue_wool
yellow_wool lime_wool pink_wool gray_wool light_gray_wool cyan_wool
purple_wool blue_wool brown_wool green_wool red_wool black_wool
white_concrete orange_concrete magenta_concrete light_blue_concrete
yellow_concrete lime_concrete pink_concrete gray_concrete
light_gray_concrete cyan_concrete purple_concrete blue_concrete
brown_concrete green_concrete red_concrete black_concrete netherrack
nether_bricks red_nether_bricks chiseled_nether_bricks
cracked_nether_bricks soul_sand soul_soil basalt polished_basalt
smooth_basalt blackstone polished_blackstone polished_blackstone_bricks
gilded_blackstone glowstone shroomlight sea_lantern prismarine
prismarine_bricks dark_prismarine end_stone end_stone_bricks purpur_block
purpur_pillar pumpkin carved_pumpkin jack_o_lantern melon hay_block
honeycomb_block slime_block honey_block magma_block sponge wet_sponge cactus
bamboo_block dried_kelp_block mushroom_stem brown_mushroom_block
red_mushroom_block ochre_froglight verdant_froglight pearlescent_froglight
amethyst_block budding_amethyst moss_block sculk sculk_catalyst mycelium
crimson_nylium warped_nylium torch lantern soul_lantern sea_pickle campfire
barrel crafting_table furnace smoker blast_furnace chest ladder scaffolding
iron_bars chain oak_fence spruce_fence birch_fence dark_oak_fence oak_slab
stone_slab smooth_stone_slab cobblestone_slab stone_brick_slab oak_stairs
stone_stairs cobblestone_stairs stone_brick_stairs oak_door oak_trapdoor
smooth_stone bedrock exposed_copper weathered_copper oxidized_copper
cut_copper waxed_copper_block bone_block ancient_debris raw_iron_block
raw_gold_block raw_copper_block`.split(/\s+/).filter(Boolean));

const unknownCounts = new Map();
function normalizeBlock(raw) {
  let name = String(raw).trim().toLowerCase().replace(/\s+/g, "_");
  if (name.startsWith("minecraft:")) name = name.slice(10);
  const bracket = name.indexOf("[");
  let state = "";
  if (bracket >= 0) { state = name.slice(bracket); name = name.slice(0, bracket); }
  if (userMap[name]) name = userMap[name];
  else if (ALIASES[name]) name = ALIASES[name];
  if (!KNOWN.has(name)) {
    unknownCounts.set(String(raw), (unknownCounts.get(String(raw)) || 0) + 1);
    return defaultBlock.startsWith("minecraft:") ? defaultBlock : "minecraft:" + defaultBlock;
  }
  return "minecraft:" + name + state;
}

// ---------------------------------------------------------------- input
function mulberry32(a) {
  return function () {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function executeVoxelCode(input) {
  const gridSize = Number(input.gridSize) || 256;
  const seed = Number.isFinite(Number(input.seed)) ? Number(input.seed) : 0;
  const grid = new Map();
  const put = (x, y, z, m) => {
    x = Math.round(x); y = Math.round(y); z = Math.round(z);
    if (x < 0 || x >= gridSize || y < 0 || y >= gridSize || z < 0 || z >= gridSize) return;
    grid.set(x + "," + y + "," + z, String(m));
  };
  const sandbox = {
    rng: mulberry32(seed),
    block: put,
    box: (x1, y1, z1, x2, y2, z2, m) => {
      const [ax, bx] = x1 <= x2 ? [x1, x2] : [x2, x1];
      const [ay, by] = y1 <= y2 ? [y1, y2] : [y2, y1];
      const [az, bz] = z1 <= z2 ? [z1, z2] : [z2, z1];
      for (let y = ay; y <= by; y++)
        for (let z = az; z <= bz; z++)
          for (let x = ax; x <= bx; x++) put(x, y, z, m);
    },
    line: (x1, y1, z1, x2, y2, z2, m) => {
      const steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1), Math.abs(z2 - z1), 1);
      for (let i = 0; i <= steps; i++) {
        const t = i / steps;
        put(x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, z1 + (z2 - z1) * t, m);
      }
    },
    Math,
  };
  vm.createContext(sandbox);
  new vm.Script(input.code, { filename: "voxel-exec.js" }).runInContext(sandbox, { timeout: 60000 });
  const blocks = [];
  for (const [key, type] of grid) {
    const [x, y, z] = key.split(",").map(Number);
    blocks.push({ x, y, z, type });
  }
  console.log(`Executed voxel.exec: gridSize=${gridSize}, seed=${seed}, blocks=${blocks.length}`);
  return blocks;
}

function findVoxelInput(node, depth = 0) {
  if (!node || typeof node !== "object" || depth > 6) return null;
  if (typeof node.code === "string" && "gridSize" in node) return node;
  if (node.tool === "voxel.exec" && node.input) return findVoxelInput(node.input, depth + 1);
  if (node.function && typeof node.function.arguments === "string") {
    try { return findVoxelInput(JSON.parse(node.function.arguments), depth + 1); } catch {}
  }
  for (const v of Array.isArray(node) ? node : Object.values(node)) {
    const found = typeof v === "object" ? findVoxelInput(v, depth + 1) : null;
    if (found) return found;
  }
  return null;
}

function extractBlocks(data) {
  let node = data;
  for (let i = 0; i < 4; i++) {
    if (node && typeof node === "object" && !Array.isArray(node)) {
      if (node.blocks) { node = node.blocks; continue; }
      if (node.build) { node = node.build; continue; }
    }
    break;
  }
  if (!Array.isArray(node)) return null;
  const keys = ["type", "block", "material", "name", "b", "t", "id"];
  const out = [];
  for (const e of node) {
    if (Array.isArray(e) && e.length >= 4) {
      out.push({ x: +e[0], y: +e[1], z: +e[2], type: e[3] });
    } else if (e && typeof e === "object") {
      const k = keys.find((k) => e[k] != null);
      if (k == null || e.x == null || e.y == null || e.z == null) return null;
      out.push({ x: +e.x, y: +e.y, z: +e.z, type: e[k] });
    } else return null;
  }
  return out.length ? out : null;
}

// ---------------------------------------------------------------- NBT writer
const chunks = [];
const w8 = (v) => chunks.push(Buffer.from([v & 0xff]));
const w16 = (v) => { const b = Buffer.alloc(2); b.writeInt16BE(v); chunks.push(b); };
const w32 = (v) => { const b = Buffer.alloc(4); b.writeInt32BE(v); chunks.push(b); };
const wstr = (s) => { const b = Buffer.from(s, "utf-8"); w16(b.length); chunks.push(b); };
function wtag(type, name) { w8(type); wstr(name); }
function wcompound(obj) {
  for (const [name, [type, value]] of Object.entries(obj)) {
    wtag(type, name);
    wpayload(type, value);
  }
  w8(0); // TAG_End
}
function wpayload(type, value) {
  if (type === 1) w8(value);
  else if (type === 2) w16(value);
  else if (type === 3) w32(value);
  else if (type === 7) { w32(value.length); chunks.push(Buffer.from(value)); }
  else if (type === 8) wstr(value);
  else if (type === 10) wcompound(value);
  else if (type === 11) { w32(value.length); for (const v of value) w32(v); }
  else throw new Error("tag " + type);
}

// ---------------------------------------------------------------- schem build
function varintBytes(n) {
  const out = [];
  while (true) {
    const b = n & 0x7f;
    n >>>= 7;
    if (n) out.push(b | 0x80);
    else { out.push(b); return out; }
  }
}

function main() {
  const raw = JSON.parse(fs.readFileSync(inputPath, "utf-8"));
  const voxelInput = findVoxelInput(raw);
  const blocks = voxelInput ? executeVoxelCode(voxelInput) : extractBlocks(raw);
  if (!blocks || !blocks.length) {
    console.error("Input is neither a voxel.exec payload nor a recognizable block list.");
    process.exit(1);
  }

  let x0 = Infinity, y0 = Infinity, z0 = Infinity, x1 = -Infinity, y1 = -Infinity, z1 = -Infinity;
  for (const b of blocks) {
    if (b.x < x0) x0 = b.x; if (b.x > x1) x1 = b.x;
    if (b.y < y0) y0 = b.y; if (b.y > y1) y1 = b.y;
    if (b.z < z0) z0 = b.z; if (b.z > z1) z1 = b.z;
  }
  const W = x1 - x0 + 1, H = y1 - y0 + 1, L = z1 - z0 + 1;
  if (Math.max(W, H, L) > 4096) {
    console.error(`Implausible size ${W}x${H}x${L}; check coordinates.`);
    process.exit(1);
  }

  const palette = new Map([["minecraft:air", 0]]);
  const grid = new Int32Array(W * H * L); // zero = air
  for (const b of blocks) {
    const name = normalizeBlock(b.type);
    let idx = palette.get(name);
    if (idx === undefined) { idx = palette.size; palette.set(name, idx); }
    grid[(b.x - x0) + (b.z - z0) * W + (b.y - y0) * W * L] = idx;
  }
  const dataBytes = [];
  for (let i = 0; i < grid.length; i++) {
    const v = grid[i];
    if (v < 128) dataBytes.push(v);
    else dataBytes.push(...varintBytes(v));
  }
  const blockData = Buffer.from(dataBytes);
  const paletteNBT = {};
  for (const [name, idx] of palette) paletteNBT[name] = [3, idx];

  if (format === "v2") {
    wtag(10, "Schematic");
    wcompound({
      Version: [3, 2],
      DataVersion: [3, dataVersion],
      Width: [2, W],
      Height: [2, H],
      Length: [2, L],
      Offset: [11, [0, 0, 0]],
      PaletteMax: [3, palette.size],
      Palette: [10, paletteNBT],
      BlockData: [7, blockData],
    });
  } else {
    // Sponge v3: unnamed root compound containing a "Schematic" compound;
    // block data lives under Blocks.{Palette,Data}.
    wtag(10, "");
    wcompound({
      Schematic: [10, {
        Version: [3, 3],
        DataVersion: [3, dataVersion],
        Width: [2, W],
        Height: [2, H],
        Length: [2, L],
        Offset: [11, [0, 0, 0]],
        Blocks: [10, {
          Palette: [10, paletteNBT],
          Data: [7, blockData],
        }],
      }],
    });
  }

  fs.writeFileSync(outPath, zlib.gzipSync(Buffer.concat(chunks)));
  console.log(`Wrote ${path.resolve(outPath)}  (Sponge ${format}, DataVersion ${dataVersion})`);
  console.log(`  Size: ${W} x ${H} x ${L} (W x H x L)`);
  console.log(`  Blocks: ${blocks.length}, palette: ${palette.size - 1} (+ air)`);
  if (unknownCounts.size) {
    console.log(`  Unrecognized block names (mapped to ${defaultBlock}):`);
    for (const [name, count] of unknownCounts) {
      console.log(`    - '${name}' x${count}  (fix with --map ${name}=<minecraft_id>)`);
    }
  }
}

main();
