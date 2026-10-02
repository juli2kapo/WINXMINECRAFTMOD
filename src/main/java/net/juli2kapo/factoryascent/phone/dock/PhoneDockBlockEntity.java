package net.juli2kapo.factoryascent.phone.dock;

import net.juli2kapo.factoryascent.capsule.DeviceBlockEntity;
import net.juli2kapo.factoryascent.phone.FactoryPhoneItem;
import net.juli2kapo.factoryascent.phone.PhoneContent;
import net.juli2kapo.factoryascent.phone.PhoneMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Phone Dock: a desk computer for the Factory Phone. Put the phone in its cradle and written Link
 * Cards in the card reader: each card's link is added to the phone and the card comes out blank in
 * the out tray. The screen lists every link on the phone with a button to remove it. With FE the
 * dock also charges the phone.
 */
public class PhoneDockBlockEntity extends DeviceBlockEntity {
    public static final int PHONE = 0, CARD_IN = 1, CARD_OUT = 2;
    public static final int CAPACITY = 100_000, MAX_INSERT = 4_096, CHARGE_RATE = 1_000;
    /** Last card result: none, added, and the reasons a card stays in the reader. */
    public static final int R_NONE = 0, R_ADDED = 1, R_NO_PHONE = 2, R_LIMIT = 3, R_TRAY_FULL = 4, R_SAME_NETWORK = 5;

    private int result = R_NONE;
    private boolean charging;

    public PhoneDockBlockEntity(BlockPos pos, BlockState state) {
        super(DockContent.PHONE_DOCK_BE.get(), pos, state, 3, CAPACITY, MAX_INSERT);
    }

    @Override
    public boolean isValid(int slot, ItemResource resource) {
        return switch (slot) {
            case PHONE -> resource.is(PhoneContent.FACTORY_PHONE.get());
            case CARD_IN -> resource.is(DockContent.LINK_CARD_ITEM.get());
            default -> resource.is(DockContent.LINK_CARD_ITEM.get()) && !resource.toStack(1).has(DockContent.LINK_CARD.get());
        };
    }

    @Override
    public int slotLimit(int slot) {
        return slot == PHONE ? 1 : 16;
    }

    public int result() {
        return result;
    }

    public boolean charging() {
        return charging;
    }

    public ItemStack phone() {
        return inventory.stack(PHONE);
    }

    private @Nullable EnergyHandler phoneBattery() {
        if (phone().isEmpty()) return null;
        return ItemAccess.forHandlerIndex(inventory, PHONE).getCapability(Capabilities.Energy.ITEM);
    }

    /** The phone's charge in thousandths, or -1 without a phone. */
    public int chargePermille() {
        EnergyHandler battery = phoneBattery();
        if (battery == null || battery.getCapacityAsLong() <= 0) return -1;
        return (int) (battery.getAmountAsLong() * 1000 / battery.getCapacityAsLong());
    }

    @Override
    public void serverTick(ServerLevel level) {
        readCard(level);
        charging = false;
        EnergyHandler battery = phoneBattery();
        if (battery != null && energy != null && energy.getAmountAsInt() > 0) {
            int moved;
            try (Transaction tx = Transaction.openRoot()) {
                moved = battery.insert(Math.min(energy.getAmountAsInt(), CHARGE_RATE), tx);
                tx.commit();
            }
            if (moved > 0) {
                pay(moved);
                charging = true;
            }
        }
        setActive(!phone().isEmpty());
    }

    /** Reads the card in the reader into the phone, if there is one of each. */
    private void readCard(ServerLevel level) {
        ItemStack card = inventory.stack(CARD_IN);
        PhoneMemory.Link link = LinkCardItem.link(card);
        if (link == null) return;
        if (phone().isEmpty()) {
            result = R_NO_PHONE;
            return;
        }
        ItemStack out = inventory.stack(CARD_OUT);
        if (!out.isEmpty() && (!out.is(DockContent.LINK_CARD_ITEM.get()) || LinkCardItem.link(out) != null
                || out.getCount() >= LinkCardItem.BLANK_STACK)) {
            result = R_TRAY_FULL;
            return;
        }
        Component error = FactoryPhoneItem.addLink(level.getServer(), phone(), link);
        if (error != null) {
            result = error.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                    && t.getKey().contains("network_already") ? R_SAME_NETWORK : R_LIMIT;
            return;
        }
        inventory.changed();
        ItemStack blank = card.copyWithCount(1);
        LinkCardItem.wipe(blank);
        card.shrink(1);
        if (out.isEmpty()) inventory.setStack(CARD_OUT, blank);
        else out.grow(1);
        inventory.changed();
        result = R_ADDED;
        level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.7f, 1.6f);
    }

    /** Removes the phone's link number {@code index} (the dock screen's remove buttons). */
    public boolean removeLink(int index) {
        ItemStack phone = phone();
        if (phone.isEmpty()) return false;
        PhoneMemory memory = PhoneMemory.of(phone);
        if (index < 0 || index >= memory.links().size()) return false;
        memory.without(memory.links().get(index).pos()).store(phone);
        inventory.changed();
        result = R_NONE;
        if (level != null) level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.6f, 0.8f);
        return true;
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new PhoneDockMenu(id, inventory, this);
    }
}
