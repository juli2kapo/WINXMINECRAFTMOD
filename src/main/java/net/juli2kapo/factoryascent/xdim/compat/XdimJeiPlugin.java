package net.juli2kapo.factoryascent.xdim.compat;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.xdim.XdimContent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** JEI: an information page for each link explaining channels, faces, reach and costs. */
@JeiPlugin
public final class XdimJeiPlugin implements IModPlugin {
    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "jei_xdim");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addIngredientInfo(XdimContent.ENDER_LINK_ITEM.get(),
                Component.translatable("jei.factoryascent.xdim.ender_link"),
                Component.translatable("jei.factoryascent.xdim.howto"),
                Component.translatable("jei.factoryascent.xdim.lava"));
        registration.addIngredientInfo(XdimContent.QUANTUM_ENTANGLER_ITEM.get(),
                Component.translatable("jei.factoryascent.xdim.quantum_entangler"),
                Component.translatable("jei.factoryascent.xdim.howto"),
                Component.translatable("jei.factoryascent.xdim.cost"));
    }
}
