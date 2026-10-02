package net.juli2kapo.factoryascent.capsule;

import com.mojang.logging.LogUtils;
import java.util.List;
import net.juli2kapo.factoryascent.mobs.CapturedMob;
import net.juli2kapo.factoryascent.mobs.MobCapsuleItem;
import net.juli2kapo.factoryascent.mobs.SizeRayItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import org.slf4j.Logger;

/**
 * Changes to a mob that sits in a Mob Capsule, without letting it out: its size (the same
 * modifier a Minimizer/Maximizer Ray shot leaves, so a released mob keeps it) and its health.
 */
public final class CapsuleOps {
    private static final Logger LOGGER = LogUtils.getLogger();

    private CapsuleOps() {}

    /** The ray size stored in the capsule's mob data (1 = normal). */
    public static double scaleOf(CapturedMob mob) {
        if (!mob.data().contains("attributes")) return 1.0;
        List<AttributeInstance.Packed> packed = AttributeInstance.Packed.LIST_CODEC
                .parse(NbtOps.INSTANCE, mob.data().get("attributes")).result().orElse(List.of());
        for (AttributeInstance.Packed p : packed) {
            if (!p.attribute().is(Attributes.SCALE)) continue;
            for (AttributeModifier m : p.modifiers()) {
                if (m.id().equals(SizeRayItem.MODIFIER)) return 1.0 + m.amount();
            }
        }
        return 1.0;
    }

    /**
     * The mob resized to {@code size} (clamped to the size-ray limits): it is rebuilt from its data
     * off-world, resized like a ray shot would, and saved again. Unchanged if it has no size.
     */
    public static CapturedMob withScale(ServerLevel level, CapturedMob mob, double size) {
        Entity entity = mob.type().create(level, EntitySpawnReason.LOAD);
        if (!(entity instanceof LivingEntity living)) {
            if (entity != null) entity.discard();
            return mob;
        }
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(entity.problemPath(), LOGGER)) {
            ValueInput in = TagValueInput.create(reporter, level.registryAccess(), mob.data());
            entity.load(in);
        }
        SizeRayItem.setSize(living, size);
        CapturedMob resized = MobCapsuleItem.snapshot(living);
        entity.discard();
        return resized;
    }

    /** The mob with {@code health} (clamped to its maximum). */
    public static CapturedMob withHealth(CapturedMob mob, float health) {
        float h = Math.max(0.5f, Math.min(mob.maxHealth(), health));
        CompoundTag data = mob.data().copy();
        data.putFloat("Health", h);
        return new CapturedMob(mob.type(), data, h, mob.maxHealth(), mob.customName());
    }
}
