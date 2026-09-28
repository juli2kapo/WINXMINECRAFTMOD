package net.juli2kapo.factoryascent.space;

import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/**
 * The Oxygen Sealer: while powered it keeps a sphere of breathable air around itself
 * ({@link SpaceConfig#SEALER_RADIUS} blocks), so a station crew can take off their helmets.
 * Running sealers are listed in {@link SpaceRules} for the breathing checks.
 */
public class OxygenSealerBlockEntity extends BlockEntity {
    public static final int CAPACITY = 64_000;
    public static final int MAX_INSERT = 1_000;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(CAPACITY, MAX_INSERT, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private boolean running;

    public OxygenSealerBlockEntity(BlockPos pos, BlockState state) {
        super(SpaceContent.OXYGEN_SEALER_BE.get(), pos, state);
    }

    public EnergyHandler energyHandler() {
        return energy;
    }

    public int energy() {
        return energy.getAmountAsInt();
    }

    public void fillEnergy() {
        energy.set(CAPACITY);
    }

    public boolean running() {
        return running;
    }

    public void serverTick(ServerLevel level) {
        int cost = SpaceConfig.get(SpaceConfig.SEALER_ENERGY);
        boolean now = energy.getAmountAsInt() >= cost;
        if (now) energy.set(energy.getAmountAsInt() - cost);
        SpaceRules.setSealer(level, worldPosition, now);
        if (now && level.getGameTime() % 20 == 0) {
            level.sendParticles(ParticleTypes.BUBBLE_POP, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1,
                    worldPosition.getZ() + 0.5, 2, 0.25, 0.05, 0.25, 0.01);
        }
        if (now != running) {
            running = now;
            BlockState state = getBlockState();
            if (state.hasProperty(OxygenMachineBlock.LIT)) level.setBlock(worldPosition, state.setValue(OxygenMachineBlock.LIT, now), Block.UPDATE_CLIENTS);
        }
    }

    public Component status() {
        int radius = SpaceConfig.get(SpaceConfig.SEALER_RADIUS);
        return running
                ? Component.translatable("message.factoryascent.sealer_on", radius, EnergyUtil.format(energy())).withStyle(ChatFormatting.AQUA)
                : Component.translatable("message.factoryascent.sealer_off", SpaceConfig.get(SpaceConfig.SEALER_ENERGY)).withStyle(ChatFormatting.RED);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) SpaceRules.setSealer(level, worldPosition, false);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
    }
}
