package net.juli2kapo.factoryascent.automation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.juli2kapo.factoryascent.Config;
import net.juli2kapo.factoryascent.machine.AbstractMachineBlockEntity;
import net.juli2kapo.factoryascent.machine.MachineBlock;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A powered lamp that throws its light where it points: a fan of rays goes out of its front
 * (slightly downwards) and wherever one hits a surface within {@link #RANGE} blocks an invisible
 * light source (vanilla {@code minecraft:light}, level 15) is placed in the air just before it.
 * The lights are removed again when the floodlight runs out of power, is turned or broken.
 */
public class FloodlightBlockEntity extends AbstractMachineBlockEntity {
    public static final int RANGE = 28;
    private static final int REFRESH = 40;
    private static final int[] YAWS = {-36, -18, 0, 18, 36};
    private static final int[] PITCHES = {-28, -14, -4};

    private final Set<BlockPos> lights = new LinkedHashSet<>();
    private int refresh;
    private float energyCarry;

    public FloodlightBlockEntity(BlockPos pos, BlockState state) {
        super(MachineType.FLOODLIGHT, pos, state);
    }

    @Override
    protected void configureEnergy() {
        energy.configure(20_000, 1_000, 0);
    }

    public int lightCount() {
        return lights.size();
    }

    @Override
    protected boolean tickMachine(ServerLevel level) {
        float cost = (float) (type.baseEnergy() * Config.MACHINE_ENERGY.get()) + energyCarry;
        int whole = (int) cost;
        if (energy.energy() < whole) {
            status = STATUS_NO_POWER;
            lastEnergyRate = 0;
            if (!lights.isEmpty()) clearLights(level);
            return false;
        }
        energyCarry = cost - whole;
        energy.consume(whole);
        lastEnergyRate = whole;
        status = STATUS_WORKING;
        if (--refresh <= 0) {
            refresh = REFRESH;
            placeLights(level);
        }
        return true;
    }

    private Set<BlockPos> targets(Level level) {
        Direction facing = getBlockState().getValue(MachineBlock.FACING);
        Vec3 origin = Vec3.atCenterOf(worldPosition).add(facing.getStepX() * 0.55, 0.1, facing.getStepZ() * 0.55);
        float baseYaw = facing.toYRot();
        Set<BlockPos> out = new LinkedHashSet<>();
        for (int yaw : YAWS) {
            for (int pitch : PITCHES) {
                Vec3 dir = Vec3.directionFromRotation(-pitch, baseYaw + yaw);
                Vec3 end = origin.add(dir.scale(RANGE));
                BlockHitResult hit = level.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY,
                        net.minecraft.world.phys.shapes.CollisionContext.empty()));
                if (hit.getType() != HitResult.Type.BLOCK) continue;
                BlockPos air = hit.getBlockPos().relative(hit.getDirection());
                if (air.distManhattan(worldPosition) < 3) continue; // right next to the lamp: it lights that itself
                out.add(air);
            }
        }
        return out;
    }

    private void placeLights(ServerLevel level) {
        Set<BlockPos> wanted = targets(level);
        List<BlockPos> stale = new ArrayList<>();
        for (BlockPos p : lights) if (!wanted.contains(p)) stale.add(p);
        for (BlockPos p : stale) {
            removeLight(level, p);
            lights.remove(p);
        }
        BlockState light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);
        for (BlockPos p : wanted) {
            if (lights.contains(p)) {
                if (!level.getBlockState(p).is(Blocks.LIGHT)) lights.remove(p);
                continue;
            }
            if (!level.isLoaded(p) || !level.getBlockState(p).isAir()) continue;
            level.setBlock(p, light, Block.UPDATE_ALL);
            lights.add(p);
        }
        setChanged();
    }

    private static void removeLight(Level level, BlockPos p) {
        if (level.isLoaded(p) && level.getBlockState(p).is(Blocks.LIGHT)) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    private void clearLights(Level level) {
        for (BlockPos p : lights) removeLight(level, p);
        lights.clear();
        refresh = 0;
        setChanged();
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && !level.isClientSide()) clearLights(level);
    }

    @Override
    public int extraA() {
        return lights.size();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        lights.clear();
        input.read("lights", BlockPos.CODEC.listOf()).ifPresent(lights::addAll);
        energyCarry = input.getFloatOr("energy_carry", 0f);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("lights", BlockPos.CODEC.listOf(), List.copyOf(lights));
        output.putFloat("energy_carry", energyCarry);
    }
}
