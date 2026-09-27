package net.juli2kapo.factoryascent.registry;

import java.util.function.Supplier;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.Tier;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FactoryAscent.MOD_ID);

    public static final Supplier<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.factoryascent"))
            .icon(() -> new ItemStack(ModBlocks.machine(MachineType.CRUSHER, Tier.ADVANCED).get()))
            .displayItems((params, out) -> {
                for (MachineType type : MachineType.VALUES) {
                    for (Tier tier : Tier.VALUES) out.accept(ModBlocks.machine(type, tier).get());
                }
                for (Tier tier : Tier.VALUES) out.accept(ModBlocks.POWER_CABLES.get(tier).get());
                for (Tier tier : Tier.VALUES) out.accept(ModBlocks.ITEM_PIPES.get(tier).get());
                out.accept(ModItems.WRENCH.get());
                out.accept(ModItems.FORGE_HAMMER.get());
                out.accept(ModItems.SPEED_UPGRADE.get());
                out.accept(ModItems.ENERGY_UPGRADE.get());
                ModItems.UPGRADE_KITS.values().forEach(k -> out.accept(k.get()));
                ModItems.FRAMES.values().forEach(f -> out.accept(f.get()));
                ModItems.CIRCUITS.values().forEach(c -> out.accept(c.get()));
                ModItems.MOLDS.values().forEach(m -> out.accept(m.get()));
                ModItems.MATERIALS.values().forEach(m -> out.accept(m.get()));
                ModBlocks.SIMPLE.values().forEach(b -> out.accept(b.get()));
            })
            .build());

    private ModCreativeTabs() {}
}
