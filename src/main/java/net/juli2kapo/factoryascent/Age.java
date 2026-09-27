package net.juli2kapo.factoryascent;

import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** The seven ages of progression. Each machine belongs to the age that introduces it. */
public enum Age {
    STONE(ChatFormatting.GRAY),
    BRONZE(ChatFormatting.GOLD),
    ELECTRIC(ChatFormatting.YELLOW),
    AUTOMATION(ChatFormatting.GREEN),
    INDUSTRIAL(ChatFormatting.BLUE),
    ORBITAL(ChatFormatting.LIGHT_PURPLE),
    QUANTUM(ChatFormatting.AQUA);

    private final ChatFormatting color;

    Age(ChatFormatting color) {
        this.color = color;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable("age.factoryascent." + id()).withStyle(color);
    }
}
