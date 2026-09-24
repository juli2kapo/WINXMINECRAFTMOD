/* ===================================================================
 * WINX ELEMENT SEED — shared family model (voxel.exec source)
 * Only the ELEMENT block below changes between elements; everything
 * under "FAMILY" is identical for all 9 seeds so the icons match.
 * STAGE 2: same egg + emblem, richer cradle (4 curled ribs, circuit-trace
 * bands with node jewels on the cup, angular girdle nodes, fuller crown
 * with a hexagonal gold finial ring).
 * =================================================================== */

/* ---------------- ELEMENT (per-element config) ---------------- */
const EL = {
  name: 'technology',
  lower: 'green_wool',     // crystal colour, bottom half
  upper: 'purple_wool',    // crystal colour, top half
  core: 'glowstone',       // emblem core (glowing)
  rim: 'green_wool',       // emblem back/side layers
  // Power symbol: thick broken ring with a vertical bar through the gap.
  // u = blocks right of axis, y = world height, E = egg params.
  emblem: function (u, y, E) {
    const cy = E.y0 + 0.53 * E.h;        // ring centre (world y)
    const dy = y - cy, r = Math.hypot(u, dy);
    const RO = 7.4, RI = 4.0;            // ring outer/inner radius (stroke ~3.4)
    // ring, with a gap at the top where the bar passes
    if (r <= RO && r >= RI && Math.abs(Math.atan2(u, dy)) >= 0.8) return true;
    // bar: 3 wide, from ring centre up past the ring top
    if (Math.abs(u) <= 1.6 && dy >= -0.5 && dy <= RO + 2.6) return true;
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

// ---- 3. cradle ribs with volutes (STAGE 2: four curled ribs, jewelled eyes)
const RIB_TOP = 0.34;
function rib(phi, s0, turns, jewel) {
  const pts = [];
  for (let v = 0.0; v <= RIB_TOP + 1e-6; v += 0.02) {
    const y = Y0 + v * E.h - 1.2;
    pts.push(azPt(phi, Math.max(3.2, surfR(y + 1.2, phi) + 0.9), y));
  }
  const last = pts[pts.length - 1];
  const rho0 = Math.hypot(last[0] - CX, last[2] - CZ), yc = last[1];
  const cr = rho0 + s0;
  for (let th = Math.PI; th >= Math.PI - turns * 2 * Math.PI; th -= 0.08) {
    const f = (Math.PI - th) / (turns * 2 * Math.PI);
    const rad = s0 * (1 - 0.62 * f);
    pts.push(azPt(phi, cr + rad * Math.cos(th), yc + rad * Math.sin(th) * 1.15));
  }
  sweep(pts, 1.55, 0.95, G);
  ball(last[0], last[1], last[2], 1.9, G);
  if (jewel) { const c = azPt(phi, cr, yc); ball(c[0], c[1], c[2], jewel, 'glowstone'); }
}
rib(Math.PI / 2, 5.2, 1.05, 1.6);
rib(-Math.PI / 2, 5.2, 1.05, 1.6);
rib(Math.PI * 0.78, 3.4, 0.9, 1.0);
rib(-Math.PI * 0.78, 3.4, 0.9, 1.0);

// ---- 4. rings: girdle, collar, and circuit-trace bands on the green cup
function ring(v, off, r, m) {
  const pts = [];
  const y = Y0 + v * E.h;
  for (let a = 0; a <= 2 * Math.PI + 0.05; a += 0.05) pts.push(azPt(a, surfR(y, a) + off, y));
  sweep(pts, r, r, m);
}
ring(RIB_TOP - 0.02, 0.6, 1.25, G);
ring(0.08, 0.5, 1.15, G);
// circuit traces: two thin bands joined by stepped (dog-leg) vertical traces,
// each trace ending in a small square glowstone "solder pad" node
const TV = 0.18;
// trace band runs round the back half only, so the front of the cup stays green at 32px
{ const pts = [], y = Y0 + TV * E.h;
  for (let a = Math.PI * 0.42; a <= Math.PI * 1.58 + 1e-6; a += 0.05) pts.push(azPt(a, surfR(y, a) + 0.3, y));
  sweep(pts, 0.6, 0.6, G); }
function surfPt(phi, v, off) { const y = Y0 + v * E.h; return azPt(phi, surfR(y, phi) + off, y); }
function cube(p, h, m) { box(Math.round(p[0] - h), Math.round(p[1] - h), Math.round(p[2] - h), Math.round(p[0] + h), Math.round(p[1] + h), Math.round(p[2] + h), m); }
for (let k = 0; k < 8; k++) {
  const a = k * Math.PI / 4;
  // stepped trace stubs only on the back half (the front cup stays green at icon size)
  const d = 0.12 * (k % 2 ? 1 : -1);
  if (Math.cos(a) < 0.2) sweep([surfPt(a + d, 0.09, 0.3), surfPt(a + d, 0.13, 0.3), surfPt(a, 0.15, 0.3), surfPt(a, TV, 0.3)], 0.5, 0.5, G);
  if (Math.cos(a) < 0.2) cube(surfPt(a, TV, 0.9), 0.7, 'glowstone');
}
// girdle nodes: angular (cube) glowstone jewels set in gold bezels
for (let k = 0; k < 8; k++) {
  const a = k * Math.PI / 4 + Math.PI / 8;
  const p = surfPt(a, RIB_TOP - 0.02, 1.7);
  cube(p, 1.0, 'glowstone');
}

// ---- 5. cage arcs to the crown (never cross the emblem), with node beads
function arc(phi) {
  const pts = [];
  for (let v = RIB_TOP + 0.04; v <= 0.95; v += 0.015) {
    const y = Y0 + v * E.h;
    pts.push(azPt(phi, surfR(y, phi) + 0.7, y));
  }
  sweep(pts, 1.1, 0.9, G);
  cube(surfPt(phi, 0.62, 1.6), 0.9, 'glowstone');
}
arc(Math.PI / 2); arc(-Math.PI / 2); arc(Math.PI * 0.78); arc(-Math.PI * 0.78);

// ---- 5b. circuit fins (STAGE 2 silhouette): on each side a small swept-back
// wing shaped like a circuit board — gold frame with a stepped (staircase) lower
// edge, green board infill, gold traces and square glowstone node pads.
function finSide(sgn) {
  const phi = sgn * (Math.PI / 2 + 0.26);
  const tx = Math.cos(VIEW + phi), tz = Math.sin(VIEW + phi);   // thickness dir (horizontal tangent)
  const yA = Y0 + 0.52 * E.h;                                   // lowest edge of the fin
  const rOut = y => (y < yA + 3 ? 16.5 : y < yA + 6 ? 18.5 : y < yA + 9 ? 20.5 : 22);
  const yTop = r => yA + 13 + (r - 9) * 0.5;
  const inside = (r, y) => y >= yA && y <= yTop(r) && r <= rOut(y) && r >= surfR(y, phi) - 1.5;
  const put = (r, y, m) => {
    const p = azPt(phi, r, y);
    for (let d = -0.5; d <= 0.5; d += 0.5) block(Math.round(p[0] + d * tx), Math.round(y), Math.round(p[2] + d * tz), m);
  };
  for (let y = yA; y <= yA + 22; y += 0.5) for (let r = 6; r <= 23; r += 0.5) {
    if (!inside(r, y)) continue;
    const e = 1.4;
    const edge = !(inside(r + e, y) && inside(r - e, y) && inside(r, y + e) && inside(r, y - e) && r >= surfR(y, phi) + 0.5);
    // traces: horizontal runs + one vertical run, joined at right angles
    const trace = (Math.abs(y - (yA + 7.5)) < 0.6 && r > 14) || (Math.abs(r - 15) < 0.6 && y > yA + 7.5 && y < yA + 12) || (Math.abs(y - (yA + 12)) < 0.6 && r < 15.5);
    put(r, y, edge || trace ? G : 'green_wool');
  }
  // node pads at the outer step corners and the tip
  [[16.5, yA + 1.5], [18.5, yA + 4.5], [20.5, yA + 7.5], [22, yTop(22) - 0.5]].forEach(([r, y]) => {
    const q = azPt(phi, r + 0.3, y);
    box(Math.round(q[0] - 1), Math.round(y - 1), Math.round(q[2] - 1), Math.round(q[0] + 1), Math.round(y + 1), Math.round(q[2] + 1), 'glowstone');
  });
}
finSide(1); finSide(-1);

// ---- 6. crown: fuller cap with a jewelled band, 8 angular leaves, hexagonal finial ring
const CAP_V = 0.88;
for (let y = Math.round(Y0 + CAP_V * E.h); y <= Y1 + 1; y++) for (let x = CX - 8; x <= CX + 8; x++) for (let z = CZ - 8; z <= CZ + 8; z++)
  if (octd(x - CX, z - CZ) <= Math.max(0, prof(y)) + 1.2 && (y - Y0) / E.h >= CAP_V) block(x, y, z, G);
ring(CAP_V, 1.0, 1.15, G);
for (let k = 0; k < 8; k++) {
  const phi = k * Math.PI / 4 + Math.PI / 8;
  const front = Math.cos(phi) > 0.5;
  const L = front ? 0.04 : 0.12;
  // angular leaf: straight out, straight down (a chevron, not a curve)
  const pts = [surfPt(phi, CAP_V, 0.6), surfPt(phi, CAP_V - L * 0.5, 2.2), surfPt(phi, CAP_V - L, 0.6)];
  sweep(pts, 1.1, 0.6, G);
}
for (let k = 0; k < 4; k++) cube(surfPt(k * Math.PI / 2 + Math.PI / 4, CAP_V, 2.0), 1.0, 'glowstone');
// finial: short gold stem, flat hexagon ring facing the camera, glowstone core
function fin(u, y, w, m) {
  const x = CX + u * Math.cos(VIEW) - w * Math.sin(VIEW);
  const z = CZ + u * Math.sin(VIEW) + w * Math.cos(VIEW);
  block(Math.round(x), Math.round(y), Math.round(z), m);
}
ball(CX, Y1 + 1.0, CZ, 1.6, G);
const HC = Y1 + 5.4, HR = 3.9;          // hexagon centre / outer apothem
function hexd(u, dy) { // hexagon distance (pointy top)
  let m = 0;
  for (let k = 0; k < 3; k++) { const t = k * Math.PI / 3; m = Math.max(m, Math.abs(u * Math.cos(t) + dy * Math.sin(t))); }
  return m;
}
for (let dy = -6; dy <= 6; dy += 0.5) for (let u = -6; u <= 6; u += 0.5) for (let w = -1.2; w <= 1.2; w += 0.4) {
  const h = hexd(u, dy);
  if (h <= HR && h >= HR - 1.5) fin(u, HC + dy, w, G);
  else if (h <= 1.8) fin(u, HC + dy, w, 'glowstone');
}
for (let y = Y1 + 1; y <= HC - HR + 0.5; y++) box(CX - 1, y, CZ - 1, CX, y, CZ, G);
