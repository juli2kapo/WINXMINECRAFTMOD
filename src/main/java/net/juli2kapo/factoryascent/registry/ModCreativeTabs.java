package net.juli2kapo.factoryascent.registry;

import java.util.function.Consumer;
import java.util.function.Supplier;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.Tier;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Creative tabs by purpose: processing, power, logistics & storage, tools, materials, world blocks. */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FactoryAscent.MOD_ID);

    private static boolean isPower(MachineType t) {
        return t.category() == MachineType.Category.GENERATOR || t.category() == MachineType.Category.STORAGE
                || t.category() == MachineType.Category.KINETIC;
    }

    /** Machines that move items and blocks around rather than process them. */
    private static boolean isLogistics(MachineType t) {
        return t == MachineType.BLOCK_BREAKER || t == MachineType.BLOCK_PLACER || t == MachineType.VACUUM_HOPPER;
    }

    private static boolean isUtility(MachineType t) {
        return t == MachineType.CHARGER || t == MachineType.FLOODLIGHT;
    }

    /** Machines in age order, so every tab reads like the progression. */
    private static void machines(CreativeModeTab.Output out, java.util.function.Predicate<MachineType> filter) {
        for (var age : net.juli2kapo.factoryascent.Age.values()) {
            for (MachineType type : MachineType.VALUES) {
                if (type.age() == age && filter.test(type)) out.accept(ModBlocks.machine(type).get());
            }
        }
    }

    public static final Supplier<CreativeModeTab> PROCESSING = tab("processing", null,
            () -> ModBlocks.machine(MachineType.CRUSHER).get(),
            out -> {
                machines(out, t -> !isPower(t) && !isLogistics(t) && !isUtility(t));
                out.accept(ModBlocks.COKE_OVEN_BRICKS.get());
                out.accept(ModBlocks.FIRE_BRICKS.get());
                ModItems.MOLDS.values().forEach(m -> out.accept(m.get()));
                out.accept(ModItems.SPEED_UPGRADE.get());
                out.accept(ModItems.ENERGY_UPGRADE.get());
            });

    public static final Supplier<CreativeModeTab> POWER = tab("power", "processing",
            () -> ModBlocks.machine(MachineType.COMBUSTION_GENERATOR).get(),
            out -> {
                machines(out, ModCreativeTabs::isPower);
                for (Tier tier : Tier.VALUES) out.accept(ModBlocks.POWER_CABLES.get(tier).get());
                net.juli2kapo.factoryascent.power.PowerContent.powerItems().forEach(i -> out.accept(i.get()));
            });

    public static final Supplier<CreativeModeTab> LOGISTICS = tab("logistics", "power",
            () -> ModBlocks.ITEM_PIPES.get(Tier.LV).get(),
            out -> {
                out.accept(ModBlocks.WOODEN_CRATE.get());
                out.accept(ModBlocks.BRONZE_CRATE.get());
                for (Tier tier : Tier.VALUES) out.accept(ModBlocks.ITEM_PIPES.get(tier).get());
                machines(out, ModCreativeTabs::isLogistics);
                net.juli2kapo.factoryascent.storagenet.StorageNetwork.creativeItems().forEach(i -> out.accept(i.get()));
            });

    public static final Supplier<CreativeModeTab> TOOLS = tab("tools", "logistics",
            () -> ModItems.ELECTRIC_DRILL.get(),
            out -> {
                ModItems.TOOLS.values().forEach(t -> out.accept(t.get()));
                net.juli2kapo.factoryascent.gear.GearContent.toolItems().forEach(i -> out.accept(i.get()));
                net.juli2kapo.factoryascent.space.SpaceContent.toolItems().forEach(i -> out.accept(i.get()));
                net.juli2kapo.factoryascent.power.PowerContent.toolItems().forEach(i -> out.accept(i.get()));
            });

    public static final Supplier<CreativeModeTab> MATERIALS = tab("materials", "orbital",
            () -> ModItems.MATERIALS.get("bronze_ingot").get(),
            out -> {
                ModItems.MATERIALS.values().forEach(m -> out.accept(m.get()));
                net.juli2kapo.factoryascent.gear.GearContent.materialItems().forEach(i -> out.accept(i.get()));
                net.juli2kapo.factoryascent.power.PowerContent.materialItems().forEach(i -> out.accept(i.get()));
            });

    public static final Supplier<CreativeModeTab> UTILITY = tab("utility", "tools",
            () -> net.juli2kapo.factoryascent.ender.EnderContent.ENDER_ANCHOR.get(),
            out -> {
                machines(out, ModCreativeTabs::isUtility);
                net.juli2kapo.factoryascent.ender.EnderContent.creativeItems().forEach(i -> out.accept(i.get()));
                net.juli2kapo.factoryascent.mobs.MobContent.creativeItems().forEach(i -> out.accept(i.get()));
            });

    public static final Supplier<CreativeModeTab> ORBITAL = tab("orbital", "utility",
            net.juli2kapo.factoryascent.orbital.OrbitalContent::tabIcon,
            out -> {
                net.juli2kapo.factoryascent.orbital.OrbitalContent.creativeItems().forEach(i -> out.accept(i.get()));
                net.juli2kapo.factoryascent.space.SpaceContent.orbitalItems().forEach(i -> out.accept(i.get()));
            });

    public static final Supplier<CreativeModeTab> WORLD = tab("world", "materials",
            () -> ModBlocks.SIMPLE.get("tin_ore").get(),
            out -> {
                ModBlocks.SIMPLE.forEach((name, b) -> {
                    if (b != ModBlocks.COKE_OVEN_BRICKS && b != ModBlocks.FIRE_BRICKS) out.accept(b.get());
                });
                net.juli2kapo.factoryascent.power.PowerContent.worldItems().forEach(i -> out.accept(i.get()));
            });

    private static Supplier<CreativeModeTab> tab(String name, String after, Supplier<? extends ItemLike> icon,
                                                 Consumer<CreativeModeTab.Output> items) {
        return TABS.register(name, () -> {
            CreativeModeTab.Builder b = CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.factoryascent." + name))
                    .icon(() -> new ItemStack(icon.get()))
                    .displayItems((params, out) -> items.accept(out));
            if (after != null) b.withTabsBefore(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, after));
            return b.build();
        });
    }

    private ModCreativeTabs() {}
}
