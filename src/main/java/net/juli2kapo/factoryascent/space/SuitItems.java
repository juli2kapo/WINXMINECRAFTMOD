package net.juli2kapo.factoryascent.space;

import java.util.EnumMap;
import java.util.function.Consumer;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * The Astronaut Suit: helmet with a gold visor, pressure suit (holds the air, as a data component),
 * leggings and boots, drawn from {@code assets/factoryascent/equipment/astronaut.json}. Air is only
 * used in airless dimensions; an Oxygen Compressor refills it.
 */
public final class SuitItems {
    public static final ResourceKey<EquipmentAsset> ASSET =
            ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "astronaut"));
    private static final TagKey<Item> TITANIUM_PLATES =
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "plates/titanium"));

    /** About iron armour: the suit is for breathing, not for fighting. */
    public static final ArmorMaterial MATERIAL = new ArmorMaterial(20, defense(), 12, SoundEvents.ARMOR_EQUIP_IRON,
            0.5f, 0f, TITANIUM_PLATES, ASSET);

    private SuitItems() {}

    private static java.util.Map<ArmorType, Integer> defense() {
        var map = new EnumMap<ArmorType, Integer>(ArmorType.class);
        map.put(ArmorType.BOOTS, 2);
        map.put(ArmorType.LEGGINGS, 5);
        map.put(ArmorType.CHESTPLATE, 6);
        map.put(ArmorType.HELMET, 2);
        map.put(ArmorType.BODY, 5);
        return map;
    }

    /** A piece of the suit, and which slot it counts for. */
    public interface SuitPiece {
        EquipmentSlot suitSlot();
    }

    public static boolean isSuitPiece(ItemStack stack, EquipmentSlot slot) {
        return stack.getItem() instanceof SuitPiece piece && piece.suitSlot() == slot;
    }

    /** A chestplate that holds air (suit or Jet Suit). */
    public static boolean holdsOxygen(ItemStack stack) {
        return stack.getItem() instanceof SuitChestItem;
    }

    public static int oxygen(ItemStack stack) {
        return holdsOxygen(stack) ? stack.getOrDefault(SpaceContent.OXYGEN.get(), 0) : 0;
    }

    public static void setOxygen(ItemStack stack, int amount) {
        if (holdsOxygen(stack)) stack.set(SpaceContent.OXYGEN.get(), Mth.clamp(amount, 0, SpaceConfig.suitOxygen()));
    }

    /** Adds up to {@code amount} units; returns how many fit. */
    public static int fill(ItemStack stack, int amount) {
        if (!holdsOxygen(stack) || amount <= 0) return 0;
        int have = oxygen(stack);
        int added = Math.min(amount, SpaceConfig.suitOxygen() - have);
        if (added > 0) setOxygen(stack, have + added);
        return Math.max(0, added);
    }

    /** "Air ■■■■■□□□□□ 50% (5:00)" */
    public static Component oxygenLine(int oxygen) {
        int max = SpaceConfig.suitOxygen();
        int filled = Math.round(10f * oxygen / Math.max(1, max));
        MutableComponent bar = Component.literal("■".repeat(filled)).withStyle(ChatFormatting.AQUA)
                .append(Component.literal("■".repeat(10 - filled)).withStyle(ChatFormatting.DARK_GRAY));
        int seconds = oxygen / 20;
        String time = String.format("%d:%02d", seconds / 60, seconds % 60);
        return Component.translatable("tooltip.factoryascent.suit_oxygen", bar, Math.round(100f * oxygen / Math.max(1, max)), time)
                .withStyle(ChatFormatting.GRAY);
    }

    // ---------------------------------------------------------------- items

    /** Helmet, leggings and boots. */
    public static class SuitPieceItem extends Item implements SuitPiece {
        private final EquipmentSlot slot;

        public SuitPieceItem(Properties properties, ArmorType type) {
            super(properties);
            this.slot = type.getSlot();
        }

        @Override
        public EquipmentSlot suitSlot() {
            return slot;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                    Consumer<Component> tooltip, TooltipFlag flag) {
            tooltip.accept(Component.translatable("tooltip.factoryascent.suit_piece").withStyle(ChatFormatting.DARK_AQUA));
            if (slot == EquipmentSlot.HEAD) {
                tooltip.accept(Component.translatable("tooltip.factoryascent.suit_helmet").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }

    /** The suit itself: holds the air. */
    public static class SuitChestItem extends Item implements SuitPiece {
        public SuitChestItem(Properties properties) {
            super(properties);
        }

        @Override
        public EquipmentSlot suitSlot() {
            return EquipmentSlot.CHEST;
        }

        @Override
        public boolean isBarVisible(ItemStack stack) {
            return true;
        }

        @Override
        public int getBarWidth(ItemStack stack) {
            return Math.round(13f * oxygen(stack) / Math.max(1, SpaceConfig.suitOxygen()));
        }

        @Override
        public int getBarColor(ItemStack stack) {
            return 0x3FC8FF;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                    Consumer<Component> tooltip, TooltipFlag flag) {
            tooltip.accept(oxygenLine(oxygen(stack)));
            if (net.juli2kapo.factoryascent.space.planet.ThermalLiningItem.lined(stack)) {
                tooltip.accept(Component.translatable("tooltip.factoryascent.thermal_lining_on").withStyle(ChatFormatting.GOLD));
            }
            tooltip.accept(Component.translatable("tooltip.factoryascent.suit_chest").withStyle(ChatFormatting.DARK_AQUA));
            tooltip.accept(Component.translatable("tooltip.factoryascent.suit_refill").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
