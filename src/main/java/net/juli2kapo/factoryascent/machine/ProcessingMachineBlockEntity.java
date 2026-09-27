package net.juli2kapo.factoryascent.machine;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.juli2kapo.factoryascent.Config;
import net.juli2kapo.factoryascent.Tier;
import net.juli2kapo.factoryascent.recipe.ChanceOutput;
import net.juli2kapo.factoryascent.recipe.MachineRecipe;
import net.juli2kapo.factoryascent.recipe.RecipeKind;
import net.juli2kapo.factoryascent.registry.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
 * Smelter, Crusher, Constructor, Foundry, Assembler and Manufacturer.
 *
 * <p>Work model: each tick the machine earns {@code tierSpeed × speedUpgrades} work points (fewer if
 * energy runs short) and completes one operation per {@code recipe.time} points, so high tiers can
 * finish several operations in a single tick.
 */
public class ProcessingMachineBlockEntity extends AbstractMachineBlockEntity {
    /** Vanilla smelting runs at this fraction of the vanilla cook time in the Smelter. */
    private static final int VANILLA_SMELT_DIVISOR = 5;
    private static final int MAX_OPS_PER_TICK = 64;

    private final RecipeKind kind;
    private @Nullable ActiveRecipe current;
    private boolean recipeDirty = true;
    private float progress;
    private float energyCarry;

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
    protected void applyTier(Tier tier) {
        int perTick = Math.max(1, Math.round(type.baseEnergy() * tier.speed() * tier.energyFactor()));
        // Enough buffer for ~20 s of full-speed work with speed upgrades installed.
        int capacity = Math.max(8_000, perTick * 4 * 400);
        energy.configure(capacity, capacity, 0);
        recipeDirty = true;
    }

    private float speed() {
        return (float) (tier().speed() * speedMultiplier() * Config.MACHINE_SPEED.get());
    }

    private float energyPerPoint() {
        return (float) (type.baseEnergy() * tier().energyFactor() * energyMultiplier() * Config.MACHINE_ENERGY.get());
    }

    @Override
    protected boolean tickMachine(ServerLevel level) {
        if (recipeDirty) {
            ActiveRecipe next = resolve(level);
            if (next == null || current == null || !next.key().equals(current.key())) progress = 0;
            current = next;
            recipeDirty = false;
        }
        lastEnergyRate = 0;
        if (current == null) {
            if (status != STATUS_TIER_TOO_LOW) status = STATUS_IDLE;
            progress = 0;
            return false;
        }
        if (!canOutput(current)) {
            status = STATUS_OUTPUT_FULL;
            return false;
        }

        float wanted = speed();
        float epp = energyPerPoint();
        float points = wanted;
        if (epp > 0) {
            float affordable = (energy.energy() + energyCarry) / epp;
            points = Math.min(wanted, affordable);
        }
        if (points <= 0.0001f) {
            status = STATUS_NO_POWER;
            return false;
        }
        float cost = points * epp + energyCarry;
        int whole = (int) cost;
        energyCarry = cost - whole;
        energy.consume(whole);
        lastEnergyRate = whole;
        status = points < wanted ? STATUS_NO_POWER : STATUS_WORKING;

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
            ItemStack second = inventory.stack(slots.firstOutput() + 1);
            if (second.isEmpty() || (ItemStack.isSameItemSameComponents(second, extra)
                    && second.getCount() + extra.getCount() <= second.getMaxStackSize())) {
                insertOutput(slots.firstOutput() + 1, extra);
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
        boolean blockedByTier = false;
        // Highest min_tier first, so upgrading a machine unlocks better yields for the same input.
        List<RecipeHolder<MachineRecipe>> candidates = new ArrayList<>(recipes.recipeMap().byType(ModRecipes.type(kind)));
        candidates.sort((a, b) -> Integer.compare(b.value().minTier(), a.value().minTier()));
        for (RecipeHolder<MachineRecipe> holder : candidates) {
            MachineRecipe r = holder.value();
            if (!r.moldMatches(mold)) continue;
            int[] found = r.findSlots(items);
            if (found == null) continue;
            if (r.minTier() > tier().level()) {
                blockedByTier = true;
                continue;
            }
            int[] counts = r.inputs().stream().mapToInt(SizedIngredient::count).toArray();
            int[] absolute = new int[found.length];
            for (int i = 0; i < found.length; i++) absolute[i] = slots.firstInput() + found[i];
            return new ActiveRecipe(holder.id(), absolute, counts, r.result().create(),
                    r.byproduct().orElse(null), r.time());
        }
        if (kind == RecipeKind.SMELTING && !items.get(0).isEmpty()) {
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
        if (blockedByTier) status = STATUS_TIER_TOO_LOW;
        return null;
    }

    // ---------------------------------------------------------------- items

    @Override
    public void onInventoryChanged(int index) {
        super.onInventoryChanged(index);
        SlotRole role = slots.role(index);
        if (role != SlotRole.FUEL) recipeDirty = true;
    }

    @Override
    public boolean canAutomationInsert(int index, ItemResource resource) {
        if (slots.role(index) != SlotRole.INPUT) return false;
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

    /** Whether any recipe of this machine can use the item. Cached per recipe reload. */
    public boolean acceptsItem(ItemStack stack) {
        if (!(level instanceof ServerLevel server)) return true;
        RecipeManager recipes = server.recipeAccess();
        if (acceptCacheOwner != recipes.recipeMap()) {
            acceptCache.clear();
            acceptCacheOwner = recipes.recipeMap();
        }
        return acceptCache.computeIfAbsent(stack.getItem(), item -> {
            for (RecipeHolder<MachineRecipe> holder : recipes.recipeMap().byType(ModRecipes.type(kind))) {
                if (holder.value().accepts(stack)) return true;
            }
            return kind == RecipeKind.SMELTING && recipes.propertySet(RecipePropertySet.FURNACE_INPUT).test(stack);
        });
    }

    // ---------------------------------------------------------------- GUI

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

    @Override
    public int extraA() {
        return Math.round(speed() * energyPerPoint());
    }

    // ---------------------------------------------------------------- persistence

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        progress = input.getFloatOr("progress", 0f);
        energyCarry = input.getFloatOr("energy_carry", 0f);
        recipeDirty = true;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putFloat("progress", progress);
        output.putFloat("energy_carry", energyCarry);
    }
}
