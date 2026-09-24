/* ===================================================================
 * WINX ELEMENT SEED — shared family model (voxel.exec source)
 * Only the ELEMENT block below changes between elements; everything
 * under "FAMILY" is identical for all 9 seeds so the icons match.
 * STAGE 2: same egg + emblem, richer cradle (4 curled ribs, staff-line
 * bands on the cup, jewelled girdle, fuller crown with a gold quaver finial).
 * =================================================================== */

/* ---------------- ELEMENT (per-element config) ---------------- */
const EL = {
  name: 'music',
  lower: 'red_wool',       // crystal colour, bottom half
  upper: 'purple_wool',    // crystal colour, top half (a dithered band blends them)
  core: 'glowstone',       // emblem core
  rim: 'yellow_wool',      // emblem back/side layers (gives the emblem visible 3D sides)
  // Emblem mask in the emblem plane. u = horizontal (screen-right at the icon
  // camera), v = height as fraction of the egg (0 bottom .. 1 tip).
  // Returns true when (u,v) is inside the emblem. Units of u: blocks.
  // Emblem mask in the emblem plane (faces the icon camera).
  // u = blocks to the right of the egg axis (screen-right), y = world height,
  // E = egg params (y0, h, R). Return true inside the emblem.
  // Keep strokes >= 3 blocks thick: 2 blocks ~= 1 px at 32x32.
  emblem: function (u, y, E) {
    const v = (y - E.y0) / E.h;
    // Scale reference: at 32x32 one icon pixel ~= 2.2 blocks.
    // note head: tilted ellipse (~4 px wide at 32x32)
    const hx = -3.0, hy = 0.44, a = 4.7, b = 3.4, ang = -0.42;
    const px = u - hx, py = (v - hy) * E.h;
    const ca = Math.cos(ang), sa = Math.sin(ang);
    const qx = px * ca - py * sa, qy = px * sa + py * ca;
    if ((qx * qx) / (a * a) + (qy * qy) / (b * b) <= 1) return true;
    // stem, 3 blocks wide, rising from the right side of the head
    const top = 0.80;
    if (u >= -0.3 && u <= 2.6 && v >= hy && v <= top) return true;
    // flag: thin hook (~1 px) from the stem top, out right and down, ~2 px gap to the stem
    for (let i = 0; i <= 40; i++) {
      const t = i / 40, it = 1 - t;
      const bx = it * it * 2.2 + 2 * it * t * 7.8 + t * t * 8.4;
      const bv = it * it * (top - 0.02) + 2 * it * t * (top - 0.05) + t * t * 0.60;
      const r = 1.35 - 0.3 * t;
      const dx = u - bx, dy = (v - bv) * E.h;
      if (dx * dx + dy * dy <= r * r) return true;
    }
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

// ---- 3. cradle ribs with clef-like volutes (STAGE 2: four curled ribs)
// Two big clef curls at the silhouette sides, two smaller curls at the back
// diagonals, and short front-diagonal ribs that stop under the girdle.
const RIB_TOP = 0.34;
function rib(phi, s0, turns, jewel, rTop) {
  const pts = [];
  const top = rTop || RIB_TOP;
  for (let v = 0.0; v <= top + 1e-6; v += 0.02) {
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
// lower counter-scrolls on the side ribs (S-scroll look): small curl hanging out at cup height
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
rib(-Math.PI * 0.78, 3.4, 0.9, 1.0);

// ---- 4. rings: jewelled girdle, low collar, and a musical staff across the cup
function ring(v, off, r, m) {
  const pts = [];
  const y = Y0 + v * E.h;
  for (let a = 0; a <= 2 * Math.PI + 0.05; a += 0.05) pts.push(azPt(a, surfR(y, a) + off, y));
  sweep(pts, r, r, m);
}
ring(RIB_TOP - 0.02, 0.6, 1.4, G);
ring(0.08, 0.5, 1.15, G);
// jewels on the girdle
for (let k = 0; k < 8; k++) {
  const a = k * Math.PI / 4 + Math.PI / 8;
  const y = Y0 + (RIB_TOP - 0.02) * E.h;
  const p = azPt(a, surfR(y, a) + 1.7, y);
  ball(p[0], p[1], p[2], 1.6, 'glowstone');
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


// ---- 5b. STAGE 2 wings: a pair of gold "note-flag" fins sweeping up and back
// from the egg's sides — three stacked quaver-flag feathers, glowing tips.
function feather(phi, vRoot, vTip, out, r0, r1) {
  const yR = Y0 + vRoot * E.h, yT = Y0 + vTip * E.h;
  const base = surfR(yR, phi);
  const rhoR = base + 0.4, rhoT = base + out;
  const cRho = rhoR + out * 0.9, cY = yR + (yT - yR) * 0.2;   // bulge outward first, then sweep up
  const pts = [], sb = Math.sign(phi);
  for (let t = 0; t <= 1.0001; t += 0.04) {
    const it = 1 - t;
    const rho = it * it * rhoR + 2 * it * t * cRho + t * t * rhoT;
    const y = it * it * yR + 2 * it * t * cY + t * t * yT;
    pts.push(azPt(phi + t * 0.3 * sb, rho, y));                 // swept back as it rises
  }
  // quaver-flag hook: the tip flicks outward and down
  for (let t = 0.1; t <= 1.0001; t += 0.1)
    pts.push(azPt(phi + 0.3 * sb, rhoT + 3.8 * Math.sin(t * Math.PI / 2), yT + 1.0 * Math.sin(t * Math.PI) - 5.0 * t * t));
  sweep(pts, r0, r1, G);
  const tip = pts[pts.length - 1];
  ball(tip[0], tip[1], tip[2], 1.4, 'glowstone');
}
for (const sg of [1, -1]) {
  const phi = sg * Math.PI / 2;
  feather(phi, 0.58, 0.97, 5.5, 2.2, 1.3);
  feather(phi, 0.46, 0.74, 8.0, 2.0, 1.2);
}

// ---- 6. crown: fuller gold cap with a jewelled band, 8 leaves (front ones short), quaver finial
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
for (let k = 0; k < 4; k++) {
  const a = k * Math.PI / 2 + Math.PI / 4;
  const y = Y0 + CAP_V * E.h;
  const p = azPt(a, surfR(y, a) + 2.0, y);
  ball(p[0], p[1], p[2], 1.3, 'glowstone');
}
// finial: small gold quaver on the tip, glowing head, facing the camera
function fin(u, y, w, m) {
  const x = CX + u * Math.cos(VIEW) - w * Math.sin(VIEW);
  const z = CZ + u * Math.sin(VIEW) + w * Math.cos(VIEW);
  block(Math.round(x), Math.round(y), Math.round(z), m);
}
ball(CX, Y1 + 1.0, CZ, 1.6, G);
const FB = Y1 + 2;                     // finial base
for (let dy = 0; dy <= 9; dy++) for (let u = -5; u <= 5; u += 0.5) for (let w = -1.5; w <= 1.5; w += 0.5) {
  const hx = u + 0.9, hy = dy - 1.6, ca = Math.cos(-0.4), sa = Math.sin(-0.4);
  const qx = hx * ca - hy * sa, qy = hx * sa + hy * ca;
  if ((qx * qx) / 5.3 + (qy * qy) / 2.6 <= 1 && Math.abs(w) <= 1.3) fin(u, FB + dy, w, 'glowstone');
  else if (u >= 0.6 && u <= 1.9 && dy >= 1 && dy <= 9 && Math.abs(w) <= 0.8) fin(u, FB + dy, w, G);
}
for (let t = 0; t <= 1.0001; t += 0.04) {
  const u = 1.9 + 3.0 * t, yy = FB + 9 - 1.2 * t - 2.8 * t * t;
  for (let w = -0.8; w <= 0.8; w += 0.4) { fin(u, yy, w, G); fin(u, yy - 0.8, w, G); }
}
