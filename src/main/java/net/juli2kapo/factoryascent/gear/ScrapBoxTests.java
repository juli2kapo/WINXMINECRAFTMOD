package net.juli2kapo.factoryascent.gear;

import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

/** Opening a Scrap Box must work with a full inventory: the loot lands on the ground. */
public final class ScrapBoxTests {
    private ScrapBoxTests() {}

    public static void opensWithFullInventory(GameTestHelper h) {
        @SuppressWarnings("removal")
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) inv.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        inv.setItem(inv.getSelectedSlot(), new ItemStack(GearContent.SCRAP_BOX.get(), 3));
        player.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(4.5, 2, 4.5)));
        GearContent.SCRAP_BOX.get().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        h.assertTrue(player.getMainHandItem().getCount() == 2, "one box must be opened, have " + player.getMainHandItem().getCount());
        h.runAfterDelay(2, () -> {
            List<ItemEntity> dropped = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(player.blockPosition()).inflate(4));
            h.assertTrue(!dropped.isEmpty(), "the loot must be dropped when the inventory is full");
            h.succeed();
        });
    }
}
