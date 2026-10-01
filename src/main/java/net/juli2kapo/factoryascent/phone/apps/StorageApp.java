package net.juli2kapo.factoryascent.phone.apps;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.juli2kapo.factoryascent.phone.PhoneApp;
import net.juli2kapo.factoryascent.phone.PhoneContext;
import net.juli2kapo.factoryascent.phone.PhoneDevices;
import net.juli2kapo.factoryascent.phone.PhoneMemory;
import net.juli2kapo.factoryascent.phone.PhoneService;
import net.juli2kapo.factoryascent.storagenet.StorageNet;
import net.juli2kapo.factoryascent.storagenet.StorageTerminalBlockEntity;
import net.juli2kapo.factoryascent.storagenet.TerminalMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Storage: the linked storage network over the uplink, like a Wireless Terminal. The app shows the
 * network's state, fill level and its most plentiful items; "Open terminal" opens the linked
 * Storage Terminal's full screen remotely (the session lasts while the phone stays in hand, charged,
 * with signal: see {@link #allowsRemote}). Link it by sneak-using the phone on a terminal or controller.
 */
public final class StorageApp implements PhoneApp {
    private static final int TOP_ITEMS = 12;

    @Override
    public String id() {
        return "storage";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public boolean needsSignal() {
        return true;
    }

    @Override
    public boolean live() {
        return true;
    }

    @Override
    public CompoundTag data(PhoneContext ctx) {
        CompoundTag tag = new CompoundTag();
        PhoneMemory.Link link = ctx.memory().storage();
        if (link == null) return tag;
        BlockPos p = link.pos().pos();
        tag.putBoolean("linked", true);
        tag.putInt("x", p.getX());
        tag.putInt("y", p.getY());
        tag.putInt("z", p.getZ());
        String why = PhoneDevices.unreachable(ctx.player(), link.pos(), ctx.signal());
        StorageTerminalBlockEntity terminal = why == null ? terminal(ctx.player(), link.pos()) : null;
        if (why == null && terminal == null) why = "gui.factoryascent.phone.gone";
        if (why != null) {
            tag.putString("problem", why);
            return tag;
        }
        StorageNet net = terminal.network();
        StorageNet.Status status = net == null ? StorageNet.Status.NO_CONTROLLER : net.status();
        tag.putString("status", status.key());
        tag.putBoolean("online", status == StorageNet.Status.ONLINE);
        if (net == null) return tag;
        tag.putLong("used", net.usedItems());
        tag.putLong("capacity", net.capacityItems());
        tag.putInt("devices", net.deviceCount());
        List<StorageNet.Stored> contents = new ArrayList<>(net.contents());
        tag.putInt("types", contents.size());
        tag.putInt("maxTypes", net.typeCapacity());
        contents.sort(Comparator.comparingLong(StorageNet.Stored::count).reversed());
        ListTag items = new ListTag();
        for (StorageNet.Stored s : contents.subList(0, Math.min(TOP_ITEMS, contents.size()))) {
            CompoundTag item = new CompoundTag();
            item.putString("id", BuiltInRegistries.ITEM.getKey(s.resource().getItem()).toString());
            item.putLong("count", s.count());
            items.add(item);
        }
        tag.put("items", items);
        return tag;
    }

    @Override
    public @Nullable Component action(PhoneContext ctx, String action, CompoundTag args) {
        if (!action.equals("terminal")) return null;
        PhoneMemory.Link link = ctx.memory().storage();
        if (link == null) return Component.translatable("gui.factoryascent.phone.storage.unlinked").withStyle(ChatFormatting.RED);
        String why = PhoneDevices.unreachable(ctx.player(), link.pos(), ctx.signal());
        StorageTerminalBlockEntity terminal = why == null ? terminal(ctx.player(), link.pos()) : null;
        if (terminal == null) {
            return Component.translatable(why == null ? "gui.factoryascent.phone.gone" : why).withStyle(ChatFormatting.RED);
        }
        BlockPos pos = terminal.getBlockPos();
        ctx.player().openMenu(new SimpleMenuProvider((id, inv, pl) -> new TerminalMenu(id, inv, terminal, true),
                terminal.getDisplayName()), buf -> buf.writeBlockPos(pos));
        return null;
    }

    private static @Nullable StorageTerminalBlockEntity terminal(ServerPlayer player, GlobalPos pos) {
        if (!pos.dimension().equals(player.level().dimension())) return null;
        return player.level().getBlockEntity(pos.pos()) instanceof StorageTerminalBlockEntity t ? t : null;
    }

    /**
     * The remote-access rule chained into {@link TerminalMenu#remoteAccess}: the player holds a
     * charged phone linked to this terminal and can still reach it (uplink signal or short range).
     */
    public static boolean allowsRemote(Player player, StorageTerminalBlockEntity terminal) {
        if (!(player instanceof ServerPlayer sp) || terminal.getLevel() == null) return false;
        GlobalPos here = GlobalPos.of(terminal.getLevel().dimension(), terminal.getBlockPos());
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = sp.getItemInHand(hand);
            if (!PhoneService.isPhone(stack) || PhoneService.battery(stack) <= 0) continue;
            PhoneMemory.Link link = PhoneMemory.of(stack).storage();
            if (link != null && link.pos().equals(here)) {
                return PhoneDevices.signal(sp) && PhoneDevices.unreachable(sp, here, true) == null;
            }
        }
        return false;
    }
}
