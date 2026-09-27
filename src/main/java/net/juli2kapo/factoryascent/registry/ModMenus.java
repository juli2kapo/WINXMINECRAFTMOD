package net.juli2kapo.factoryascent.registry;

import java.util.function.Supplier;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.machine.MachineMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, FactoryAscent.MOD_ID);

    public static final Supplier<MenuType<MachineMenu>> MACHINE = MENUS.register("machine",
            () -> IMenuTypeExtension.create(MachineMenu::fromNetwork));

    private ModMenus() {}
}
