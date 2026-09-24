/* ===================================================================
 * WINX ELEMENT SEED — shared family model (voxel.exec source)
 * Only the ELEMENT block below changes between elements; everything
 * under "FAMILY" is identical for all 9 seeds so the icons match.
 * STAGE 3 (final form, Bloom's Dragon Flame): same egg + flame emblem; the
 * stage-2 flame wings grow into big layered phoenix wings of flame tongues
 * (glowstone cores, swept up and back to frame the egg), a tall blazing crown
 * of flame spires with glowing embers and a big glowing flame finial.
 * =================================================================== */

/* ---------------- ELEMENT (per-element config) ---------------- */
// FIRE (Bloom): a bright flame -- glowing glowstone heart inside an orange
// outer flame body with three tongues -- in front of a red wall.
// Two-material emblem: emblem() records which part (heart / body) was hit and
// the core/rim getters return that part's material.
const EL = {
  name: 'fire',
  lower: 'orange_wool',  // crystal colour, bottom half (warm cup)
  upper: 'red_wool',     // crystal colour, top half (dark red behind the flame for contrast)
  _part: 'body',
  get core() { return this._part === 'heart' ? 'glowstone' : 'orange_wool'; },
  get rim() { return this._part === 'heart' ? 'yellow_wool' : 'orange_wool'; },
  // u = blocks right of the egg axis (screen-right), y = world height,
  // E = egg params. Strokes >= 3 blocks (1 px at 32x32 ~= 2.2 blocks), gaps >= 4.
  emblem: function (u, y, E) {
    const s = y - E.y0;                    // height above egg bottom, blocks
    // tapered, curved tongue from (x0,s0) with half-width w0 to a point at (x1,s1)
    function tongue(x0, s0, x1, s1, w0, bend) {
      if (s < s0 || s > s1) return false;
      const f = (s - s0) / (s1 - s0);
      const xc = x0 + (x1 - x0) * f + bend * Math.sin(f * Math.PI);
      return Math.abs(u - xc) <= Math.max(w0 * Math.pow(1 - f, 0.85), 0.55);
    }
    const bs = 23.0, br = 6.6;
    // glowing heart: teardrop in the belly, tip leaning right
    const hs = s - 22.0, hx = u - 0.3;
    const heart = (hs <= 0 ? hx * hx + (hs * 1.1) ** 2 <= 3.8 * 3.8
                           : hs <= 11.0 && Math.abs(hx - 1.0 * hs / 11.0) <= 3.8 * (1 - hs / 11.0) ** 0.75);
    if (heart) { this._part = 'heart'; return true; }
    let on = u * u + ((s - bs) * 1.1) ** 2 <= br * br;
    on = on || tongue(0.2, 26.0, 1.4, 39.0, 4.6, 2.2);    // centre tongue, S-leans right
    on = on || tongue(-4.9, 23.5, -8.0, 34.0, 2.5, -0.4);  // left tongue
    on = on || tongue(5.0, 24.0, 7.2, 31.5, 2.2, 0.4);     // right tongue
    if (on) { this._part = 'body'; return true; }
    return false;
  },
};

/* ---------------- FAMILY (shared, do not edit per element) ---------------- */
const CX = 128, CZ = 128;
const VIEW = 30 * Math.PI / 180;  // MUST equal CAMERA_YAW in render_icon.js: emblem faces the icon camera
const E = { y0: 6, h: 46, R: 15 }; // egg: bottom y, height, max apothem
// faceted profile: piecewise-linear apothem (fraction of R) at knots of v
const KN = [[0, 0.0], [0.03, 0.42], [0.10, 0.74], [0.20, 0.94], [0.32, 1.0], [0.46, 0.95], [0.60, 0.80], [0.74, 0.58], [0.87, 0.33], [0.96, 0.12], [1.0, 0.0]];
function prof(y) {
  const v = (y - E.y0) / E.h;
  if (v < 0 || v > 1) return -1;
  for (let i = 1; i < KN.length; i++) if (v <= KN[i][0]) {
    const f = (v - KN[i - 1][0]) / (KN[i][0] - KN[i - 1][0]);
    return E.R * (KN[i - 1][1] + f * (KN[i][1] - KN[i - 1][1]));
  }
  return 0;
}
// octagonal cross-section (8 facets), one flat facet facing the camera
function octd(dx, dz) {
  let m = 0;
  for (let k = 0; k < 4; k++) {
    const th = VIEW + k * Math.PI / 4;
    m = Math.max(m, Math.abs(dx * Math.sin(th) - dz * Math.cos(th)));
  }
  return m;
}
function inEgg(x, y, z) { const r = prof(y); return r > 0 && octd(x - CX, z - CZ) <= r + 0.35; }
// surface radius along azimuth phi (phi=0 -> towards the camera)
function surfR(y, phi) {
  const dx = Math.sin(VIEW + phi), dz = -Math.cos(VIEW + phi);
  return Math.max(0, prof(y)) / octd(dx, dz);
}
function azPt(phi, rho, y) { return [CX + Math.sin(VIEW + phi) * rho, y, CZ - Math.cos(VIEW + phi) * rho]; }

// ---- 1. crystal body: glass skin, colour-blended interior, emblem core
const Y0 = E.y0, Y1 = E.y0 + E.h;
const EMB_W0 = -3, EMB_W1 = 1, WALL_W = 2, SOLID_V = 0.33; // emblem slab depth range, back wall start; below SOLID_V (inside the cup) the core is solid
for (let y = Y0; y <= Y1; y++) for (let x = CX - E.R - 2; x <= CX + E.R + 2; x++) for (let z = CZ - E.R - 2; z <= CZ + E.R + 2; z++) {
  if (!inEgg(x, y, z)) continue;
  const skin = !(inEgg(x + 1, y, z) && inEgg(x - 1, y, z) && inEgg(x, y + 1, z) && inEgg(x, y - 1, z) && inEgg(x, y, z + 1) && inEgg(x, y, z - 1));
  const u = (x - CX) * Math.cos(VIEW) + (z - CZ) * Math.sin(VIEW);
  const v = (y - Y0) / E.h;
  if (skin) { block(x, y, z, 'glass'); continue; }
  // w: depth along the camera's horizontal view direction (negative = towards camera)
  const w = -(x - CX) * Math.sin(VIEW) + (z - CZ) * Math.cos(VIEW);
  if (w < EMB_W0 && v > SOLID_V) continue;                   // hollow front: you look through glass into the core
  if (w <= EMB_W1 && EL.emblem(u, y, E)) { block(x, y, z, w < EMB_W0 + 1.5 ? EL.core : EL.rim); continue; }
  if (w < WALL_W && v > SOLID_V) continue;                                  // gap so the emblem floats in front of the back wall
  // back wall: lower colour -> dithered band -> upper colour
  const band = 0.30 + 0.015 * Math.sin(Math.atan2(z - CZ, x - CX) * 4);
  let m;
  if (v < band - 0.04) m = EL.lower; else if (v > band + 0.04) m = EL.upper;
  else m = ((x + y + z) & 1) ? EL.lower : EL.upper;
  block(x, y, z, m);
}

// ---- helpers for the gold filigree (swept spheres)
function ball(cx, cy, cz, r, m) {
  for (let x = Math.floor(cx - r); x <= Math.ceil(cx + r); x++)
    for (let y = Math.floor(cy - r); y <= Math.ceil(cy + r); y++)
      for (let z = Math.floor(cz - r); z <= Math.ceil(cz + r); z++)
        if ((x - cx) ** 2 + (y - cy) ** 2 + (z - cz) ** 2 <= r * r) block(x, y, z, m);
}
function sweep(pts, r0, r1, m) {
  let L = 0; const seg = [];
  for (let i = 1; i < pts.length; i++) { const d = Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1], pts[i][2] - pts[i - 1][2]); seg.push(d); L += d; }
  let acc = 0;
  for (let i = 1; i < pts.length; i++) {
    const n = Math.max(1, Math.ceil(seg[i - 1] * 3));
    for (let j = 0; j <= n; j++) {
      const t = j / n, s = (acc + seg[i - 1] * t) / (L || 1);
      ball(pts[i - 1][0] + (pts[i][0] - pts[i - 1][0]) * t, pts[i - 1][1] + (pts[i][1] - pts[i - 1][1]) * t, pts[i - 1][2] + (pts[i][2] - pts[i - 1][2]) * t, r0 + (r1 - r0) * s, m);
    }
    acc += seg[i - 1];
  }
}
const G = 'gold_block';

// ---- 2. foot: stepped octagonal pedestal (touches Y=0) + neck knot
for (let y = 0; y <= 4; y++) {
  const r = [6.4, 6.4, 4.6, 3.2, 3.6][y];
  for (let x = CX - 8; x <= CX + 8; x++) for (let z = CZ - 8; z <= CZ + 8; z++)
    if (octd(x - CX, z - CZ) <= r) block(x, y, z, G);
}
for (let x = CX - 5; x <= CX + 5; x++) for (let z = CZ - 5; z <= CZ + 5; z++)
  if (octd(x - CX, z - CZ) <= 1.6) block(x, 1, z, 'glowstone'); // hidden jewel seat (keeps foot solid)
[0, 1, 2, 3].forEach(k => { const p = azPt(k * Math.PI / 2 + Math.PI / 4, 6.2, 1.2); ball(p[0], p[1], p[2], 1.3, 'glowstone'); }); // 4 foot jewels

// ---- 3. cradle ribs with clef-like volutes
// 3 ribs: two at the silhouette sides (+-90deg from camera) with big clef curls,
// one at the back with a small curl; nothing crosses the emblem window at the front.
const RIB_TOP = 0.34; // ribs hug the egg up to here, then curl outward
function rib(phi, s0, turns) {
  const pts = [];
  for (let v = 0.0; v <= RIB_TOP + 1e-6; v += 0.02) {
    const y = Y0 + v * E.h - 1.2;
    pts.push(azPt(phi, Math.max(3.2, surfR(y + 1.2, phi) + 0.9), y));
  }
  // volute: spiral in the (radial, y) plane, starting upward, curling outward & down
  const last = pts[pts.length - 1];
  const rho0 = Math.hypot(last[0] - CX, last[2] - CZ), yc = last[1];
  const cr = rho0 + s0;
  for (let th = Math.PI; th >= Math.PI - turns * 2 * Math.PI; th -= 0.08) {
    const f = (Math.PI - th) / (turns * 2 * Math.PI);
    const rad = s0 * (1 - 0.62 * f);
    pts.push(azPt(phi, cr + rad * Math.cos(th), yc + rad * Math.sin(th) * 1.15));
  }
  sweep(pts, 1.55, 0.95, G);
  // bead where the rib leaves the egg
  ball(last[0], last[1], last[2], 1.9, G);
}
rib(Math.PI / 2, 5.2, 1.05);
rib(-Math.PI / 2, 5.2, 1.05);
rib(Math.PI, 2.6, 0.8);

// ---- 4. rings: girdle at the top of the cup + a low collar
function ring(v, off, r, m) {
  const pts = [];
  const y = Y0 + v * E.h;
  for (let a = 0; a <= 2 * Math.PI + 0.05; a += 0.05) pts.push(azPt(a, surfR(y, a) + off, y));
  sweep(pts, r, r, m);
}
ring(RIB_TOP - 0.02, 0.6, 1.25, G);
ring(0.08, 0.5, 1.15, G);

// ---- 5. side cage arcs: from the side volutes up to the crown (never cross the emblem)
function arc(phi) {
  const pts = [];
  for (let v = RIB_TOP + 0.04; v <= 0.95; v += 0.015) {
    const y = Y0 + v * E.h;
    pts.push(azPt(phi, surfR(y, phi) + 0.7, y));
  }
  sweep(pts, 1.05, 0.9, G);
}
arc(Math.PI / 2); arc(-Math.PI / 2); arc(Math.PI);

// ---- STAGE 3 parameters
const WING = {
  vR: 0.40, sweep: 0.78, amax: 28, bmin: -9, bmax: 42,
  root: [2.0, 1.0, 4.0, 5.0], len: 40, hot: 0.24, warm: 0.44, tip: 0.80,
  tongues: [   // a fan of flame feathers rising from the root: short inner ones, tallest at the outer top
    { p: [[0, 2], [2, 12], [5, 21], [3, 30]], w: [3.4, 0.6] },
    { p: [[1, 1], [6, 12], [12, 24], [10, 38]], w: [3.6, 0.6] },
    { p: [[1, 0], [10, 7], [19, 18], [19, 35]], w: [3.4, 0.6] },
    { p: [[1, -1], [12, 0], [22, 5], [26, 19]], w: [3.0, 0.6] },
    { p: [[1, -3], [9, -6], [18, -6], [24, 1]], w: [2.5, 0.6] },
  ],
};
const CROWN = { front: 6, back: 15, out: 4.5, fin: 1.4, stem: 4 };

// ---- 5b. STAGE 3 wings: Bloom's Dragon Flame -- a pair of big phoenix wings of
// layered flame tongues rising from the egg's sides, swept up and back so they
// frame the egg (glowstone cores, yellow/orange body, dark red tips).
function flameWing(sg) {
  const phi0 = sg * Math.PI / 2;           // silhouette side
  const SWEEP = WING.sweep;                // radians swept back at the far tip
  const vR = WING.vR, yR = Y0 + vR * E.h;  // root height
  const rho0 = surfR(yR, phi0) - 0.5;      // root sits on the glass skin
  const T = WING.tongues;
  function bez(p, f) {
    const g = 1 - f;
    return [g * g * g * p[0][0] + 3 * g * g * f * p[1][0] + 3 * g * f * f * p[2][0] + f * f * f * p[3][0],
            g * g * g * p[0][1] + 3 * g * g * f * p[1][1] + 3 * g * f * f * p[2][1] + f * f * f * p[3][1]];
  }
  const samp = T.map(t => { const s = []; for (let f = 0; f <= 1.0001; f += 0.02) { const c = bez(t.p, f); s.push([c[0], c[1], t.w[0] + (t.w[1] - t.w[0]) * Math.pow(f, 0.8), f]); } return s; });
  const cells = new Map();
  const rank = { red_wool: 0, orange_wool: 1, gold_block: 2, yellow_wool: 3, glowstone: 4 };
  const RB = WING.root;
  for (let a = -1; a <= WING.amax; a += 0.4) for (let b = WING.bmin; b <= WING.bmax; b += 0.4) {
    let dn = 9, fb = 0;
    for (const s of samp) for (const c of s) {
      const d = Math.hypot(a - c[0], b - c[1]) / c[2];
      if (d < dn) { dn = d; fb = c[3]; }
    }
    const db = Math.hypot((a - RB[0]) / RB[2], (b - RB[1]) / RB[3]);
    if (db < dn) { dn = db; fb = 0; }
    if (dn > 1) continue;
    // colour by distance from the wing root: white-hot at the root, orange body, dark red tips
    const rr = Math.hypot(a - RB[0], (b - RB[1]) * 0.9) / WING.len;
    let m = 'orange_wool';
    if (rr < WING.hot && dn < 0.8) m = 'glowstone';
    else if (rr < WING.warm && dn < 0.75) m = 'yellow_wool';
    else if (rr > WING.tip) m = 'red_wool';
    else if (dn > 0.8 && rr < 0.2) m = 'gold_block';
    const half = 1.2 + 1.6 * (1 - dn) * (1 - 0.5 * fb);
    const k = Math.max(0, a) / WING.amax;
    const phi = phi0 + sg * SWEEP * Math.min(1, k * k * 1.3 + 0.2 * Math.max(0, b) / WING.bmax);
    const rho = rho0 + a;
    const y = yR + b;
    const tx = Math.cos(VIEW + phi), tz = Math.sin(VIEW + phi);
    for (let t = -half; t <= half + 1e-6; t += 0.5) {
      const p = azPt(phi, rho, y);
      const x = Math.round(p[0] + tx * t), yy = Math.round(y), z = Math.round(p[2] + tz * t);
      if (inEgg(x, yy, z) || yy < 0) continue;
      const key = x + ',' + yy + ',' + z, old = cells.get(key);
      if (!old || rank[m] > rank[old]) cells.set(key, m);
    }
  }
  for (const [key, m] of cells) { const q = key.split(',').map(Number); block(q[0], q[1], q[2], m); }
}
flameWing(1); flameWing(-1);

// ---- 6. crown: tall gold cap, ring of tall blazing spikes, big glowing flame finial
const CAP_V = 0.86;
for (let y = Math.round(Y0 + CAP_V * E.h); y <= Y1 + 1; y++) for (let x = CX - 9; x <= CX + 9; x++) for (let z = CZ - 9; z <= CZ + 9; z++)
  if (octd(x - CX, z - CZ) <= Math.max(0, prof(y)) + 1.3 && (y - Y0) / E.h >= CAP_V) block(x, y, z, G);
ring(CAP_V, 1.0, 1.3, G);
for (let k = 0; k < 6; k++) {
  const phi = k * Math.PI / 3 + Math.PI / 6;
  const front = Math.cos(phi) > 0.6;
  const len = front ? CROWN.front : CROWN.back;
  const y0 = Y0 + CAP_V * E.h;
  const r0 = surfR(y0, phi) + 0.8;
  const pts = [];
  for (let t = 0; t <= 1.0001; t += 0.05) {
    const lean = front ? Math.sin(t * Math.PI * 0.9) * 2.4 - 1.4 * t : CROWN.out * Math.sin(t * Math.PI * 0.5);
    pts.push(azPt(phi + 0.2 * Math.sin(t * Math.PI), r0 + lean, y0 + len * t));
  }
  sweep(pts.slice(0, 13), 2.1, 1.3, G);
  sweep(pts.slice(12), 1.3, 0.5, 'orange_wool');
  if (!front) { const g = pts[7]; ball(g[0], g[1], g[2], 1.3, 'glowstone');   }  // glowing ember on each tall spike
}
ball(CX, Y1 + 1.0, CZ, 2.2, G);
sweep([[CX, Y1 + 1, CZ], [CX, Y1 + 1 + CROWN.stem, CZ]], 1.6, 1.2, G);   // gold stem lifts the finial above the spires
const YF = Y1 + CROWN.stem;
// finial: a big glowing flame teardrop with a flicking tip
const FS = CROWN.fin;
for (let y = YF + 1; y <= YF + 2 + Math.ceil(FS * 5.5); y++) for (let x = CX - 7; x <= CX + 7; x++) for (let z = CZ - 7; z <= CZ + 7; z++) {
  const s = y - (YF + 1.5 * FS + 1);
  const u = (x - CX) * Math.cos(VIEW) + (z - CZ) * Math.sin(VIEW);
  const w = -(x - CX) * Math.sin(VIEW) + (z - CZ) * Math.cos(VIEW);
  const R0 = 3.0 * FS;
  let r, uc = 0;
  if (s <= 0) r = Math.sqrt(Math.max(0, R0 * R0 - (s * 1.1) ** 2));
  else { const f = s / (8.5 * FS); if (f > 1) continue; r = R0 * Math.pow(1 - f, 0.8); uc = 1.8 * FS * Math.sin(f * Math.PI * 0.7); }
  const d = Math.hypot(u - uc, w * 1.15);
  if (d <= r + 0.2) block(x, y, z, (d <= r - 1.3 || s < 3 * FS) ? 'glowstone' : (s > 6 * FS ? 'red_wool' : 'orange_wool'));
}
