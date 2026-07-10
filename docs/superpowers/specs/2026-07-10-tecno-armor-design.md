# Tecno Armor + Freeze Time Slot — Design

Date: 2026-07-10
Status: Approved (pending spec review)

## Goal

Replace the diamond-chestplate placeholders (`TODO CAMBIAR POR TECNOARMOR`) with a real
Tecno armor set, and rename the tech slot-1 power to match what it actually does
(freeze time).

## Scope

- Full 4-piece armor set: `tecno_helmet`, `tecno_chestplate`, `tecno_leggings`, `tecno_boots`.
- New sprites drawn to match the purple/violet + blue palette of the technology stage
  seed items, with cyan "circuit" accents.
- Rename `EnumPowers.SHORT_RANGE_XRAY` → `FREEZE_TIME` (already calls
  `TechnologyPowers::freezeTime`; the name is the only change).

Out of scope: implementing an actual X-ray power, other missing element powers,
illusion mob fixes.

## Components

### 1. `ModArmorMaterials` (new enum, `item` package)

One value `TECNO` implementing `ArmorMaterial`:

- Protection (boots/leggings/chest/helmet): 4 / 7 / 9 / 4 (netherite is 3/6/8/3)
- Toughness: 4.0F (netherite 3.0F)
- Knockback resistance: 0.15F
- Durability multiplier: 40 (netherite 37), × vanilla per-slot base {13, 15, 16, 11}
- Enchantability: 15
- Equip sound: `SoundEvents.ARMOR_EQUIP_NETHERITE`
- Repair ingredient: `ModItems.HIGHQMANACRYSTAL`
- Name: `"minewinx:tecno"` → worn textures resolve to
  `assets/minewinx/textures/models/armor/tecno_layer_1.png` / `tecno_layer_2.png`

### 2. Items (`ModItems`)

Four plain `ArmorItem` registrations with `ArmorItem.Type.HELMET/CHESTPLATE/LEGGINGS/BOOTS`
and default `Item.Properties()`.

### 3. Textures (drawn as part of this work)

- Four 16×16 item icons: `textures/item/tecno_helmet.png`, etc.
- Two worn layers: `textures/models/armor/tecno_layer_1.png` (64×32, helmet + chest + boots)
  and `tecno_layer_2.png` (64×32, leggings), based on the vanilla armor layer layout,
  recolored to the tech palette (purple/violet body, blue panels, cyan accents).

### 4. Recipes (`ModRecipeProvider`)

Replace the placeholder `tecnoArmor` method. For each piece, same shape as the existing
chestplate recipe: matching netherite piece + nether star + matching diamond piece in the
middle row (`"   "` / `"#SC"` / `"   "`). Unlock criterion: has nether star.

### 5. Restrictions (`ServerEvents`)

- `onItemCrafted`: if crafted item is any tecno armor piece and player's element is not
  Technology → destroy the item (existing behavior, generalized from
  `Items.DIAMOND_CHESTPLATE` to the four new items). Stage is NOT checked here — a tech
  player below max stage may craft ahead.
- `onEquipmentChange`: if equipped item is any tecno armor piece and the player is not
  (Technology element AND stage 3) → cancel the equip and message the player. This is
  the "only wearable with technopowers at max level" rule.
- A small helper `isTecnoArmor(ItemStack)` shared by both handlers.

### 6. Full-set perk (`ServerEvents.onPlayerTick`)

If the player wears all four tecno pieces: apply Haste, Speed, and Night Vision.
Durations ~210+ ticks, re-applied each tick like the existing element passives;
night vision duration ≥ 210 ticks so the screen doesn't flicker. Perk applies to any
wearer, but in practice only max-stage tech players can wear the set (equip gate above).

### 7. Wiring

- `ModCreativeModTabs`: add the four pieces to the existing tab.
- `ModItemModelProvider`: `simpleItem` entries for the four icons.
- `en_us.json`: display names (Tecno Helmet, Tecno Chestplate, Tecno Leggings, Tecno Boots).
- `EnumPowers`: rename `SHORT_RANGE_XRAY` → `FREEZE_TIME`.
- Regenerate data (`runData`) so item model + recipe JSONs are produced.

## Error handling

- Equip cancellation messages the player in Spanish, consistent with existing messages.
- No new nullable paths: registry objects resolve at runtime like every existing item.

## Testing

- `gradlew runData` succeeds and emits the four item models + four recipes.
- `gradlew build` (or `compileJava`) passes.
- Manual in-game check (runClient): craft gate, equip gate at stage <3 vs stage 3,
  full-set effects, armor renders on the player.
