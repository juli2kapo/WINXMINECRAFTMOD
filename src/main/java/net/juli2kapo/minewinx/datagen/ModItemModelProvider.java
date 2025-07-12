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
        simpleItem(ModItems.FIRESTAGE1);
        simpleItem(ModItems.FIRESTAGE2);
        simpleItem(ModItems.FIRESTAGE3);
        simpleItem(ModItems.WATERSTAGE1);
        simpleItem(ModItems.WATERSTAGE2);
        simpleItem(ModItems.WATERSTAGE3);
        simpleItem(ModItems.NATURESTAGE1);
        simpleItem(ModItems.NATURESTAGE2);
        simpleItem(ModItems.NATURESTAGE3);
        simpleItem(ModItems.MANARADAR);
    }

    private ItemModelBuilder simpleItem(RegistryObject<Item> item){
        return withExistingParent(item.getId().getPath(),
                new ResourceLocation("item/generated")).texture("layer0",
                new ResourceLocation(MineWinx.MOD_ID,"item/" + item.getId().getPath()));


    }
}
