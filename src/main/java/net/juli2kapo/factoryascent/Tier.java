package net.juli2kapo.factoryascent;

import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Infrastructure tiers (voltage-style, like GregTech/Modern Industrialization): only cables,
 * item pipes and energy cells come in tiers. Machines are distinct blocks per {@link Age}.
 */
public enum Tier {
    LV(ChatFormatting.WHITE),
    MV(ChatFormatting.GOLD),
    HV(ChatFormatting.LIGHT_PURPLE),
    EV(ChatFormatting.AQUA);

    public static final Tier[] VALUES = values();

    private final ChatFormatting color;

    Tier(ChatFormatting color) {
        this.color = color;
    }

    public ChatFormatting color() {
        return color;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable("tier.factoryascent." + id()).withStyle(color);
    }
}
