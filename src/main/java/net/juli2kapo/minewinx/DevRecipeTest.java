package net.juli2kapo.minewinx;

import net.juli2kapo.minewinx.item.ModItems;
import net.juli2kapo.minewinx.util.PlayerDataProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** TEMP: automated check of Tecno armor crafting/unequip (MINEWINX_RECIPETEST=1). Not for commit. */
@Mod.EventBusSubscriber(modid = MineWinx.MOD_ID)
public class DevRecipeTest {
    private static final boolean ON = System.getenv("MINEWINX_RECIPETEST") != null;
    private static int ticks;

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (!ON || event.phase != TickEvent.Phase.END) return;
        if (event.getServer().getPlayerList().getPlayers().isEmpty()) return;
        ServerPlayer p = event.getServer().getPlayerList().getPlayers().get(0);
        ticks++;
        if (ticks == 60) {
            p.setGameMode(GameType.SURVIVAL);
            PlayerDataProvider.setElement(p, "Fire");
            PlayerDataProvider.setStage(p, 3);
            MineWinx.LOGGER.info("[RecipeTest] non-tech result: '{}'", craftHelmet(p));
            PlayerDataProvider.setElement(p, "Technology");
            PlayerDataProvider.setStage(p, 2);
            MineWinx.LOGGER.info("[RecipeTest] tech stage2 result: '{}'", craftHelmet(p));
            PlayerDataProvider.setStage(p, 3);
            MineWinx.LOGGER.info("[RecipeTest] tech stage3 result: '{}'", craftHelmet(p));
            // equip while eligible, then lose the element
            p.getInventory().clearContent();
            p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.TECNO_HELMET.get()));
            PlayerDataProvider.setElement(p, "Water");
            for (String id : new String[]{"music_stage_3", "technology_stage_3", "sunandmoon_stage_3", "tecno_helmet"}) {
                MineWinx.LOGGER.info("[RecipeTest] recipe {} loaded: {}", id, event.getServer().getRecipeManager()
                        .byKey(ResourceLocation.fromNamespaceAndPath(MineWinx.MOD_ID, id)).isPresent());
            }
        }
        if (ticks == 70) {
            MineWinx.LOGGER.info("[RecipeTest] after losing element: head='{}' helmetInInventory={}",
                    p.getItemBySlot(EquipmentSlot.HEAD), p.getInventory().contains(new ItemStack(ModItems.TECNO_HELMET.get())));
            MineWinx.LOGGER.info("[RecipeTest] DONE");
            Runtime.getRuntime().halt(0);
        }
    }

    private static String craftHelmet(ServerPlayer p) {
        CraftingMenu menu = new CraftingMenu(99, p.getInventory(), ContainerLevelAccess.create(p.level(), p.blockPosition()));
        menu.getSlot(4).set(new ItemStack(Items.NETHERITE_HELMET));
        menu.getSlot(5).set(new ItemStack(Items.NETHER_STAR));
        menu.getSlot(6).set(new ItemStack(Items.DIAMOND_HELMET));
        menu.slotsChanged(menu.getSlot(4).container);
        return menu.getSlot(0).getItem().toString();
    }
}
