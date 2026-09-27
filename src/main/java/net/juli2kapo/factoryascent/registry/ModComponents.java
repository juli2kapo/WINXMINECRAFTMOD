package net.juli2kapo.factoryascent.registry;

import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, FactoryAscent.MOD_ID);

    /** Forge Energy stored in a powered item (Electric Drill…). */
    public static final Supplier<DataComponentType<Integer>> ENERGY = COMPONENTS.registerComponentType("energy",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    private ModComponents() {}
}
