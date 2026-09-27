package net.juli2kapo.factoryascent.item.drill;

import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * The Electric Drill's own data component ({@code factoryascent:drill_mode}). Registered through
 * {@link EventBusSubscriber} so the drill needs no hook in the mod constructor.
 */
@EventBusSubscriber(modid = FactoryAscent.MOD_ID)
public final class DrillComponents {
    /** Selected mining mode; absent means {@link DrillMode#SINGLE}. */
    public static final DataComponentType<DrillMode> MODE = DataComponentType.<DrillMode>builder()
            .persistent(DrillMode.CODEC).networkSynchronized(DrillMode.STREAM_CODEC).build();

    private DrillComponents() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.DATA_COMPONENT_TYPE,
                Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "drill_mode"), () -> MODE);
    }
}
