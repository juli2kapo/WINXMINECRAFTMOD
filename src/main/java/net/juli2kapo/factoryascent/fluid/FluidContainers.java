package net.juli2kapo.factoryascent.fluid;

import net.juli2kapo.factoryascent.power.PowerContent;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

/**
 * Buckets and cells in a machine's or tank's container slots: what a full one holds and leaves
 * behind, and what an empty one becomes when filled. (Pipes and players' hands use the item
 * capabilities instead; these are the same rules for slots.)
 */
public final class FluidContainers {
    private FluidContainers() {}

    /** What emptying one item gives: the fluid, how much, and what is left (may be empty). */
    public record Drain(Fluid fluid, int amount, ItemStack rest) {}

    /** What filling one empty item takes and makes. */
    public record Fill(Fluid fluid, int amount, ItemStack result) {}

    public static @Nullable Drain drain(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (stack.getItem() instanceof BucketItem bucket && bucket.content != Fluids.EMPTY) {
            return new Drain(bucket.content, 1000, new ItemStack(Items.BUCKET));
        }
        CellFluidHandler.Content c = CellFluidHandler.contentOf(stack.getItem());
        if (c != null) return new Drain(c.fluid(), c.amount(), c.refillable() ? new ItemStack(PowerContent.EMPTY_CELL.get()) : ItemStack.EMPTY);
        return null;
    }

    public static @Nullable Fill fill(ItemStack stack, Fluid fluid) {
        if (stack.isEmpty() || fluid == Fluids.EMPTY) return null;
        if (stack.is(Items.BUCKET)) {
            Item full = fluid.getBucket();
            return full == null || full == Items.AIR ? null : new Fill(fluid, 1000, new ItemStack(full));
        }
        if (stack.is(PowerContent.EMPTY_CELL.get())) {
            Item cell = CellFluidHandler.cellFor(fluid);
            return cell == null ? null : new Fill(fluid, CellFluidHandler.CELL, new ItemStack(cell));
        }
        return null;
    }

    public static boolean isContainer(ItemStack stack) {
        return drain(stack) != null || stack.is(Items.BUCKET) || stack.is(PowerContent.EMPTY_CELL.get());
    }

    /**
     * One step of a container slot pair: empties a full container from {@code in} into the tank,
     * or (if {@code allowFill}) fills an empty one from the tank, putting what results into
     * {@code out}. Returns true if something happened.
     */
    public static boolean process(FluidTank tank, ItemStack in, ItemStack out, java.util.function.Consumer<ItemStack> setOut,
                                  Runnable changed, boolean allowDrainIntoTank, boolean allowFill) {
        if (in.isEmpty()) return false;
        Drain d = allowDrainIntoTank ? drain(in) : null;
        if (d != null && tank.isValid(0, FluidResource.of(d.fluid())) && (tank.isEmpty() || tank.holds(d.fluid()))
                && tank.space() >= d.amount() && fits(out, d.rest())) {
            tank.forceFill(d.fluid(), d.amount());
            in.shrink(1);
            merge(out, d.rest(), setOut);
            changed.run();
            return true;
        }
        if (allowFill && !tank.isEmpty()) {
            Fill f = fill(in, tank.fluid());
            if (f != null && tank.amount() >= f.amount() && fits(out, f.result())) {
                tank.drain(f.amount());
                in.shrink(1);
                merge(out, f.result(), setOut);
                changed.run();
                return true;
            }
        }
        return false;
    }

    private static boolean fits(ItemStack out, ItemStack add) {
        return add.isEmpty() || out.isEmpty()
                || ItemStack.isSameItemSameComponents(out, add) && out.getCount() + add.getCount() <= out.getMaxStackSize();
    }

    private static void merge(ItemStack out, ItemStack add, java.util.function.Consumer<ItemStack> setOut) {
        if (add.isEmpty()) return;
        if (out.isEmpty()) setOut.accept(add.copy());
        else out.grow(add.getCount());
    }
}
