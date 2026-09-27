package net.juli2kapo.factoryascent.miner;

import java.util.ArrayList;
import java.util.List;
import net.juli2kapo.factoryascent.Config;
import net.juli2kapo.factoryascent.Tier;
import net.juli2kapo.factoryascent.machine.AbstractMachineBlockEntity;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Digs out only ore blocks ({@code #c:ores}) in a square below itself, layer by layer from the top,
 * and leaves stone/deepslate/netherrack in their place so caves and terrain stay intact. It never
 * loads chunks, and every break fires a normal break event so claim/protection mods can veto it.
 */
public class MinerBlockEntity extends AbstractMachineBlockEntity {
    /** Work points to dig one ore; Basic earns 1 point per tick, so one ore per second. */
    public static final int POINTS_PER_ORE = 20;
    private static final int SCAN_PER_TICK = 384;
    private static final int[] RADIUS = {5, 8, 12, 16, 24};
    private static final ItemStack TOOL = new ItemStack(Items.DIAMOND_PICKAXE);

    private int cursorX;
    private int cursorZ;
    private int cursorY = Integer.MAX_VALUE;
    private boolean finished;
    private float progress;
    private float energyCarry;
    private int mined;
    private final List<ItemStack> overflow = new ArrayList<>();

    public MinerBlockEntity(BlockPos pos, BlockState state) {
        super(MachineType.MINER, pos, state);
    }

    /** Radius before the server's {@code minerMaxRadius} cap (safe to call on the client). */
    public static int baseRadius(Tier tier) {
        return RADIUS[tier.ordinal()];
    }

    public static int radius(Tier tier) {
        return Math.min(RADIUS[tier.ordinal()], Config.MINER_MAX_RADIUS.get());
    }

    @Override
    protected void applyTier(Tier tier) {
        int perTick = Math.max(1, Math.round(type.baseEnergy() * tier.speed() * tier.energyFactor()));
        energy.configure(Math.max(16_000, perTick * 4 * 400), Math.max(16_000, perTick * 4 * 400), 0);
        restart();
    }

    /** Start the sweep again from the top (after an upgrade widens the area, or a finished run). */
    public void restart() {
        cursorY = Integer.MAX_VALUE;
        finished = false;
    }

    private float speed() {
        return (float) (tier().speed() * speedMultiplier() * Config.MINER_RATE.get());
    }

    private float energyPerPoint() {
        return (float) (type.baseEnergy() * tier().energyFactor() * energyMultiplier() * Config.MACHINE_ENERGY.get());
    }

    @Override
    protected boolean tickMachine(ServerLevel level) {
        lastEnergyRate = 0;
        flushOverflow();
        if (!overflow.isEmpty()) {
            status = STATUS_OUTPUT_FULL;
            return false;
        }
        if (finished) {
            status = STATUS_IDLE;
            if (level.getGameTime() % 1200 == 0) restart(); // look again every minute
            return false;
        }

        float wanted = speed();
        float epp = energyPerPoint();
        float points = epp <= 0 ? wanted : Math.min(wanted, (energy.energy() + energyCarry) / epp);
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
        progress = Math.min(progress + points, POINTS_PER_ORE * 64f);

        int scanned = 0;
        while (progress >= POINTS_PER_ORE && scanned < SCAN_PER_TICK && !finished && overflow.isEmpty()) {
            BlockPos target = nextCandidate(level, SCAN_PER_TICK - scanned);
            scanned += lastScanCost;
            if (target == null) break;
            if (mine(level, target)) progress -= POINTS_PER_ORE;
        }
        if (finished) progress = 0;
        setChanged();
        return true;
    }

    private int lastScanCost;

    /** Advances the cursor until it finds an ore, scanning at most {@code budget} positions. */
    private BlockPos nextCandidate(ServerLevel level, int budget) {
        int r = radius(tier());
        int minY = level.getMinY();
        if (cursorY == Integer.MAX_VALUE) {
            cursorY = worldPosition.getY() - 1;
            cursorX = -r;
            cursorZ = -r;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < budget; i++) {
            if (cursorY < minY) {
                finished = true;
                lastScanCost = i;
                return null;
            }
            pos.set(worldPosition.getX() + cursorX, cursorY, worldPosition.getZ() + cursorZ);
            advance(r);
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.is(Tags.Blocks.ORES) && !state.hasBlockEntity()) {
                lastScanCost = i + 1;
                return pos.immutable();
            }
        }
        lastScanCost = budget;
        return null;
    }

    private void advance(int r) {
        if (++cursorX > r) {
            cursorX = -r;
            if (++cursorZ > r) {
                cursorZ = -r;
                cursorY--;
            }
        }
    }

    private boolean mine(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        var fakePlayer = FakePlayerFactory.getMinecraft(level);
        if (NeoForge.EVENT_BUS.post(new BreakBlockEvent(level, pos, state, fakePlayer)).isCanceled()) return false;
        List<ItemStack> drops = Block.getDrops(state, level, pos, null, null, TOOL);
        BlockState filler = state.is(Tags.Blocks.ORES_IN_GROUND_DEEPSLATE) ? Blocks.DEEPSLATE.defaultBlockState()
                : state.is(Tags.Blocks.ORES_IN_GROUND_NETHERRACK) ? Blocks.NETHERRACK.defaultBlockState()
                : Blocks.STONE.defaultBlockState();
        level.setBlock(pos, filler, Block.UPDATE_ALL);
        for (ItemStack drop : drops) {
            ItemStack rest = insertOutput(drop);
            if (!rest.isEmpty()) overflow.add(rest);
        }
        mined++;
        return true;
    }

    private ItemStack insertOutput(ItemStack stack) {
        for (int i = slots.firstOutput(); i < slots.firstUpgrade() && !stack.isEmpty(); i++) {
            ItemStack s = inventory.stack(i);
            if (s.isEmpty()) {
                inventory.setStack(i, stack.copy());
                return ItemStack.EMPTY;
            }
            if (ItemStack.isSameItemSameComponents(s, stack)) {
                int move = Math.min(stack.getCount(), s.getMaxStackSize() - s.getCount());
                if (move > 0) {
                    s.grow(move);
                    stack.shrink(move);
                    inventory.changed(i);
                }
            }
        }
        return stack;
    }

    private void flushOverflow() {
        overflow.replaceAll(this::insertOutput);
        overflow.removeIf(ItemStack::isEmpty);
    }

    @Override
    public boolean canAutomationInsert(int index, ItemResource resource) {
        return false;
    }

    /** How far through the area the sweep is, in permille. */
    @Override
    public int progressPermille() {
        if (finished) return 1000;
        if (cursorY == Integer.MAX_VALUE || level == null) return 0;
        int top = worldPosition.getY() - 1;
        int total = Math.max(1, top - level.getMinY() + 1);
        return Math.min(1000, (top - cursorY) * 1000 / total);
    }

    /** Ores per minute ×10 at full speed. */
    @Override
    public int ratePerMinuteX10() {
        return Math.round(speed() * 1200f / POINTS_PER_ORE * 10);
    }

    @Override
    public int extraA() {
        return radius(tier());
    }

    /** Current layer being scanned. */
    @Override
    public int extraB() {
        return cursorY == Integer.MAX_VALUE ? worldPosition.getY() - 1 : cursorY;
    }

    public int mined() {
        return mined;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        cursorX = input.getIntOr("cursor_x", 0);
        cursorY = input.getIntOr("cursor_y", Integer.MAX_VALUE);
        cursorZ = input.getIntOr("cursor_z", 0);
        finished = input.getBooleanOr("finished", false);
        progress = input.getFloatOr("progress", 0f);
        energyCarry = input.getFloatOr("energy_carry", 0f);
        mined = input.getIntOr("mined", 0);
        overflow.clear();
        input.read("overflow", ItemStack.OPTIONAL_CODEC.listOf()).ifPresent(overflow::addAll);
        overflow.removeIf(ItemStack::isEmpty);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("cursor_x", cursorX);
        output.putInt("cursor_y", cursorY);
        output.putInt("cursor_z", cursorZ);
        output.putBoolean("finished", finished);
        output.putFloat("progress", progress);
        output.putFloat("energy_carry", energyCarry);
        output.putInt("mined", mined);
        output.store("overflow", ItemStack.OPTIONAL_CODEC.listOf(), overflow);
    }
}
