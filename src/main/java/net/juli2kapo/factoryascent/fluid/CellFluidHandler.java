package net.juli2kapo.factoryascent.fluid;

import java.util.function.Supplier;
import net.juli2kapo.factoryascent.power.PowerContent;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.transfer.ItemAccessResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * Fluid view of the power ladder's cells (and of the items that are "solid" fluid units), so
 * pipes, tanks and machines can fill and empty them:
 * <ul>
 * <li>Empty Cell &harr; Deuterium / Tritium / Coolant Cell: 1000 mB of deuterium, tritium or coolant;</li>
 * <li>Helium-3: 1000 mB of helium-3 gas (emptying it uses the item up);</li>
 * <li>Rocket Fuel: 250 mB of rocket fuel (emptying it uses the item up).</li>
 * </ul>
 */
public final class CellFluidHandler extends ItemAccessResourceHandler<FluidResource> {
    public static final int CELL = 1000, ROCKET_FUEL_ITEM = 250;

    public CellFluidHandler(ItemAccess access) {
        super(access, 1);
    }

    private static Item emptyCell() {
        return PowerContent.EMPTY_CELL.get();
    }

    /** The fluid and amount one item of this kind holds, or null. */
    public static @Nullable Content contentOf(Item item) {
        if (item == PowerContent.DEUTERIUM_CELL.get()) return new Content(ModFluids.DEUTERIUM.source(), CELL, true);
        if (item == PowerContent.TRITIUM_CELL.get()) return new Content(ModFluids.TRITIUM.source(), CELL, true);
        if (item == PowerContent.COOLANT_CELL.get()) return new Content(ModFluids.COOLANT.source(), CELL, true);
        if (item == PowerContent.helium3()) return new Content(ModFluids.HELIUM_3.source(), CELL, false);
        if (item == net.juli2kapo.factoryascent.orbital.OrbitalContent.ROCKET_FUEL.get()) {
            return new Content(ModFluids.ROCKET_FUEL.source(), ROCKET_FUEL_ITEM, false);
        }
        return null;
    }

    /** The full cell for a fluid, or null if no cell holds it. */
    public static @Nullable Item cellFor(Fluid fluid) {
        if (fluid.isSame(ModFluids.DEUTERIUM.source())) return PowerContent.DEUTERIUM_CELL.get();
        if (fluid.isSame(ModFluids.TRITIUM.source())) return PowerContent.TRITIUM_CELL.get();
        if (fluid.isSame(ModFluids.COOLANT.source())) return PowerContent.COOLANT_CELL.get();
        return null;
    }

    public record Content(Fluid fluid, int amount, boolean refillable) {}

    @Override
    protected FluidResource getResourceFrom(ItemResource access, int index) {
        Content c = contentOf(access.getItem());
        return c == null ? FluidResource.EMPTY : FluidResource.of(c.fluid());
    }

    @Override
    protected int getAmountFrom(ItemResource access, int index) {
        Content c = contentOf(access.getItem());
        return c == null ? 0 : c.amount();
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        Item item = itemAccess.getResource().getItem();
        if (item == emptyCell()) return cellFor(resource.getFluid()) != null;
        Content c = contentOf(item);
        return c != null && resource.getFluid().isSame(c.fluid());
    }

    @Override
    protected int getCapacity(int index, FluidResource resource) {
        Item item = itemAccess.getResource().getItem();
        if (item == emptyCell()) return CELL;
        Content c = contentOf(item);
        return c == null ? 0 : c.amount();
    }

    @Override
    protected @Nullable ItemResource update(ItemResource access, int index, FluidResource resource, int newAmount) {
        Item item = access.getItem();
        if (newAmount == 0) {
            Content c = contentOf(item);
            if (c == null) return ItemResource.EMPTY;
            return c.refillable() ? ItemResource.of(emptyCell()) : null; // non-refillable items are used up
        }
        if (newAmount != CELL) return ItemResource.EMPTY; // whole cells only
        Item full = cellFor(resource.getFluid());
        return full == null ? ItemResource.EMPTY : ItemResource.of(full);
    }

    public static Supplier<Item>[] items() {
        @SuppressWarnings("unchecked")
        Supplier<Item>[] out = new Supplier[] {PowerContent.EMPTY_CELL::get, PowerContent.DEUTERIUM_CELL::get,
                PowerContent.TRITIUM_CELL::get, PowerContent.COOLANT_CELL::get};
        return out;
    }
}
