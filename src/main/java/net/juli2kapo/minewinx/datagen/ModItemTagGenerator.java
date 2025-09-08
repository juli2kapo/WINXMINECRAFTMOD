package net.juli2kapo.minewinx.datagen;

import net.juli2kapo.minewinx.MineWinx;
import net.juli2kapo.minewinx.block.ModBlocks;
import net.juli2kapo.minewinx.item.ModItems;
import net.juli2kapo.minewinx.util.ModTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class ModItemTagGenerator extends ItemTagsProvider {
    public ModItemTagGenerator(PackOutput p_275343_, CompletableFuture<HolderLookup.Provider> p_275729_, CompletableFuture<TagLookup<Block>> p_275322_, @Nullable ExistingFileHelper existingFileHelper) {
        super(p_275343_, p_275729_, p_275322_, MineWinx.MOD_ID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider pProvider) {

        this.tag(ModTags.Items.ELEMENTSSEEDS)
                .add(
                        ModItems.FIRESTAGE1.get(),
                        ModItems.FIRESTAGE2.get(),
                        ModItems.FIRESTAGE3.get(),
                        ModItems.WATERSTAGE1.get(),
                        ModItems.WATERSTAGE2.get(),
                        ModItems.WATERSTAGE3.get(),
                        ModItems.NATURESTAGE1.get(),
                        ModItems.NATURESTAGE2.get(),
                        ModItems.NATURESTAGE3.get(),
                        ModItems.ICESTAGE1.get(),
                        ModItems.ICESTAGE2.get(),
                        ModItems.ICESTAGE3.get(),
                        ModItems.MUSICSTAGE1.get(),
                        ModItems.MUSICSTAGE2.get(),
                        ModItems.MUSICSTAGE3.get()
                );
    }
}
