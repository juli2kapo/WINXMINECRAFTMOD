package net.juli2kapo.factoryascent.fusion;

import java.util.function.Consumer;
import net.juli2kapo.factoryascent.item.DescribedBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

/** Fusion Casing (the tokamak's shell) and the Fusion Magnet coils that hold the plasma. */
public class FusionPartBlock extends Block implements DescribedBlock {
    private final boolean magnet;

    public FusionPartBlock(boolean magnet, Properties properties) {
        super(properties);
        this.magnet = magnet;
    }

    public boolean magnet() {
        return magnet;
    }

    @Override
    public void describe(Consumer<Component> tooltip) {
        tooltip.accept(Component.translatable(magnet ? "tooltip.factoryascent.fusion_magnet" : "tooltip.factoryascent.fusion_casing")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.tokamak_build").withStyle(ChatFormatting.DARK_GRAY));
    }
}
