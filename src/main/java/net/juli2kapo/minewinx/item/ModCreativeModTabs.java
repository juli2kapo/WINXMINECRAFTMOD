package net.juli2kapo.minewinx.item;

import net.juli2kapo.minewinx.MineWinx;
import net.juli2kapo.minewinx.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeModTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MineWinx.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MINEWINXORESTAB =
            CREATIVE_MODE_TAB.register("minewinxore_tab", () -> CreativeModeTab.builder()
                    .icon(()-> new ItemStack(ModItems.LOWQMANACRYSTAL.get()))
                    .title(Component.translatable("creativetab.minewinxore_tab"))
                    .displayItems((itemDisplayParameters, output) -> {
                        output.accept(ModBlocks.MANA_CRYSTAL_ORE.get());
                        output.accept(ModBlocks.DEEPSLATE_MANA_CRYSTAL_ORE.get());
                        output.accept(ModItems.LOWQMANACRYSTAL.get());
                        output.accept(ModItems.MEDIUMQMANACRYSTAL.get());
                        output.accept(ModItems.HIGHQMANACRYSTAL.get());
                        output.accept(ModItems.MANARADAR.get());
                        output.accept(ModItems.FIRESTAGE1.get());
                        output.accept(ModItems.FIRESTAGE2.get());
                        output.accept(ModItems.FIRESTAGE3.get());
                        output.accept(ModItems.WATERSTAGE1.get());
                        output.accept(ModItems.WATERSTAGE2.get());
                        output.accept(ModItems.WATERSTAGE3.get());
                        output.accept(ModItems.NATURESTAGE1.get());
                        output.accept(ModItems.NATURESTAGE2.get());
                        output.accept(ModItems.NATURESTAGE3.get());
                        output.accept(ModItems.ICESTAGE1.get());
                        output.accept(ModItems.ICESTAGE2.get());
                        output.accept(ModItems.ICESTAGE3.get());
                        output.accept(ModItems.MUSICSTAGE1.get());
                        output.accept(ModItems.MUSICSTAGE2.get());
                        output.accept(ModItems.MUSICSTAGE3.get());
                        output.accept(ModItems.TECHNOLOGYSTAGE1.get());
                        output.accept(ModItems.TECHNOLOGYSTAGE2.get());
                        output.accept(ModItems.TECHNOLOGYSTAGE3.get());
                        output.accept(ModItems.CLEANSER.get());
                    })
                    .build());

    public static void register(IEventBus eventBus){
        CREATIVE_MODE_TAB.register(eventBus);
    }
}
