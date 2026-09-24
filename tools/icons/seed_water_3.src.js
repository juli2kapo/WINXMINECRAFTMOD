/* ===================================================================
 * WINX ELEMENT SEED — shared family model (voxel.exec source)
 * Only the ELEMENT block below changes between elements; everything
 * under "FAMILY" is identical for all 9 seeds so the icons match.
 * STAGE 3 (final form): stage-2 cradle + two great breaking-wave wings
 * (gold rim, blue/sea body, white foam crest curling in over the egg,
 * glowing eyes) swept up and back, and a tall fountain spire crowned by a
 * big glowing droplet.
 * =================================================================== */

/* ---------------- ELEMENT (per-element config) ---------------- */
const EL = {
  name: 'water',
  lower: 'blue_wool',      // deep ocean blue, bottom half
  upper: 'water',          // lighter sea blue, top half
  core: 'glowstone',       // droplet face: glowing light core
  rim: 'white_wool',       // droplet sides: white edge
  // Water droplet: round belly (r 6.4) + tapering pointed tip, ~13 blocks
  // wide, v 0.40..0.85 (a cut-out shine slot was tried: reads as a dot at 32px).
  // u = blocks right of the egg axis (screen-right), y = world height, E = egg.
  emblem: function (u, y, E) {
    const v = (y - E.y0) / E.h;
    const R = 6.4, vc = 0.535, L = 14.5, cu = 0.3;
    const x = u - cu, h = (v - vc) * E.h;
    let inside = x * x + h * h <= R * R;
    if (!inside && h > 0 && h < L) inside = Math.abs(x) <= R * Math.pow(1 - h / L, 1.25);
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

// ---- 3. cradle ribs with wave-curl volutes (STAGE 2: four curled ribs, jewelled eyes)
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
// lower wave curls on the side ribs: a breaking-wave scroll hanging out at cup height
function scroll(phi, v0, s0) {
  const y0 = Y0 + v0 * E.h, rho0 = surfR(y0, phi) + 0.9;
  const pts = [], cr = rho0 + s0;
  for (let th = Math.PI; th <= Math.PI + 1.7 * Math.PI; th += 0.08) {
    const f = (th - Math.PI) / (1.7 * Math.PI), rad = s0 * (1 - 0.6 * f);
    pts.push(azPt(phi, cr + rad * Math.cos(th), y0 + rad * Math.sin(th) * 1.1));
  }
  sweep(pts, 1.3, 0.85, G);
  const c = azPt(phi, cr, y0); ball(c[0], c[1], c[2], 0.9, 'glowstone');
}
scroll(Math.PI / 2, 0.17, 2.8); scroll(-Math.PI / 2, 0.17, 2.8);

// ---- 4. rings: girdle with droplet jewels, collar
function ring(v, off, r, m) {
  const pts = [];
  const y = Y0 + v * E.h;
  for (let a = 0; a <= 2 * Math.PI + 0.05; a += 0.05) pts.push(azPt(a, surfR(y, a) + off, y));
  sweep(pts, r, r, m);
}
ring(RIB_TOP - 0.02, 0.6, 1.4, G);
ring(0.08, 0.5, 1.15, G);
function surfPt(phi, v, off) { const y = Y0 + v * E.h; return azPt(phi, surfR(y, phi) + off, y); }
// droplet jewel: glowing round belly with a point on top, set in a gold bezel
function droplet(p, r, m) {
  ball(p[0], p[1], p[2], r, m);
  for (let t = 0; t <= 1.0001; t += 0.1) ball(p[0], p[1] + r * 0.4 + t * r * 1.5, p[2], r * (1 - t) * 0.85, m);
}
for (let k = 0; k < 8; k++) {
  const a = k * Math.PI / 4 + Math.PI / 8;
  droplet(surfPt(a, RIB_TOP - 0.02, 1.7), 1.35, 'glowstone');
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

// ---- 6. crown: fuller cap with a jewelled band, 8 wave-crest leaves, glowing droplet finial
const CAP_V = 0.88;
for (let y = Math.round(Y0 + CAP_V * E.h); y <= Y1 + 1; y++) for (let x = CX - 8; x <= CX + 8; x++) for (let z = CZ - 8; z <= CZ + 8; z++)
  if (octd(x - CX, z - CZ) <= Math.max(0, prof(y)) + 1.2 && (y - Y0) / E.h >= CAP_V) block(x, y, z, G);
ring(CAP_V, 1.0, 1.15, G);
for (let k = 0; k < 8; k++) {
  const phi = k * Math.PI / 4 + Math.PI / 8;
  const front = Math.cos(phi) > 0.5;               // leaves over the emblem window stay short
  const L = front ? 0.04 : 0.12;
  const pts = [];
  for (let t = 0; t <= 1.0001; t += 0.1) {
    const y = Y0 + (CAP_V - L * t) * E.h;
    pts.push(azPt(phi, surfR(y, phi) + 0.6 + 1.4 * Math.sin(t * Math.PI), y));
  }
  sweep(pts, 1.1, 0.6, G);
}
for (let k = 0; k < 4; k++) droplet(surfPt(k * Math.PI / 2 + Math.PI / 4, CAP_V, 2.0), 1.2, 'glowstone');
// ---- 7. STAGE 3 crown: a fountain spire. Tall tapering gold spire with
// a knop and a jewelled bezel holding a big glowing droplet.
for (let y = Y1 + 1; y <= Y1 + 9; y++) {
  const r = 2.6 - 1.4 * (y - Y1 - 1) / 8;
  for (let x = CX - 4; x <= CX + 4; x++) for (let z = CZ - 4; z <= CZ + 4; z++)
    if (octd(x - CX, z - CZ) <= r + 0.3) block(x, y, z, G);
}
ball(CX, Y1 + 4, CZ, 2.4, G);                                  // knop on the spire
for (let x = CX - 6; x <= CX + 6; x++) for (let z = CZ - 6; z <= CZ + 6; z++) {
  const d = Math.hypot(x - CX, z - CZ);
  if (d <= 3.6) block(x, Y1 + 9, z, G);
  if (d <= 4.4 && d >= 3.0) block(x, Y1 + 10, z, G);        // bezel lip
}
[0, 1, 2, 3].forEach(k => { const p = azPt(k * Math.PI / 2 + Math.PI / 4, 4.2, Y1 + 9.5); ball(p[0], p[1], p[2], 0.9, 'glowstone'); });
const DC = Y1 + 13.0, DR = 3.8, DL = 8.5;
for (let y = Y1 + 9; y <= Y1 + 24; y++) for (let x = CX - 5; x <= CX + 5; x++) for (let z = CZ - 5; z <= CZ + 5; z++) {
  const d = Math.hypot(x - CX, z - CZ), h = y - DC;
  let inside = d * d + h * h <= DR * DR + 0.3;
  if (!inside && h > 0 && h < DL) inside = d <= DR * Math.pow(1 - h / DL, 1.2) + 0.3;
  if (inside) block(x, y, z, 'glowstone');
}

const WCX = 19, WCY = 55.5, WR = 7.2, WTOP = 52, WSW = 0.45;
// ---- 8. STAGE 3 silhouette: great breaking-wave wings. Each wing is a filled
// wave in the vertical plane at azimuth phi (face-on to the camera, swept a
// little back): the body rises from the girdle, sweeps up past the egg's
// shoulder and breaks outward into a curling lip. Gold rim, blue/sea interior,
// white foam at the crest and inside the curl, glowing eye and rim pearls.
function stroke(P, r0, r1) {
  const L = [0]; for (let i = 1; i < P.length; i++) L.push(L[i - 1] + Math.hypot(P[i][0] - P[i - 1][0], P[i][1] - P[i - 1][1]));
  return { P, L, r0, r1 };
}
function inStroke(p, s, y) {
  const T = p.L[p.L.length - 1];
  for (let i = 0; i < p.P.length; i++) { const r = p.r0 + (p.r1 - p.r0) * p.L[i] / T; if ((s - p.P[i][0]) ** 2 + (y - p.P[i][1]) ** 2 <= r * r) return true; }
  return false;
}
function waveStroke(sx, sy, cx, cy, R, turns, r0, r1) {
  const P = [], top = [cx - R * 0.3, cy + R], c = [sx - 1, cy + R * 0.2];
  for (let t = 0; t <= 1; t += 0.03) { const u = 1 - t; P.push([u * u * sx + 2 * u * t * c[0] + t * t * top[0], u * u * sy + 2 * u * t * c[1] + t * t * top[1]]); }
  const a0 = Math.atan2(top[1] - cy, top[0] - cx), R0 = Math.hypot(top[0] - cx, top[1] - cy);
  for (let a = a0; a >= a0 - turns * 2 * Math.PI; a -= 0.05) {
    const f = (a0 - a) / (turns * 2 * Math.PI);
    P.push([cx + R0 * (1 - 0.55 * f) * Math.cos(a), cy + R0 * (1 - 0.55 * f) * Math.sin(a)]);
  }
  return stroke(P, r0, r1);
}
// body: a crescent between an inner and an outer edge, rising from the girdle
function sIn(y) { return 12.5 + 3.5 * Math.pow(Math.max(0, y - 16) / 38, 1.6); }
function sOut(y) { return 16.5 + 12.5 * Math.sqrt(Math.max(0, y - 13) / 41); }
// lip: the crest breaks over INWARDS (towards the crown) around a glowing eye
const LIP = (() => {
  const P = [], cx = WCX, cy = WCY;
  for (let a = -0.35; a <= -0.35 + 1.1 * 2 * Math.PI; a += 0.05) {
    const f = (a + 0.35) / (1.1 * 2 * Math.PI), R = WR * (1 - 0.55 * f);
    P.push([cx + R * Math.cos(a), cy + R * Math.sin(a)]);
  }
  return stroke(P, 3.0, 1.2);
})();
function wmask(s, y) {
  if (y >= 14 && y <= WTOP + (s - WCX) * 0.95 && s >= sIn(y) && s <= sOut(y)) return 1;
  if (inStroke(LIP, s, y)) return 2;
  return 0;
}
const WMAP = new Map();
function wingSample(s, y) {
  const k = s * 2 + ',' + y * 2;
  if (!WMAP.has(k)) WMAP.set(k, wmask(s, y));
  return WMAP.get(k);
}
function wing(phi) {
  const tx = Math.cos(VIEW + phi), tz = Math.sin(VIEW + phi);
  for (let y = 10; y <= 72; y += 0.5) for (let s = 6; s <= 34; s += 0.5) {
    const k = wingSample(s, y);
    if (!k) continue;
    let edge = false;
    for (const [ds, dy] of [[1.5, 0], [-1.5, 0], [0, 1.5], [0, -1.5], [1, 1], [-1, 1], [1, -1], [-1, -1]])
      if (!wingSample(s + ds, y + dy)) { edge = true; break; }
    let m;
    if (edge) m = G;
    else if (k === 2 || y > WTOP + (s - WCX) * 0.95 - 4) m = 'white_wool';        // foam on the breaking crest
    else if (((y + Math.round(s * 0.6)) % 9) === 0) m = 'white_wool'; // foam streaks
    else if (y > 32) m = 'water';
    else m = 'blue_wool';
    const q = azPt(phi, s, y);
    const th = edge ? 1.0 : 0.5;
    for (let w = -th; w <= th; w += 0.5) block(Math.round(q[0] + tx * w), Math.round(y), Math.round(q[2] + tz * w), m);
  }
  let q = azPt(phi, WCX, WCY); ball(q[0], q[1], q[2], 1.6, 'glowstone');       // glowing eye of the curl
  [30, 39, 47].forEach(y => { const p = azPt(phi, sOut(y) + 0.3, y); ball(p[0], p[1], p[2], 1.3, 'glowstone'); }); // glowing pearls on the wave's outer rim
}
wing(Math.PI / 2 + WSW); wing(-Math.PI / 2 - WSW);
