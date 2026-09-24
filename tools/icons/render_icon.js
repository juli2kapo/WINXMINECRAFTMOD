#!/usr/bin/env node
/**
 * render_icon.js — voxel model (voxel.exec JSON) -> Minecraft-style item icon.
 *
 *   node render_icon.js <model.json> <out.png> [--size 32] [--preview big.png] [--preview-size 512] [--debug]
 *   (--debug prints the winning material of every icon pixel)
 *
 * No npm deps: executes the voxel.exec code in a vm sandbox (same semantics as
 * tools/wings/converter.js: Math.round on coords, grid bounds, mulberry32
 * seeded rng, last write wins), ray-casts it with an orthographic camera, then
 * downsamples to pixel art and writes RGBA PNGs with a hand-rolled encoder.
 *
 * ---------------------------------------------------------------------------
 * FIXED PARAMETERS — identical for every seed icon. Do not tweak per element;
 * change them here only if you intend to re-render ALL icons.
 * ---------------------------------------------------------------------------
 *  Camera      orthographic, CAMERA_YAW = 30 deg (camera sits front-right of the
 *              model: sees the -Z face on the left of the screen and +X face on
 *              the right), CAMERA_PITCH = 22 deg looking down. Models should
 *              put their "front" (emblem) facing azimuth CAMERA_YAW (the seed
 *              model's VIEW constant).
 *  Lighting    per-face, vanilla-item style:  top (+Y) 1.00, screen-left face
 *              (-Z) 0.88, screen-right face (+X) 0.74, bottom 0.55.
 *              Plus soft ambient occlusion (AO_STRENGTH) from the 8 neighbours
 *              of each visible face, so curved voxel forms read as round.
 *  Emissive    glowstone blocks ignore face shading (0.95-1.0);
 *              gold_block uses a lifted ramp (1.00 / 0.90 / 0.74) so the metal
 *              stays bright but still shows its planes.
 *  Glass       semi-transparent: each air->glass boundary blends GLASS_RGB at
 *              GLASS_ALPHA over what is behind (glass->glass faces are invisible,
 *              like in-game), top-facing glass gets an extra sheen.
 *  Framing     model silhouette cropped exactly (projected voxel corners), fit
 *              into (size - 2*MARGIN - 2*OUTLINE) px keeping aspect, centred;
 *              MARGIN = 0 transparent px, OUTLINE = 1 px -> e.g. 30 px model on 32.
 *  Sampling    SS x SS rays per icon pixel (SS = 384/size, min 6). Coverage >= 50%
 *              -> opaque. Majority vote by MATERIAL (GROUP_WEIGHT: glowstone 1.3,
 *              gold/yellow 1.15 so emblem + filigree survive), colour = mean of
 *              the winning material's samples (keeps its face shading mix) — no
 *              muddy cross-material blends.
 *  Palette     k-means clean-up done PER MATERIAL (PALETTE_PER_MATERIAL: 4 shades
 *              at 32px, 6 at 64px) so materials never merge into each other.
 *  Contours    inner lines: a pixel whose neighbour is >= EDGE_DEPTH (3) blocks
 *              closer gets darkened by INNER_DARKEN (0.55) — separates emblem,
 *              filigree and crystal like the internal lines of vanilla sprites.
 *  Outline     1 px outside the silhouette (4-connected), colour = darkest
 *              adjacent pixel * OUTLINE_DARKEN, fully opaque, like vanilla items.
 *  Preview     same camera/lighting at --preview-size with 3x3 AA, on transparent.
 * ---------------------------------------------------------------------------
 */
"use strict";
const fs = require("fs");
const vm = require("vm");
const zlib = require("zlib");

// ============================== FIXED PARAMETERS ==============================
const CAMERA_YAW = 30;          // degrees
const CAMERA_PITCH = 22;        // degrees
const SHADE = { top: 1.0, left: 0.88, right: 0.74, bottom: 0.55 };
const GOLD_SHADE = { top: 1.0, left: 0.90, right: 0.74, bottom: 0.6 };
const EMISSIVE_SHADE = { top: 1.0, left: 0.97, right: 0.93, bottom: 0.9 };
const AO_STRENGTH = 0.22;       // 0 = off
const GLASS_RGB = [245, 250, 255];
const GLASS_ALPHA = 0.12;
const GLASS_SHEEN = 0.18;       // extra white on top-facing glass
const MARGIN = 0;               // transparent px around the outline
const OUTLINE = 1;              // px
const OUTLINE_DARKEN = 0.28;
const EDGE_DEPTH = 3.0;         // blocks; depth jump that draws an inner contour
const INNER_DARKEN = 0.55;
const PALETTE_PER_MATERIAL = { 16: 3, 32: 4, 64: 6 };
const GROUP_WEIGHT = { glowstone: 1.3, yellow_wool: 1.15, gold_block: 1.15 };
const EMISSIVE = new Set(["glowstone"]);
// Block colours (average vanilla texture colour, slightly punched up for icons).
const COLORS = {
  stone: [125, 125, 125], cobblestone: [110, 110, 110], oak_planks: [162, 130, 78], bricks: [150, 97, 83],
  stone_bricks: [122, 121, 122], grass_block: [95, 160, 60], dirt: [134, 96, 67], sand: [219, 207, 163],
  oak_log: [109, 85, 50], oak_leaves: [60, 120, 40], water: [50, 90, 210], white_wool: [234, 236, 237],
  black_wool: [21, 21, 26], red_wool: [176, 36, 42], blue_wool: [53, 57, 170], green_wool: [84, 118, 27],
  yellow_wool: [252, 204, 48], orange_wool: [240, 118, 19], purple_wool: [128, 40, 184], brown_wool: [114, 72, 41],
  gray_wool: [63, 68, 72], glass: GLASS_RGB, glowstone: [255, 236, 160], iron_block: [220, 220, 220],
  gold_block: [250, 212, 64],
};
// ==============================================================================

// ------------------------------------------------------------------ voxel.exec
function mulberry32(a) {
  return function () {
    a |= 0; a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
function findInput(n, d = 0) {
  if (!n || typeof n !== "object" || d > 6) return null;
  if (typeof n.code === "string") return n;
  if (n.input) return findInput(n.input, d + 1);
  for (const v of Object.values(n)) { const f = typeof v === "object" ? findInput(v, d + 1) : null; if (f) return f; }
  return null;
}
function execVoxel(input) {
  const gs = Number(input.gridSize) || 256;
  const seed = Number.isFinite(Number(input.seed)) ? Number(input.seed) : 0;
  const grid = new Map();
  const put = (x, y, z, m) => {
    x = Math.round(x); y = Math.round(y); z = Math.round(z);
    if (x < 0 || x >= gs || y < 0 || y >= gs || z < 0 || z >= gs) return;
    grid.set(x + "," + y + "," + z, String(m));
  };
  const sb = {
    rng: mulberry32(seed), block: put, Math,
    box: (x1, y1, z1, x2, y2, z2, m) => {
      const [ax, bx] = x1 <= x2 ? [x1, x2] : [x2, x1], [ay, by] = y1 <= y2 ? [y1, y2] : [y2, y1], [az, bz] = z1 <= z2 ? [z1, z2] : [z2, z1];
      for (let y = ay; y <= by; y++) for (let z = az; z <= bz; z++) for (let x = ax; x <= bx; x++) put(x, y, z, m);
    },
    line: (x1, y1, z1, x2, y2, z2, m) => {
      const s = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1), Math.abs(z2 - z1), 1);
      for (let i = 0; i <= s; i++) { const t = i / s; put(x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, z1 + (z2 - z1) * t, m); }
    },
  };
  vm.createContext(sb);
  new vm.Script(input.code, { filename: "voxel-exec.js" }).runInContext(sb, { timeout: 60000 });
  return grid;
}

// ------------------------------------------------------------------ dense grid
function buildVolume(grid) {
  let mn = [1e9, 1e9, 1e9], mx = [-1e9, -1e9, -1e9];
  for (const k of grid.keys()) { const p = k.split(",").map(Number); for (let i = 0; i < 3; i++) { mn[i] = Math.min(mn[i], p[i]); mx[i] = Math.max(mx[i], p[i]); } }
  const dim = [mx[0] - mn[0] + 1, mx[1] - mn[1] + 1, mx[2] - mn[2] + 1];
  const names = [null], ids = new Map();
  const vol = new Uint8Array(dim[0] * dim[1] * dim[2]);
  for (const [k, m] of grid) {
    const p = k.split(",").map(Number);
    if (!ids.has(m)) { ids.set(m, names.length); names.push(m); }
    vol[(p[0] - mn[0]) + dim[0] * ((p[1] - mn[1]) + dim[1] * (p[2] - mn[2]))] = ids.get(m);
  }
  const at = (x, y, z) => (x < 0 || y < 0 || z < 0 || x >= dim[0] || y >= dim[1] || z >= dim[2]) ? 0 : vol[x + dim[0] * (y + dim[1] * z)];
  return { vol, dim, names, at, count: grid.size };
}

// ------------------------------------------------------------------ camera
const yaw = CAMERA_YAW * Math.PI / 180, pit = CAMERA_PITCH * Math.PI / 180;
const CAM_R = [Math.cos(yaw), 0, Math.sin(yaw)];                                           // screen right
const CAM_U = [-Math.sin(yaw) * Math.sin(pit), Math.cos(pit), Math.cos(yaw) * Math.sin(pit)]; // screen up
const CAM_C = [Math.sin(yaw) * Math.cos(pit), Math.sin(pit), -Math.cos(yaw) * Math.cos(pit)]; // towards camera
const DIR = CAM_C.map((v) => -v);
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];

function projectedExtent(V) {
  let u0 = 1e9, u1 = -1e9, v0 = 1e9, v1 = -1e9;
  const { dim, at } = V;
  for (let z = 0; z < dim[2]; z++) for (let y = 0; y < dim[1]; y++) for (let x = 0; x < dim[0]; x++) {
    if (!at(x, y, z)) continue;
    for (let c = 0; c < 8; c++) {
      const p = [x + (c & 1), y + ((c >> 1) & 1), z + ((c >> 2) & 1)];
      const u = dot(p, CAM_R), v = dot(p, CAM_U);
      if (u < u0) u0 = u; if (u > u1) u1 = u; if (v < v0) v0 = v; if (v > v1) v1 = v;
    }
  }
  return { u0, u1, v0, v1 };
}

// face ids: 0 +X,1 -X,2 +Y,3 -Y,4 +Z,5 -Z
function faceShade(name, face) {
  const t = EMISSIVE.has(name) ? EMISSIVE_SHADE : name === "gold_block" ? GOLD_SHADE : SHADE;
  if (face === 2) return t.top;
  if (face === 3) return t.bottom;
  if (face === 5 || face === 1) return t.left;   // -Z (and hidden -X) -> screen-left plane
  return t.right;                                 // +X (and hidden +Z)
}
// soft AO: count occupied non-glass voxels around the face (in the face plane, one step out)
function ambientOcclusion(V, glassId, x, y, z, face) {
  if (!AO_STRENGTH) return 1;
  const ax = face >> 1, s = (face & 1) ? -1 : 1;
  const o = [x, y, z]; o[ax] += s;
  const a1 = (ax + 1) % 3, a2 = (ax + 2) % 3;
  let occ = 0, tot = 0;
  for (let i = -1; i <= 1; i++) for (let j = -1; j <= 1; j++) {
    if (!i && !j) continue;
    const p = o.slice(); p[a1] += i; p[a2] += j;
    const m = V.at(p[0], p[1], p[2]);
    const w = (i && j) ? 0.5 : 1;
    tot += w; if (m && m !== glassId) occ += w;
  }
  return 1 - AO_STRENGTH * (occ / tot);
}

// Cast one ray. Returns {a, rgb, key, depth} ; rgb is premultiplied-free colour.
function castRay(V, glassId, ou, ov) {
  const { dim, at, names } = V;
  // ray origin on the plane through the volume centre, pulled back along +C
  const ctr = [dim[0] / 2, dim[1] / 2, dim[2] / 2];
  const back = dim[0] + dim[1] + dim[2];
  const cu = dot(ctr, CAM_R), cv = dot(ctr, CAM_U);
  const o = [0, 1, 2].map((i) => ctr[i] + (ou - cu) * CAM_R[i] + (ov - cv) * CAM_U[i] + back * CAM_C[i]);
  // clip to box
  let t0 = 0, t1 = 1e9;
  for (let i = 0; i < 3; i++) {
    if (Math.abs(DIR[i]) < 1e-12) { if (o[i] < 0 || o[i] > dim[i]) return null; continue; }
    let a = (0 - o[i]) / DIR[i], b = (dim[i] - o[i]) / DIR[i];
    if (a > b) [a, b] = [b, a];
    t0 = Math.max(t0, a); t1 = Math.min(t1, b);
  }
  if (t0 >= t1) return null;
  const p = o.map((v, i) => v + DIR[i] * (t0 + 1e-6));
  let cell = p.map((v, i) => Math.min(dim[i] - 1, Math.max(0, Math.floor(v))));
  const step = DIR.map((d) => (d > 0 ? 1 : -1));
  const tDelta = DIR.map((d) => Math.abs(1 / d));
  const tMax = DIR.map((d, i) => { const nb = d > 0 ? cell[i] + 1 : cell[i]; return t0 + (nb - p[i]) / d; });
  // entry face for first cell: axis with largest t among entry planes
  let face = -1;
  { let best = -1e9; for (let i = 0; i < 3; i++) { const d = DIR[i]; if (Math.abs(d) < 1e-12) continue; const a = ((d > 0 ? 0 : dim[i]) - o[i]) / d; if (a > best) { best = a; face = i * 2 + (d > 0 ? 1 : 0); } } }
  let r = 0, g = 0, b = 0, trans = 1, prev = 0, sheen = 0, glassLayers = 0;
  let t = t0;
  for (let guard = 0; guard < 4096; guard++) {
    const m = at(cell[0], cell[1], cell[2]);
    if (m) {
      const name = names[m];
      if (m === glassId) {
        if (prev !== glassId) {
          const sh = faceShade("glass", face);
          const a = GLASS_ALPHA + (face === 2 ? GLASS_SHEEN : 0);
          r += trans * a * Math.min(255, GLASS_RGB[0] * sh); g += trans * a * Math.min(255, GLASS_RGB[1] * sh); b += trans * a * Math.min(255, GLASS_RGB[2] * sh);
          trans *= 1 - a; glassLayers++;
          sheen = 1;
        }
      } else {
        const c = COLORS[name] || [255, 0, 255];
        const sh = faceShade(name, face) * (EMISSIVE.has(name) ? 1 : ambientOcclusion(V, glassId, cell[0], cell[1], cell[2], face));
        r += trans * Math.min(255, c[0] * sh); g += trans * Math.min(255, c[1] * sh); b += trans * Math.min(255, c[2] * sh);
        return { a: 1, rgb: [r, g, b], key: name, name, depth: t };
      }
    }
    prev = m;
    // advance
    let ax = tMax[0] < tMax[1] ? (tMax[0] < tMax[2] ? 0 : 2) : (tMax[1] < tMax[2] ? 1 : 2);
    t = tMax[ax];
    cell[ax] += step[ax]; tMax[ax] += tDelta[ax];
    face = ax * 2 + (step[ax] > 0 ? 1 : 0); // entering through the face pointing back at the camera
    if (cell[ax] < 0 || cell[ax] >= dim[ax]) break;
  }
  if (glassLayers) { // pure glass hit with nothing behind it
    const a = 1 - trans;
    return { a, rgb: [r / a, g / a, b / a], key: "glass", name: "glass", depth: t };
  }
  return null;
}

// Render a W x H grid of samples. Model extent fits `fit` px inside, centred.
function renderSamples(V, ext, W, H, fitW, fitH, ss) {
  const glassId = V.names.indexOf("glass");
  const eu = ext.u1 - ext.u0, ev = ext.v1 - ext.v0;
  const s = Math.min(fitW / eu, fitH / ev); // px per block
  const midU = (ext.u0 + ext.u1) / 2, midV = (ext.v0 + ext.v1) / 2;
  const out = new Array(W * H);
  for (let py = 0; py < H; py++) for (let px = 0; px < W; px++) {
    const list = [];
    for (let j = 0; j < ss; j++) for (let i = 0; i < ss; i++) {
      const sx = px + (i + 0.5) / ss, sy = py + (j + 0.5) / ss;
      const u = midU + (sx - W / 2) / s, v = midV - (sy - H / 2) / s;
      list.push(castRay(V, glassId, u, v));
    }
    out[py * W + px] = list;
  }
  return out;
}

// ------------------------------------------------------------------ pixel-art reduction
function reduce(samples, ss) {
  const n = ss * ss;
  return samples.map((list) => {
    const hits = list.filter((h) => h && h.a > 0.5);
    if (hits.length < n / 2) return null;
    const groups = new Map();
    for (const h of hits) {
      let g = groups.get(h.key);
      if (!g) { g = { w: 0, n: 0, r: 0, gg: 0, b: 0, d: 0, name: h.name }; groups.set(h.key, g); }
      g.w += GROUP_WEIGHT[h.name] || 1; g.n++; g.r += h.rgb[0]; g.gg += h.rgb[1]; g.b += h.rgb[2]; g.d += h.depth;
    }
    let best = null;
    for (const g of groups.values()) if (!best || g.w > best.w) best = g;
    return [best.r / best.n, best.gg / best.n, best.b / best.n, best.name, best.d / best.n];
  });
}

function kmeans(pixels, k) {
  const pts = pixels.filter(Boolean).map((p) => p.slice(0, 3));
  const uniq = [...new Map(pts.map((p) => [p.map(Math.round).join(","), p])).values()];
  if (uniq.length <= k) return (p) => p;
  // deterministic farthest-point init
  const cents = [uniq.reduce((a, p) => (p[0] + p[1] + p[2] > a[0] + a[1] + a[2] ? p : a)).slice()];
  const d2 = (a, b) => (a[0] - b[0]) ** 2 * 0.9 + (a[1] - b[1]) ** 2 * 1.2 + (a[2] - b[2]) ** 2 * 0.7;
  while (cents.length < k) {
    let bp = null, bd = -1;
    for (const p of uniq) { const d = Math.min(...cents.map((c) => d2(p, c))); if (d > bd) { bd = d; bp = p; } }
    cents.push(bp.slice());
  }
  for (let it = 0; it < 30; it++) {
    const acc = cents.map(() => [0, 0, 0, 0]);
    for (const p of pts) { let bi = 0, bd = 1e18; cents.forEach((c, i) => { const d = d2(p, c); if (d < bd) { bd = d; bi = i; } }); acc[bi][0] += p[0]; acc[bi][1] += p[1]; acc[bi][2] += p[2]; acc[bi][3]++; }
    acc.forEach((a, i) => { if (a[3]) cents[i] = [a[0] / a[3], a[1] / a[3], a[2] / a[3]]; });
  }
  return (p) => { let bi = 0, bd = 1e18; cents.forEach((c, i) => { const d = d2(p, c); if (d < bd) { bd = d; bi = i; } }); return cents[bi]; };
}

// Inner contours: a pixel whose 4-neighbour is >= EDGE_DEPTH blocks closer to the
// camera is on the far side of an edge -> darken it (vanilla-style internal lines).
function innerContours(px, W, H) {
  const out = px.slice();
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const p = px[y * W + x]; if (!p) continue;
    let edge = false;
    for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
      const X = x + dx, Y = y + dy; if (X < 0 || Y < 0 || X >= W || Y >= H) continue;
      const q = px[Y * W + X]; if (q && q[4] < p[4] - EDGE_DEPTH) edge = true;
    }
    if (edge && !EMISSIVE.has(p[3])) out[y * W + x] = [p[0] * INNER_DARKEN, p[1] * INNER_DARKEN, p[2] * INNER_DARKEN, p[3], p[4]];
  }
  return out;
}

function addOutline(px, W, H) {
  const out = px.slice();
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    if (px[y * W + x]) continue;
    let dark = null;
    for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
      const X = x + dx, Y = y + dy;
      if (X < 0 || Y < 0 || X >= W || Y >= H) continue;
      const q = px[Y * W + X];
      if (q && (!dark || q[0] + q[1] + q[2] < dark[0] + dark[1] + dark[2])) dark = q;
    }
    if (dark) out[y * W + x] = [dark[0] * OUTLINE_DARKEN, dark[1] * OUTLINE_DARKEN, dark[2] * OUTLINE_DARKEN, "outline"];
  }
  return out;
}

// ------------------------------------------------------------------ PNG
const CRC_T = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
function crc32(buf) { let c = 0xffffffff; for (const x of buf) c = CRC_T[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; }
function writePNG(file, W, H, rgba) {
  const raw = Buffer.alloc((W * 4 + 1) * H);
  for (let y = 0; y < H; y++) { raw[y * (W * 4 + 1)] = 0; for (let i = 0; i < W * 4; i++) raw[y * (W * 4 + 1) + 1 + i] = rgba[y * W * 4 + i]; }
  const chunk = (ty, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length); const td = Buffer.concat([Buffer.from(ty), d]); const c = Buffer.alloc(4); c.writeUInt32BE(crc32(td)); return Buffer.concat([l, td, c]); };
  const h = Buffer.alloc(13); h.writeUInt32BE(W, 0); h.writeUInt32BE(H, 4); h[8] = 8; h[9] = 6; h[10] = 0; h[11] = 0; h[12] = 0;
  fs.writeFileSync(file, Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", h), chunk("IDAT", zlib.deflateSync(raw, { level: 9 })), chunk("IEND", Buffer.alloc(0))]));
}
function toRGBA(px, W, H) {
  const a = new Uint8Array(W * H * 4);
  px.forEach((p, i) => { if (!p) return; a[i * 4] = Math.round(Math.min(255, p[0])); a[i * 4 + 1] = Math.round(Math.min(255, p[1])); a[i * 4 + 2] = Math.round(Math.min(255, p[2])); a[i * 4 + 3] = 255; });
  return a;
}

// ------------------------------------------------------------------ main
function renderIcon(V, ext, size, debug) {
  const ss = Math.max(6, Math.round(384 / size));
  const fit = size - 2 * (MARGIN + OUTLINE);
  const samples = renderSamples(V, ext, size, size, fit, fit, ss);
  let px = reduce(samples, ss);
  if (debug) { // winning material per pixel
    const ch = {}; let next = 0; const sym = "#GyrpbwoKLMNOPQ";
    let o = ""; for (let y = 0; y < size; y++) { for (let x = 0; x < size; x++) { const p = px[y * size + x]; if (!p) { o += " "; continue; } if (!(p[3] in ch)) ch[p[3]] = p[3] === "glass" ? "." : sym[next++]; o += ch[p[3]]; } o += "\n"; }
    console.log(o + JSON.stringify(ch));
  }
  // palette clean-up per material: never merges e.g. emblem into lit crystal
  const k = PALETTE_PER_MATERIAL[size] || 4, qs = {};
  for (const name of new Set(px.filter(Boolean).map((p) => p[3]))) qs[name] = kmeans(px.filter((p) => p && p[3] === name), k);
  px = px.map((p) => (p ? [...qs[p[3]](p), p[3], p[4]] : null));
  px = innerContours(px, size, size);
  px = addOutline(px, size, size);
  return toRGBA(px, size, size);
}
function renderPreview(V, ext, size) {
  const ss = 3, pad = Math.round(size * 0.04);
  const samples = renderSamples(V, ext, size, size, size - 2 * pad, size - 2 * pad, ss);
  const a = new Uint8Array(size * size * 4);
  samples.forEach((list, i) => {
    let r = 0, g = 0, b = 0, al = 0;
    for (const h of list) if (h) { r += h.rgb[0] * h.a; g += h.rgb[1] * h.a; b += h.rgb[2] * h.a; al += h.a; }
    if (!al) return;
    a[i * 4] = r / al; a[i * 4 + 1] = g / al; a[i * 4 + 2] = b / al; a[i * 4 + 3] = Math.round(255 * al / list.length);
  });
  return a;
}

// ------------------------------------------------------------------ args
function parseArgs() {
const argv = process.argv.slice(2);
const opt = (f, d) => { const i = argv.indexOf(f); return i >= 0 ? argv[i + 1] : d; };
const pos = argv.filter((a, i) => !a.startsWith("--") && !(i > 0 && argv[i - 1].startsWith("--")));
if (pos.length < 2) {
  console.error("Usage: node render_icon.js <model.json> <out.png> [--size 32] [--preview big.png] [--preview-size 512]");
  process.exit(1);
}
const [modelPath, outPath] = pos;
const SIZE = Number(opt("--size", 32));
const previewPath = opt("--preview", null);
const PREVIEW_SIZE = Number(opt("--preview-size", 512));
return { modelPath, outPath, SIZE, previewPath, PREVIEW_SIZE, debug: argv.includes("--debug") };
}

if (require.main === module) {
  const { modelPath, outPath, SIZE, previewPath, PREVIEW_SIZE, debug } = parseArgs();
  const input = findInput(JSON.parse(fs.readFileSync(modelPath, "utf8")));
  if (!input) { console.error("No voxel.exec input found in " + modelPath); process.exit(1); }
  const V = buildVolume(execVoxel(input));
  const ext = projectedExtent(V);
  console.log(`model: ${V.count} blocks, ${V.dim.join("x")} (x,y,z), materials: ${V.names.slice(1).join(", ")}`);
  writePNG(outPath, SIZE, SIZE, renderIcon(V, ext, SIZE, debug));
  console.log("wrote " + outPath);
  if (previewPath) { writePNG(previewPath, PREVIEW_SIZE, PREVIEW_SIZE, renderPreview(V, ext, PREVIEW_SIZE)); console.log("wrote " + previewPath); }
}
module.exports = { writePNG, crc32 };
