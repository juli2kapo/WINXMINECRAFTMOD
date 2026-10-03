package net.juli2kapo.factoryascent.stationkit;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * Cargo Pod (Orbital age): a 27-slot freight canister for the Launch Pad. Right-click it to load it
 * like a chest (or mount it empty and let hoppers and pipes fill it through the Launch Controller);
 * launched, it is unloaded into the cargo hold of your team's Station Core in orbit above the pad
 * ({@link StationKits#deliver}). Without a station there it refuses to launch.
 */
public class CargoPodItem extends Item {
    public static final int SLOTS = 27;

    public CargoPodItem(Properties properties) {
        super(properties);
    }

    /** The pod's contents (always {@link #SLOTS} stacks, copies). */
    public static NonNullList<ItemStack> contents(ItemStack pod) {
        NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ItemContainerContents c = pod.get(DataComponents.CONTAINER);
        if (c != null) c.copyInto(items);
        return items;
    }

    public static void setContents(ItemStack pod, NonNullList<ItemStack> items) {
        if (items.stream().allMatch(ItemStack::isEmpty)) pod.remove(DataComponents.CONTAINER);
        else pod.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
    }

    /** Whether a stack may ride in a pod (no pods or shulker boxes inside pods). */
    public static boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && !(stack.getItem() instanceof CargoPodItem) && stack.getItem().canFitInsideContainerItems();
    }

    /** How many of {@code stack} would fit into {@code items} (not changed). */
    public static int room(NonNullList<ItemStack> items, ItemStack stack) {
        if (!accepts(stack)) return 0;
        int room = 0;
        for (ItemStack s : items) {
            if (s.isEmpty()) room += stack.getMaxStackSize();
            else if (ItemStack.isSameItemSameComponents(s, stack)) room += Math.max(0, s.getMaxStackSize() - s.getCount());
        }
        return room;
    }

    /** Adds up to {@code count} of {@code stack} into {@code items}; returns how many went in. */
    public static int add(NonNullList<ItemStack> items, ItemStack stack, int count) {
        if (!accepts(stack)) return 0;
        int left = count;
        for (int i = 0; i < items.size() && left > 0; i++) {
            ItemStack s = items.get(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, stack)) {
                int n = Math.min(left, s.getMaxStackSize() - s.getCount());
                if (n > 0) {
                    s.grow(n);
                    left -= n;
                }
            }
        }
        for (int i = 0; i < items.size() && left > 0; i++) {
            if (items.get(i).isEmpty()) {
                int n = Math.min(left, stack.getMaxStackSize());
                items.set(i, stack.copyWithCount(n));
                left -= n;
            }
        }
        return count - left;
    }

    public static int stacks(ItemStack pod) {
        return (int) contents(pod).stream().filter(s -> !s.isEmpty()).count();
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack pod = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            sp.openMenu(new SimpleMenuProvider((id, inv, p) -> menu(id, inv, new PodContainer(pod, p, hand)), pod.getHoverName()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.factoryascent.cargo_pod").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.cargo_pod_how").withStyle(ChatFormatting.DARK_AQUA));
        int n = stacks(stack);
        tooltip.accept(Component.translatable("tooltip.factoryascent.cargo_pod_load", n, SLOTS)
                .withStyle(n > 0 ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
        int shown = 0;
        for (ItemStack s : contents(stack)) {
            if (s.isEmpty()) continue;
            if (shown++ >= 5) {
                tooltip.accept(Component.literal("  …").withStyle(ChatFormatting.DARK_GRAY));
                break;
            }
            tooltip.accept(Component.literal("  " + s.getCount() + "× ").append(s.getHoverName()).withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * A chest screen over the pod, whose slots refuse pods and shulker boxes (vanilla chest slots
     * accept anything, so a pod could be put inside itself and vanish).
     */
    static ChestMenu menu(int id, net.minecraft.world.entity.player.Inventory inventory, PodContainer pod) {
        ChestMenu menu = ChestMenu.threeRows(id, inventory, pod);
        for (int i = 0; i < SLOTS; i++) {
            net.minecraft.world.inventory.Slot old = menu.slots.get(i);
            net.minecraft.world.inventory.Slot slot = new net.minecraft.world.inventory.Slot(pod, old.getContainerSlot(), old.x, old.y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return accepts(stack);
                }
            };
            slot.index = i;
            menu.slots.set(i, slot);
        }
        return menu;
    }

    /** The open pod: edits go straight back into the held stack. */
    static final class PodContainer extends SimpleContainer {
        private final ItemStack pod;
        private final Player player;
        private final InteractionHand hand;

        PodContainer(ItemStack pod, Player player, InteractionHand hand) {
            super(SLOTS);
            this.pod = pod;
            this.player = player;
            this.hand = hand;
            NonNullList<ItemStack> items = contents(pod);
            for (int i = 0; i < SLOTS; i++) super.setItem(i, items.get(i));
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return accepts(stack);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
            for (int i = 0; i < SLOTS; i++) items.set(i, getItem(i).copy());
            setContents(pod, items);
        }

        @Override
        public boolean stillValid(Player p) {
            return p == player && player.getItemInHand(hand) == pod && !pod.isEmpty();
        }
    }
}
