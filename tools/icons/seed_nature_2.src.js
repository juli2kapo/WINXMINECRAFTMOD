/* ===================================================================
 * WINX ELEMENT SEED — shared family model (voxel.exec source)
 * Only the ELEMENT block below changes between elements; everything
 * under "FAMILY" is identical for all 9 seeds so the icons match.
 * STAGE 2: same egg + emblem; a pair of leaf wings (broad green leaves on gold
 * stems with a red blossom) sweeps up and back from the sides, and the crown
 * becomes a fuller sprout crown of green leaves around a glowing bud.
 * =================================================================== */

/* ---------------- ELEMENT (per-element config) ---------------- */
const EL = {
  name: 'nature',
  lower: 'red_wool',       // blossom red cup (Flora's flower accent)
  upper: 'white_wool',     // light blossom-white top so the green leaf pops
  // Two-material emblem: emblem() records which part was hit in EL._part,
  // core/rim then return that part's material.
  _part: 'leaf',
  get core() { return this._part === 'vein' ? 'glowstone' : 'grass_block'; }, // glowing midrib, bright green leaf face
  get rim() { return this._part === 'vein' ? 'glowstone' : 'green_wool'; },   // leaf sides / back
  // Emblem: one broad, round leaf with a glowing midrib and stem.
  // u = blocks to the right of the egg axis (screen-right), y = world height,
  // E = egg params (y0, h, R). Return true inside the emblem.
  // Keep strokes >= 3 blocks thick: 2 blocks ~= 1 px at 32x32.
  emblem: function (u, y, E) {
    const Y = y - E.y0; // height above the egg bottom, blocks
    const bx = -3.4, by = 0.42 * E.h;       // leaf base point
    const ang = 58 * Math.PI / 180, L = 18;  // axis direction and length
    const ca = Math.cos(ang), sa = Math.sin(ang);
    const px = u - bx, py = Y - by;
    const t = px * ca + py * sa, n = -px * sa + py * ca;
    if (t >= 0 && t <= L) {
      const s = t / L;
      const mid = -0.6 * Math.sin(Math.PI * s);        // slight curve
      const W = 6.6 * Math.pow(Math.sin(Math.PI * Math.pow(s, 0.8)), 0.7) + 0.3; // broad, round
      const d = n - mid;
      if (Math.abs(d) <= W) {
        this._part = (s < 0.86 && Math.abs(d) <= 1.1) ? 'vein' : 'leaf';
        return true;
      }
    }
    // stem: glowing, continues the midrib down and slightly left
    for (let i = 0; i <= 20; i++) {
      const k = i / 20;
      const sx = bx + 0.8 - 2.4 * k - 0.8 * k * k, sy = by + 1.5 - 5.5 * k;
      const dx = u - sx, dy = Y - sy;
      if (dx * dx + dy * dy <= 1.6 * 1.6) { this._part = 'vein'; return true; }
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

// ---- 6. crown (STAGE 2): gold cap with a band, a ring of green sprout leaves, glowing bud
const CAP_V = 0.88;
for (let y = Math.round(Y0 + CAP_V * E.h); y <= Y1 + 1; y++) for (let x = CX - 8; x <= CX + 8; x++) for (let z = CZ - 8; z <= CZ + 8; z++)
  if (octd(x - CX, z - CZ) <= Math.max(0, prof(y)) + 1.2 && (y - Y0) / E.h >= CAP_V) block(x, y, z, G);
ring(CAP_V, 1.0, 1.15, G);

// generic 3D leaf: base B, axis A (unit), in-plane width dir D (unit), length L,
// half-width W, in-plane bend (towards D, at the tip), out-of-plane cup/droop.
function norm(v) { const l = Math.hypot(v[0], v[1], v[2]); return [v[0] / l, v[1] / l, v[2] / l]; }
function cross(a, b) { return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]; }
// opt.face01: 0..1 rotates the leaf about its axis so its face turns towards the camera
const CAM = norm([Math.sin(VIEW), 0.45, -Math.cos(VIEW)]);
function leaf(B, A, D, L, W, bend, droop, opt) {
  opt = opt || {};
  A = norm(A); D = norm(D);
  if (opt.face01) {
    let Dc = norm(cross(CAM, A)); if (Dc[0] * D[0] + Dc[1] * D[1] + Dc[2] * D[2] < 0) Dc = Dc.map(c => -c);
    const f = opt.face01; D = norm([D[0] * (1 - f) + Dc[0] * f, D[1] * (1 - f) + Dc[1] * f, D[2] * (1 - f) + Dc[2] * f]);
  }
  const N = norm(cross(A, D));
  const face = opt.face || 'grass_block', edge = opt.edge || 'green_wool', rib = opt.rib || G;
  const placed = new Set();
  for (let t = 0; t <= L; t += 0.35) {
    const s = t / L;
    const Wt = W * Math.pow(Math.sin(Math.PI * Math.pow(s, 0.75)), 0.8) + 0.35;
    for (let n = -Wt; n <= Wt + 1e-6; n += 0.35) {
      const off = bend * s * s * L;                      // in-plane curl of the whole leaf
      const dz = droop * s * s * L + 0.5 * (n / (W + 0.01)) ** 2; // out-of-plane: tip droop + cupped sides
      for (let k = -0.7; k <= 0.7; k += 0.35) {
        const px = B[0] + A[0] * t + D[0] * (n + off) + N[0] * (dz + k);
        const py = B[1] + A[1] * t + D[1] * (n + off) + N[1] * (dz + k);
        const pz = B[2] + A[2] * t + D[2] * (n + off) + N[2] * (dz + k);
        const x = Math.round(px), y = Math.round(py), z = Math.round(pz), key = x + ',' + y + ',' + z;
        let m = face;
        if (Math.abs(n) > Wt - 0.7) m = edge;
        if (Math.abs(n) < 0.75 && s < 0.75) m = rib;
        if (placed.has(key) && m !== rib) continue;
        placed.add(key); block(x, y, z, m);
      }
    }
  }
}
function radial(phi) { return [Math.sin(VIEW + phi), 0, -Math.cos(VIEW + phi)]; }
function tangent(phi) { return [Math.cos(VIEW + phi), 0, Math.sin(VIEW + phi)]; }
// direction in the (radial, up) plane at azimuth phi, elevation el
function dirRU(phi, el) { const r = radial(phi); return [r[0] * Math.cos(el), Math.sin(el), r[2] * Math.cos(el)]; }
// perpendicular in the same plane (rotated +90deg towards up/inward)
function perpRU(phi, el) { return dirRU(phi, el + Math.PI / 2); }

// sprout crown: leaves rising from the cap band, splaying outward
const CY = Y0 + CAP_V * E.h;
[[Math.PI / 2, 0.8, 9, 2.8], [-Math.PI / 2, 0.8, 9, 2.8], [Math.PI * 0.85, 1.0, 8, 2.6], [-Math.PI * 0.85, 1.0, 8, 2.6]].forEach(([phi, el, L, W]) => {
  const B = azPt(phi, surfR(CY, phi) + 0.2, CY + 0.5);
  leaf(B, dirRU(phi, el), perpRU(phi, el), L, W, -0.2, 0, { rib: 'green_wool', face01: 0.8 });
});
// central bud: gold calyx with a tall glowing bud
ball(CX, Y1 + 1.0, CZ, 2.0, G);
for (let y = Y1 + 2; y <= Y1 + 11; y++) for (let x = CX - 4; x <= CX + 4; x++) for (let z = CZ - 4; z <= CZ + 4; z++) {
  const h = (y - (Y1 + 2)) / 9, r = 3.0 * Math.pow(Math.sin(Math.PI * Math.min(1, 0.25 + h * 0.8)), 0.9) + 0.2;
  if (Math.hypot(x - CX, z - CZ) <= r) block(x, y, z, 'glowstone');
}
// calyx: four small gold sepals hugging the bud base
for (let k = 0; k < 4; k++) { const ph = k * Math.PI / 2 + Math.PI / 4; sweep([azPt(ph, 1.0, Y1 + 1.5), azPt(ph, 3.2, Y1 + 3.5), azPt(ph, 2.6, Y1 + 5.5)], 1.0, 0.6, G); }

// ---- 7. STAGE 2 silhouette: leaf wings. From each side rib a gold stem
// sweeps up and back; it carries a big upswept leaf, a smaller outward leaf
// and a red blossom with a glowing heart where they meet.
function wing(phi) {
  const yB = Y0 + (RIB_TOP + 0.02) * E.h;
  const J = azPt(phi, surfR(yB, phi) + 4.0, yB + 4.5);            // junction (blossom)
  const s0 = azPt(phi, surfR(yB - 1, phi) + 1.0, yB - 1);
  const s1 = azPt(phi, surfR(yB + 2, phi) + 2.0, yB + 2.5);
  sweep([s0, s1, J], 1.4, 1.1, G);
  // big leaf: up and outward, curling back in at the tip
  leaf(J, dirRU(phi, 0.95), perpRU(phi, 0.95), 16, 5.0, 0.16, 0.0, { face01: 0.85 });
  // small leaf: outward and slightly up, tip curling upward
  leaf(J, dirRU(phi, 0.05), perpRU(phi, 0.05), 10, 3.2, 0.2, 0.0, { face01: 0.85 });
  // blossom: five red petals around a glowing heart, facing the camera side
  const T = tangent(phi), R = radial(phi);
  for (let k = 0; k < 5; k++) {
    const a = k * 2 * Math.PI / 5 + 0.3;
    const d = [R[0] * Math.cos(a), Math.sin(a), R[2] * Math.cos(a)];
    ball(J[0] + d[0] * 2.3 + CAM[0] * 1.5, J[1] + d[1] * 2.3 + CAM[1] * 1.5, J[2] + d[2] * 2.3 + CAM[2] * 1.5, 1.8, 'red_wool');
  }
  ball(J[0] + CAM[0] * 2.4, J[1] + CAM[1] * 2.4, J[2] + CAM[2] * 2.4, 1.4, 'glowstone');
}
const SWEEP = 0.35; // radians swept back from the pure sides
wing(Math.PI / 2 + SWEEP); wing(-Math.PI / 2 - SWEEP);
