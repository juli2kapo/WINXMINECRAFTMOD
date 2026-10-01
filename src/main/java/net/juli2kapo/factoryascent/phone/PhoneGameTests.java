package net.juli2kapo.factoryascent.phone;

import java.util.UUID;
import net.juli2kapo.factoryascent.Tier;
import net.juli2kapo.factoryascent.machine.AbstractMachineBlockEntity;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.nuclear.ReactorControllerBlockEntity;
import net.juli2kapo.factoryascent.orbital.FactoryTeams;
import net.juli2kapo.factoryascent.orbital.OrbitRegistry;
import net.juli2kapo.factoryascent.orbital.Satellite;
import net.juli2kapo.factoryascent.orbital.SatelliteType;
import net.juli2kapo.factoryascent.orbital.SurveyService;
import net.juli2kapo.factoryascent.phone.PhonePayloads.Action;
import net.juli2kapo.factoryascent.phone.apps.TeamApp;
import net.juli2kapo.factoryascent.power.PowerConfig;
import net.juli2kapo.factoryascent.power.PowerContent;
import net.juli2kapo.factoryascent.registry.ModBlocks;
import net.juli2kapo.factoryascent.registry.ModComponents;
import net.juli2kapo.factoryascent.storagenet.StorageContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Game test bodies for the Factory Phone, listed in {@code ModGameTests.TESTS}. */
public final class PhoneGameTests {
    private PhoneGameTests() {}

    private static ItemStack phone(int energy) {
        ItemStack stack = new ItemStack(PhoneContent.FACTORY_PHONE.get());
        stack.set(ModComponents.ENERGY.get(), energy);
        return stack;
    }

    /** A mock player standing in the test area, holding a charged phone in the main hand. */
    private static ServerPlayer holder(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setPos(h.absoluteVec(new Vec3(4.5, 2, 4.5)));
        player.setItemInHand(InteractionHand.MAIN_HAND, phone(FactoryPhoneItem.CAPACITY));
        return player;
    }

    /** A network app that only counts its actions: proves the signal gate in {@link PhoneService#handle}. */
    static final class Probe implements PhoneApp {
        static int actions;

        @Override
        public String id() {
            return "signal_probe";
        }

        @Override
        public int order() {
            return 1000;
        }

        @Override
        public boolean needsSignal() {
            return true;
        }

        @Override
        public CompoundTag data(PhoneContext ctx) {
            return new CompoundTag();
        }

        @Override
        public net.minecraft.network.chat.Component action(PhoneContext ctx, String action, CompoundTag args) {
            actions++;
            return null;
        }
    }

    private static void uplink(MinecraftServer server, ServerPlayer player) {
        String team = FactoryTeams.get(server).teamOf(player.getUUID());
        OrbitRegistry.get(server).add(team, new Satellite(SatelliteType.UPLINK, player.level().dimension(), 0L, "Relay", player.getUUID()));
    }

    private static Action action(String app, String action) {
        return new Action(app, action, new CompoundTag());
    }

    /**
     * Network apps need the team's Uplink Satellite over the dimension; offline apps always work.
     * Without uplink: no bars, network apps' buttons never reach the app, the Map app can't open
     * the survey map, team chat refuses, but Settings works. With uplink: 3 bars, the app gets them.
     */
    public static void signalRule(GameTestHelper h) {
        if (PhoneApps.get("signal_probe") == null) PhoneApps.register(new Probe()); // GameTest server only
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer player = holder(h);
        h.assertTrue(PhoneService.open(player, InteractionHand.MAIN_HAND) == null, "a charged phone must open");
        h.assertTrue(PhoneService.isOpen(player.getUUID()), "the phone session must be open");
        h.assertTrue(PhoneService.bars(player) == 0, "no uplink: no signal bars");
        int probed = Probe.actions;
        PhoneService.handle(player, action("signal_probe", "press"));
        h.assertTrue(Probe.actions == probed, "no signal: a network app's action must not run");
        PhoneService.handle(player, action("map", "launch"));
        h.assertTrue(!SurveyService.isOpen(player.getUUID()), "no signal: the Map app must not open the survey map");
        h.assertTrue(TeamApp.send(player, "hello?") != null, "no signal: team chat must refuse");
        String team = FactoryTeams.get(server).teamOf(player.getUUID());
        h.assertTrue(net.juli2kapo.factoryascent.phone.TeamChat.get(server).messages(team).isEmpty(), "nothing may be stored without signal");
        boolean sound = PhoneMemory.of(player.getMainHandItem()).sound();
        PhoneService.handle(player, action("settings", "sound"));
        h.assertTrue(PhoneMemory.of(player.getMainHandItem()).sound() != sound, "offline apps (Settings) must work without signal");
        uplink(server, player);
        h.assertTrue(PhoneService.bars(player) == 3, "one uplink: 3 bars, got " + PhoneService.bars(player));
        PhoneService.handle(player, action("signal_probe", "press"));
        h.assertTrue(Probe.actions == probed + 1, "with signal the network app's action must run");
        h.assertTrue(TeamApp.send(player, "hello!") == null, "with signal team chat must work");
        PhoneService.handle(player, new Action("", "close", new CompoundTag()));
        h.assertTrue(!PhoneService.isOpen(player.getUUID()), "close must end the session");
        boolean after = PhoneMemory.of(player.getMainHandItem()).sound();
        PhoneService.handle(player, action("settings", "sound"));
        h.assertTrue(PhoneMemory.of(player.getMainHandItem()).sound() == after, "actions without an open phone must be ignored");
        h.succeed();
    }

    /**
     * Sneak-using the phone links a machine (and again unlinks it); cables link their energy
     * network, a terminal the storage app; Settings' Unlink removes one; the watch list is capped.
     */
    public static void linkAndUnlink(GameTestHelper h) {
        ServerPlayer player = holder(h);
        player.setShiftKeyDown(true);
        BlockPos crusher = new BlockPos(1, 1, 1), cable = new BlockPos(3, 1, 1), cable2 = new BlockPos(4, 1, 1), terminal = new BlockPos(6, 1, 1);
        h.setBlock(crusher, ModBlocks.machine(MachineType.CRUSHER).get());
        h.setBlock(cable, ModBlocks.POWER_CABLES.get(Tier.LV).get());
        h.setBlock(cable2, ModBlocks.POWER_CABLES.get(Tier.LV).get());
        h.setBlock(terminal, StorageContent.TERMINAL.get());
        ItemStack stack = player.getMainHandItem();
        // the real sneak-use path
        BlockPos abs = h.absolutePos(crusher);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false)));
        h.assertTrue(PhoneMemory.of(stack).of(PhoneMemory.MACHINE).size() == 1, "sneak-use must link the crusher");
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false)));
        h.assertTrue(PhoneMemory.of(stack).of(PhoneMemory.MACHINE).isEmpty(), "sneak-using again must unlink it");
        FactoryPhoneItem.toggleLink(player, stack, h.getLevel(), abs, PhoneMemory.MACHINE);
        FactoryPhoneItem.toggleLink(player, stack, h.getLevel(), h.absolutePos(cable), PhoneMemory.POWER);
        FactoryPhoneItem.toggleLink(player, stack, h.getLevel(), h.absolutePos(cable2), PhoneMemory.POWER);
        h.assertTrue(PhoneMemory.of(stack).of(PhoneMemory.POWER).size() == 1, "two cables of one network are one link");
        FactoryPhoneItem.toggleLink(player, stack, h.getLevel(), h.absolutePos(terminal), PhoneMemory.STORAGE);
        PhoneMemory.Link storage = PhoneMemory.of(stack).storage();
        h.assertTrue(storage != null && storage.pos().pos().equals(h.absolutePos(terminal)), "the terminal must be the storage link");
        h.assertTrue(PhoneMemory.of(stack).links().size() == 3, "three links, got " + PhoneMemory.of(stack).links());
        // Settings' Unlink (needs an open phone; the position must match the index)
        h.assertTrue(PhoneService.open(player, InteractionHand.MAIN_HAND) == null, "open");
        int index = PhoneMemory.of(stack).links().indexOf(PhoneMemory.of(stack).find(net.minecraft.core.GlobalPos.of(h.getLevel().dimension(), abs)));
        CompoundTag wrong = new CompoundTag();
        wrong.putInt("index", index);
        wrong.putInt("x", abs.getX() + 1);
        wrong.putInt("z", abs.getZ());
        PhoneService.handle(player, new Action("settings", "unlink", wrong));
        h.assertTrue(PhoneMemory.of(stack).links().size() == 3, "a stale position must not unlink");
        CompoundTag args = new CompoundTag();
        args.putInt("index", index);
        args.putInt("x", abs.getX());
        args.putInt("z", abs.getZ());
        PhoneService.handle(player, new Action("settings", "unlink", args));
        h.assertTrue(PhoneMemory.of(stack).of(PhoneMemory.MACHINE).isEmpty() && PhoneMemory.of(stack).links().size() == 2,
                "Settings' Unlink must remove the crusher");
        // the cap
        int max = PhoneConfig.MAX_MACHINES.get();
        for (int i = 0; i <= max && i < 9; i++) {
            BlockPos p = new BlockPos(i, 1, 7);
            h.setBlock(p, ModBlocks.machine(MachineType.CRUSHER).get());
            FactoryPhoneItem.toggleLink(player, stack, h.getLevel(), h.absolutePos(p), PhoneMemory.MACHINE);
        }
        h.assertTrue(PhoneMemory.of(stack).of(PhoneMemory.MACHINE).size() == Math.min(max, 9), "the watch list is capped at " + max);
        PhoneService.handle(player, new Action("", "close", new CompoundTag()));
        h.succeed();
    }

    /**
     * Alerts: a watched reactor above the alarm temperature pushes an alarm at once; a watched
     * crusher that ran and then stopped (out of input) pushes a "stopped" alert after a few seconds.
     */
    public static void alertsWhenWatchedMachineStops(GameTestHelper h) {
        ServerPlayer player = holder(h);
        ItemStack stack = player.getMainHandItem();
        UUID id = player.getUUID();
        BlockPos reactor = new BlockPos(6, 1, 6);
        h.setBlock(reactor, PowerContent.REACTOR_CONTROLLER.get());
        ReactorControllerBlockEntity core = h.getBlockEntity(reactor, ReactorControllerBlockEntity.class);
        FactoryPhoneItem.toggleLink(player, stack, h.getLevel(), h.absolutePos(reactor), PhoneMemory.MACHINE);
        int alarm = PowerConfig.get(PowerConfig.ALARM_TEMPERATURE), meltdown = PowerConfig.get(PowerConfig.MELTDOWN_TEMPERATURE);
        core.setTemperature((alarm + meltdown) / 2f);
        PhoneService.watch(player);
        h.assertTrue(PhoneService.notificationsOf(id).stream().anyMatch(n -> n.level() == 2 && n.app().equals("machines")),
                "an overheating reactor must push an alarm, got " + PhoneService.notificationsOf(id));
        core.setTemperature(20f);
        BlockPos crusher = new BlockPos(2, 1, 2);
        h.setBlock(crusher, ModBlocks.machine(MachineType.CRUSHER).get());
        AbstractMachineBlockEntity be = h.getBlockEntity(crusher, AbstractMachineBlockEntity.class);
        be.energy().produce(be.energy().capacity());
        be.inventory().setStack(0, new ItemStack(Items.RAW_IRON));
        FactoryPhoneItem.toggleLink(player, stack, h.getLevel(), h.absolutePos(crusher), PhoneMemory.MACHINE);
        int before = PhoneService.notificationsOf(id).size();
        boolean[] sawWorking = {false};
        h.succeedWhen(() -> {
            if (be.status() == AbstractMachineBlockEntity.STATUS_WORKING) sawWorking[0] = true;
            PhoneService.watch(player);
            var list = PhoneService.notificationsOf(id);
            h.assertTrue(sawWorking[0], "the crusher must have worked first");
            h.assertTrue(list.size() > before && list.getLast().level() == 1 && list.getLast().app().equals("machines"),
                    "a watched machine that stopped must push an alert, got " + list);
        });
    }

    /**
     * Team chat: a message is stored for the team and pops up on online teammates' phones (with
     * signal), not on the sender's, not on another team's, and not on a phone with notifications off.
     */
    public static void teamChatDelivery(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        FactoryTeams teams = FactoryTeams.get(server);
        ServerPlayer alice = holder(h), bob = h.makeMockServerPlayerInLevel(), carol = holder(h);
        bob.getInventory().setItem(9, phone(FactoryPhoneItem.CAPACITY)); // in the backpack, not in hand
        String name = "Chat_" + UUID.randomUUID().toString().substring(0, 8);
        h.assertTrue(teams.create(alice.getUUID(), name).ok(), "team");
        h.assertTrue(teams.invite(alice.getUUID(), bob.getUUID()).ok() && teams.join(bob.getUUID(), name).ok(), "join");
        uplink(server, alice);
        uplink(server, carol);
        h.assertTrue(TeamApp.send(alice, "Reactor is up") == null, "sending with signal must work");
        String key = teams.teamOf(alice.getUUID());
        var messages = TeamChat.get(server).messages(key);
        h.assertTrue(!messages.isEmpty() && messages.getLast().text().equals("Reactor is up"), "the message must be stored for the team");
        h.assertTrue(PhoneService.notificationsOf(bob.getUUID()).stream().anyMatch(n -> n.app().equals("team")
                && n.body().getString().equals("Reactor is up")), "the teammate's phone must get it");
        h.assertTrue(PhoneService.notificationsOf(alice.getUUID()).isEmpty(), "the sender gets no notification");
        h.assertTrue(PhoneService.notificationsOf(carol.getUUID()).isEmpty(), "another team gets nothing");
        h.assertTrue(TeamApp.send(alice, "again") != null, "messages too fast must be refused");
        ItemStack bobs = bob.getInventory().getItem(9);
        PhoneMemory.of(bobs).withAlerts(false).store(bobs);
        h.runAfterDelay(12, () -> {
            int seen = PhoneService.notificationsOf(bob.getUUID()).size();
            h.assertTrue(TeamApp.send(alice, "quiet please") == null, "second message");
            h.assertTrue(PhoneService.notificationsOf(bob.getUUID()).size() == seen, "notifications off: nothing pushed");
            h.assertTrue(TeamChat.get(server).messages(key).getLast().text().equals("quiet please"), "but the message is stored");
            h.succeed();
        });
    }

    /**
     * Battery: an open phone drains every second, an empty phone won't open (and an open one shuts
     * down), and the Charger charges it.
     */
    public static void batteryDrainAndCharge(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        int start = 10_000;
        player.setItemInHand(InteractionHand.MAIN_HAND, phone(start));
        h.assertTrue(PhoneService.open(player, InteractionHand.MAIN_HAND) == null, "open");
        ServerPlayer flat = h.makeMockServerPlayerInLevel();
        flat.setItemInHand(InteractionHand.MAIN_HAND, phone(0));
        h.assertTrue(PhoneService.open(flat, InteractionHand.MAIN_HAND) != null, "an empty phone must not open");
        BlockPos charger = new BlockPos(4, 1, 4);
        h.setBlock(charger, ModBlocks.machine(MachineType.CHARGER).get());
        AbstractMachineBlockEntity be = h.getBlockEntity(charger, AbstractMachineBlockEntity.class);
        be.energy().produce(be.energy().capacity());
        be.inventory().setStack(be.inventory().slots().firstInput(), phone(0));
        h.runAfterDelay(45, () -> {
            int now = PhoneService.battery(player.getMainHandItem());
            h.assertTrue(now <= start - PhoneConfig.DRAIN_OPEN.get(), "an open phone must drain, still " + now);
            ItemStack in = be.inventory().stack(be.inventory().slots().firstInput());
            ItemStack out = be.inventory().stack(be.inventory().slots().firstOutput());
            int charged = Math.max(PhoneService.battery(in), PhoneService.battery(out));
            h.assertTrue(charged > 0, "the Charger must charge the phone");
            // nearly empty: the open phone shuts down within a second or two
            player.getMainHandItem().set(ModComponents.ENERGY.get(), 1);
            h.runAfterDelay(45, () -> {
                h.assertTrue(!PhoneService.isOpen(player.getUUID()), "a phone that runs flat must close");
                h.assertTrue(PhoneService.battery(player.getMainHandItem()) == 0, "and be empty");
                h.succeed();
            });
        });
    }
}
