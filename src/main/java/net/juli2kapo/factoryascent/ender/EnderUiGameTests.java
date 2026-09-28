package net.juli2kapo.factoryascent.ender;

import net.juli2kapo.factoryascent.ui.ScreenPayloads;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** GameTests for the ender screens' server side (registered in ModGameTests.TESTS). */
public final class EnderUiGameTests {
    private EnderUiGameTests() {}

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper h, BlockPos near, boolean survival) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        if (survival) {
            p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            p.getAbilities().instabuild = false;
        }
        BlockPos at = h.absolutePos(near);
        p.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        return p;
    }

    /** Only the owner renames a beacon; the name is cleaned and cut, and charms on the owner pick it up. */
    public static void beaconRename(GameTestHelper h) {
        BlockPos rel = new BlockPos(4, 2, 4);
        h.setBlock(rel, EnderContent.ENDER_BEACON.get());
        EnderBeaconBlockEntity beacon = h.getBlockEntity(rel, EnderBeaconBlockEntity.class);
        BlockPos pos = h.absolutePos(rel);
        ServerPlayer owner = player(h, new BlockPos(2, 2, 2), false);
        ServerPlayer stranger = player(h, new BlockPos(2, 2, 3), false);
        beacon.setOwner(owner.getUUID());
        ItemStack charm = new ItemStack(EnderContent.RECALL_CHARM.get());
        charm.set(EnderContent.LINKED_BEACON.get(), GlobalPos.of(h.getLevel().dimension(), pos));
        owner.getInventory().setItem(9, charm);

        h.assertFalse(EnderBeaconBlockEntity.handleRename(stranger, pos, "Mine now"), "a stranger must not rename it");
        h.assertTrue(beacon.name().isEmpty(), "the name must be unchanged, is " + beacon.name());
        h.assertTrue(EnderBeaconBlockEntity.handleRename(owner, pos, "  Home Base  "), "the owner must rename it");
        h.assertTrue(beacon.name().equals("Home Base"), "the name must be trimmed, is '" + beacon.name() + "'");
        ItemStack held = owner.getInventory().getItem(9);
        h.assertTrue("Home Base".equals(held.get(EnderContent.LINKED_BEACON_NAME.get())),
                "the owner's linked charm must show the new name, has " + held.get(EnderContent.LINKED_BEACON_NAME.get()));
        EnderBeaconBlockEntity.handleRename(owner, pos, "x".repeat(80));
        h.assertTrue(beacon.name().length() == EnderBeaconBlockEntity.MAX_NAME, "long names must be cut");
        EnderBeaconBlockEntity.handleRename(owner, pos, "");
        h.assertTrue(beacon.name().isEmpty(), "an empty name must reset it");
        owner.discard();
        stranger.discard();
        h.succeed();
    }

    /** The charm screen's Unlink forgets the beacon, only for a charm really in that hand. */
    public static void charmUnlink(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(2, 2, 2), false);
        ItemStack charm = new ItemStack(EnderContent.RECALL_CHARM.get());
        charm.set(EnderContent.LINKED_BEACON.get(), GlobalPos.of(h.getLevel().dimension(), h.absolutePos(new BlockPos(5, 2, 5))));
        charm.set(EnderContent.LINKED_BEACON_NAME.get(), "Home");
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STICK));
        h.assertFalse(RecallCharmItem.applyAction(p, 1, ScreenPayloads.CharmAction.UNLINK), "a stick is not a charm");
        p.setItemInHand(InteractionHand.MAIN_HAND, charm);
        h.assertFalse(RecallCharmItem.applyAction(p, 7, ScreenPayloads.CharmAction.UNLINK), "a bad hand index must be ignored");
        h.assertTrue(RecallCharmItem.applyAction(p, 0, ScreenPayloads.CharmAction.UNLINK), "unlink must work on a held linked charm");
        ItemStack held = p.getMainHandItem();
        h.assertFalse(held.has(EnderContent.LINKED_BEACON.get()) || held.has(EnderContent.LINKED_BEACON_NAME.get()),
                "the charm must forget its beacon");
        h.assertFalse(RecallCharmItem.applyAction(p, 0, ScreenPayloads.CharmAction.UNLINK), "nothing left to unlink");
        p.discard();
        h.succeed();
    }

    /** The anchor screen: a pearl goes in through the slot; only the owner switches it off, which frees the chunks. */
    public static void anchorMenu(GameTestHelper h) {
        BlockPos rel = new BlockPos(4, 2, 4);
        var lower = EnderContent.ENDER_ANCHOR.get().defaultBlockState();
        h.setBlock(rel, lower);
        h.setBlock(rel.above(), lower.setValue(EnderAnchorBlock.HALF, DoubleBlockHalf.UPPER));
        EnderAnchorBlockEntity anchor = h.getBlockEntity(rel, EnderAnchorBlockEntity.class);
        ServerPlayer owner = player(h, new BlockPos(2, 2, 2), true);
        ServerPlayer stranger = player(h, new BlockPos(2, 2, 3), true);
        anchor.setOwner(owner.getUUID());

        EnderAnchorMenu menu = new EnderAnchorMenu(1, owner.getInventory(), anchor);
        ItemStack pearls = new ItemStack(Items.ENDER_PEARL, 3);
        ItemStack left = menu.getSlot(0).safeInsert(pearls);
        h.assertTrue(anchor.hasPearl(), "a pearl put in the slot must load the anchor");
        h.assertTrue(left.getCount() == 2, "only one pearl must be taken, left " + left.getCount());
        h.assertFalse(menu.getSlot(0).mayPickup(owner), "the pearl must not come back out");

        EnderAnchorMenu strangers = new EnderAnchorMenu(2, stranger.getInventory(), anchor);
        h.assertFalse(strangers.clickMenuButton(stranger, EnderAnchorMenu.BUTTON_TOGGLE), "a stranger must not switch it");
        h.assertTrue(anchor.isEnabled(), "still on");
        h.runAfterDelay(3, () -> {
            h.assertBlockProperty(rel, EnderAnchorBlock.ACTIVE, true);
            h.assertTrue(anchor.forcedRadius() >= 0, "a running anchor forces chunks");
            h.assertTrue(menu.clickMenuButton(owner, EnderAnchorMenu.BUTTON_TOGGLE), "the owner must switch it off");
            h.runAfterDelay(2, () -> {
                h.assertBlockProperty(rel, EnderAnchorBlock.ACTIVE, false);
                h.assertTrue(anchor.forcedRadius() == -1, "switched off, it must release its chunks");
                h.assertTrue(anchor.hasPearl(), "switched off, it keeps its pearl");
                owner.discard();
                stranger.discard();
                h.succeed();
            });
        });
    }
}
