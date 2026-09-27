package net.juli2kapo.factoryascent.item;

import java.util.function.Consumer;
import net.juli2kapo.factoryascent.energy.PowerCableBlock;
import net.juli2kapo.factoryascent.generator.EnergyCellBlockEntity;
import net.juli2kapo.factoryascent.machine.MachineBlock;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.miner.MinerBlockEntity;
import net.juli2kapo.factoryascent.pipe.ItemPipeBlock;
import net.juli2kapo.factoryascent.pipe.ItemPipeBlockEntity;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

/** Block item for every mod block, with a tooltip saying which age it belongs to and what it does. */
public class FactoryBlockItem extends BlockItem {
    public FactoryBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    private static MutableComponent line(String key, Object... args) {
        return Component.translatable("tooltip.factoryascent." + key, args).withStyle(ChatFormatting.GRAY);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        Block block = getBlock();
        if (block instanceof MachineBlock machine) {
            MachineType type = machine.type();
            tooltip.accept(Component.translatable("tooltip.factoryascent.age", type.age().displayName()).withStyle(ChatFormatting.DARK_GRAY));
            tooltip.accept(Component.translatable("desc.factoryascent." + type.id()).withStyle(ChatFormatting.GRAY));
            switch (type.category()) {
                case PROCESSOR, FARMER -> {
                    tooltip.accept(line("power." + type.power().name().toLowerCase(java.util.Locale.ROOT)));
                    if (type.power() == MachineType.Power.ELECTRIC) {
                        tooltip.accept(line("energy_use", type.baseEnergy()));
                    }
                    if (type.upgradeSlots() > 0) tooltip.accept(line("upgrade_slot_count", type.upgradeSlots()));
                    if (type.isMultiblock()) tooltip.accept(line("multiblock." + type.id()).withStyle(ChatFormatting.GOLD));
                }
                case MINER -> {
                    tooltip.accept(line("miner_radius", MinerBlockEntity.CLAIM_CHUNKS, MinerBlockEntity.CLAIM_CHUNKS));
                    tooltip.accept(line("energy_use", type.baseEnergy()));
                }
                case GENERATOR -> tooltip.accept(line(type == MachineType.GEOTHERMAL_GENERATOR ? "generation_per_lava" : "generation",
                        type == MachineType.GEOTHERMAL_GENERATOR ? type.baseEnergy() / 2 : type.baseEnergy()));
                case STORAGE -> {
                    var tier = type.tier();
                    tooltip.accept(line("capacity", EnergyUtil.format(EnergyCellBlockEntity.capacity(tier))));
                    tooltip.accept(line("transfer", EnergyUtil.format(EnergyCellBlockEntity.transferRate(tier))));
                    tooltip.accept(line("cell_front").withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        } else if (block instanceof PowerCableBlock cable) {
            tooltip.accept(cable.tier().displayName());
            tooltip.accept(line("cable_rate", EnergyUtil.format(cable.rate())));
            tooltip.accept(line("cable_bottleneck").withStyle(ChatFormatting.DARK_GRAY));
        } else if (block instanceof ItemPipeBlock pipe) {
            tooltip.accept(pipe.tier().displayName());
            tooltip.accept(line("pipe_rate", pipe.rate() * 20 / ItemPipeBlockEntity.EXTRACT_INTERVAL));
            tooltip.accept(line("pipe_hint").withStyle(ChatFormatting.DARK_GRAY));
        } else if (block instanceof net.juli2kapo.factoryascent.storage.CrateBlock crate) {
            tooltip.accept(line("crate", crate.rows() * 9));
        }
    }
}
