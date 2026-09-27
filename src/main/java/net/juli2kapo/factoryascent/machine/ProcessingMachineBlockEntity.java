package net.juli2kapo.factoryascent.machine;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.juli2kapo.factoryascent.Config;
import net.juli2kapo.factoryascent.recipe.ChanceOutput;
import net.juli2kapo.factoryascent.recipe.MachineRecipe;
import net.juli2kapo.factoryascent.recipe.RecipeKind;
import net.juli2kapo.factoryascent.registry.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * Every recipe-driven machine: Quern, kilns, burner machines, multiblocks and electric machines.
 *
 * <p>Work model: each tick the machine earns work points from its power source (crank, fuel,
 * electricity or nothing at all for the coke oven) and completes one operation per
 * {@code recipe.time} points. Recipes above the machine's grade are out of reach, and when several
 * recipes match, the one with the highest {@code min_grade} wins, so better machines give better
 * yields from the same input.
 */
public class ProcessingMachineBlockEntity extends AbstractMachineBlockEntity {
    public static final TagKey<Item> COKE_TAG = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "coal_coke"));
    /** Vanilla smelting runs at this fraction of the vanilla cook time in the Electric Furnace. */
    private static final int VANILLA_SMELT_DIVISOR = 5;
    private static final int MAX_OPS_PER_TICK = 64;
    /** Work points one crank of the Quern adds. */
    private static final int CRANK_POINTS = 10;

    private final RecipeKind kind;
    private @Nullable ActiveRecipe current;
    private boolean recipeDirty = true;
    private float progress;
    private float energyCarry;
    private int burnRemaining;
    private int burnTotal;
    private float crankPoints;
    private long lastCrank;
    private boolean structureOk = true;
    private int structureMissing;

    private @Nullable RecipeMap acceptCacheOwner;
    private final Map<Item, Boolean> acceptCache = new IdentityHashMap<>();

    /** A resolved operation: which slots to consume, how much, and what comes out. */
    private record ActiveRecipe(Object key, int[] slots, int[] counts, ItemStack result,
                                @Nullable ChanceOutput byproduct, int time) {}

    public ProcessingMachineBlockEntity(MachineType type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.kind = type.recipeKind();
    }

    @Override
    protected void configureEnergy() {
        if (type.power() != MachineType.Power.ELECTRIC) {
            energy.configure(0, 0, 0);
            return;
        }
        int capacity = Math.max(8_000, type.baseEnergy() * 4 * 400);
        energy.configure(capacity, capacity, 0);
    }

    private float speed() {
        return (float) (type.speed() * speedMultiplier() * Config.MACHINE_SPEED.get());
    }

    /** Energy per work point for electric machines. */
    private float energyPerPoint() {
        return (float) (type.baseEnergy() / type.speed() * energyMultiplier() * Config.MACHINE_ENERGY.get());
    }

    // ---------------------------------------------------------------- manual power

    public static final int EVENT_CRANK = 1;

    /** Client only: degrees the quern's runner stone still has to turn, and its current angle. */
    public float pendingTurnDegrees;
    public float runnerAngle;
    public float runnerLastTime = -1;

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id == EVENT_CRANK) {
            if (level != null && level.isClientSide()) pendingTurnDegrees = Math.min(pendingTurnDegrees + 360f, 720f);
            return true;
        }
        return super.triggerEvent(id, param);
    }

    /** Quern: one turn of the handle. */
    public void crank(Player player) {
        if (level == null || level.getGameTime() - lastCrank < 4) return;
        lastCrank = level.getGameTime();
        crankPoints = Math.min(crankPoints + CRANK_POINTS, CRANK_POINTS * 4);
        level.playSound(null, worldPosition, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.5f,
                0.8f + level.getRandom().nextFloat() * 0.4f);
        // Tell watching clients to turn the runner stone one full turn (see QuernRenderer).
        level.blockEvent(worldPosition, getBlockState().getBlock(), EVENT_CRANK, 0);
        player.causeFoodExhaustion(0.05f);
    }

    // ---------------------------------------------------------------- ticking

    @Override
    protected boolean tickMachine(ServerLevel level) {
        if (type.isMultiblock() && (level.getGameTime() + worldPosition.asLong()) % 40 == 0) {
            structureMissing = Multiblocks.missing(type, level, worldPosition, getBlockState().getValue(MachineBlock.FACING));
            structureOk = structureMissing == 0;
        }
        if (recipeDirty) {
            ActiveRecipe next = resolve(level);
            if (next == null || current == null || !next.key().equals(current.key())) progress = 0;
            current = next;
            recipeDirty = false;
        }
        lastEnergyRate = 0;
        if (!structureOk) {
            status = STATUS_INCOMPLETE;
            return false;
        }
        if (current == null) {
            if (status != STATUS_TIER_TOO_LOW) status = STATUS_IDLE;
            progress = 0;
            return burnRemaining > 0 && tickBurn();
        }
        if (!canOutput(current)) {
            status = STATUS_OUTPUT_FULL;
            return false;
        }

        float points = earnPoints();
        if (points <= 0.0001f) return false;

        progress += points;
        int ops = 0;
        while (current != null && progress >= current.time() && ops < MAX_OPS_PER_TICK) {
            if (!canOutput(current)) {
                progress = current.time();
                status = STATUS_OUTPUT_FULL;
                break;
            }
            craft(level, current);
            progress -= current.time();
            ops++;
            if (!inputsStillSatisfy(current)) {
                ActiveRecipe next = resolve(level);
                if (next == null || !next.key().equals(current.key())) progress = 0;
                current = next;
            }
        }
        recipeDirty = false;
        setChanged();
        return true;
    }

    /** Burns fuel without work (the fire keeps going until the current item is spent). */
    private boolean tickBurn() {
        burnRemaining--;
        return true;
    }

    /** Work points available this tick from the machine's power source; sets {@link #status}. */
    private float earnPoints() {
        float wanted = speed();
        switch (type.power()) {
            case NONE -> {
                status = STATUS_WORKING;
                return wanted;
            }
            case MANUAL -> {
                float points = Math.min(crankPoints, wanted * 2);
                crankPoints -= points;
                status = points > 0 ? STATUS_WORKING : STATUS_NEEDS_CRANK;
                return points;
            }
            case FUEL -> {
                if (burnRemaining <= 0 && !ignite()) {
                    status = STATUS_NO_FUEL;
                    return 0;
                }
                burnRemaining--;
                status = STATUS_WORKING;
                return wanted;
            }
            default -> {
                float epp = energyPerPoint();
                float points = epp <= 0 ? wanted : Math.min(wanted, (energy.energy() + energyCarry) / epp);
                if (points <= 0.0001f) {
                    status = STATUS_NO_POWER;
                    return 0;
                }
                float cost = points * epp + energyCarry;
                int whole = (int) cost;
                energyCarry = cost - whole;
                energy.consume(whole);
                lastEnergyRate = whole;
                status = points < wanted ? STATUS_NO_POWER : STATUS_WORKING;
                return points;
            }
        }
    }

    private boolean ignite() {
        if (level == null || slots.fuel() == 0) return false;
        ItemStack fuel = inventory.stack(slots.firstFuel());
        int burn = burnTime(fuel);
        if (burn <= 0) return false;
        var remainder = fuel.getItem().getCraftingRemainder(fuel);
        fuel.shrink(1);
        if (fuel.isEmpty() && remainder != null) inventory.setStack(slots.firstFuel(), remainder.create());
        else inventory.changed(slots.firstFuel());
        burnTotal = burn;
        burnRemaining = burn;
        return true;
    }

    private int burnTime(ItemStack fuel) {
        if (fuel.isEmpty() || level == null) return 0;
        // The Blast Furnace only runs on coke.
        if (type == MachineType.BLAST_FURNACE && !fuel.is(COKE_TAG)) return 0;
        return fuel.getBurnTime(RecipeType.SMELTING, level.fuelValues());
    }

    private boolean inputsStillSatisfy(ActiveRecipe recipe) {
        for (int i = 0; i < recipe.slots().length; i++) {
            if (inventory.stack(recipe.slots()[i]).getCount() < recipe.counts()[i]) return false;
        }
        return true;
    }

    private boolean canOutput(ActiveRecipe recipe) {
        ItemStack out = inventory.stack(slots.firstOutput());
        ItemStack result = recipe.result();
        if (out.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(out, result)
                && out.getCount() + result.getCount() <= Math.min(out.getMaxStackSize(), 99);
    }

    private void craft(ServerLevel level, ActiveRecipe recipe) {
        for (int i = 0; i < recipe.slots().length; i++) {
            int slot = recipe.slots()[i];
            ItemStack in = inventory.stack(slot);
            var template = in.getItem().getCraftingRemainder(in);
            ItemStack remainder = in.getCount() == recipe.counts()[i] && template != null ? template.create() : ItemStack.EMPTY;
            in.shrink(recipe.counts()[i]);
            if (in.isEmpty() && !remainder.isEmpty()) inventory.setStack(slot, remainder);
        }
        insertOutput(slots.firstOutput(), recipe.result().copy());
        ChanceOutput by = recipe.byproduct();
        if (by != null && level.getRandom().nextFloat() < by.chance()) {
            ItemStack extra = by.item().create();
            // Same item as the main output (e.g. the Quern's "50% chance of a second dust") goes there first.
            int target = ItemStack.isSameItemSameComponents(extra, inventory.stack(slots.firstOutput()))
                    && inventory.stack(slots.firstOutput()).getCount() + extra.getCount() <= 64
                    ? slots.firstOutput() : slots.firstOutput() + 1;
            ItemStack there = inventory.stack(target);
            if (there.isEmpty() || (ItemStack.isSameItemSameComponents(there, extra)
                    && there.getCount() + extra.getCount() <= there.getMaxStackSize())) {
                insertOutput(target, extra);
            }
        }
        inventory.changed(slots.firstInput());
    }

    private void insertOutput(int slot, ItemStack stack) {
        ItemStack existing = inventory.stack(slot);
        if (existing.isEmpty()) {
            inventory.setStack(slot, stack);
        } else {
            existing.grow(stack.getCount());
            inventory.changed(slot);
        }
    }

    private List<ItemStack> inputStacks() {
        List<ItemStack> items = new ArrayList<>(slots.inputs());
        for (int i = 0; i < slots.inputs(); i++) items.add(inventory.stack(slots.firstInput() + i));
        return items;
    }

    private @Nullable ActiveRecipe resolve(ServerLevel level) {
        List<ItemStack> items = inputStacks();
        status = STATUS_IDLE;
        if (items.stream().allMatch(ItemStack::isEmpty)) return null;
        RecipeManager recipes = level.recipeAccess();
        ItemStack mold = slots.mold() > 0 ? inventory.stack(slots.firstMold()) : ItemStack.EMPTY;
        boolean blockedByGrade = false;
        List<RecipeHolder<MachineRecipe>> candidates = new ArrayList<>(recipes.recipeMap().byType(ModRecipes.type(kind)));
        candidates.sort((a, b) -> Integer.compare(b.value().minGrade(), a.value().minGrade()));
        for (RecipeHolder<MachineRecipe> holder : candidates) {
            MachineRecipe r = holder.value();
            if (!r.moldMatches(mold)) continue;
            int[] found = r.findSlots(items);
            if (found == null) continue;
            if (r.minGrade() > type.grade()) {
                blockedByGrade = true;
                continue;
            }
            int[] counts = r.inputs().stream().mapToInt(SizedIngredient::count).toArray();
            int[] absolute = new int[found.length];
            for (int i = 0; i < found.length; i++) absolute[i] = slots.firstInput() + found[i];
            return new ActiveRecipe(holder.id(), absolute, counts, r.result().create(), r.byproduct().orElse(null), r.time());
        }
        if (type.runsVanillaSmelting() && !items.get(0).isEmpty()) {
            SingleRecipeInput input = new SingleRecipeInput(items.get(0));
            var vanilla = recipes.getRecipeFor(RecipeType.SMELTING, input, level);
            if (vanilla.isPresent()) {
                RecipeHolder<SmeltingRecipe> holder = vanilla.get();
                ItemStack result = holder.value().assemble(input);
                if (!result.isEmpty()) {
                    int time = Math.max(4, holder.value().cookingTime() / VANILLA_SMELT_DIVISOR);
                    return new ActiveRecipe(holder.id(), new int[]{slots.firstInput()}, new int[]{1}, result, null, time);
                }
            }
        }
        if (blockedByGrade) status = STATUS_TIER_TOO_LOW;
        return null;
    }

    // ---------------------------------------------------------------- items

    @Override
    public void onInventoryChanged(int index) {
        super.onInventoryChanged(index);
        if (slots.role(index) != SlotRole.FUEL) recipeDirty = true;
    }

    @Override
    public boolean isItemValid(int index, ItemResource resource) {
        if (slots.role(index) == SlotRole.FUEL) return burnTime(resource.toStack(1)) > 0;
        return super.isItemValid(index, resource);
    }

    @Override
    public boolean canAutomationInsert(int index, ItemResource resource) {
        SlotRole role = slots.role(index);
        if (role == SlotRole.FUEL) return isItemValid(index, resource);
        if (role != SlotRole.INPUT) return false;
        if (resource.getItem() instanceof net.juli2kapo.factoryascent.item.MoldItem) return false;
        if (!acceptsItem(resource.toStack(1))) return false;
        // Keep multi-input machines tidy: one item type per slot, no duplicates across slots.
        ItemStack here = inventory.stack(index);
        if (!here.isEmpty()) return resource.matches(here);
        for (int i = 0; i < slots.inputs(); i++) {
            int other = slots.firstInput() + i;
            if (other != index && resource.matches(inventory.stack(other))) return false;
        }
        return true;
    }

    /** Whether any recipe this machine can reach uses the item. Cached per recipe reload. */
    public boolean acceptsItem(ItemStack stack) {
        if (!(level instanceof ServerLevel server)) return true;
        RecipeManager recipes = server.recipeAccess();
        if (acceptCacheOwner != recipes.recipeMap()) {
            acceptCache.clear();
            acceptCacheOwner = recipes.recipeMap();
        }
        return acceptCache.computeIfAbsent(stack.getItem(), item -> {
            for (RecipeHolder<MachineRecipe> holder : recipes.recipeMap().byType(ModRecipes.type(kind))) {
                if (holder.value().minGrade() <= type.grade() && holder.value().accepts(stack)) return true;
            }
            return type.runsVanillaSmelting() && recipes.propertySet(RecipePropertySet.FURNACE_INPUT).test(stack);
        });
    }

    // ---------------------------------------------------------------- GUI

    /** For multiblocks: how many structure blocks are wrong or missing (0 = formed). */
    @Override
    public int extraB() {
        return structureMissing;
    }

    @Override
    public int progressPermille() {
        if (current == null || current.time() <= 0) return 0;
        return Math.min(1000, Math.round(progress / current.time() * 1000));
    }

    @Override
    public int ratePerMinuteX10() {
        if (current == null) return 0;
        double opsPerMinute = 1200.0 * speed() / current.time();
        return (int) Math.min(Integer.MAX_VALUE, Math.round(opsPerMinute * current.result().getCount() * 10));
    }

    /** Burn progress (permille) for fuel machines; crank charge for the Quern. */
    @Override
    public int extraA() {
        return switch (type.power()) {
            case FUEL -> burnTotal <= 0 ? 0 : Math.max(0, burnRemaining * 1000 / burnTotal);
            case MANUAL -> Math.round(crankPoints * 1000 / (CRANK_POINTS * 4));
            default -> Math.round(speed() * energyPerPoint());
        };
    }

    // ---------------------------------------------------------------- persistence

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        progress = input.getFloatOr("progress", 0f);
        energyCarry = input.getFloatOr("energy_carry", 0f);
        burnRemaining = input.getIntOr("burn_remaining", 0);
        burnTotal = input.getIntOr("burn_total", 0);
        crankPoints = input.getFloatOr("crank", 0f);
        recipeDirty = true;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putFloat("progress", progress);
        output.putFloat("energy_carry", energyCarry);
        output.putInt("burn_remaining", burnRemaining);
        output.putInt("burn_total", burnTotal);
        output.putFloat("crank", crankPoints);
    }
}
