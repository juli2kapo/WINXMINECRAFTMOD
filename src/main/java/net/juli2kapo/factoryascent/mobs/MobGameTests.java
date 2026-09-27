package net.juli2kapo.factoryascent.mobs;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Game test bodies for the mob tools, listed in {@code ModGameTests.TESTS}. */
public final class MobGameTests {
    private MobGameTests() {}

    /** A named, hurt, shrunken cow goes into a capsule and comes back out the same (with a new UUID). */
    public static void capsuleRoundTrip(GameTestHelper h) {
        Cow cow = h.spawnWithNoFreeWill(EntityType.COW, new BlockPos(3, 1, 3));
        cow.setCustomName(Component.literal("Bessie"));
        cow.setHealth(4.5f);
        cow.setAge(-24000);
        SizeRayItem.applyShot(cow, 0.5);
        var oldUuid = cow.getUUID();
        h.assertTrue(MobCapsuleItem.refusal(cow).isEmpty(), "a cow must be capturable");

        ItemStack full = MobCapsuleItem.store(cow);
        h.assertTrue(cow.isRemoved(), "the captured cow must leave the world");
        h.assertTrue(MobCapsuleItem.isFull(full), "the capsule must hold the cow");
        h.assertTrue(full.getMaxStackSize() == 1, "a full capsule must not stack");
        CapturedMob mob = MobCapsuleItem.captured(full);
        h.assertTrue(mob.type() == EntityType.COW, "stored type must be cow, got " + mob.type());

        Entity out = MobCapsuleItem.restore(h.getLevel(), mob, h.absoluteVec(new Vec3(6.5, 1, 6.5)), 0f);
        h.assertTrue(out instanceof Cow, "release must create a cow, got " + out);
        Cow back = (Cow) out;
        h.assertTrue(back.getCustomName() != null && back.getCustomName().getString().equals("Bessie"),
                "name must survive, got " + back.getCustomName());
        h.assertTrue(Math.abs(back.getHealth() - 4.5f) < 1e-3, "health must survive, got " + back.getHealth());
        h.assertTrue(back.isBaby(), "a calf must stay a calf");
        h.assertTrue(Math.abs(back.getAttributeValue(Attributes.SCALE) - 0.5) < 1e-6,
                "the ray's size must survive, got " + back.getAttributeValue(Attributes.SCALE));
        h.assertTrue(!back.getUUID().equals(oldUuid), "the released mob must get a new UUID");
        h.succeed();
    }

    /** Blacklisted entities (bosses, wardens…) are refused; ordinary mobs are not. */
    public static void capsuleRefusesBlacklisted(GameTestHelper h) {
        for (EntityType<? extends net.minecraft.world.entity.Mob> type : java.util.List.of(
                EntityType.WARDEN, EntityType.ELDER_GUARDIAN, EntityType.WITHER)) {
            var mob = h.spawnWithNoFreeWill(type, new BlockPos(4, 1, 4));
            h.assertTrue(mob.is(MobCapsuleItem.BLACKLIST), type + " must be in factoryascent:capsule_blacklist");
            h.assertTrue(MobCapsuleItem.refusal(mob).isPresent(), type + " must be refused");
            mob.discard();
        }
        var pig = h.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(4, 1, 4));
        h.assertTrue(MobCapsuleItem.refusal(pig).isEmpty(), "a pig must be accepted");
        var chicken = h.spawnWithNoFreeWill(EntityType.CHICKEN, new BlockPos(4, 1, 4));
        chicken.startRiding(pig, true, false);
        h.assertTrue(MobCapsuleItem.refusal(pig).isPresent(), "a mob carrying a passenger must be refused");
        h.assertTrue(MobCapsuleItem.refusal(chicken).isPresent(), "a riding mob must be refused");
        h.succeed();
    }

    /** Minimizer halves the size down to 0.25; Maximizer brings it back to 1 and removes the modifier. */
    public static void sizeRays(GameTestHelper h) {
        LivingEntity cow = h.spawnWithNoFreeWill(EntityType.COW, new BlockPos(4, 1, 4));
        double[] down = {0.5, 0.25, 0.25};
        for (double expected : down) {
            SizeRayItem.applyShot(cow, 0.5);
            h.assertTrue(Math.abs(cow.getAttributeValue(Attributes.SCALE) - expected) < 1e-6,
                    "minimizer: expected scale " + expected + ", got " + cow.getAttributeValue(Attributes.SCALE));
        }
        double[] up = {0.5, 1.0};
        for (double expected : up) {
            SizeRayItem.applyShot(cow, 2.0);
            h.assertTrue(Math.abs(cow.getAttributeValue(Attributes.SCALE) - expected) < 1e-6,
                    "maximizer: expected scale " + expected + ", got " + cow.getAttributeValue(Attributes.SCALE));
        }
        h.assertTrue(!cow.getAttribute(Attributes.SCALE).hasModifier(SizeRayItem.MODIFIER),
                "back at 1.0 the size modifier must be gone");
        for (int i = 0; i < 4; i++) SizeRayItem.applyShot(cow, 2.0);
        h.assertTrue(Math.abs(cow.getAttributeValue(Attributes.SCALE) - 4.0) < 1e-6, "maximizer must stop at 4");
        h.succeed();
    }
}
