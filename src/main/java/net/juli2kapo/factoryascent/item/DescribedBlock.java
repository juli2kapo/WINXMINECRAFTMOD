package net.juli2kapo.factoryascent.item;

import java.util.function.Consumer;
import net.minecraft.network.chat.Component;

/** A block that writes its own lines into its item's tooltip (see {@link FactoryBlockItem}). */
public interface DescribedBlock {
    void describe(Consumer<Component> tooltip);
}
