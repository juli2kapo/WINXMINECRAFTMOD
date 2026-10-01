package net.juli2kapo.factoryascent.xdim;

import java.util.UUID;
import net.juli2kapo.factoryascent.Tier;
import net.juli2kapo.factoryascent.fluid.FluidContent;
import net.juli2kapo.factoryascent.fluid.machine.FluidMachine;
import net.juli2kapo.factoryascent.fluid.machine.PumpBlockEntity;
import net.juli2kapo.factoryascent.power.Generator;
import net.juli2kapo.factoryascent.power.MagmaticGeneratorBlockEntity;
import net.juli2kapo.factoryascent.power.PowerContent;
import net.juli2kapo.factoryascent.xdim.LinkTier.Resource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * GameTests of the interdimensional links. The far endpoints are placed straight into the Nether
 * or the End (each test in a chunk of its own, far from everything) and removed again at the end.
 */
public final class XdimGameTests {
    private XdimGameTests() {}

    /** A chunk-aligned spot in another dimension, one per test (slot), far out. */
    private static BlockPos far(int slot) {
        return new BlockPos(20_000 + slot * 64 + 8, 100, 20_000 + 8);
    }

    private static LinkBlockEntity link(GameTestHelper h, BlockPos rel, LinkTier tier, UUID owner) {
        h.setBlock(rel, (tier == LinkTier.QUANTUM ? XdimContent.QUANTUM_ENTANGLER : XdimContent.ENDER_LINK).get());
        LinkBlockEntity be = h.getBlockEntity(rel, LinkBlockEntity.class);
        be.setOwner(owner);
        allOff(be);
        return be;
    }

    private static ServerLevel level(GameTestHelper h, ResourceKey<Level> dim) {
        ServerLevel level = h.getLevel().getServer().getLevel(dim);
        if (level == null) throw h.assertionException("dimension " + dim.identifier() + " is not available");
        return level;
    }

    /** Places an endpoint in another dimension (loading the chunk first), with stone round it. */
    private static LinkBlockEntity remote(GameTestHelper h, ResourceKey<Level> dim, BlockPos pos, LinkTier tier, UUID owner) {
        ServerLevel level = level(h, dim);
        level.getChunk(pos);
        for (Direction d : Direction.values()) level.setBlock(pos.relative(d), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pos, (tier == LinkTier.QUANTUM ? XdimContent.QUANTUM_ENTANGLER : XdimContent.ENDER_LINK).get().defaultBlockState(),
                Block.UPDATE_ALL);
        LinkBlockEntity be = (LinkBlockEntity) level.getBlockEntity(pos);
        if (be == null) throw h.assertionException("remote endpoint missing");
        be.setOwner(owner);
        allOff(be);
        return be;
    }

    private static void clear(ServerLevel level, BlockPos center, int r) {
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
            if (!level.getBlockState(p).isAir()) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static void allOff(LinkBlockEntity be) {
        for (Direction d : Direction.values()) for (Resource r : Resource.VALUES) be.setMode(d, r, LinkBlockEntity.OFF);
    }

    private static int channel(MinecraftServer server, UUID owner, String name, LinkBlockEntity... links) {
        int id = XdimNetwork.get(server).create(owner, name, false).id();
        for (LinkBlockEntity l : links) l.setChannel(id);
        return id;
    }

    private static int count(ResourceHandler<ItemResource> h) {
        int n = 0;
        for (int i = 0; i < h.size(); i++) n += h.getAmountAsInt(i);
        return n;
    }

    private static void give(ResourceHandler<ItemResource> h, ItemStack stack) {
        try (Transaction tx = Transaction.openRoot()) {
            h.insert(ItemResource.of(stack), stack.getCount(), tx);
            tx.commit();
        }
    }

    private static int countIn(Container c) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) n += c.getItem(i).getCount();
        return n;
    }

    // ---------------------------------------------------------------- tests

    /** Items: a chest next to an Ender Link in the Overworld empties into a chest next to its partner in the Nether. */
    public static void itemsOverworldToNether(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        UUID owner = UUID.randomUUID();
        h.setBlock(new BlockPos(1, 1, 2), Blocks.CHEST);
        Container chest = (Container) h.getBlockEntity(new BlockPos(1, 1, 2), net.minecraft.world.level.block.entity.ChestBlockEntity.class);
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 24));
        LinkBlockEntity here = link(h, new BlockPos(2, 1, 2), LinkTier.ENDER, owner);
        here.setMode(Direction.WEST, Resource.ITEM, LinkBlockEntity.SEND);
        BlockPos farPos = far(0);
        LinkBlockEntity there = remote(h, Level.NETHER, farPos, LinkTier.ENDER, owner);
        there.setMode(Direction.UP, Resource.ITEM, LinkBlockEntity.RECEIVE);
        ServerLevel nether = level(h, Level.NETHER);
        nether.setBlock(farPos.above(), Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        channel(server, owner, "items", here, there);
        h.startSequence()
                .thenWaitUntil(() -> {
                    Container out = (Container) nether.getBlockEntity(farPos.above());
                    h.assertTrue(out != null && countIn(out) == 24, "24 cobblestone should arrive in the Nether, have "
                            + (out == null ? -1 : countIn(out)));
                    h.assertTrue(countIn(chest) == 0, "the Overworld chest should be empty");
                })
                .thenExecute(() -> clear(nether, farPos, 1))
                .thenSucceed();
    }

    /**
     * The use case end to end: a Pump on a Nether lava pool feeds a fluid pipe into an Ender Link;
     * its partner in the Overworld pushes the lava down a pipe into a Magmatic Generator, which runs.
     */
    public static void netherLavaToMagmatic(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        UUID owner = UUID.randomUUID();
        ServerLevel nether = level(h, Level.NETHER);
        BlockPos c = far(1);
        nether.getChunk(c);
        // a 3x3 lava pool in an obsidian basin, the pump over its middle, a pipe, then the link
        for (int dx = -2; dx <= 3; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -2; dy <= 2; dy++) nether.setBlock(c.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                nether.setBlock(c.offset(dx, -2, dz), Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_CLIENTS);
                boolean pool = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                nether.setBlock(c.offset(dx, -1, dz), pool ? Blocks.LAVA.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        nether.setBlock(c, FluidContent.machine(FluidMachine.PUMP).get().defaultBlockState(), Block.UPDATE_ALL);
        PumpBlockEntity pump = (PumpBlockEntity) nether.getBlockEntity(c);
        if (pump == null) throw h.assertionException("no pump");
        pump.energy().produce(pump.energy().capacity());
        BlockPos linkPos = c.east(2);
        nether.setBlock(linkPos, XdimContent.ENDER_LINK.get().defaultBlockState(), Block.UPDATE_ALL);
        LinkBlockEntity there = (LinkBlockEntity) nether.getBlockEntity(linkPos);
        if (there == null) throw h.assertionException("no nether link");
        there.setOwner(owner);
        allOff(there);
        there.setMode(Direction.WEST, Resource.FLUID, LinkBlockEntity.SEND);
        nether.setBlock(c.east(), FluidContent.FLUID_PIPES.get(Tier.LV).get().defaultBlockState(), Block.UPDATE_ALL);

        LinkBlockEntity here = link(h, new BlockPos(1, 1, 2), LinkTier.ENDER, owner);
        here.setMode(Direction.EAST, Resource.FLUID, LinkBlockEntity.RECEIVE);
        h.setBlock(new BlockPos(3, 1, 2), PowerContent.generator(Generator.MAGMATIC_GENERATOR).get().defaultBlockState());
        h.setBlock(new BlockPos(2, 1, 2), FluidContent.FLUID_PIPES.get(Tier.LV).get());
        MagmaticGeneratorBlockEntity gen = h.getBlockEntity(new BlockPos(3, 1, 2), MagmaticGeneratorBlockEntity.class);
        channel(server, owner, "lava", here, there);
        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(gen.energy().energy() > 0, "the Magmatic Generator should make FE from Nether lava (has "
                            + gen.fluidHandler().getAmountAsLong(0) + " mB, link in " + here.inFluid().amount() + ")");
                })
                .thenExecute(() -> {
                    h.assertTrue(pump.tank().amount() < pump.tank().capacity(), "the pump must not be backed up");
                    clear(nether, c, 3);
                })
                .thenSucceed();
    }

    /** Energy only moves between Quantum Entanglers, and loses its configured share on the way (×2 between two dimensions). */
    public static void energyOverworldToNether(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        UUID owner = UUID.randomUUID();
        LinkBlockEntity here = link(h, new BlockPos(2, 1, 2), LinkTier.QUANTUM, owner);
        here.setMode(Direction.DOWN, Resource.ENERGY, LinkBlockEntity.SEND);
        int amount = 10_000;
        here.outEnergy().set(amount);
        BlockPos farPos = far(2);
        LinkBlockEntity there = remote(h, Level.NETHER, farPos, LinkTier.QUANTUM, owner);
        there.setMode(Direction.UP, Resource.ENERGY, LinkBlockEntity.RECEIVE);
        // an Ender Link on the same channel never gets energy
        LinkBlockEntity ender = link(h, new BlockPos(5, 1, 5), LinkTier.ENDER, owner);
        ender.setMode(Direction.UP, Resource.ENERGY, LinkBlockEntity.RECEIVE);
        h.assertTrue(ender.mode(Direction.UP, Resource.ENERGY) == LinkBlockEntity.OFF, "an Ender Link has no energy faces");
        channel(server, owner, "energy", here, there, ender);
        ServerLevel nether = level(h, Level.NETHER);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(here.outEnergy().getAmountAsInt() == 0, "all energy should leave"))
                .thenExecute(() -> {
                    int loss = Math.min(1000, XdimConfig.ENERGY_LOSS_PERMILLE.get() * 2);
                    int expected = amount * (1000 - loss) / 1000;
                    int got = there.inEnergy().getAmountAsInt();
                    h.assertTrue(Math.abs(got - expected) <= 2, "expected about " + expected + " FE to arrive, got " + got);
                    h.assertTrue(ender.inEnergy().getAmountAsInt() == 0, "the Ender Link must not receive energy");
                    clear(nether, farPos, 1);
                })
                .thenSucceed();
    }

    /** Only faces set to send take in, only faces set to receive put out; off faces leave their neighbours alone. */
    public static void faceModesRespected(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        UUID owner = player.getUUID();
        LinkBlockEntity a = link(h, new BlockPos(2, 1, 2), LinkTier.ENDER, owner);
        LinkBlockEntity b = link(h, new BlockPos(5, 1, 2), LinkTier.ENDER, owner);
        h.setBlock(new BlockPos(1, 1, 2), Blocks.CHEST);  // a: west, off
        h.setBlock(new BlockPos(2, 1, 1), Blocks.CHEST);  // a: north, send
        h.setBlock(new BlockPos(5, 2, 2), Blocks.CHEST);  // b: up, receive
        h.setBlock(new BlockPos(5, 1, 1), Blocks.CHEST);  // b: north, off
        Container offA = (Container) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(1, 1, 2)));
        Container sendA = (Container) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(2, 1, 1)));
        Container recvB = (Container) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(5, 2, 2)));
        Container offB = (Container) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(5, 1, 1)));
        offA.setItem(0, new ItemStack(Items.DIRT, 8));
        sendA.setItem(0, new ItemStack(Items.SAND, 8));
        // set the faces the way the screen does it
        int id = XdimNetwork.get(server).create(owner, "faces", false).id();
        h.assertTrue(XdimPayloads.apply(player, a, XdimPayloads.SET_MODE, Direction.NORTH.get3DDataValue() * 3, LinkBlockEntity.SEND, ""), "set send");
        h.assertTrue(XdimPayloads.apply(player, b, XdimPayloads.SET_MODE, Direction.UP.get3DDataValue() * 3, LinkBlockEntity.RECEIVE, ""), "set receive");
        h.assertTrue(!XdimPayloads.apply(player, a, XdimPayloads.SET_MODE, Direction.UP.get3DDataValue() * 3 + 2, LinkBlockEntity.SEND, ""),
                "an Ender Link has no energy mode");
        h.assertTrue(XdimPayloads.apply(player, a, XdimPayloads.TUNE, id, 0, ""), "tune a");
        h.assertTrue(XdimPayloads.apply(player, b, XdimPayloads.TUNE, id, 0, ""), "tune b");
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(countIn(recvB) == 8, "8 sand should arrive in the receiving chest, has " + countIn(recvB)))
                .thenIdle(20)
                .thenExecute(() -> {
                    h.assertTrue(countIn(offA) == 8, "the off face's chest must keep its dirt");
                    h.assertTrue(countIn(sendA) == 0, "the sending face's chest should be empty");
                    h.assertTrue(countIn(offB) == 0, "nothing may come out of an off face");
                    h.assertTrue(recvB.getItem(0).is(Items.SAND) || recvB.getItem(1).is(Items.SAND), "sand, not dirt");
                })
                .thenSucceed();
    }

    /** A private channel is the team's: another team can't tune to it and its endpoints there stay dead, until it goes public. */
    public static void teamPrivacy(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer founder = h.makeMockServerPlayerInLevel();
        ServerPlayer outsider = h.makeMockServerPlayerInLevel();
        outsider.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);  // creative players may touch anything
        LinkBlockEntity mine = link(h, new BlockPos(2, 1, 2), LinkTier.ENDER, founder.getUUID());
        LinkBlockEntity theirs = link(h, new BlockPos(5, 1, 2), LinkTier.ENDER, outsider.getUUID());
        mine.setMode(Direction.UP, Resource.ITEM, LinkBlockEntity.SEND);
        theirs.setMode(Direction.UP, Resource.ITEM, LinkBlockEntity.RECEIVE);
        XdimNetwork net = XdimNetwork.get(server);
        int id = net.create(founder.getUUID(), "secret", false).id();
        h.assertTrue(XdimPayloads.apply(founder, mine, XdimPayloads.TUNE, id, 0, ""), "the founder tunes in");
        h.assertTrue(!XdimPayloads.apply(outsider, theirs, XdimPayloads.TUNE, id, 0, ""), "an outsider may not tune to a private channel");
        h.assertTrue(!XdimPayloads.apply(outsider, mine, XdimPayloads.SET_MODE, 0, LinkBlockEntity.SEND, ""), "nor change someone else's link");
        h.assertTrue(net.visibleTo(server, outsider.getUUID()).stream().noneMatch(c -> c.id() == id), "the private channel is not listed for outsiders");
        h.assertTrue(!XdimPayloads.apply(outsider, theirs, XdimPayloads.SET_PUBLIC, id, 1, ""), "outsiders can't open it up");
        theirs.setChannel(id);  // even forced onto the channel, it must stay dead
        give(mine.outItems(), new ItemStack(Items.DIAMOND, 4));
        h.startSequence()
                .thenIdle(30)
                .thenExecute(() -> {
                    h.assertTrue(count(theirs.inItems()) == 0, "nothing may reach another team's endpoint on a private channel");
                    h.assertTrue(theirs.status() == LinkBlockEntity.ST_DENIED, "the intruding endpoint is denied, status " + theirs.status());
                    h.assertTrue(XdimPayloads.apply(founder, mine, XdimPayloads.SET_PUBLIC, id, 1, ""), "the owner opens the channel up");
                })
                .thenWaitUntil(() -> h.assertTrue(count(theirs.inItems()) == 4, "a public channel delivers to anyone"))
                .thenSucceed();
    }

    /** A tuned endpoint keeps its own chunk loaded and ticking with nobody around; untuning it lets the chunk go. */
    public static void chunkLoadingKeepsRemoteTicking(GameTestHelper h) {
        UUID owner = UUID.randomUUID();
        ServerLevel nether = level(h, Level.NETHER);
        BlockPos farPos = far(3);
        LinkBlockEntity there = remote(h, Level.NETHER, farPos, LinkTier.ENDER, owner);
        LinkBlockEntity here = link(h, new BlockPos(2, 1, 2), LinkTier.ENDER, owner);
        channel(h.getLevel().getServer(), owner, "anchor", here, there);
        long[] start = new long[1];
        h.startSequence()
                .thenExecuteAfter(5, () -> {
                    h.assertTrue(there.anchored(), "a tuned endpoint should keep its chunk loaded");
                    h.assertTrue(XdimNetwork.get(nether.getServer()).isAnchored(there.globalPos()), "the network knows it is anchored");
                    start[0] = there.ticksRun();
                })
                .thenExecuteAfter(100, () -> {
                    long ran = there.ticksRun() - start[0];
                    h.assertTrue(ran >= 90, "the remote endpoint should keep ticking with nobody there, ran " + ran + " of 100 ticks");
                    h.assertTrue(nether.isPositionEntityTicking(farPos), "its chunk should be ticking");
                    there.setChannel(0);
                    h.assertTrue(!there.anchored(), "an untuned endpoint releases its chunk");
                    clear(nether, farPos, 1);
                    h.assertTrue(!XdimNetwork.get(nether.getServer()).isAnchored(new net.minecraft.core.GlobalPos(Level.NETHER, farPos)),
                            "a removed endpoint leaves the network");
                })
                .thenSucceed();
    }

    /** Ender Links don't reach the End; a Quantum Entangler does. Ender Links do reach the Nether. */
    public static void tierDimensionRules(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        UUID owner = UUID.randomUUID();
        h.assertTrue(LinkTier.ENDER.reaches(Level.OVERWORLD, Level.NETHER) == XdimConfig.ENDER_NETHER.get(), "ender: overworld-nether");
        h.assertTrue(!LinkTier.ENDER.reaches(Level.OVERWORLD, Level.END), "ender: not the End");
        h.assertTrue(!LinkTier.ENDER.reaches(Level.NETHER, net.juli2kapo.factoryascent.space.SpaceRules.ORBIT), "ender: not orbit");
        h.assertTrue(LinkTier.QUANTUM.reaches(Level.OVERWORLD, net.juli2kapo.factoryascent.space.SpaceRules.ORBIT), "quantum: orbit");
        h.assertTrue(LinkTier.distanceFactor(Level.OVERWORLD, Level.OVERWORLD) == 1
                && LinkTier.distanceFactor(Level.OVERWORLD, Level.NETHER) == 2
                && LinkTier.distanceFactor(Level.OVERWORLD, net.juli2kapo.factoryascent.space.SpaceRules.ORBIT) == 4, "distance factors");
        // Overworld sender → an Ender Link and a Quantum Entangler in the End
        LinkBlockEntity sender = link(h, new BlockPos(2, 1, 2), LinkTier.QUANTUM, owner);
        sender.setMode(Direction.UP, Resource.ITEM, LinkBlockEntity.SEND);
        sender.power().set(sender.power().getCapacityAsInt());
        BlockPos e1 = far(4), e2 = far(5);
        LinkBlockEntity enderEnd = remote(h, Level.END, e1, LinkTier.ENDER, owner);
        LinkBlockEntity quantumEnd = remote(h, Level.END, e2, LinkTier.QUANTUM, owner);
        enderEnd.setMode(Direction.UP, Resource.ITEM, LinkBlockEntity.RECEIVE);
        quantumEnd.setMode(Direction.UP, Resource.ITEM, LinkBlockEntity.RECEIVE);
        channel(server, owner, "far", sender, enderEnd, quantumEnd);
        give(sender.outItems(), new ItemStack(Items.IRON_INGOT, 32));
        ServerLevel end = level(h, Level.END);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(count(quantumEnd.inItems()) == 32, "the Quantum Entangler in the End should get all 32 ingots, has "
                        + count(quantumEnd.inItems())))
                .thenExecute(() -> {
                    h.assertTrue(count(enderEnd.inItems()) == 0, "an Ender Link in the End must get nothing");
                    clear(end, e1, 1);
                    clear(end, e2, 1);
                })
                .thenSucceed();
    }

    /** A Quantum link pays FE per item × distance; with power for only part of the load, only that part moves. */
    public static void quantumCostsFe(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        UUID owner = UUID.randomUUID();
        LinkBlockEntity sender = link(h, new BlockPos(2, 1, 2), LinkTier.QUANTUM, owner);
        sender.setMode(Direction.UP, Resource.ITEM, LinkBlockEntity.SEND);
        BlockPos farPos = far(6);
        LinkBlockEntity receiver = remote(h, Level.NETHER, farPos, LinkTier.QUANTUM, owner);
        receiver.setMode(Direction.UP, Resource.ITEM, LinkBlockEntity.RECEIVE);
        int per = XdimConfig.FE_PER_ITEM.get() * 2;  // Overworld ↔ Nether
        int affordable = 5;
        sender.power().set(per * affordable);
        give(sender.outItems(), new ItemStack(Items.GOLD_INGOT, 12));
        channel(server, owner, "toll", sender, receiver);
        ServerLevel nether = level(h, Level.NETHER);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(count(receiver.inItems()) >= affordable, "paid items should arrive"))
                .thenIdle(20)
                .thenExecute(() -> {
                    if (per > 0) {
                        h.assertTrue(count(receiver.inItems()) == affordable, "only " + affordable + " items are paid for, "
                                + count(receiver.inItems()) + " arrived");
                        h.assertTrue(sender.power().getAmountAsInt() == 0, "the operating buffer is spent");
                        h.assertTrue(sender.status() == LinkBlockEntity.ST_NO_POWER, "the sender reports no power, status " + sender.status());
                    }
                    // the faces with energy off feed the operating buffer
                    var face = sender.energyHandler(Direction.NORTH);
                    try (Transaction tx = Transaction.openRoot()) {
                        h.assertTrue(face.insert(per * 100, tx) == per * 100, "an energy-off face takes operating power");
                        tx.commit();
                    }
                })
                .thenWaitUntil(() -> h.assertTrue(count(receiver.inItems()) == 12, "with power back, the rest moves"))
                .thenExecute(() -> clear(nether, farPos, 1))
                .thenSucceed();
    }

    /** The Wrench cycles the clicked face through off → send → receive for everything the tier carries; strangers can't. */
    public static void wrenchCyclesFace(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        ServerPlayer stranger = h.makeMockServerPlayerInLevel();
        stranger.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        LinkBlockEntity q = link(h, new BlockPos(2, 1, 2), LinkTier.QUANTUM, player.getUUID());
        BlockPos abs = h.absolutePos(new BlockPos(2, 1, 2));
        ItemStack wrench = new ItemStack(net.juli2kapo.factoryascent.registry.ModItems.WRENCH.get());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, wrench);
        stranger.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, wrench.copy());
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(abs).relative(Direction.EAST, 0.5),
                Direction.EAST, abs, false);
        var state = h.getLevel().getBlockState(abs);
        for (int expected : new int[] {LinkBlockEntity.SEND, LinkBlockEntity.RECEIVE, LinkBlockEntity.OFF}) {
            state.useItemOn(player.getMainHandItem(), h.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            for (Resource r : Resource.VALUES) {
                h.assertTrue(q.mode(Direction.EAST, r) == expected, "east " + r.key() + " should be mode " + expected + ", is " + q.mode(Direction.EAST, r));
            }
        }
        state.useItemOn(stranger.getMainHandItem(), h.getLevel(), stranger, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        h.assertTrue(q.mode(Direction.EAST, Resource.ITEM) == LinkBlockEntity.OFF, "a stranger's wrench changes nothing");
        h.succeed();
    }
}
