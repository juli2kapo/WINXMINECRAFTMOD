package net.juli2kapo.factoryascent.capsule;

import net.juli2kapo.factoryascent.mobs.CapturedMob;
import net.juli2kapo.factoryascent.mobs.MobCapsuleItem;
import net.juli2kapo.factoryascent.mobs.SizeRayItem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/** Game test bodies for the Wither capture rule, the Size Chamber and the Mob Releaser (listed in ModGameTests.TESTS). */
public final class CapsuleGameTests {
    private CapsuleGameTests() {}

    /** A Wither can be captured only once it is below 10% health (and the Ender Dragon never). */
    public static void witherCaptureThreshold(GameTestHelper h) {
        WitherBoss wither = h.spawnWithNoFreeWill(EntityTypes.WITHER, new BlockPos(4, 2, 4));
        wither.setInvulnerableTicks(0);
        h.assertTrue(MobCapsuleItem.refusal(wither).isPresent(), "a healthy Wither must be refused");
        wither.setHealth(wither.getMaxHealth() * 0.11f);
        h.assertTrue(MobCapsuleItem.refusal(wither).isPresent(), "a Wither at 11% must still be refused");
        wither.setHealth(wither.getMaxHealth() * 0.08f);
        h.assertTrue(MobCapsuleItem.refusal(wither).isEmpty(), "a Wither below 10% must be capturable");
        float health = wither.getHealth();
        ItemStack capsule = MobCapsuleItem.store(wither);
        CapturedMob mob = MobCapsuleItem.captured(capsule);
        h.assertTrue(mob != null && mob.type() == EntityTypes.WITHER, "the capsule must hold the Wither");
        h.assertTrue(Math.abs(mob.health() - health) < 0.01f, "the capsule must keep the Wither's health");
        var dragon = EntityTypes.ENDER_DRAGON.create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.LOAD);
        h.assertTrue(dragon != null && MobCapsuleItem.refusal(dragon).isPresent(), "the Ender Dragon must stay refused");
        if (dragon != null) dragon.discard();
        h.succeed();
    }

    private static ItemStack capsuleOf(GameTestHelper h, LivingEntity entity) {
        return MobCapsuleItem.store(entity);
    }

    /** The Size Chamber resizes the captured mob to the target (in the capsule data), then heals it with FE. */
    public static void sizeChamberScalesAndHeals(GameTestHelper h) {
        Zombie zombie = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2));
        zombie.setHealth(4f);
        ItemStack capsule = capsuleOf(h, zombie);
        BlockPos pos = new BlockPos(4, 1, 4);
        h.setBlock(pos, CapsuleContent.SIZE_CHAMBER.get());
        SizeChamberBlockEntity chamber = h.getBlockEntity(pos, SizeChamberBlockEntity.class);
        chamber.inventory.setStack(0, capsule);
        chamber.energy().set(SizeChamberBlockEntity.CAPACITY);
        chamber.setTarget(500);
        for (int i = 0; i < SizeChamberBlockEntity.resizeDuration() + 2; i++) chamber.serverTick(h.getLevel());
        CapturedMob mob = chamber.mob();
        h.assertTrue(mob != null && Math.abs(CapsuleOps.scaleOf(mob) - 0.5) < 1e-3, "the mob should be at half size, is "
                + (mob == null ? "gone" : CapsuleOps.scaleOf(mob)));
        h.assertTrue(chamber.energyStored() < SizeChamberBlockEntity.CAPACITY, "resizing must cost energy");
        for (int i = 0; i < 200 && chamber.status() != SizeChamberBlockEntity.ST_DONE; i++) chamber.serverTick(h.getLevel());
        mob = chamber.mob();
        h.assertTrue(mob != null && mob.health() >= mob.maxHealth() - 1e-3, "the mob should be healed, has " + (mob == null ? 0 : mob.health()));
        h.assertTrue(Math.abs(mob.data().getFloatOr("Health", 0) - mob.maxHealth()) < 1e-3, "the saved health must match");
        // the target is clamped to the size-ray limits
        chamber.setTarget(1_000_000);
        h.assertTrue(chamber.target() == SizeChamberBlockEntity.maxPermille(), "the target must be capped at sizeRayMaxScale");
        // released, the mob keeps both
        LivingEntity out = (LivingEntity) MobCapsuleItem.restore(h.getLevel(), mob, h.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 1, 6.5)), 0f);
        h.assertTrue(out != null && Math.abs(SizeRayItem.sizeFactor(out) - 0.5) < 1e-3 && out.getHealth() >= out.getMaxHealth() - 1e-3,
                "the released mob must keep its size and health");
        h.succeed();
    }

    /** The Mob Releaser lets every captured mob out at once (on a rising redstone edge), keeping size and health. */
    public static void mobReleaserReleasesAll(GameTestHelper h) {
        BlockPos pos = new BlockPos(4, 1, 7);
        h.setBlock(pos, CapsuleContent.MOB_RELEASER.get());
        MobReleaserBlockEntity releaser = h.getBlockEntity(pos, MobReleaserBlockEntity.class);
        releaser.setDistance(3);
        Zombie small = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
        SizeRayItem.setSize(small, 0.5);
        Zombie plain = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 1, 1));
        WitherBoss wither = h.spawnWithNoFreeWill(EntityTypes.WITHER, new BlockPos(6, 2, 1));
        wither.setInvulnerableTicks(0);
        wither.setHealth(20f);
        releaser.inventory.setStack(0, capsuleOf(h, small));
        releaser.inventory.setStack(1, capsuleOf(h, plain));
        releaser.inventory.setStack(4, capsuleOf(h, wither));
        h.assertTrue(releaser.filled() == 3, "three capsules loaded");
        releaser.redstone(h.getLevel(), true);
        h.assertTrue(releaser.filled() == 3 && releaser.noPower(), "without FE the releaser lets nobody out");
        releaser.redstone(h.getLevel(), false);
        releaser.energy().set(MobReleaserBlockEntity.CAPACITY); // the releaser pays from its own buffer: the capsules have no charge
        releaser.redstone(h.getLevel(), true);
        h.assertTrue(releaser.filled() == 0, "a redstone pulse must release every capsule, " + releaser.filled() + " left");
        h.assertTrue(releaser.lastReleased() == 3, "three mobs released");
        h.assertTrue(releaser.energyStored() == MobReleaserBlockEntity.CAPACITY - 3 * MobReleaserBlockEntity.costPerMob(),
                "each mob costs the releaser " + MobReleaserBlockEntity.costPerMob() + " FE");
        AABB area = new AABB(h.absolutePos(new BlockPos(0, 0, 0))).expandTowards(9, 12, 9);
        var zombies = h.getLevel().getEntitiesOfClass(Zombie.class, area, Entity::isAlive);
        h.assertTrue(zombies.size() == 2, "two zombies out, found " + zombies.size());
        h.assertTrue(zombies.stream().anyMatch(z -> Math.abs(SizeRayItem.sizeFactor(z) - 0.5) < 1e-3), "the small zombie keeps its size");
        var withers = h.getLevel().getEntitiesOfClass(WitherBoss.class, area, Entity::isAlive);
        h.assertTrue(withers.size() == 1 && Math.abs(withers.get(0).getHealth() - 20f) < 0.01f, "the Wither comes out with its health");
        // only a rising edge fires: still powered, a new capsule waits
        Zombie later = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 1, 2));
        releaser.inventory.setStack(2, capsuleOf(h, later));
        releaser.redstone(h.getLevel(), true);
        h.assertTrue(releaser.filled() == 1, "a held signal must not fire again");
        releaser.redstone(h.getLevel(), false);
        releaser.redstone(h.getLevel(), true);
        h.assertTrue(releaser.filled() == 0, "a new pulse fires again");
        withers.forEach(Entity::discard);
        h.succeed();
    }
}
