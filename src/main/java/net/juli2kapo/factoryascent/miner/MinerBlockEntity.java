package net.juli2kapo.factoryascent.miner;

import java.util.ArrayList;
import java.util.List;
import net.juli2kapo.factoryascent.Config;
import net.juli2kapo.factoryascent.machine.AbstractMachineBlockEntity;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * Mines ore in The Deep ({@link TheDeep}). On first use it claims its own chunk column there, then
 * sweeps it layer by layer from the ceiling down, digging out every ore block (the ore becomes a
 * tunnel) and skipping sections that have none. When the claim is dug out it takes the next free
 * one. The claim is only kept loaded while the miner works, with a ticket that expires by itself.
 */
public class MinerBlockEntity extends AbstractMachineBlockEntity {
    /** Work points to dig one ore; the Miner earns 2 points per tick, so it digs two ores a second. */
    public static final int POINTS_PER_ORE = 20;
    /** Positions examined per tick at most; whole ore-free sections are skipped for free. */
    private static final int SCAN_PER_TICK = 1024;
    /** Chunk columns in one claim (per side). */
    public static final int CLAIM_CHUNKS = 1;
    private static final ItemStack TOOL = new ItemStack(Items.DIAMOND_PICKAXE);

    private int claim = -1;
    private int cursorX;
    private int cursorZ;
    private int cursorY = Integer.MAX_VALUE;
    private float progress;
    private float energyCarry;
    private int mined;
    private final List<ItemStack> overflow = new ArrayList<>();

    public MinerBlockEntity(BlockPos pos, BlockState state) {
        super(MachineType.MINER, pos, state);
    }

    @Override
    protected void configureEnergy() {
        int capacity = Math.max(16_000, type.baseEnergy() * 4 * 400);
        energy.configure(capacity, capacity, 0);
    }

    private float speed() {
        return (float) (type.speed() * speedMultiplier() * Config.MINER_RATE.get());
    }

    private float energyPerPoint() {
        if (!Config.MINERS_NEED_POWER.get()) return 0;
        return (float) (type.baseEnergy() / type.speed() * energyMultiplier() * Config.MACHINE_ENERGY.get());
    }

    /** The claim this miner digs in, or -1 before its first run. */
    public int claim() {
        return claim;
    }

    public ChunkPos claimChunk() {
        return TheDeep.claimChunk(Math.max(claim, 0));
    }

    @Override
    protected boolean tickMachine(ServerLevel level) {
        lastEnergyRate = 0;
        flushOverflow();
        if (!overflow.isEmpty()) {
            status = STATUS_OUTPUT_FULL;
            return false;
        }
        ServerLevel deep = TheDeep.level(level.getServer());
        if (deep == null) { // data pack removed
            status = STATUS_IDLE;
            return false;
        }

        float wanted = speed();
        float epp = energyPerPoint();
        float points = epp <= 0 ? wanted : Math.min(wanted, (energy.energy() + energyCarry) / epp);
        if (points <= 0.0001f) {
            status = STATUS_NO_POWER;
            return false;
        }

        if (claim < 0) {
            claim = TheDeep.allocateClaim(level.getServer());
            cursorY = Integer.MAX_VALUE;
            setChanged();
        }
        ChunkPos chunkPos = claimChunk();
        if (level.getGameTime() % 100 == 0 || deep.getChunkSource().getChunkNow(chunkPos.x(), chunkPos.z()) == null) {
            deep.getChunkSource().addTicketWithRadius(TheDeep.MINER_TICKET.get(), chunkPos, 0);
        }
        LevelChunk chunk = deep.getChunkSource().getChunkNow(chunkPos.x(), chunkPos.z());
        if (chunk == null) { // still loading or generating, off-thread
            status = STATUS_LOADING;
            return false;
        }

        float cost = points * epp + energyCarry;
        int whole = (int) cost;
        energyCarry = cost - whole;
        energy.consume(whole);
        lastEnergyRate = whole;
        status = points < wanted ? STATUS_NO_POWER : STATUS_WORKING;
        progress = Math.min(progress + points, POINTS_PER_ORE * 64f);

        int budget = SCAN_PER_TICK;
        while (progress >= POINTS_PER_ORE && budget > 0 && overflow.isEmpty()) {
            BlockPos target = nextOre(chunk, budget);
            budget -= lastScanCost;
            if (target == null) break;
            mine(deep, chunk, target);
            progress -= POINTS_PER_ORE;
        }
        if (cursorY < chunk.getMinY()) {
            // Claim dug out: move on to a fresh one.
            claim = TheDeep.allocateClaim(level.getServer());
            cursorY = Integer.MAX_VALUE;
        }
        setChanged();
        return true;
    }

    private int lastScanCost;

    /** Advances the cursor through the claim until it finds an ore, examining at most {@code budget} positions. */
    private @Nullable BlockPos nextOre(LevelChunk chunk, int budget) {
        int minY = chunk.getMinY();
        if (cursorY == Integer.MAX_VALUE) {
            cursorY = chunk.getMaxY();
            cursorX = 0;
            cursorZ = 0;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        for (int i = 0; i < budget; i++) {
            if (cursorY < minY) {
                lastScanCost = i;
                return null;
            }
            if (cursorX == 0 && cursorZ == 0) {
                // Starting a layer: skip down past sections that cannot hold ore.
                LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(cursorY));
                if (section.hasOnlyAir() || !section.maybeHas(s -> s.is(Tags.Blocks.ORES))) {
                    cursorY = SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(cursorY)) - 1;
                    continue;
                }
            }
            pos.set(baseX + cursorX, cursorY, baseZ + cursorZ);
            advance();
            BlockState state = chunk.getBlockState(pos);
            if (state.is(Tags.Blocks.ORES) && !state.hasBlockEntity()) {
                lastScanCost = i + 1;
                return pos.immutable();
            }
        }
        lastScanCost = budget;
        return null;
    }

    private void advance() {
        if (++cursorX > 15) {
            cursorX = 0;
            if (++cursorZ > 15) {
                cursorZ = 0;
                cursorY--;
            }
        }
    }

    private void mine(ServerLevel deep, LevelChunk chunk, BlockPos pos) {
        BlockState state = chunk.getBlockState(pos);
        List<ItemStack> drops = Block.getDrops(state, deep, pos, null, null, TOOL);
        deep.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        for (ItemStack drop : drops) {
            ItemStack rest = insertOutput(drop);
            if (!rest.isEmpty()) overflow.add(rest);
        }
        mined++;
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

    /** How far the current claim is dug, in permille. */
    @Override
    public int progressPermille() {
        if (claim < 0 || cursorY == Integer.MAX_VALUE) return 0;
        return Math.clamp((DEEP_TOP - cursorY) * 1000L / DEEP_TOP, 0, 1000);
    }

    /** The Deep's build height (the dimension spans y 0..255). */
    private static final int DEEP_TOP = 255;

    /** Ores per minute ×10 at full speed. */
    @Override
    public int ratePerMinuteX10() {
        return Math.round(speed() * 1200f / POINTS_PER_ORE * 10);
    }

    /** Claim number shown in the GUI (1-based, 0 = none yet). */
    @Override
    public int extraA() {
        return claim + 1;
    }

    /** Current layer being scanned in The Deep. */
    @Override
    public int extraB() {
        return cursorY == Integer.MAX_VALUE ? DEEP_TOP : cursorY;
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
        claim = input.getIntOr("deep_claim", -1);
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
        output.putInt("deep_claim", claim);
        output.putFloat("progress", progress);
        output.putFloat("energy_carry", energyCarry);
        output.putInt("mined", mined);
        output.store("overflow", ItemStack.OPTIONAL_CODEC.listOf(), overflow);
    }
}
