package net.juli2kapo.minewinx.datagen;

import net.juli2kapo.minewinx.MineWinx;
import net.juli2kapo.minewinx.item.ModItems;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.model.generators.ItemModelBuilder;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.RegistryObject;

public class ModItemModelProvider extends ItemModelProvider {
    public ModItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, MineWinx.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        simpleItem(ModItems.LOWQMANACRYSTAL);
        simpleItem(ModItems.MEDIUMQMANACRYSTAL);
        simpleItem(ModItems.HIGHQMANACRYSTAL);
        seedItem(ModItems.FIRESTAGE1);
        seedItem(ModItems.FIRESTAGE2);
        seedItem(ModItems.FIRESTAGE3);
        seedItem(ModItems.WATERSTAGE1);
        seedItem(ModItems.WATERSTAGE2);
        seedItem(ModItems.WATERSTAGE3);
        seedItem(ModItems.NATURESTAGE1);
        seedItem(ModItems.NATURESTAGE2);
        seedItem(ModItems.NATURESTAGE3);
        seedItem(ModItems.ICESTAGE1);
        seedItem(ModItems.ICESTAGE2);
        seedItem(ModItems.ICESTAGE3);
        seedItem(ModItems.MUSICSTAGE1);
        seedItem(ModItems.MUSICSTAGE2);
        seedItem(ModItems.MUSICSTAGE3);
        seedItem(ModItems.TECHNOLOGYSTAGE1);
        seedItem(ModItems.TECHNOLOGYSTAGE2);
        seedItem(ModItems.TECHNOLOGYSTAGE3);
        seedItem(ModItems.SUNANDMOONSTAGE1);
        seedItem(ModItems.SUNANDMOONSTAGE2);
        seedItem(ModItems.SUNANDMOONSTAGE3);
        seedItem(ModItems.STORMSTAGE1);
        seedItem(ModItems.STORMSTAGE2);
        seedItem(ModItems.STORMSTAGE3);
        simpleItem(ModItems.MANARADAR);
        simpleItem(ModItems.TECNO_HELMET);
        simpleItem(ModItems.TECNO_CHESTPLATE);
        simpleItem(ModItems.TECNO_LEGGINGS);
        simpleItem(ModItems.TECNO_BOOTS);
        simpleItem(ModItems.ORIGIN_SAPLING);
        simpleItem(ModItems.CLEANSER);
    }

    private ItemModelBuilder simpleItem(RegistryObject<Item> item){
        return withExistingParent(item.getId().getPath(),
                new ResourceLocation("item/generated")).texture("layer0",
                new ResourceLocation(MineWinx.MOD_ID,"item/" + item.getId().getPath()));


    }

    /**
     * Semilla: ícono plano en el inventario (gui) y modelo 3D en el resto de
     * las vistas (mano, piso, marco) vía SeedItemRenderer.
     */
    private void seedItem(RegistryObject<Item> item) {
        String name = item.getId().getPath();
        getBuilder(name)
                .customLoader(net.minecraftforge.client.model.generators.loaders.SeparateTransformsModelBuilder::begin)
                .base(nested().parent(new net.minecraftforge.client.model.generators.ModelFile.UncheckedModelFile("builtin/entity"))
                        .transforms()
                        .transform(net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND).rotation(0, 0, 0).translation(0, 3, 1).scale(0.45F).end()
                        .transform(net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND).rotation(0, 0, 0).translation(0, 3, 1).scale(0.45F).end()
                        .transform(net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND).rotation(0, 0, 0).translation(0, 4, 0).scale(0.3F).end()
                        .transform(net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND).rotation(0, 0, 0).translation(0, 4, 0).scale(0.3F).end()
                        .transform(net.minecraft.world.item.ItemDisplayContext.GROUND).translation(0, 2, 0).scale(0.4F).end()
                        .transform(net.minecraft.world.item.ItemDisplayContext.FIXED).rotation(0, 180, 0).scale(0.6F).end()
                        .transform(net.minecraft.world.item.ItemDisplayContext.HEAD).translation(0, 13, 0).scale(0.6F).end()
                        .end())
                .perspective(net.minecraft.world.item.ItemDisplayContext.GUI, nested()
                        .parent(new net.minecraftforge.client.model.generators.ModelFile.UncheckedModelFile("item/generated"))
                        .texture("layer0", new ResourceLocation(MineWinx.MOD_ID, "item/" + name)))
                .end();
    }
}
