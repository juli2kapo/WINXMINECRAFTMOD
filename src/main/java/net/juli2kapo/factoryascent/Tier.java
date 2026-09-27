package net.juli2kapo.factoryascent;

import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The five machine tiers. Every tiered block exists once per tier ({@code basic_crusher},
 * {@code reinforced_crusher}, …) and these numbers are the whole balance model: speed doubles per
 * tier, energy per operation drops 10% per tier, and upgrade slots unlock as you climb.
 */
public enum Tier {
    BASIC(1, 1.00f, 1, ChatFormatting.WHITE),
    REINFORCED(2, 0.90f, 2, ChatFormatting.BLUE),
    ADVANCED(4, 0.80f, 3, ChatFormatting.GOLD),
    ELITE(8, 0.70f, 3, ChatFormatting.LIGHT_PURPLE),
    ULTIMATE(16, 0.60f, 3, ChatFormatting.AQUA);

    public static final Tier[] VALUES = values();

    private final int speed;
    private final float energyFactor;
    private final int upgradeSlots;
    private final ChatFormatting color;

    Tier(int speed, float energyFactor, int upgradeSlots, ChatFormatting color) {
        this.speed = speed;
        this.energyFactor = energyFactor;
        this.upgradeSlots = upgradeSlots;
        this.color = color;
    }

    /** Work points gained per tick relative to Basic. */
    public int speed() {
        return speed;
    }

    /** Multiplier on the energy cost of one unit of work. */
    public float energyFactor() {
        return energyFactor;
    }

    /** How many of a machine's three upgrade slots are usable at this tier. */
    public int upgradeSlots() {
        return upgradeSlots;
    }

    public ChatFormatting color() {
        return color;
    }

    /** 1-based tier number, as used by {@code min_tier} in recipes. */
    public int level() {
        return ordinal() + 1;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public @Nullable Tier next() {
        return this == ULTIMATE ? null : VALUES[ordinal() + 1];
    }

    public Component displayName() {
        return Component.translatable("tier.factoryascent." + id()).withStyle(color);
    }

    public static Tier byLevel(int level) {
        return VALUES[Math.max(0, Math.min(VALUES.length - 1, level - 1))];
    }
}
