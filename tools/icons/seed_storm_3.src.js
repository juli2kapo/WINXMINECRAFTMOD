/* ===================================================================
 * WINX ELEMENT SEED — shared family model (voxel.exec source)
 * Only the ELEMENT block below changes between elements; everything
 * under "FAMILY" is identical for all 9 seeds so the icons match.
 * STAGE 3 (final form): stage-2 cradle + large forked lightning wings swept
 * up and back framing the egg (glowstone cores + glowing bolt tips), a tall
 * crackling crown of bolt spires and a big glowing bolt finial.
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

/* ---------------- STAGE 3: forked lightning wings + crackling crown ---------------- */
// zig-zag path helper in the (rho, y) half-plane; azimuth may drift (sweep back) along the path
function boltPath(phi, P, r0, r1, m) { sweep(P.map(([rho, y, dphi]) => azPt(phi + (dphi || 0), rho, y)), r0, r1, m); }
const sv = (v) => Y0 + v * E.h;
// Each wing: one big solid lightning-bolt blade (glowstone core, gold rim) rising
// up & out from the egg's side and swept back, plus a slimmer forked bolt above it.
// Blade outline in (rho = blocks from axis, v = egg-height fraction):
// = the emblem's bolt, flipped to point up, scaled x1.1 and leaned 4deg upward
// about its inner jog so the glowing tip shoots up and away from the egg.
const BOLT_UV = [[1.5, 0], [-5, 0], [-2, 7], [-8.5, 7], [5.5, 19.5], [1.5, 11], [6.5, 11]];
const LEAN = -4 * Math.PI / 180, BS = 1.1;
const BLADE = BOLT_UV.map(([u, s]) => {
  const a = (u + 8.5) * BS, b = (s - 7) * BS;
  const ro = a * Math.cos(LEAN) + b * Math.sin(LEAN), up = -a * Math.sin(LEAN) + b * Math.cos(LEAN);
  return [14.5 + ro, 0.58 + up / E.h];
});
const BACK = (rho) => 0.30;                                     // flat blade, rotated back a little
function inPoly(P, px, py) {
  let ins = false;
  for (let i = 0, j = P.length - 1; i < P.length; j = i++) {
    const [xi, yi] = P[i], [xj, yj] = P[j];
    if ((yi > py) !== (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi) ins = !ins;
  }
  return ins;
}
function wing(sg) {
  const phi = sg * Math.PI / 2;
  const PY = BLADE.map(([r, v]) => [r, sv(v)]);
  const inset = (r, y) => { // inside & at least ~1.6 blocks from the outline
    for (let k = 0; k < 8; k++) { const a = k * Math.PI / 4; if (!inPoly(PY, r + 1.6 * Math.cos(a), y + 1.6 * Math.sin(a))) return false; }
    return true;
  };
  for (let x = CX - 38; x <= CX + 38; x++) for (let z = CZ - 38; z <= CZ + 38; z++) {
    const dx = x - CX, dz = z - CZ, rho = Math.hypot(dx, dz);
    if (rho < 14 || rho > 36) continue;
    // azimuth relative to camera, same convention as azPt
    const az = Math.atan2(dx, -dz) - VIEW;
    let d = az - (phi + sg * BACK(rho)); d = Math.atan2(Math.sin(d), Math.cos(d));
    const t = d * rho;                                          // tangential offset, blocks
    if (Math.abs(t) > 1.6) continue;
    for (let y = Math.floor(sv(0.2)); y <= Math.ceil(sv(1.2)); y++) {
      if (!inPoly(PY, rho, y)) continue;
      block(x, y, z, inset(rho, y) ? 'glowstone' : G);
    }
  }
  const tip = azPt(phi + sg * BACK(BLADE[4][0]), BLADE[4][0], sv(BLADE[4][1]));
  ball(tip[0], tip[1], tip[2], 1.9, 'glowstone');               // glowing bolt tip
  // slimmer forked bolt above the blade
  const U = [[15.0, 0.64], [19.5, 0.86], [16.0, 0.88], [20.5, 1.12]].map(([r, v]) => azPt(phi + sg * (BACK(r) + 0.35), r, sv(v)));
  sweep(U, 2.2, 0.6, G);
  ball(U[3][0], U[3][1], U[3][2], 1.7, 'glowstone');
}
wing(1); wing(-1);

// tall crown: taller gold cap band + crackling zig-zag bolt spires tipped with glowstone
const CAP_V = 0.86;
for (let y = Math.round(Y0 + CAP_V * E.h); y <= Y1 + 1; y++) for (let x = CX - 8; x <= CX + 8; x++) for (let z = CZ - 8; z <= CZ + 8; z++)
  if (octd(x - CX, z - CZ) <= Math.max(0, prof(y)) + 1.3 && (y - Y0) / E.h >= CAP_V) block(x, y, z, G);
function spire(phi, h, out) {
  const y0 = Y0 + CAP_V * E.h, r = surfR(y0, phi) + 0.6;
  const P = [[r, y0], [r + out * 0.6, y0 + h * 0.45], [r + out * 0.1, y0 + h * 0.5], [r + out, y0 + h]];
  boltPath(phi, P, 1.6, 0.6, G);
  const t = azPt(phi, r + out, y0 + h);
  ball(t[0], t[1] + 0.6, t[2], 1.4, 'glowstone');
}
spire(Math.PI / 2, 11, 4); spire(-Math.PI / 2, 11, 4);
spire(Math.PI * 0.75, 10, 3); spire(-Math.PI * 0.75, 10, 3);
spire(Math.PI, 11, 2);
// finial: big glowstone lightning bolt standing on the tip, on a gold knot
ball(CX, Y1 + 1.5, CZ, 2.0, G);
sweep([[CX, Y1 + 2, CZ], [CX + 2.0, Y1 + 6, CZ - 1.1], [CX - 1.6, Y1 + 6.5, CZ + 0.9], [CX + 1.0, Y1 + 10, CZ - 0.6]], 2.2, 0.7, 'glowstone');
