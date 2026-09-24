# Seed icon family — design rules

Every element seed icon is a 3D voxel model rendered by `render_icon.js` with a
fixed camera, lighting and outline, so all icons match.

## Pipeline (per element / stage)
1. Copy the closest existing source (`seed_music.src.js` or the previous stage of
   the same element) to `seed_<el>_<stage>.src.js` and edit it.
2. `node wrap_voxel.js seed_<el>_<stage>.src.js seed_<el>_<stage>.json`
3. `node render_icon.js seed_<el>_<stage>.json out/<el>_stage_<stage>_32.png --size 32 --preview out/<el>_stage_<stage>_preview.png`
4. `node render_icon.js seed_<el>_<stage>.json out/<el>_stage_<stage>_64.png --size 64`
5. `node compare_sheet.js ../../src/main/resources/assets/minewinx/textures/item/<el>_stage_<stage>.png out/<el>_stage_<stage>_32.png out/<el>_stage_<stage>_64.png out/<el>_stage_<stage>_compare.png`
(`--debug` on render_icon.js prints which block type won each pixel.)

Music stage 1 (`seed_music.src.js`, `out/music_stage_1_*`) is the approved reference.

## Shared shape (identical for every element)
- Everything under "FAMILY" in the source is shared; only the `EL` block changes:
  `lower` colour, `upper` colour, `core` (emblem front, glowing), `rim` (emblem
  sides) and the `emblem(u, y, E)` shape function.
- Crystal egg: 46 blocks tall, max half-width 15, bottom at y=6, octagonal
  cross-section with a stepped (cut-gem) outline, 1-block glass skin.
- Core: front half hollow; emblem is a 4-block-thick slab (front 1.5 layers
  `core`, `rim` behind); coloured back wall 1 block behind the emblem. Below 33%
  of egg height the core is solid `lower`; above the gold girdle ring the wall is
  all `upper` (blend band hidden behind the ring).
- Emblem sizing: at 32px one pixel ≈ 2.2 blocks. Strokes ≥ 3 blocks thick, gaps
  ≥ 4 blocks, within ~14 blocks wide and ~38%–85% of egg height. No gold may
  cross the front "emblem window".
- Gold cradle: stepped foot touching Y=0 with 4 glowstone jewels, two side ribs
  ending in big curls, a back rib with a small curl, girdle ring at 32%, collar
  ring at 8%, three cage arcs to the crown, gold cap with 4 leaves + glowstone
  finial.
- `VIEW = 30°` in the model must equal `CAMERA_YAW` in the renderer.

## Stage progression (same egg + emblem, the cradle grows)
- Stage 1: the base cradle above.
- Stage 2: visibly richer — e.g. curls on all four ribs, a fuller crown, more
  glowstone jewels; the element motif starts to appear in the goldwork.
- Stage 3: the most majestic — e.g. a large crown and/or small gold wings,
  radiating element motifs, the brightest glow.
The stages must be distinguishable at 32px in an inventory slot, while still
clearly the same seed. Keep the silhouette inside the icon (the renderer fits
the whole model, so a much wider model shrinks the egg — grow mostly upward
and keep added width modest).

## Known limits
- The palette has no stained glass: element colour comes from the wool back
  wall behind clear glass.
