package net.juli2kapo.factoryascent.ships;

import java.util.List;
import net.juli2kapo.factoryascent.orbital.OrbitalContent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Game test bodies for the ships, listed in {@code ModGameTests.TESTS}. */
public final class ShipGameTests {
    private ShipGameTests() {}

    private static void check(GameTestHelper h, boolean ok, String message) {
        if (!ok) h.fail(message);
    }

    /** A 9×9 pool one block deep over the arena floor. */
    private static void pool(GameTestHelper h) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                h.setBlock(x, 1, z, Blocks.WATER);
                for (int y = 2; y < 6; y++) h.setBlock(x, y, z, Blocks.AIR);
            }
        }
    }

    private static <T extends AbstractShip> T launch(GameTestHelper h, net.minecraft.world.entity.EntityType<T> type, double z) {
        T ship = h.spawn(type, new Vec3(4.5, 1.7, z));
        ship.setYRot(0f); // bow toward +z
        ship.setYHeadRot(0f);
        return ship;
    }

    /** The wind rules: beam reach beats head to wind, weather adds, and the factor stays in its clamp. */
    public static void sailRules(GameTestHelper h) {
        check(h, ShipMath.sailEfficiency(90) > ShipMath.sailEfficiency(0), "a beam reach must beat running downwind");
        check(h, ShipMath.sailEfficiency(0) > ShipMath.sailEfficiency(170), "running must beat sailing into the wind");
        check(h, ShipMath.sailEfficiency(180) > 0.1f, "head to wind still creeps");
        float wind = 30f;
        float beam = ShipMath.sailSpeedFactor(wind + 90, wind, 1f, 1.0);
        float irons = ShipMath.sailSpeedFactor(wind + 180, wind, 1f, 1.0);
        check(h, beam > irons * 2, "beam reach must be much faster than in irons: " + beam + " vs " + irons);
        check(h, ShipMath.windStrength(0, 1f, 1f) > ShipMath.windStrength(0, 0f, 0f), "storms must blow harder");
        check(h, ShipMath.sailSpeedFactor(wind + 180, wind, 0.3f, 1.0) >= 0.25f, "the factor has a floor");
        check(h, Math.abs(ShipMath.sailSpeedFactor(wind + 180, wind, 1f, 0.0) - 0.85f) < 1e-4, "no wind influence = 85%");
        h.succeed();
    }

    /** A cog with the sail set gathers way along its heading on water. */
    public static void cogSails(GameTestHelper h) {
        pool(h);
        BronzeCog cog = launch(h, ShipContent.BRONZE_COG.get(), 2.5);
        double z0 = cog.getZ();
        cog.setScriptedInput(AbstractShip.IN_FORWARD);
        h.runAfterDelay(40, () -> {
            check(h, cog.afloat(), "the cog must float");
            double speed = cog.getDeltaMovement().horizontalDistance();
            check(h, speed > 0.05, "the cog must be under way, speed " + speed);
            check(h, cog.getZ() - z0 > 1.0, "the cog must move along its heading, moved " + (cog.getZ() - z0));
            double expected = BronzeCog.BASE_SPEED * cog.sailFactor();
            check(h, speed < expected * 1.05, "speed " + speed + " must stay under the wind's top speed " + expected);
            h.succeed();
        });
    }

    /** Full ahead drains the battery at the configured rate; a stopped engine draws nothing. */
    public static void motorUsesPower(GameTestHelper h) {
        pool(h);
        MotorShip ship = launch(h, ShipContent.MOTOR_SHIP.get(), 2.5);
        ship.setFuel(10_000);
        ship.setScriptedInput(AbstractShip.IN_FORWARD);
        h.runAfterDelay(20, () -> {
            int used = 10_000 - ship.fuel();
            check(h, used >= 19 * ShipConfig.motorFePerTick() && used <= 21 * ShipConfig.motorFePerTick(),
                    "20 ticks full ahead should use about " + 20 * ShipConfig.motorFePerTick() + " FE, used " + used);
            check(h, ship.getDeltaMovement().horizontalDistance() > 0.05, "the ship must be moving");
            ship.setScriptedInput(0);
            int before = ship.fuel();
            h.runAfterDelay(10, () -> {
                check(h, ship.fuel() == before, "a stopped engine must not draw power");
                // coal in the power slot charges the battery through the on-board generator
                ship.setFuel(0);
                ship.setItem(ship.fuelSlot(), new ItemStack(Items.COAL, 1));
                h.runAfterDelay(2, () -> {
                    check(h, ship.fuel() == ShipMath.energyFromBurnTime(1600), "one coal = 64k FE, got " + ship.fuel());
                    check(h, ship.getItem(ship.fuelSlot()).isEmpty(), "the coal must be burnt");
                    h.succeed();
                });
            });
        });
    }

    /** The hold survives a save/load round trip, and breaking the ship packs it into the dropped item. */
    public static void cargoPersists(GameTestHelper h) {
        pool(h);
        MotorShip ship = launch(h, ShipContent.MOTOR_SHIP.get(), 4.5);
        ship.setItem(0, new ItemStack(Items.DIAMOND, 7));
        ship.setItem(53, new ItemStack(Items.OAK_LOG, 64));
        ship.setItem(ship.fuelSlot(), new ItemStack(Items.COAL, 3));
        ship.setFuel(1234);
        // save / load
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
        ship.saveWithoutId(out);
        MotorShip copy = ShipContent.MOTOR_SHIP.get().create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.LOAD);
        copy.load(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), out.buildResult()));
        check(h, copy.getItem(0).is(Items.DIAMOND) && copy.getItem(0).getCount() == 7, "diamonds must survive saving");
        check(h, copy.getItem(53).is(Items.OAK_LOG) && copy.getItem(53).getCount() == 64, "last hold slot must survive");
        check(h, copy.getItem(copy.fuelSlot()).is(Items.COAL), "the power slot must survive");
        check(h, copy.fuel() == 1234, "the battery must survive, got " + copy.fuel());
        // break it: one item comes out, carrying the hold and the charge
        ship.destroy(h.getLevel(), h.getLevel().damageSources().generic());
        List<ItemEntity> drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(ship.blockPosition()).inflate(4));
        check(h, drops.size() == 1, "breaking must drop exactly the ship item, got " + drops.size() + " drops");
        ItemStack dropped = drops.getFirst().getItem();
        check(h, dropped.is(ShipContent.MOTOR_SHIP_ITEM.get()), "the drop must be the motor ship, got " + dropped);
        ItemContainerContents contents = dropped.get(DataComponents.CONTAINER);
        check(h, contents != null, "the item must carry the hold");
        check(h, dropped.getOrDefault(ShipContent.SHIP_FUEL.get(), 0) == 1234, "the item must carry the charge");
        // and placing it again restores everything
        MotorShip placed = ShipContent.MOTOR_SHIP.get().create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.SPAWN_ITEM_USE);
        placed.readFromItem(dropped);
        check(h, placed.getItem(0).getCount() == 7 && placed.getItem(53).getCount() == 64, "re-placing must unpack the hold");
        check(h, placed.fuel() == 1234, "re-placing must restore the charge");
        drops.forEach(ItemEntity::discard);
        h.succeed();
    }

    /** The shuttle burns fuel to climb, and refuses to lift off on an empty tank. */
    public static void shuttleClimbs(GameTestHelper h) {
        for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++) for (int y = 1; y < 12; y++) h.setBlock(x, y, z, Blocks.AIR);
        Shuttle full = h.spawn(ShipContent.SHUTTLE.get(), new Vec3(4.5, 1, 4.5));
        full.setFuel(1000);
        double y0 = full.getY();
        full.setScriptedInput(AbstractShip.IN_UP);
        h.runAfterDelay(12, () -> {
            check(h, full.getY() - y0 > 1.5, "the shuttle must climb, rose " + (full.getY() - y0));
            check(h, full.fuel() < 1000 - 11 * 6 * ShipConfig.fuelUse() + 1, "climbing must burn 6 units a tick, tank " + full.fuel());
            full.discard();
            Shuttle empty = h.spawn(ShipContent.SHUTTLE.get(), new Vec3(4.5, 1, 4.5));
            double e0 = empty.getY();
            empty.setScriptedInput(AbstractShip.IN_UP);
            h.runAfterDelay(10, () -> {
                check(h, empty.getY() - e0 < 0.2, "an empty shuttle must not lift off");
                // pouring Rocket Fuel into the cockpit's slot fills the tank
                empty.setItem(empty.fuelSlot(), new ItemStack(OrbitalContent.ROCKET_FUEL.get(), 2));
                h.runAfterDelay(2, () -> {
                    check(h, empty.fuel() >= 2 * ShipConfig.fuelPerItem() - 20, "two Rocket Fuel must fill the tank, got " + empty.fuel());
                    check(h, empty.getItem(empty.fuelSlot()).isEmpty(), "the fuel slot must be poured out");
                    h.succeed();
                });
            });
        });
    }

    /** When a shuttle changes dimension (the thresholds as a pure rule; test servers have no orbit dimension). */
    public static void transferThresholds(GameTestHelper h) {
        ShipMath.Thresholds t = ShipMath.Thresholds.DEFAULT;
        int top = 320;
        var OW = ShipMath.Realm.OVERWORLD;
        var ORBIT = ShipMath.Realm.ORBIT;
        check(h, ShipMath.decide(OW, 419.9, 1.0, top, t) == ShipMath.Transfer.NONE, "below 420 stays");
        check(h, ShipMath.decide(OW, 420.0, 1.0, top, t) == ShipMath.Transfer.TO_ORBIT, "climbing through 420 goes to orbit");
        check(h, ShipMath.decide(OW, 430.0, -0.5, top, t) == ShipMath.Transfer.NONE, "falling above the line doesn't leave");
        check(h, ShipMath.decide(ShipMath.Realm.OTHER, 1000, 1, top, t) == ShipMath.Transfer.NONE, "other dimensions never transfer");
        check(h, ShipMath.decide(ORBIT, 15.9, -0.3, top, t) == ShipMath.Transfer.TO_OVERWORLD, "descending below 16 re-enters");
        check(h, ShipMath.decide(ORBIT, 15.9, 0.3, top, t) == ShipMath.Transfer.NONE, "climbing in orbit never re-enters");
        check(h, ShipMath.decide(ORBIT, 16, -0.3, top, t) == ShipMath.Transfer.NONE, "at the line stays in orbit");
        // arrivals never trigger the opposite transfer
        double inOrbit = ShipMath.arrivalY(ShipMath.Transfer.TO_ORBIT, top, t);
        double back = ShipMath.arrivalY(ShipMath.Transfer.TO_OVERWORLD, top, t);
        check(h, ShipMath.decide(ORBIT, inOrbit, -0.5, top, t) == ShipMath.Transfer.NONE, "arriving in orbit must not bounce back");
        check(h, ShipMath.decide(OW, back, 0.2, top, t) == ShipMath.Transfer.NONE, "re-entering must not bounce back up");
        check(h, back == 380, "re-entry arrives 40 below the line, got " + back);
        ShipMath.Thresholds odd = new ShipMath.Thresholds(0, 10, 16, 1);
        check(h, odd.arrivalInOrbit() >= odd.reentryY() + 32, "a bad arrival height is lifted clear of the re-entry line");
        check(h, ShipMath.decide(OW, odd.arrivalInOverworld(top), 0.1, top, odd) == ShipMath.Transfer.NONE,
                "a tiny re-entry drop still arrives below the line");
        // fuel rules: nothing on the ground, hovering costs, orbit is cheap
        check(h, ShipMath.shuttleFuelPerTick(false, false, false, false, false, 1) == 0, "parked burns nothing");
        check(h, ShipMath.shuttleFuelPerTick(false, true, false, false, false, 1) == 2, "hovering burns 2");
        check(h, ShipMath.shuttleFuelPerTick(true, true, false, false, false, 1) == 0, "drifting in orbit burns nothing");
        check(h, ShipMath.shuttleFuelPerTick(true, true, true, false, true, 1) == 2, "two axes in orbit burn 2");
        h.succeed();
    }
}
