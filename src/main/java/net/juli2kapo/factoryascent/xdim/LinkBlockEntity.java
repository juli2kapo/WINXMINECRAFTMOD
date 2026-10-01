package net.juli2kapo.factoryascent.xdim;

import java.util.Arrays;
import java.util.UUID;
import net.juli2kapo.factoryascent.fluid.FluidTank;
import net.juli2kapo.factoryascent.orbital.FactoryTeams;
import net.juli2kapo.factoryascent.xdim.LinkTier.Resource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * One endpoint of an interdimensional link (an Ender Link or a Quantum Entangler).
 *
 * <p>Every face has a mode per resource (items, fluids, energy): {@link #SEND} takes in what pipes,
 * cables and machines push into that face (and pulls from inventories and tanks next to it) into
 * the outgoing buffers; {@link #RECEIVE} pushes the incoming buffers out of that face;
 * {@link #OFF} does neither. {@link XdimService} moves the outgoing buffers of every endpoint on a
 * channel into the incoming buffers of the others, wherever they are.
 *
 * <p>While tuned to a channel the endpoint keeps its own chunk loaded (within the team's limit),
 * so the far end keeps pumping while nobody is there. A Quantum Entangler also has an operating
 * buffer (fed through faces whose energy mode is off) that pays for what it sends.
 */
public class LinkBlockEntity extends BlockEntity {
    public static final int OFF = 0, SEND = 1, RECEIVE = 2;
    public static final int SLOTS = 9;
    /** How long after the last transfer the endpoint still shows as active (ticks). */
    private static final int ACTIVE_TICKS = 40;

    public static final int ST_IDLE = 0, ST_ACTIVE = 1, ST_NO_CHANNEL = 2, ST_ALONE = 3, ST_NO_POWER = 4, ST_DENIED = 5;

    private final LinkTier tier;
    private @Nullable UUID owner;
    private int channel;
    /** face * 3 + resource → mode. */
    private final byte[] modes = new byte[18];

    final ItemStacksResourceHandler outItems = new Items();
    final ItemStacksResourceHandler inItems = new Items();
    final FluidTank outFluid, inFluid;
    final SimpleEnergyHandler outEnergy, inEnergy, power;

    @SuppressWarnings("unchecked")
    private final ResourceHandler<ItemResource>[] itemFaces = new ResourceHandler[6];
    @SuppressWarnings("unchecked")
    private final ResourceHandler<FluidResource>[] fluidFaces = new ResourceHandler[6];
    private final EnergyHandler[] energyFaces = new EnergyHandler[6];
    @SuppressWarnings("unchecked")
    private final BlockCapabilityCache<ResourceHandler<ItemResource>, @Nullable Direction>[] itemCaches = new BlockCapabilityCache[6];
    @SuppressWarnings("unchecked")
    private final BlockCapabilityCache<ResourceHandler<FluidResource>, @Nullable Direction>[] fluidCaches = new BlockCapabilityCache[6];
    @SuppressWarnings("unchecked")
    private final BlockCapabilityCache<EnergyHandler, @Nullable Direction>[] energyCaches = new BlockCapabilityCache[6];

    /** Moved this second (sent items, received items, sent mB, received mB, sent FE, received FE). */
    private final long[] counting = new long[6];
    /** Last full second's numbers (what the screen shows). */
    private final long[] lastSecond = new long[6];
    private long lastTransfer = Long.MIN_VALUE / 2;
    /** Ticks this endpoint has run its server tick (tests use it to see a remote endpoint is alive). */
    private long ticksRun;
    private boolean anchored;
    private int status = ST_NO_CHANNEL;
    /** FE spent on transfers this second / last second (quantum). */
    private long costCounting, costLastSecond;

    public LinkBlockEntity(BlockPos pos, BlockState state) {
        super(XdimContent.LINK_BE.get(), pos, state);
        this.tier = state.getBlock() instanceof LinkBlock b ? b.tier() : LinkTier.ENDER;
        int fluidCap = Math.max(4000, tier.rate(Resource.FLUID) * 40);
        outFluid = new FluidTank(fluidCap, r -> true, this::setChanged);
        inFluid = new FluidTank(fluidCap, r -> true, this::setChanged);
        int energyCap = Math.max(1, tier.rate(Resource.ENERGY) * 20);
        outEnergy = energy(energyCap);
        inEnergy = energy(energyCap);
        power = energy(tier == LinkTier.QUANTUM ? XdimConfig.QUANTUM_POWER_CAPACITY.get() : 1);
        // Pipes face every side: the handlers exist on all six and follow the face's mode.
        for (Direction d : Direction.values()) {
            itemFaces[d.ordinal()] = new Face<>(d, Resource.ITEM, outItems, inItems);
            fluidFaces[d.ordinal()] = new Face<>(d, Resource.FLUID, outFluid, inFluid);
            energyFaces[d.ordinal()] = new EnergyFace(d);
        }
        // Sensible defaults: nothing moves until faces are set (a fresh endpoint receives on top,
        // sends from the bottom; the screen and the wrench change that).
        setMode(Direction.DOWN, Resource.ITEM, SEND);
        setMode(Direction.DOWN, Resource.FLUID, SEND);
        if (tier.carries(Resource.ENERGY)) setMode(Direction.DOWN, Resource.ENERGY, SEND);
        setMode(Direction.UP, Resource.ITEM, RECEIVE);
        setMode(Direction.UP, Resource.FLUID, RECEIVE);
        if (tier.carries(Resource.ENERGY)) setMode(Direction.UP, Resource.ENERGY, RECEIVE);
    }

    private SimpleEnergyHandler energy(int cap) {
        return new SimpleEnergyHandler(cap, cap, cap) {
            @Override
            protected void onEnergyChanged(int previousAmount) {
                setChanged();
            }
        };
    }

    private final class Items extends ItemStacksResourceHandler {
        Items() {
            super(SLOTS);
        }

        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            setChanged();
        }
    }

    // ---------------------------------------------------------------- state

    public LinkTier tier() {
        return tier;
    }

    public @Nullable UUID owner() {
        return owner;
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        setChanged();
        syncLedger();
    }

    public int channel() {
        return channel;
    }

    public void setChannel(int channel) {
        if (this.channel == channel) return;
        this.channel = channel;
        setChanged();
        syncLedger();
        if (level instanceof ServerLevel server) updateAnchor(server);
        sync();
    }

    public int mode(Direction face, Resource r) {
        return modes[face.ordinal() * 3 + r.ordinal()];
    }

    public void setMode(Direction face, Resource r, int mode) {
        if (!tier.carries(r)) mode = OFF;
        modes[face.ordinal() * 3 + r.ordinal()] = (byte) Math.floorMod(mode, 3);
        setChanged();
    }

    public byte[] modes() {
        return Arrays.copyOf(modes, modes.length);
    }

    public boolean has(Resource r, int mode) {
        for (Direction d : Direction.values()) if (mode(d, r) == mode) return true;
        return false;
    }

    /** May this player change the endpoint (the owner's team, an unowned endpoint, or creative). */
    public boolean mayControl(Player player) {
        if (owner == null || player.getAbilities().instabuild) return true;
        return level != null && level.getServer() != null && FactoryTeams.get(level.getServer()).sameTeam(owner, player.getUUID());
    }

    public boolean anchored() {
        return anchored;
    }

    public long ticksRun() {
        return ticksRun;
    }

    public int status() {
        return status;
    }

    public long[] lastSecond() {
        return lastSecond.clone();
    }

    public long costLastSecond() {
        return costLastSecond;
    }

    public boolean isActive() {
        return level != null && level.getGameTime() - lastTransfer < ACTIVE_TICKS;
    }

    public FluidTank outFluid() {
        return outFluid;
    }

    public FluidTank inFluid() {
        return inFluid;
    }

    public ItemStacksResourceHandler outItems() {
        return outItems;
    }

    public ItemStacksResourceHandler inItems() {
        return inItems;
    }

    public SimpleEnergyHandler outEnergy() {
        return outEnergy;
    }

    public SimpleEnergyHandler inEnergy() {
        return inEnergy;
    }

    public SimpleEnergyHandler power() {
        return power;
    }

    public GlobalPos globalPos() {
        return GlobalPos.of(level == null ? net.minecraft.world.level.Level.OVERWORLD : level.dimension(), worldPosition);
    }

    // ---------------------------------------------------------------- capabilities

    public ResourceHandler<ItemResource> itemHandler(@Nullable Direction side) {
        return side == null ? outItems : itemFaces[side.ordinal()];
    }

    public ResourceHandler<FluidResource> fluidHandler(@Nullable Direction side) {
        return side == null ? outFluid : fluidFaces[side.ordinal()];
    }

    public EnergyHandler energyHandler(@Nullable Direction side) {
        return side == null ? power : energyFaces[side.ordinal()];
    }

    /** One face's view of a resource: inserts go to the outgoing buffer on a sending face, extracts come from the incoming one on a receiving face. */
    private final class Face<T extends net.neoforged.neoforge.transfer.resource.Resource> implements ResourceHandler<T> {
        private final Direction side;
        private final LinkTier.Resource kind;
        private final ResourceHandler<T> out, in;

        Face(Direction side, LinkTier.Resource kind, ResourceHandler<T> out, ResourceHandler<T> in) {
            this.side = side;
            this.kind = kind;
            this.out = out;
            this.in = in;
        }

        private ResourceHandler<T> shown() {
            return mode(side, kind) == RECEIVE ? in : out;
        }

        @Override
        public int size() {
            return shown().size();
        }

        @Override
        public T getResource(int index) {
            return shown().getResource(index);
        }

        @Override
        public long getAmountAsLong(int index) {
            return shown().getAmountAsLong(index);
        }

        @Override
        public long getCapacityAsLong(int index, T resource) {
            return shown().getCapacityAsLong(index, resource);
        }

        @Override
        public boolean isValid(int index, T resource) {
            return mode(side, kind) == SEND && out.isValid(index, resource);
        }

        @Override
        public int insert(int index, T resource, int amount, TransactionContext transaction) {
            return mode(side, kind) == SEND ? out.insert(index, resource, amount, transaction) : 0;
        }

        @Override
        public int extract(int index, T resource, int amount, TransactionContext transaction) {
            return mode(side, kind) == RECEIVE ? in.extract(index, resource, amount, transaction) : 0;
        }
    }

    /** Energy through one face: sending faces fill the outgoing buffer, off faces feed the operating buffer (quantum). */
    private final class EnergyFace implements EnergyHandler {
        private final Direction side;

        EnergyFace(Direction side) {
            this.side = side;
        }

        private @Nullable SimpleEnergyHandler target() {
            return switch (mode(side, Resource.ENERGY)) {
                case SEND -> outEnergy;
                case RECEIVE -> inEnergy;
                default -> tier == LinkTier.QUANTUM ? power : null;
            };
        }

        @Override
        public long getAmountAsLong() {
            SimpleEnergyHandler t = target();
            return t == null ? 0 : t.getAmountAsLong();
        }

        @Override
        public long getCapacityAsLong() {
            SimpleEnergyHandler t = target();
            return t == null ? 0 : t.getCapacityAsLong();
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            int m = mode(side, Resource.ENERGY);
            if (m == RECEIVE) return 0;
            SimpleEnergyHandler t = target();
            return t == null ? 0 : t.insert(amount, transaction);
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            return mode(side, Resource.ENERGY) == RECEIVE ? inEnergy.extract(amount, transaction) : 0;
        }
    }

    // ---------------------------------------------------------------- ticking

    private <C> @Nullable C neighbour(BlockCapabilityCache<C, @Nullable Direction>[] caches,
                                      net.neoforged.neoforge.capabilities.BlockCapability<C, @Nullable Direction> cap,
                                      ServerLevel level, Direction d) {
        int i = d.ordinal();
        if (caches[i] == null) caches[i] = BlockCapabilityCache.create(cap, level, worldPosition.relative(d), d.getOpposite());
        return caches[i].getCapability();
    }

    void serverTick(ServerLevel level, BlockState state) {
        ticksRun++;
        long now = level.getGameTime();
        int itemMove = Math.max(1, tier.rate(Resource.ITEM) * 2);
        int fluidMove = Math.max(1000, tier.rate(Resource.FLUID) * 2);
        int energyMove = Math.max(1, tier.rate(Resource.ENERGY) * 2);
        for (Direction d : Direction.values()) {
            int im = mode(d, Resource.ITEM), fm = mode(d, Resource.FLUID), em = mode(d, Resource.ENERGY);
            if (im == RECEIVE && !ResourceHandlerUtil.isEmpty(inItems)) {
                ResourceHandlerUtil.moveStacking(inItems, neighbour(itemCaches, Capabilities.Item.BLOCK, level, d), r -> true, itemMove, null);
            } else if (im == SEND) {
                ResourceHandlerUtil.moveStacking(neighbour(itemCaches, Capabilities.Item.BLOCK, level, d), outItems, r -> true, itemMove, null);
            }
            if (fm == RECEIVE && !inFluid.isEmpty()) {
                ResourceHandlerUtil.moveStacking(inFluid, neighbour(fluidCaches, Capabilities.Fluid.BLOCK, level, d), r -> true, fluidMove, null);
            } else if (fm == SEND && outFluid.space() > 0) {
                ResourceHandlerUtil.moveStacking(neighbour(fluidCaches, Capabilities.Fluid.BLOCK, level, d), outFluid, r -> true, fluidMove, null);
            }
            if (tier.carries(Resource.ENERGY)) {
                if (em == RECEIVE && inEnergy.getAmountAsInt() > 0) {
                    EnergyHandlerUtil.move(inEnergy, neighbour(energyCaches, Capabilities.Energy.BLOCK, level, d), energyMove, null);
                }
            }
        }
        if (now % 20 == 0) {
            System.arraycopy(counting, 0, lastSecond, 0, counting.length);
            Arrays.fill(counting, 0);
            costLastSecond = costCounting;
            costCounting = 0;
            syncLedger();
            updateAnchor(level);
            boolean active = isActive();
            if (state.getValue(LinkBlock.ACTIVE) != active) {
                level.setBlock(worldPosition, state.setValue(LinkBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Called by {@link XdimService} after it moved something through this endpoint. */
    void counted(Resource r, boolean sent, long amount) {
        if (amount <= 0) return;
        counting[r.ordinal() * 2 + (sent ? 0 : 1)] += amount;
        long before = lastTransfer;
        lastTransfer = level == null ? 0 : level.getGameTime();
        if (level instanceof ServerLevel server && before < lastTransfer - ACTIVE_TICKS && !getBlockState().getValue(LinkBlock.ACTIVE)) {
            server.setBlock(worldPosition, getBlockState().setValue(LinkBlock.ACTIVE, true), Block.UPDATE_CLIENTS);
        }
    }

    void paid(long fe) {
        costCounting += fe;
    }

    void setStatus(int status) {
        this.status = status;
    }

    /** Takes {@code fe} from the operating buffer; false (and nothing taken) if it hasn't enough. */
    boolean pay(int fe, TransactionContext tx) {
        return power.extract(fe, tx) == fe;
    }

    // ---------------------------------------------------------------- ledger and chunk loading

    private void syncLedger() {
        if (!(level instanceof ServerLevel server) || owner == null || isRemoved()) return;
        XdimNetwork.get(server.getServer()).put(new XdimNetwork.Endpoint(globalPos(), channel, owner, tier, anchored));
    }

    /** Keeps the own chunk loaded while tuned to a channel that exists (within the team's limit). */
    void updateAnchor(ServerLevel level) {
        boolean want = false;
        if (owner != null && channel > 0) {
            XdimNetwork net = XdimNetwork.get(level.getServer());
            if (net.channel(channel) != null) {
                String team = FactoryTeams.get(level.getServer()).teamOf(owner);
                want = anchored || net.anchoredFor(level.getServer(), team, globalPos()) < XdimConfig.LOADED_PER_TEAM.get();
            }
        }
        setAnchored(level, want);
    }

    private void setAnchored(ServerLevel level, boolean want) {
        if (want == anchored) return;
        ChunkPos c = ChunkPos.containing(worldPosition);
        XdimContent.TICKETS.forceChunk(level, worldPosition, c.x(), c.z(), want, true);
        anchored = want;
        setChanged();
        syncLedger();
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel server) {
            setAnchored(server, false);
            XdimNetwork.get(server.getServer()).remove(GlobalPos.of(server.dimension(), pos));
            for (ItemStacksResourceHandler h : new ItemStacksResourceHandler[] {outItems, inItems}) {
                for (int i = 0; i < h.size(); i++) {
                    ItemResource r = h.getResource(i);
                    int n = h.getAmountAsInt(i);
                    if (!r.isEmpty() && n > 0) Block.popResource(server, pos, r.toStack(n));
                }
            }
        }
    }

    // ---------------------------------------------------------------- save and sync

    void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("channel", channel);
        tag.putLong("last_transfer", lastTransfer);
        return tag;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
        channel = input.getIntOr("channel", 0);
        String m = input.getStringOr("modes", "");
        if (m.length() == modes.length) {
            for (int i = 0; i < modes.length; i++) modes[i] = (byte) Math.floorMod(m.charAt(i) - '0', 3);
        }
        anchored = input.getBooleanOr("anchored", false);
        lastTransfer = input.getLongOr("last_transfer", Long.MIN_VALUE / 2);
        outItems.deserialize(input.childOrEmpty("out_items"));
        inItems.deserialize(input.childOrEmpty("in_items"));
        outFluid.load(input, "out_fluid");
        inFluid.load(input, "in_fluid");
        outEnergy.deserialize(input.childOrEmpty("out_energy"));
        inEnergy.deserialize(input.childOrEmpty("in_energy"));
        power.deserialize(input.childOrEmpty("power"));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (owner != null) output.store("owner", UUIDUtil.CODEC, owner);
        output.putInt("channel", channel);
        StringBuilder sb = new StringBuilder();
        for (byte b : modes) sb.append((char) ('0' + b));
        output.putString("modes", sb.toString());
        output.putBoolean("anchored", anchored);
        output.putLong("last_transfer", lastTransfer);
        outItems.serialize(output.child("out_items"));
        inItems.serialize(output.child("in_items"));
        outFluid.save(output, "out_fluid");
        inFluid.save(output, "in_fluid");
        outEnergy.serialize(output.child("out_energy"));
        inEnergy.serialize(output.child("in_energy"));
        power.serialize(output.child("power"));
    }
}
