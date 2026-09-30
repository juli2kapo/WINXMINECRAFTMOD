package net.juli2kapo.factoryascent.nuclear;

import java.util.HashMap;
import java.util.Map;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.power.PowerConfig;
import net.juli2kapo.factoryascent.power.PowerContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * Radiation: radioactive items in your inventory, lying on the ground or stored in ordinary
 * containers nearby, and radioactive blocks (uranium ore, corium) give off a dose rate in rad/s
 * (blocks and containers fall off with the square of the distance). Each Hazmat Suit piece stops
 * a quarter of it; the full suit stops all of it. The absorbed dose builds up while exposed and
 * slowly heals away when not, and above 30 / 150 / 400 / 800 rad you get radiation sickness I-IV.
 * The Waste Barrel and the RTG are lead-lined: what's inside them doesn't count.
 */
public final class Radiation {
    public static final ResourceKey<DamageType> DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "radiation"));
    public static final float[] SICKNESS = {30, 150, 400, 800};
    private static Map<Item, Float> items;
    private static Map<Block, Float> blocks;

    private Radiation() {}

    private static void init() {
        if (items != null) return;
        Map<Item, Float> i = new HashMap<>();
        i.put(PowerContent.RAW_URANIUM.get(), 0.05f);
        i.put(PowerContent.URANIUM_DUST.get(), 0.05f);
        i.put(PowerContent.URANIUM_INGOT.get(), 0.05f);
        i.put(PowerContent.DEEPSLATE_URANIUM_ORE.get().asItem(), 0.05f);
        i.put(PowerContent.ENRICHED_URANIUM.get(), 0.1f);
        i.put(PowerContent.DEPLETED_URANIUM.get(), 0.02f);
        i.put(PowerContent.FUEL_ROD.get(), 0.3f);
        i.put(PowerContent.DEPLETED_FUEL_ROD.get(), 3.0f);
        i.put(PowerContent.NUCLEAR_WASTE.get(), 1.5f);
        i.put(PowerContent.RADIOISOTOPE_PELLET.get(), 1.0f);
        i.put(PowerContent.TRITIUM_CELL.get(), 0.05f);
        i.put(PowerContent.CORIUM.get().asItem(), 8.0f);
        Map<Block, Float> b = new HashMap<>();
        b.put(PowerContent.DEEPSLATE_URANIUM_ORE.get(), 0.3f);
        b.put(PowerContent.CORIUM.get(), 25f);
        items = i;
        blocks = b;
    }

    /** rad/s one of this item gives off, unshielded. */
    public static float of(Item item) {
        init();
        return items.getOrDefault(item, 0f);
    }

    public static float of(ItemStack stack) {
        return stack.isEmpty() ? 0 : of(stack.getItem()) * stack.getCount();
    }

    public static float ofBlock(Block block) {
        init();
        return blocks.getOrDefault(block, 0f);
    }

    public static boolean isRadioactive(ItemStack stack) {
        return of(stack.getItem()) > 0;
    }

    public static DamageSource damageSource(ServerLevel level) {
        return level.damageSources().source(DAMAGE);
    }

    public static RadiationState state(Player player) {
        return player.getData(PowerContent.RADIATION_STATE.get());
    }

    /** Hazmat pieces worn, as the fraction of radiation they stop (4 pieces = all of it). */
    public static float shielding(Player player) {
        int pieces = 0;
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (player.getItemBySlot(slot).getItem() instanceof HazmatSuitItem) pieces++;
        }
        return pieces / 4f;
    }

    /** Dose rate around the player before shielding (rad/s). */
    public static float exposure(Player player) {
        float rate = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) rate += of(inv.getItem(i));
        if (!(player.level() instanceof ServerLevel level)) return rate;
        int r = PowerConfig.get(PowerConfig.RADIATION_SCAN_RADIUS);
        BlockPos center = player.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
            float s = ofBlock(level.getBlockState(p).getBlock());
            if (s > 0) rate += s / Math.max(1f, (float) p.distToCenterSqr(player.getX(), player.getY() + 1, player.getZ()));
        }
        // ordinary containers (chests, crates, hoppers...) only shield half; the Waste Barrel shields all
        for (int cx = (center.getX() - r) >> 4; cx <= (center.getX() + r) >> 4; cx++) {
            for (int cz = (center.getZ() - r) >> 4; cz <= (center.getZ() + r) >> 4; cz++) {
                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof Container container) || be instanceof WasteBarrelBlockEntity) continue;
                    BlockPos p = be.getBlockPos();
                    if (Math.abs(p.getX() - center.getX()) > r || Math.abs(p.getY() - center.getY()) > r
                            || Math.abs(p.getZ() - center.getZ()) > r) continue;
                    float s = 0;
                    for (int i = 0; i < container.getContainerSize(); i++) s += of(container.getItem(i));
                    if (s > 0) rate += 0.5f * s / Math.max(1f, (float) p.distToCenterSqr(player.getX(), player.getY() + 1, player.getZ()));
                }
            }
        }
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(r))) {
            float s = of(item.getItem());
            if (s > 0) rate += s / Math.max(1f, (float) item.distanceToSqr(player));
        }
        return rate;
    }

    /**
     * One second of radiation for a player (called every 20 ticks): updates the dose and the
     * sickness. Returns the dose rate that got through.
     */
    public static float tick(Player player) {
        if (!PowerConfig.get(PowerConfig.RADIATION_ENABLED) || player.isCreative() || player.isSpectator()) {
            RadiationState old = state(player);
            if (old.dose() != 0 || old.rate() != 0) player.setData(PowerContent.RADIATION_STATE.get(), RadiationState.NONE);
            return 0;
        }
        float exposure = exposure(player);
        float rate = exposure * (1 - shielding(player));
        if (rate < 0.001f) rate = 0;
        RadiationState old = state(player);
        float dose = old.dose() + rate;
        if (rate < 0.05f) dose = Math.max(0f, dose - 0.5f - dose * 0.01f);
        if (dose != old.dose() || rate != old.rate() || exposure != old.exposure()) {
            player.setData(PowerContent.RADIATION_STATE.get(), new RadiationState(dose, rate, exposure));
        }
        int sickness = sickness(dose);
        if (sickness >= 0) {
            player.addEffect(new MobEffectInstance(PowerContent.RADIATION, 60, sickness, false, true, true));
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) PowerContent.award(sp, "industrial_radiation");
        }
        return rate;
    }

    /** Sickness level (effect amplifier) for an absorbed dose, -1 for none. */
    public static int sickness(float dose) {
        int level = -1;
        for (int i = 0; i < SICKNESS.length; i++) if (dose >= SICKNESS[i]) level = i;
        return level;
    }

    /** A dose rate for the HUD and the Geiger counter. */
    public static String format(float rate) {
        return rate >= 100 ? String.format("%.0f", rate) : rate >= 10 ? String.format("%.1f", rate) : String.format("%.2f", rate);
    }
}
