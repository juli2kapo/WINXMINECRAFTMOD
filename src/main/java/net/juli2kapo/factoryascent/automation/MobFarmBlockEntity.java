package net.juli2kapo.factoryascent.automation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.juli2kapo.factoryascent.Config;
import net.juli2kapo.factoryascent.machine.AbstractMachineBlockEntity;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.machine.SlotRole;
import net.juli2kapo.factoryascent.mobs.CapturedMob;
import net.juli2kapo.factoryascent.mobs.MobCapsuleItem;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * Mob Farm Controller: put a filled Mob Capsule in and it produces what that mob drops when a
 * player kills it (its loot table, rolled with a fake player as the killer), one kill's worth
 * every {@link #TICKS_PER_KILL} ticks at speed 1, without ever spawning the mob. The capsule is
 * never used up. Bosses and the capsule blacklist are refused (the capsule can't hold them
 * anyway), and so are babies, which drop nothing.
 */
public class MobFarmBlockEntity extends AbstractMachineBlockEntity {
    public static final int TICKS_PER_KILL = 200;

    private float progress;
    private float energyCarry;
    /** The loot "victim", rebuilt whenever the capsule changes; never added to the world. */
    private @Nullable LivingEntity victim;
    private @Nullable CapturedMob victimOf;

    public MobFarmBlockEntity(BlockPos pos, BlockState state) {
        super(MachineType.MOB_FARM, pos, state);
    }

    @Override
    protected void configureEnergy() {
        energy.configure(100_000, 8_000, 0);
    }

    /** Whether this capsule's mob can be farmed. */
    public static boolean farmable(@Nullable CapturedMob mob) {
        if (mob == null) return false;
        var type = mob.type();
        var holder = type.builtInRegistryHolder();
        return !holder.is(MobCapsuleItem.BLACKLIST) && !holder.is(Tags.EntityTypes.BOSSES) && !mob.isBaby()
                && type.getDefaultLootTable().isPresent();
    }

    private @Nullable LivingEntity victim(ServerLevel level) {
        CapturedMob mob = MobCapsuleItem.captured(inventory.stack(slots.firstInput()));
        if (!farmable(mob)) {
            victim = null;
            victimOf = null;
            return null;
        }
        if (victim != null && mob.equals(victimOf)) return victim;
        Entity entity = mob.type().create(level, new net.minecraft.world.entity.EntitySpawnRequest(EntitySpawnReason.SPAWNER, true));
        victim = null;
        victimOf = mob;
        if (!(entity instanceof LivingEntity living)) return null;
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(entity.problemPath(), com.mojang.logging.LogUtils.getLogger())) {
            ValueInput in = TagValueInput.create(reporter, level.registryAccess(), mob.data());
            entity.load(in);
        }
        entity.snapTo(worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, 0f, 0f);
        if (living instanceof Mob m && m.isBaby()) return null;
        victim = living;
        return victim;
    }

    /** One kill's loot. */
    public List<ItemStack> rollLoot(ServerLevel level) {
        LivingEntity mob = victim(level);
        List<ItemStack> out = new ArrayList<>();
        if (mob == null) return out;
        Optional<ResourceKey<LootTable>> key = mob.getLootTable();
        if (key.isEmpty()) return out;
        Player killer = FakePlayerFactory.getMinecraft(level);
        DamageSource source = level.damageSources().playerAttack(killer);
        LootTable table = level.getServer().reloadableRegistries().getLootTable(key.get());
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, mob)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(worldPosition))
                .withParameter(LootContextParams.DAMAGE_SOURCE, source)
                .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, killer)
                .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, killer)
                .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer)
                .create(LootContextParamSets.ENTITY);
        table.getRandomItems(params, out::add);
        return out;
    }

    @Override
    protected boolean tickMachine(ServerLevel level) {
        lastEnergyRate = 0;
        if (victim(level) == null) {
            progress = 0;
            status = STATUS_IDLE;
            return false;
        }
        if (MachineOutputs.emptySlots(inventory) == 0) {
            status = STATUS_OUTPUT_FULL;
            return false;
        }
        float speed = (float) (type.speed() * speedMultiplier() * Config.MACHINE_SPEED.get());
        float cost = (float) (type.baseEnergy() * speed * energyMultiplier() * Config.MACHINE_ENERGY.get()) + energyCarry;
        int whole = (int) cost;
        if (energy.energy() < whole) {
            status = STATUS_NO_POWER;
            return false;
        }
        energyCarry = cost - whole;
        energy.consume(whole);
        lastEnergyRate = whole;
        progress += speed;
        status = STATUS_WORKING;
        while (progress >= TICKS_PER_KILL) {
            progress -= TICKS_PER_KILL;
            for (ItemStack drop : rollLoot(level)) MachineOutputs.insertOrDrop(inventory, drop, level, worldPosition);
        }
        return true;
    }

    @Override
    public boolean isItemValid(int index, ItemResource resource) {
        if (slots.role(index) == SlotRole.INPUT) return resource.getItem() instanceof MobCapsuleItem;
        return super.isItemValid(index, resource);
    }

    @Override
    public boolean canAutomationInsert(int index, ItemResource resource) {
        return slots.role(index) == SlotRole.INPUT && inventory.stack(index).isEmpty()
                && farmable(MobCapsuleItem.captured(resource.toStack(1)));
    }

    @Override
    public void onInventoryChanged(int index) {
        super.onInventoryChanged(index);
        if (slots.role(index) == SlotRole.INPUT) progress = 0;
    }

    @Override
    public int progressPermille() {
        return Math.min(1000, Math.round(progress * 1000 / TICKS_PER_KILL));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        progress = input.getFloatOr("progress", 0f);
        energyCarry = input.getFloatOr("energy_carry", 0f);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putFloat("progress", progress);
        output.putFloat("energy_carry", energyCarry);
    }
}
