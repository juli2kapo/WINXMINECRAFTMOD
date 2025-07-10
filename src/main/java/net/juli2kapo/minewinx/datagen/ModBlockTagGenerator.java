package net.juli2kapo.minewinx.datagen;

import net.juli2kapo.minewinx.MineWinx;
import net.juli2kapo.minewinx.block.ModBlocks;
import net.juli2kapo.minewinx.item.ModItems;
import net.juli2kapo.minewinx.util.ModTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class ModBlockTagGenerator extends BlockTagsProvider {

    public ModBlockTagGenerator(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, MineWinx.MOD_ID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider pProvider) {
        this.tag(ModTags.Blocks.MANAORES)
                .add(ModBlocks.DEEPSLATE_MANA_CRYSTAL_ORE.get(), ModBlocks.MANA_CRYSTAL_ORE.get());

        this.tag(BlockTags.NEEDS_IRON_TOOL)
                .add(ModBlocks.DEEPSLATE_MANA_CRYSTAL_ORE.get(), ModBlocks.MANA_CRYSTAL_ORE.get());

        this.tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(ModBlocks.DEEPSLATE_MANA_CRYSTAL_ORE.get(), ModBlocks.MANA_CRYSTAL_ORE.get());
    }
}
