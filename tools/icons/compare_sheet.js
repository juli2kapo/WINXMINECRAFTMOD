#!/usr/bin/env node
/**
 * compare_sheet.js — review sheet for one seed icon.
 *   node compare_sheet.js <current.png> <new32.png> <new64.png> <out.png>
 * Row 1 (on a checkerboard, nearest-neighbour x8/x4): current | new 32 | new 64
 * Row 2 (Minecraft inventory slots #8B8B8B with bevel): new32 in a 1x slot (drawn 16px),
 *        new32 in a 2x slot (pixel-perfect), new32 4x, current 4x, new64 4x.
 */
"use strict";
const fs = require("fs");
const zlib = require("zlib");
const { writePNG } = require("./render_icon.js");

function readPNG(file) {
  const b = fs.readFileSync(file);
  let p = 8, W, H, bd, ct, pal = null, trns = null; const idat = [];
  while (p < b.length) {
    const len = b.readUInt32BE(p), type = b.toString("ascii", p + 4, p + 8), d = b.subarray(p + 8, p + 8 + len);
    if (type === "IHDR") { W = d.readUInt32BE(0); H = d.readUInt32BE(4); bd = d[8]; ct = d[9]; if (d[12]) throw new Error("interlaced PNG not supported"); }
    else if (type === "PLTE") pal = d; else if (type === "tRNS") trns = d; else if (type === "IDAT") idat.push(d);
    p += 12 + len;
  }
  if (bd !== 8) throw new Error("only 8-bit PNGs supported: " + file);
  const ch = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[ct], bpp = ch, stride = W * ch;
  const raw = zlib.inflateSync(Buffer.concat(idat)), px = Buffer.alloc(stride * H);
  for (let y = 0; y < H; y++) {
    const f = raw[y * (stride + 1)], src = raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1));
    for (let i = 0; i < stride; i++) {
      const a = i >= bpp ? px[y * stride + i - bpp] : 0, up = y ? px[(y - 1) * stride + i] : 0, c = (y && i >= bpp) ? px[(y - 1) * stride + i - bpp] : 0;
      let v = src[i];
      if (f === 1) v += a; else if (f === 2) v += up; else if (f === 3) v += (a + up) >> 1;
      else if (f === 4) { const q = a + up - c, pa = Math.abs(q - a), pb = Math.abs(q - up), pc = Math.abs(q - c); v += pa <= pb && pa <= pc ? a : pb <= pc ? up : c; }
      px[y * stride + i] = v & 255;
    }
  }
  const out = new Uint8Array(W * H * 4);
  for (let i = 0; i < W * H; i++) {
    if (ct === 6) for (let k = 0; k < 4; k++) out[i * 4 + k] = px[i * 4 + k];
    else if (ct === 2) { out[i * 4] = px[i * 3]; out[i * 4 + 1] = px[i * 3 + 1]; out[i * 4 + 2] = px[i * 3 + 2]; out[i * 4 + 3] = 255; }
    else if (ct === 0) { out[i * 4] = out[i * 4 + 1] = out[i * 4 + 2] = px[i]; out[i * 4 + 3] = 255; }
    else if (ct === 4) { out[i * 4] = out[i * 4 + 1] = out[i * 4 + 2] = px[i * 2]; out[i * 4 + 3] = px[i * 2 + 1]; }
    else if (ct === 3) { const k = px[i]; out[i * 4] = pal[k * 3]; out[i * 4 + 1] = pal[k * 3 + 1]; out[i * 4 + 2] = pal[k * 3 + 2]; out[i * 4 + 3] = trns && k < trns.length ? trns[k] : 255; }
  }
  return { W, H, px: out };
}

function Canvas(W, H, bg) {
  const px = new Uint8Array(W * H * 4);
  for (let i = 0; i < W * H; i++) px.set(bg, i * 4);
  const fill = (x0, y0, w, h, c) => { for (let y = y0; y < y0 + h; y++) for (let x = x0; x < x0 + w; x++) if (x >= 0 && y >= 0 && x < W && y < H) px.set(c, (y * W + x) * 4); };
  const blit = (img, x0, y0, scale, destW) => { // nearest neighbour, alpha-over; destW lets 64px images fit same box
    const s = destW ? destW / img.W : scale;
    const dw = Math.round(img.W * s), dh = Math.round(img.H * s);
    for (let y = 0; y < dh; y++) for (let x = 0; x < dw; x++) {
      const sx = Math.min(img.W - 1, Math.floor(x / s)), sy = Math.min(img.H - 1, Math.floor(y / s)), si = (sy * img.W + sx) * 4;
      const a = img.px[si + 3] / 255; if (!a) continue;
      const X = x0 + x, Y = y0 + y; if (X < 0 || Y < 0 || X >= W || Y >= H) continue;
      const di = (Y * W + X) * 4;
      for (let k = 0; k < 3; k++) px[di + k] = img.px[si + k] * a + px[di + k] * (1 - a);
    }
  };
  return { W, H, px, fill, blit };
}
// Minecraft inventory slot: 18x18 with 1px dark top/left, 1px white bottom/right, #8B8B8B inside
function slot(cv, x, y, s) {
  cv.fill(x, y, 18 * s, 18 * s, [139, 139, 139, 255]);
  cv.fill(x, y, 17 * s, s, [55, 55, 55, 255]); cv.fill(x, y, s, 17 * s, [55, 55, 55, 255]);
  cv.fill(x + s, y + 17 * s, 17 * s, s, [255, 255, 255, 255]); cv.fill(x + 17 * s, y + s, s, 17 * s, [255, 255, 255, 255]);
  cv.fill(x + 17 * s, y, s, s, [139, 139, 139, 255]); cv.fill(x, y + 17 * s, s, s, [139, 139, 139, 255]);
}
function checker(cv, x0, y0, w, h, c = 8) {
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) cv.px.set((((x / c) | 0) + ((y / c) | 0)) & 1 ? [58, 60, 66, 255] : [72, 74, 80, 255], ((y0 + y) * cv.W + x0 + x) * 4);
}

if (require.main === module) {
  const [cur, n32, n64, out] = process.argv.slice(2);
  if (!out) { console.error("Usage: node compare_sheet.js <current.png> <new32.png> <new64.png> <out.png>"); process.exit(1); }
  const A = readPNG(cur), B = readPNG(n32), C = readPNG(n64);
  const box = 256, gap = 16, W = gap + 3 * (box + gap), H = gap + box + gap + 72 + gap;
  const cv = Canvas(W, H, [40, 42, 48, 255]);
  [A, B, C].forEach((img, i) => { const x = gap + i * (box + gap); checker(cv, x, gap, box, box); cv.blit(img, x, gap, 0, box); });
  // slots row: 1x slot with new32 (MC renders items at 16px in an 18px slot; at GUI scale 1 a 32px texture shows as 16px, so
  // we show it at native 16px (downsampled nearest) AND 32px (GUI scale 2), then a 4x slot.
  let y = gap * 2 + box, x = gap;
  // slots: 1x (item drawn 16px, GUI scale 1) | 2x (32px texture pixel-perfect, GUI scale 2) | 4x new32 | 4x current | 4x new64
  slot(cv, x, y, 1); cv.blit(B, x + 1, y + 1, 0.5);            // 1x slot, item drawn 16px
  x += 18 + gap;
  slot(cv, x, y, 2); cv.blit(B, x + 2, y + 2, 1);              // GUI scale 2: 32px texture pixel-perfect
  x += 36 + gap;
  slot(cv, x, y, 4); cv.blit(B, x + 4, y + 4, 2);              // 4x slot, new 32
  x += 72 + gap;
  slot(cv, x, y, 4); cv.blit(A, x + 4, y + 4, 0, 64);          // 4x slot, current icon for reference
  x += 72 + gap;
  slot(cv, x, y, 4); cv.blit(C, x + 4, y + 4, 1);              // 4x slot, new 64
  writePNG(out, W, H, cv.px);
  console.log("wrote " + out);
}
module.exports = { readPNG };
