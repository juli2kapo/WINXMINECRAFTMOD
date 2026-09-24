#!/usr/bin/env node
// Wrap a voxel.exec JS source into the MineBench tool-call JSON.
//   node wrap_voxel.js seed_music.src.js seed_music.json [--seed 123]
"use strict";
const fs = require("fs");
const [src, out] = process.argv.slice(2);
const i = process.argv.indexOf("--seed");
const seed = i > 0 ? Number(process.argv[i + 1]) : 123;
if (!src || !out) { console.error("Usage: node wrap_voxel.js <code.js> <out.json> [--seed N]"); process.exit(1); }
const code = fs.readFileSync(src, "utf8");
fs.writeFileSync(out, JSON.stringify({ tool: "voxel.exec", input: { code, gridSize: 256, palette: "simple", seed } }) + "\n");
console.log("wrote " + out);
