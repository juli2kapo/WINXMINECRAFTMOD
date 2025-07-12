package net.juli2kapo.minewinx.datagen.loot;

import net.juli2kapo.minewinx.block.ModBlocks;
import net.juli2kapo.minewinx.item.ModItems;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.minecraftforge.registries.RegistryObject;

import java.util.Set;

public class ModBlockLootTables extends BlockLootSubProvider {
    public ModBlockLootTables() {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags());
    }

    @Override
    protected void generate() {
        this.add(ModBlocks.DEEPSLATE_MANA_CRYSTAL_ORE.get(), block -> createDiamondP1LikeOreDrops(ModBlocks.DEEPSLATE_MANA_CRYSTAL_ORE.get(), ModItems.LOWQMANACRYSTAL.get()));
        this.add(ModBlocks.MANA_CRYSTAL_ORE.get(), block -> createDiamondP1LikeOreDrops(ModBlocks.MANA_CRYSTAL_ORE.get(), ModItems.LOWQMANACRYSTAL.get()));
    }

    protected LootTable.Builder createDiamondP1LikeOreDrops(Block pBlock, Item item) {
        return createSilkTouchDispatchTable(
                pBlock, this.applyExplosionDecay(
                        pBlock, LootItem.lootTableItem(
                                item).apply(SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 2.0F))).apply(ApplyBonusCount.addOreBonusCount(Enchantments.BLOCK_FORTUNE))));
    }


    @Override
    protected Iterable<Block> getKnownBlocks() {
        return ModBlocks.BLOCKS.getEntries().stream().map(RegistryObject::get)::iterator;
    }
}
