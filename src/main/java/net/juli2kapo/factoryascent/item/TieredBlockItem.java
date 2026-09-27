package net.juli2kapo.factoryascent.item;

import java.util.function.Consumer;
import net.juli2kapo.factoryascent.Tier;
import net.juli2kapo.factoryascent.energy.PowerCableBlock;
import net.juli2kapo.factoryascent.generator.CombustionGeneratorBlockEntity;
import net.juli2kapo.factoryascent.generator.EnergyCellBlockEntity;
import net.juli2kapo.factoryascent.machine.MachineBlock;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.miner.MinerBlockEntity;
import net.juli2kapo.factoryascent.pipe.ItemPipeBlock;
import net.juli2kapo.factoryascent.pipe.ItemPipeBlockEntity;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

/** Block item for every tiered block, with a tooltip showing what the tier actually changes. */
public class TieredBlockItem extends BlockItem {
    public TieredBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    private static net.minecraft.network.chat.MutableComponent line(String key, Object... args) {
        return Component.translatable("tooltip.factoryascent." + key, args).withStyle(ChatFormatting.GRAY);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        Block block = getBlock();
        if (block instanceof MachineBlock machine) {
            Tier tier = machine.tier();
            MachineType type = machine.type();
            tooltip.accept(tier.displayName());
            switch (type.category()) {
                case PROCESSOR -> {
                    tooltip.accept(line("speed", tier.speed()));
                    tooltip.accept(line("energy_use", Math.round(type.baseEnergy() * tier.speed() * tier.energyFactor())));
                    tooltip.accept(line("upgrade_slot_count", tier.upgradeSlots()));
                }
                case MINER -> {
                    tooltip.accept(line("miner_radius", MinerBlockEntity.baseRadius(tier)));
                    tooltip.accept(line("miner_rate", tier.speed() * 60 * 20 / MinerBlockEntity.POINTS_PER_ORE));
                    tooltip.accept(line("energy_use", Math.round(type.baseEnergy() * tier.speed() * tier.energyFactor())));
                    tooltip.accept(line("upgrade_slot_count", tier.upgradeSlots()));
                }
                case GENERATOR -> {
                    int out = switch (type) {
                        case COMBUSTION_GENERATOR -> Math.round(type.baseEnergy() * tier.speed() * CombustionGeneratorBlockEntity.efficiency(tier));
                        default -> type.baseEnergy() * tier.speed();
                    };
                    String key = type == MachineType.GEOTHERMAL_GENERATOR ? "generation_per_lava" : "generation";
                    tooltip.accept(line(key, out));
                    if (type == MachineType.COMBUSTION_GENERATOR) {
                        tooltip.accept(line("fuel_efficiency", Math.round(CombustionGeneratorBlockEntity.efficiency(tier) * 100)));
                    }
                }
                case STORAGE -> {
                    tooltip.accept(line("capacity", EnergyUtil.format(EnergyCellBlockEntity.capacity(tier))));
                    tooltip.accept(line("transfer", EnergyUtil.format(EnergyCellBlockEntity.transferRate(tier))));
                    tooltip.accept(line("cell_front").withStyle(ChatFormatting.DARK_GRAY));
                }
            }
            Tier next = tier.next();
            if (next != null) tooltip.accept(line("upgradable", next.displayName()).withStyle(ChatFormatting.DARK_GRAY));
        } else if (block instanceof PowerCableBlock cable) {
            tooltip.accept(cable.tier().displayName());
            tooltip.accept(line("cable_rate", EnergyUtil.format(cable.rate())));
            tooltip.accept(line("cable_bottleneck").withStyle(ChatFormatting.DARK_GRAY));
        } else if (block instanceof ItemPipeBlock pipe) {
            tooltip.accept(pipe.tier().displayName());
            tooltip.accept(line("pipe_rate", pipe.rate() * 20 / ItemPipeBlockEntity.EXTRACT_INTERVAL));
            tooltip.accept(line("pipe_hint").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
