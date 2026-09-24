#!/bin/sh
# Rebuild the MUSIC stage-1 pilot: model JSON -> 32/64 icons + preview + compare sheet.
set -e
cd "$(dirname "$0")"
TEX=../../src/main/resources/assets/minewinx/textures/item
node wrap_voxel.js seed_music.src.js seed_music.json
node render_icon.js seed_music.json out/music_stage_1_32.png --size 32 --preview out/music_stage_1_preview.png
node render_icon.js seed_music.json out/music_stage_1_64.png --size 64
node compare_sheet.js $TEX/music_stage_1.png out/music_stage_1_32.png out/music_stage_1_64.png out/music_compare.png
