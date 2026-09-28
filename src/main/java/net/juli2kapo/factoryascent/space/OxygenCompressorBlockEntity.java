package net.juli2kapo.factoryascent.space;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/**
 * The Oxygen Compressor: runs on FE and pumps air into an Astronaut Suit (or Jet Suit) in its
 * slot, and into the suits of players standing on or right next to it.
 */
public class OxygenCompressorBlockEntity extends BlockEntity {
    public static final int CAPACITY = 64_000;
    public static final int MAX_INSERT = 1_000;
    /** Oxygen units (ticks of air) pumped per tick into each suit: three seconds of air per tick. */
    public static final int RATE = 60;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(CAPACITY, MAX_INSERT, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private final SimpleContainer items = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            OxygenCompressorBlockEntity.this.setChanged();
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return SuitItems.holdsOxygen(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    };
    /** Pumped something this tick (lights the front, synced through the block state). */
    private boolean working;
    private int workingTicks;

    public OxygenCompressorBlockEntity(BlockPos pos, BlockState state) {
        super(SpaceContent.OXYGEN_COMPRESSOR_BE.get(), pos, state);
    }

    public EnergyHandler energyHandler() {
        return energy;
    }

    public int energy() {
        return energy.getAmountAsInt();
    }

    /** Test / creative helper. */
    public void fillEnergy() {
        energy.set(CAPACITY);
    }

    public SimpleContainer items() {
        return items;
    }

    /** FE needed for {@code units} of air (rounded up). */
    static int cost(int units) {
        return (int) Math.ceil(units * SpaceConfig.get(SpaceConfig.COMPRESSOR_ENERGY_PER_SECOND) / 20.0);
    }

    /** Pumps up to one tick's worth of air into a suit; returns units added. */
    int pump(ItemStack suit) {
        if (!SuitItems.holdsOxygen(suit)) return 0;
        int room = SpaceConfig.suitOxygen() - SuitItems.oxygen(suit);
        int units = Math.min(RATE, room);
        if (units <= 0) return 0;
        int perSecond = SpaceConfig.get(SpaceConfig.COMPRESSOR_ENERGY_PER_SECOND);
        if (perSecond > 0) units = Math.min(units, energy.getAmountAsInt() * 20 / perSecond);
        if (units <= 0) return 0;
        energy.set(Math.max(0, energy.getAmountAsInt() - cost(units)));
        return SuitItems.fill(suit, units);
    }

    public void serverTick(ServerLevel level) {
        int pumped = pump(items.getItem(0));
        if (pumped > 0) items.setChanged();
        AABB around = new AABB(worldPosition).inflate(1.5, 0.5, 1.5).expandTowards(0, 1.5, 0);
        for (Player player : level.getEntitiesOfClass(Player.class, around)) {
            pumped += pump(SpaceRules.suitTank(player));
        }
        if (pumped > 0) {
            workingTicks = 10;
            if (level.getGameTime() % 8 == 0) {
                level.sendParticles(ParticleTypes.CLOUD, worldPosition.getX() + 0.5, worldPosition.getY() + 1.05,
                        worldPosition.getZ() + 0.5, 2, 0.2, 0.02, 0.2, 0.01);
            }
            if (level.getGameTime() % 30 == 0) {
                level.playSound(null, worldPosition, SoundEvents.BREEZE_IDLE_GROUND, SoundSource.BLOCKS, 0.3f, 1.4f);
            }
        } else if (workingTicks > 0) {
            workingTicks--;
        }
        boolean now = workingTicks > 0;
        if (now != working) {
            working = now;
            BlockState state = getBlockState();
            if (state.hasProperty(OxygenMachineBlock.LIT)) level.setBlock(worldPosition, state.setValue(OxygenMachineBlock.LIT, now), Block.UPDATE_CLIENTS);
        }
    }

    public void dropContents() {
        if (level != null) Containers.dropContents(level, worldPosition, NonNullList.of(ItemStack.EMPTY, items.getItem(0)));
        items.setItem(0, ItemStack.EMPTY);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        dropContents();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
        items.setItem(0, input.read("suit", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        ItemStack suit = items.getItem(0);
        if (!suit.isEmpty()) output.store("suit", ItemStack.OPTIONAL_CODEC, suit);
    }
}
