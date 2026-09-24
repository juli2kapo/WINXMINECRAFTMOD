/* ===================================================================
 * WINX ELEMENT SEED — shared family model (voxel.exec source)
 * Only the ELEMENT block below changes between elements; everything
 * under "FAMILY" is identical for all 9 seeds so the icons match.
 * STAGE 2: same egg + bolt emblem; adds gold zig-zag lightning fins on the
 * sides and a fuller crown (5 bolt spikes + glowing bolt finial).
 * =================================================================== */

/* ---------------- ELEMENT (per-element config) ---------------- */
const EL = {
  name: 'storm',
  lower: 'gray_wool',      // crystal colour, bottom half (dark storm cloud)
  upper: 'blue_wool',      // crystal colour, top half (stormy night sky)
  core: 'glowstone',       // emblem front (glowing lightning core)
  rim: 'yellow_wool',      // emblem back/side layers (bolt rim)
  // Lightning-bolt emblem: a classic zig-zag polygon in the emblem plane.
  // u = blocks right of the egg axis (screen-right), y = world height,
  // E = egg params. Strokes >= 3 blocks (1 px at 32x32 ~= 2.2 blocks).
  emblem: function (u, y, E) {
    const s = y - E.y0; // height above egg bottom, blocks
    // polygon (u, s): top bar -> slash down-left -> jog right -> long tip down-left -> back up -> jog left
    const P = [[-1.5, 38], [5, 38], [2, 31], [8.5, 31], [-5.5, 18.5], [-1.5, 27], [-6.5, 27]];
    let inside = false;
    for (let i = 0, j = P.length - 1; i < P.length; j = i++) {
      const [xi, yi] = P[i], [xj, yj] = P[j];
      if ((yi > s) !== (yj > s) && u < (xj - xi) * (s - yi) / (yj - yi) + xi) inside = !inside;
    }
    return inside;
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

// ---- 6. crown: gold cap over the tip, four small leaves, glowing finial
for (let y = Math.round(Y0 + 0.9 * E.h); y <= Y1 + 1; y++) for (let x = CX - 6; x <= CX + 6; x++) for (let z = CZ - 6; z <= CZ + 6; z++)
  if (octd(x - CX, z - CZ) <= Math.max(0, prof(y)) + 1.0 && (y - Y0) / E.h >= 0.9) block(x, y, z, G);
for (let k = 0; k < 4; k++) {
  const phi = k * Math.PI / 2 + Math.PI / 4;
  const pts = [];
  for (let t = 0; t <= 1.0001; t += 0.1) {
    const y = Y0 + (0.9 - 0.1 * t) * E.h;
    pts.push(azPt(phi, surfR(y, phi) + 0.6 + 1.2 * Math.sin(t * Math.PI), y));
  }
  sweep(pts, 1.1, 0.6, G);
}
ball(CX, Y1 + 3.2, CZ, 2.2, 'glowstone');
ball(CX, Y1 + 1.0, CZ, 1.4, G);

/* ---------------- STAGE 2: storm fins + bolt crown ---------------- */
// zig-zag path helper in the (rho, y) half-plane at azimuth phi
function boltPath(phi, P, r0, r1, m) { sweep(P.map(([rho, y]) => azPt(phi, rho, y)), r0, r1, m); }
// side lightning fins: gold zig-zag bolts rising up & out from the side volutes
function fin(phi) {
  const s = (v) => Y0 + v * E.h;
  // bold zig-zag bolt: out & up, flat jog back towards the egg, long tapering tip
  boltPath(phi, [[15.5, s(0.44)], [23.0, s(0.61)], [18.5, s(0.61)], [25.5, s(0.88)]], 2.5, 0.5, G);
  // glowing spark core along the bolt's first leg
  boltPath(phi, [[17.5, s(0.49)], [21.5, s(0.58)]], 1.0, 1.0, 'glowstone');
}
fin(Math.PI / 2); fin(-Math.PI / 2);

// fuller crown: taller gold cap band + five zig-zag bolt spikes + glowing bolt finial
const CAP_V = 0.86;
for (let y = Math.round(Y0 + CAP_V * E.h); y <= Y1 + 1; y++) for (let x = CX - 8; x <= CX + 8; x++) for (let z = CZ - 8; z <= CZ + 8; z++)
  if (octd(x - CX, z - CZ) <= Math.max(0, prof(y)) + 1.2 && (y - Y0) / E.h >= CAP_V) block(x, y, z, G);
function spike(phi, h, out) {
  const y0 = Y0 + CAP_V * E.h, r = surfR(y0, phi) + 0.6;
  boltPath(phi, [[r, y0], [r + out * 0.6, y0 + h * 0.5], [r + out * 0.1, y0 + h * 0.55], [r + out, y0 + h]], 1.4, 0.5, G);
}
spike(Math.PI / 2, 9, 3.5); spike(-Math.PI / 2, 9, 3.5);
spike(Math.PI * 0.78, 7, 2.5); spike(-Math.PI * 0.78, 7, 2.5);
spike(Math.PI, 8, 2);
// finial: stubby glowstone lightning bolt standing on the tip
sweep([[CX, Y1 + 1, CZ], [CX + 1.2, Y1 + 4, CZ - 0.7], [CX - 1.2, Y1 + 4.5, CZ + 0.7], [CX + 0.6, Y1 + 8, CZ - 0.35]], 1.5, 0.6, 'glowstone');
