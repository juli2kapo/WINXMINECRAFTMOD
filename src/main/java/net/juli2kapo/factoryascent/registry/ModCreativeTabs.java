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
            .icon(() -> new ItemStack(ModBlocks.machine(MachineType.CRUSHER).get()))
            .displayItems((params, out) -> {
                // In age order, so the tab reads like the progression.
                for (var age : net.juli2kapo.factoryascent.Age.values()) {
                    for (MachineType type : MachineType.VALUES) {
                        if (type.age() == age) out.accept(ModBlocks.machine(type).get());
                    }
                    if (age == net.juli2kapo.factoryascent.Age.STONE) out.accept(ModBlocks.WOODEN_CRATE.get());
                    if (age == net.juli2kapo.factoryascent.Age.BRONZE) {
                        out.accept(ModBlocks.COKE_OVEN_BRICKS.get());
                        out.accept(ModBlocks.FIRE_BRICKS.get());
                        out.accept(ModBlocks.BRONZE_CRATE.get());
                    }
                }
                net.juli2kapo.factoryascent.storagenet.StorageNetwork.creativeItems().forEach(i -> out.accept(i.get()));
                for (Tier tier : Tier.VALUES) out.accept(ModBlocks.POWER_CABLES.get(tier).get());
                for (Tier tier : Tier.VALUES) out.accept(ModBlocks.ITEM_PIPES.get(tier).get());
                ModItems.TOOLS.values().forEach(t -> out.accept(t.get()));
                out.accept(ModItems.SPEED_UPGRADE.get());
                out.accept(ModItems.ENERGY_UPGRADE.get());
                ModItems.MOLDS.values().forEach(m -> out.accept(m.get()));
                ModItems.MATERIALS.values().forEach(m -> out.accept(m.get()));
                ModBlocks.SIMPLE.values().forEach(b -> {
                    if (b != ModBlocks.COKE_OVEN_BRICKS && b != ModBlocks.FIRE_BRICKS) out.accept(b.get());
                });
            })
            .build());

    private ModCreativeTabs() {}
}
