/* ===================================================================
 * WINX ELEMENT SEED — shared family model (voxel.exec source)
 * Only the ELEMENT block below changes between elements; everything
 * under "FAMILY" is identical for all 9 seeds so the icons match.
 * Built by tools/icons/build_seed.js -> seed_<element>.json
 * =================================================================== */

/* ---------------- ELEMENT (per-element config) ---------------- */
// SUN AND MOON (Stella): warm day below the girdle, deep night sky behind the
// emblem; a glowing sun disc half-wrapped by a pale crescent moon.
// The emblem uses two materials: emblem() records which part (sun / moon) was
// hit, and the core/rim getters return that part's material.
const EL = {
  name: 'sunandmoon',
  lower: 'orange_wool',    // crystal colour, bottom half (day)
  upper: 'blue_wool',      // crystal colour, top half (night sky behind the emblem)
  _part: 'sun',
  get core() { return { sun: 'glowstone', corona: 'yellow_wool', moon: 'white_wool' }[this._part]; },
  get rim() { return this._part === 'moon' ? 'iron_block' : 'gold_block'; },
  // u = blocks to the right of the egg axis (screen-right), y = world height,
  // E = egg params (y0, h, R). Return true inside the emblem.
  // At 32x32 one icon pixel ~= 2.2 blocks: strokes >= 3 blocks, gaps >= ~3-4.
  emblem: function (u, y, E) {
    const v = (y - E.y0) / E.h;
    const cu = SUN_U, cy = SUN_V * E.h;            // sun centre (u, height above egg bottom)
    const du = u - cu, dy = v * E.h - cy;
    // sun disc
    const d2 = du * du + dy * dy;
    if (d2 <= SUN_R * SUN_R) { this._part = d2 <= SUN_IN * SUN_IN ? 'sun' : 'corona'; return true; }
    // crescent: inside the outer moon circle, outside the inner cut circle
    const ou = du - MOON_OX, iu = du - MOON_IX;
    const iny = dy - MOON_IY;
    if (ou * ou + dy * dy <= MOON_RO * MOON_RO && iu * iu + iny * iny > MOON_RI * MOON_RI) { this._part = 'moon'; return true; }
    return false;
  },
};
const SUN_U = 2.6, SUN_V = 0.575, SUN_R = 4.8, SUN_IN = 99; // sun disc: solid glowstone (SUN_IN < SUN_R would add a yellow corona ring)
const MOON_OX = -2.4, MOON_RO = 9.4;               // outer moon circle (offset left of the sun)
const MOON_IX = 0.9, MOON_IY = 0.6, MOON_RI = 7.9; // inner cut circle

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

// ---- 3. cradle ribs with volutes (STAGE 2: four curled ribs, jewelled curl eyes)
// Two big curls at the silhouette sides, two smaller curls at the back diagonals;
// nothing crosses the emblem window at the front.
const RIB_TOP = 0.34; // ribs hug the egg up to here, then curl outward
function rib(phi, s0, turns, jewel) {
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
  ball(last[0], last[1], last[2], 1.9, G);             // bead where the rib leaves the egg
  if (jewel) { const c = azPt(phi, cr, yc); ball(c[0], c[1], c[2], jewel, 'glowstone'); } // jewel in the curl's eye
}
rib(Math.PI / 2, 5.2, 1.05, 1.6);
rib(-Math.PI / 2, 5.2, 1.05, 1.6);
rib(Math.PI * 0.78, 3.4, 0.9, 1.0);
rib(-Math.PI * 0.78, 3.4, 0.9, 1.0);

// ---- 4. rings: jewelled girdle at the top of the cup + a low collar
function ring(v, off, r, m) {
  const pts = [];
  const y = Y0 + v * E.h;
  for (let a = 0; a <= 2 * Math.PI + 0.05; a += 0.05) pts.push(azPt(a, surfR(y, a) + off, y));
  sweep(pts, r, r, m);
}
ring(RIB_TOP - 0.02, 0.6, 1.4, G);
ring(0.08, 0.5, 1.15, G);
// star jewels on the girdle: a glowstone bead with four tiny gold points
for (let k = 0; k < 8; k++) {
  const a = k * Math.PI / 4 + Math.PI / 8;
  const y = Y0 + (RIB_TOP - 0.02) * E.h;
  const p = azPt(a, surfR(y, a) + 1.8, y);
  ball(p[0], p[1], p[2], 1.5, 'glowstone');
}

// ---- 5. cage arcs to the crown (never cross the emblem)
function arc(phi) {
  const pts = [];
  for (let v = RIB_TOP + 0.04; v <= 0.95; v += 0.015) {
    const y = Y0 + v * E.h;
    pts.push(azPt(phi, surfR(y, phi) + 0.7, y));
  }
  sweep(pts, 1.1, 0.9, G);
}
arc(Math.PI / 2); arc(-Math.PI / 2); arc(Math.PI * 0.78); arc(-Math.PI * 0.78);

// ---- 6. crown: fuller gold cap, sun-ray spikes around a jewelled band,
//         crescent-moon finial cradling a small glowing sun
const CAP_V = 0.87;
for (let y = Math.round(Y0 + CAP_V * E.h); y <= Y1 + 1; y++) for (let x = CX - 8; x <= CX + 8; x++) for (let z = CZ - 8; z <= CZ + 8; z++)
  if (octd(x - CX, z - CZ) <= Math.max(0, prof(y)) + 1.2 && (y - Y0) / E.h >= CAP_V) block(x, y, z, G);
ring(CAP_V, 1.0, 1.15, G);
// sun rays: tapered spikes radiating out and up from the cap band
const RAYS = 12;
for (let k = 0; k < RAYS; k++) {
  const phi = k * 2 * Math.PI / RAYS;
  const front = Math.cos(phi) > 0.6;                 // rays over the emblem window stay short
  const yb = Y0 + CAP_V * E.h;
  const b = azPt(phi, surfR(yb, phi) + 1.2, yb);
  const L = front ? 2.5 : (k % 2 ? 4.0 : 5.5);      // alternating long / short rays
  const t = azPt(phi, surfR(yb, phi) + 1.2 + L * 0.75, yb + L * 0.75);
  sweep([b, t], 1.2, 0.35, G);
}
// glowing jewels between the rays
for (let k = 0; k < 4; k++) {
  const a = k * Math.PI / 2 + Math.PI / 4;
  const y = Y0 + CAP_V * E.h;
  const p = azPt(a, surfR(y, a) + 2.0, y);
  ball(p[0], p[1], p[2], 1.3, 'glowstone');
}
// finial: gold crescent moon facing the camera, opening to the right, holding a glowing sun
function fin(u, y, w, m) {
  const x = CX + u * Math.cos(VIEW) - w * Math.sin(VIEW);
  const z = CZ + u * Math.sin(VIEW) + w * Math.cos(VIEW);
  block(Math.round(x), Math.round(y), Math.round(z), m);
}
ball(CX, Y1 + 1.0, CZ, 1.8, G);
const FB = Y1 + 2, FR = 4.2, FCY = FB + FR;          // crescent: outer circle centre / radius
for (let dy = 0; dy <= 2 * FR + 1; dy += 0.5) for (let u = -FR - 1; u <= FR + 1; u += 0.5) for (let w = -1.5; w <= 1.5; w += 0.5) {
  const yy = FB + dy, du = u, dv = yy - FCY;
  const inO = du * du + dv * dv <= FR * FR;
  const ci = du - 2.0, cv = dv - 0.5;
  const inI = ci * ci + cv * cv <= 3.1 * 3.1;
  if (inO && !inI && Math.abs(w) <= 1.2) fin(u, yy, w, G);
}
ball(CX + 1.9 * Math.cos(VIEW), FCY + 0.3, CZ + 1.9 * Math.sin(VIEW), 1.7, 'glowstone');

// ---- 7. STAGE 2 wings: a sunburst fan on each side of the egg, set in the
//         camera-facing plane just behind the egg axis so the rays read broad-on,
//         swept upward; long gold rays alternate with short glowstone-tipped ones.
function uvw(u, y, w) { return [CX + u * Math.cos(VIEW) - w * Math.sin(VIEW), y, CZ + u * Math.sin(VIEW) + w * Math.cos(VIEW)]; }
function wing(side) {
  const vh = 0.55, yh = Y0 + vh * E.h;
  const uh = side * (prof(yh) - 1.5), wh = 3.0;     // hub tucked against the egg side, behind the emblem slab
  const hub = uvw(uh, yh, wh);
  const N = 4;
  for (let i = 0; i < N; i++) {
    const el = (8 + i * 25) * Math.PI / 180;          // 8..83 deg above horizontal
    const long = i === 1 || i === 2;
    const L = long ? 13.0 : 9.0;
    const pts = [];
    for (let t = 0; t <= 1.0001; t += 0.1) {
      const d = L * t;
      pts.push(uvw(uh + side * d * Math.cos(el), yh + d * Math.sin(el), wh + 1.5 * t));
    }
    sweep(pts, long ? 2.4 : 2.0, 0.6, G);
    if (!long) { const p = pts[pts.length - 1]; ball(p[0], p[1], p[2], 1.2, 'glowstone'); }
  }
  ball(hub[0], hub[1], hub[2], 2.6, G);
  const j = uvw(uh + side * 1.5, yh + 1.2, wh - 1.5); ball(j[0], j[1], j[2], 1.4, 'glowstone');
}
wing(1); wing(-1);
