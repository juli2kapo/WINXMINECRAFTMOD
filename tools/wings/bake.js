#!/usr/bin/env node
/**
 * bake.js — Converts a MineBench voxel.exec wing build into a compact mesh the
 * mod renders on transformed players (WingsLayer / WingMesh).
 *
 * Usage: node bake.js <element>_wings.json [--factor 2] [--out file.bin]
 * Default output: src/main/resources/assets/minewinx/wings/<element>.bin
 *
 * The build is downsampled (factor^3 cells, majority material), hidden faces
 * are culled and coplanar same-material faces are greedily merged. Voxels with
 * x < 128 go to the left wing, the rest to the right wing, so each half can be
 * flapped around the root independently.
 *
 * Format (big-endian):
 *   magic "WING", u8 version=1
 *   f32 pivotX, pivotY, pivotZ, f32 minX, minY, minZ, maxX, maxY, maxZ (cell units)
 *   u8 paletteSize, then per entry: u8 r, g, b, a, u8 flags (1 = emissive)
 *   for side in [left, right]: i32 quadCount, then per quad:
 *     u8 dir (0 -X,1 +X,2 -Y,3 +Y,4 -Z,5 +Z), u8 plane, u8 u0, v0, u1, v1, u8 palette
 *   (u = the first remaining axis in x,y,z order, v = the second; ranges end-exclusive)
 */
"use strict";
const fs = require("fs"), path = require("path"), vm = require("vm");

const MATERIALS = {
  stone: [125, 125, 125], cobblestone: [110, 110, 110], oak_planks: [162, 130, 78],
  bricks: [150, 97, 83], stone_bricks: [122, 121, 122], grass_block: [95, 160, 60],
  dirt: [134, 96, 67], sand: [219, 207, 163], oak_log: [109, 85, 50],
  oak_leaves: [72, 140, 48], water: [60, 110, 230, 170], white_wool: [234, 236, 237],
  black_wool: [30, 30, 38], red_wool: [176, 46, 38], blue_wool: [60, 68, 180],
  green_wool: [94, 124, 22], yellow_wool: [249, 198, 40], orange_wool: [240, 118, 19],
  purple_wool: [137, 50, 184], brown_wool: [114, 72, 41], gray_wool: [70, 76, 80],
  glass: [200, 235, 245, 110], glowstone: [255, 228, 150, 255, 1], iron_block: [220, 220, 224],
  gold_block: [250, 212, 64],
};

function mulberry32(a) {
  return function () {
    a |= 0; a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

// Same semantics as converter.js so the in-game wings match the .schem.
function execute(input) {
  const grid = new Map();
  const put = (x, y, z, m) => {
    x = Math.round(x); y = Math.round(y); z = Math.round(z);
    if (x < 0 || x > 255 || y < 0 || y > 255 || z < 0 || z > 255) return;
    grid.set(x | (y << 8) | (z << 16), String(m));
  };
  const sandbox = {
    rng: mulberry32(Number(input.seed) || 0), Math, block: put,
    box: (x1, y1, z1, x2, y2, z2, m) => {
      for (let y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
        for (let z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
          for (let x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) put(x, y, z, m);
    },
    line: (x1, y1, z1, x2, y2, z2, m) => {
      const s = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1), Math.abs(z2 - z1), 1);
      for (let i = 0; i <= s; i++) { const t = i / s; put(x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, z1 + (z2 - z1) * t, m); }
    },
  };
  vm.createContext(sandbox);
  new vm.Script(input.code).runInContext(sandbox, { timeout: 60000 });
  return grid;
}

const argv = process.argv.slice(2);
const flag = (f, d) => { const i = argv.indexOf(f); return i >= 0 ? argv[i + 1] : d; };
const inputPath = argv[0];
const factor = Number(flag("--factor", 2));
const element = path.basename(inputPath).replace(/_wings\.json$/i, "").replace(/\.json$/i, "");
const outPath = flag("--out", path.join(__dirname, "../../src/main/resources/assets/minewinx/wings", element + ".bin"));

const raw = JSON.parse(fs.readFileSync(inputPath, "utf-8"));
const voxels = execute(raw.input || raw);

// ---- downsample: a cell is filled when enough of its voxels are, material = most common
const N = Math.ceil(256 / factor);
const counts = new Map();
for (const [k, m] of voxels) {
  const x = k & 255, y = (k >> 8) & 255, z = (k >> 16) & 255;
  const ck = Math.floor(x / factor) + Math.floor(y / factor) * N + Math.floor(z / factor) * N * N;
  let c = counts.get(ck); if (!c) counts.set(ck, c = {});
  c[m] = (c[m] || 0) + 1;
}
const threshold = Math.max(1, Math.floor(factor * factor * factor / 4));
const palette = [], palIndex = new Map();
const cells = new Map(); // ck -> palette index
for (const [ck, c] of counts) {
  let total = 0, best = null, bestN = -1;
  for (const [m, n] of Object.entries(c)) { total += n; if (n > bestN) { best = m; bestN = n; } }
  if (total < threshold) continue;
  if (!palIndex.has(best)) { palIndex.set(best, palette.length); palette.push(best); }
  cells.set(ck, palIndex.get(best));
}
const at = (x, y, z) => (x < 0 || y < 0 || z < 0 || x >= N || y >= N || z >= N) ? -1 : (cells.has(x + y * N + z * N * N) ? cells.get(x + y * N + z * N * N) : -1);
const translucent = (p) => p >= 0 && (MATERIALS[palette[p]] || [])[3] !== undefined && MATERIALS[palette[p]][3] < 255;

// ---- pivot (wing root): centre of the cells within a few cells of the mirror plane
const mid = 128 / factor;
let px = 0, py = 0, pz = 0, pn = 0;
let mn = [1e9, 1e9, 1e9], mx = [-1e9, -1e9, -1e9];
for (const ck of cells.keys()) {
  const x = ck % N, y = Math.floor(ck / N) % N, z = Math.floor(ck / (N * N));
  mn = [Math.min(mn[0], x), Math.min(mn[1], y), Math.min(mn[2], z)];
  mx = [Math.max(mx[0], x + 1), Math.max(mx[1], y + 1), Math.max(mx[2], z + 1)];
  if (Math.abs(x + 0.5 - mid) <= 4) { px += x + 0.5; py += y + 0.5; pz += z + 0.5; pn++; }
}
const pivot = pn ? [mid, py / pn, pz / pn] : [mid, (mn[1] + mx[1]) / 2, (mn[2] + mx[2]) / 2];

// ---- greedy meshing per side
const DIRS = [[-1, 0, 0], [1, 0, 0], [0, -1, 0], [0, 1, 0], [0, 0, -1], [0, 0, 1]];
function mesh(side) {
  const inSide = (x) => side === 0 ? x + 0.5 < mid : x + 0.5 >= mid;
  const quads = [];
  for (let d = 0; d < 6; d++) {
    const axis = d >> 1, [dx, dy, dz] = DIRS[d];
    const ua = axis === 0 ? 1 : 0, va = axis === 2 ? 1 : 2;
    for (let p = 0; p < N; p++) {
      const mask = new Int16Array(N * N).fill(-1);
      let any = false;
      for (let v = 0; v < N; v++) for (let u = 0; u < N; u++) {
        const c = [0, 0, 0]; c[axis] = p; c[ua] = u; c[va] = v;
        if (!inSide(c[0])) continue;
        const a = at(c[0], c[1], c[2]); if (a < 0) continue;
        const b = at(c[0] + dx, c[1] + dy, c[2] + dz);
        const visible = b < 0 || (translucent(b) && !translucent(a));
        if (visible) { mask[u + v * N] = a; any = true; }
      }
      if (!any) continue;
      for (let v = 0; v < N; v++) for (let u = 0; u < N; ) {
        const m = mask[u + v * N]; if (m < 0) { u++; continue; }
        let w = 1; while (u + w < N && mask[u + w + v * N] === m) w++;
        let h = 1;
        outer: while (v + h < N) { for (let k = 0; k < w; k++) if (mask[u + k + (v + h) * N] !== m) break outer; h++; }
        for (let j = 0; j < h; j++) for (let k = 0; k < w; k++) mask[u + k + (v + j) * N] = -1;
        const plane = d & 1 ? p + 1 : p;
        quads.push([d, plane, u, v, u + w, v + h, m]);
        u += w;
      }
    }
  }
  return quads;
}
const sides = [mesh(0), mesh(1)];

// ---- write
const parts = [];
const u8 = (...v) => parts.push(Buffer.from(v));
const f32 = (v) => { const b = Buffer.alloc(4); b.writeFloatBE(v); parts.push(b); };
const i32 = (v) => { const b = Buffer.alloc(4); b.writeInt32BE(v); parts.push(b); };
parts.push(Buffer.from("WING")); u8(1);
[...pivot, ...mn, ...mx].forEach(f32);
u8(palette.length);
for (const m of palette) {
  const [r, g, b, a = 255, fl = 0] = MATERIALS[m] || [255, 0, 255];
  u8(r, g, b, a, fl);
}
for (const q of sides) {
  i32(q.length);
  for (const quad of q) {
    if (quad.some((v) => v > 255)) throw new Error("coordinate > 255 in quad; use a larger --factor");
    u8(...quad);
  }
}
fs.mkdirSync(path.dirname(outPath), { recursive: true });
fs.writeFileSync(outPath, Buffer.concat(parts));
console.log(`${element}: ${voxels.size} voxels -> ${cells.size} cells (factor ${factor}), quads L=${sides[0].length} R=${sides[1].length}, ` +
  `pivot ${pivot.map((v) => v.toFixed(1)).join(",")}, ${fs.statSync(outPath).size} bytes -> ${path.relative(process.cwd(), outPath)}`);
