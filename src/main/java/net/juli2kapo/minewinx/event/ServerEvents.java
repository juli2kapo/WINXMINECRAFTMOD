package net.juli2kapo.minewinx.event;

import net.juli2kapo.minewinx.MineWinx;
import net.juli2kapo.minewinx.effect.ModEffects;
import net.juli2kapo.minewinx.entity.PlayerIllusionEntity;
import net.juli2kapo.minewinx.item.ModItems;
import net.juli2kapo.minewinx.powers.DarkPowers;
import net.juli2kapo.minewinx.powers.EnumPowers;
import net.juli2kapo.minewinx.powers.NaturePowers;
import net.juli2kapo.minewinx.powers.StormPowers;
import net.juli2kapo.minewinx.powers.SunAndMoonPowers;
import net.juli2kapo.minewinx.powers.WaterPowers;
import net.juli2kapo.minewinx.util.PlayerDataProvider;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MineWinx.MOD_ID)
public class ServerEvents {

    // NOTA: PlayerTickEvent NO llega en este entorno (verificado con logs);
    // todas las pasivas por jugador corren desde onServerTick → tickPlayer.

    private static final java.util.Map<java.util.UUID, String> lastSyncedHud = new java.util.concurrent.ConcurrentHashMap<>();

    /** Pasivas por elemento, bonus de armadura y limpieza de vuelo — cada tick. */
    private static void tickPlayer(net.minecraft.server.level.ServerPlayer player) {
        String elementStr = PlayerDataProvider.getElement(player);
        int stage = PlayerDataProvider.getStage(player);
        EnumPowers.Element element = EnumPowers.Element.fromName(elementStr);

        switch (element) {
            case FIRE -> applyFireEffects(player, stage);
            case WATER -> applyWaterEffects(player, stage);
            case NATURE -> applyNatureEffects(player, stage);
            default -> {}
        }

        applyTecnoArmorSetBonus(player);

        // Las winx no vuelan: limpiar vuelo residual (NUNCA tocar creativo/espectador)
        if (!player.isCreative() && !player.isSpectator()) {
            if (player.getAbilities().mayfly || player.getAbilities().flying) {
                player.getAbilities().mayfly = false;
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
        }
    }

    private static void applyTecnoArmorSetBonus(Player player) {
        boolean fullSet = player.getItemBySlot(EquipmentSlot.HEAD).getItem() == ModItems.TECNO_HELMET.get()
                && player.getItemBySlot(EquipmentSlot.CHEST).getItem() == ModItems.TECNO_CHESTPLATE.get()
                && player.getItemBySlot(EquipmentSlot.LEGS).getItem() == ModItems.TECNO_LEGGINGS.get()
                && player.getItemBySlot(EquipmentSlot.FEET).getItem() == ModItems.TECNO_BOOTS.get();
        if (fullSet) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 210, 0, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 210, 0, false, false, true));
            // Duración larga para que la visión nocturna no parpadee en pantalla.
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, false, false, true));
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.level().isClientSide() && entity.hasEffect(ModEffects.SLEEP.get())) {
            entity.removeEffect(ModEffects.SLEEP.get());
        }
        // Pasiva de tormenta: inmunidad a los rayos (puede pararse dentro de su propio campo)
        if (!entity.level().isClientSide() && entity instanceof Player player
                && event.getSource().is(DamageTypes.LIGHTNING_BOLT)) {
            String element = PlayerDataProvider.getElement(player);
            if ("Storm".equalsIgnoreCase(element) && PlayerDataProvider.getStage(player) >= 1) {
                event.setCanceled(true);
                player.clearFire();
            }
        }
    }

    private static void applyFireEffects(Player player, int stage) {
        if (!player.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0, false, false));
        }
    }
    private static void applyEarthEffects(Player player, int stage) {
        if (!player.hasEffect(MobEffects.DAMAGE_RESISTANCE)) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0, false, false));
        }
    }
    private static void applyAirEffects(Player player, int stage) {
        if (!player.hasEffect(MobEffects.MOVEMENT_SPEED)) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0, false, false));
        }
    }
    private static void applyWaterEffects(Player player, int stage) {
        player.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 205, stage-1, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 205, stage-1, false, false, true));
        if (player.isInWaterOrRain()) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 205, stage-1, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 205, stage-1, false, false, true));
        }
    }

    /**
     * Ilusiones de mobs: se rompen (sin morir, sin loot) al ser golpeadas por un
     * jugador y son inmunes a cualquier otro daño. Reemplaza el viejo override de
     * hurt() del Mob anónimo — ahora las ilusiones son mobs reales con IA completa.
     */
    @SubscribeEvent
    public static void onIllusionAttacked(LivingAttackEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (entity instanceof PlayerIllusionEntity) return; // las de jugador tienen su propia lógica
        if (!(entity instanceof Mob) || !entity.getTags().contains("Illusion")) return;

        event.setCanceled(true);
        if (event.getSource().getEntity() instanceof Player) {
            ServerLevel serverLevel = (ServerLevel) entity.level();
            serverLevel.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0F, 1.2F);
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    entity.getX(), entity.getY() + entity.getBbHeight() / 2.0, entity.getZ(),
                    15, 0.3, 0.3, 0.3, 0.1);
            entity.discard();
        }
    }

    /**
     * Las ilusiones nunca eligen como objetivo a su creadora (la IA vanilla de un
     * monstruo real apuntaría a cualquier jugador cercano).
     */
    @SubscribeEvent
    public static void onIllusionChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!entity.getTags().contains("Illusion")) return;

        if (event.getNewTarget() instanceof Player target) {
            CompoundTag data = entity.getPersistentData();
            if (data.hasUUID(DarkPowers.CREATOR_UUID_TAG)
                    && data.getUUID(DarkPowers.CREATOR_UUID_TAG).equals(target.getUUID())) {
                event.setCanceled(true);
            }
        }
    }

    private static boolean isTecnoArmor(ItemStack stack) {
        return stack.getItem() == ModItems.TECNO_HELMET.get()
                || stack.getItem() == ModItems.TECNO_CHESTPLATE.get()
                || stack.getItem() == ModItems.TECNO_LEGGINGS.get()
                || stack.getItem() == ModItems.TECNO_BOOTS.get();
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        ItemStack crafted = event.getCrafting();
        if (isTecnoArmor(crafted)) {
            String element = PlayerDataProvider.getElement(player);
            if (!"Technology".equalsIgnoreCase(element)) {
                crafted.setCount(0); // Elimina el ítem
                player.sendSystemMessage(Component.translatable("message.minewinx.tecno_armor.craft_denied"));
            }
        }
    }
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            ItemStack newItem = event.getTo();
            if (isTecnoArmor(newItem)) {
                // Creativo: sin restricción (y evita duplicaciones — el inventario
                // creativo es autoritativo del cliente y "devolver" la pieza la clona)
                if (player.isCreative()) return;

                String element = PlayerDataProvider.getElement(player);
                int stage = PlayerDataProvider.getStage(player);
                if (!"Technology".equalsIgnoreCase(element) || stage < 3) {
                    // El evento NO es cancelable: rechazar a mano. Vaciar el slot
                    // (NO restaurar 'from': en un swap con cursor eso duplicaba)
                    // y devolver la pieza rechazada una única vez.
                    ItemStack rejected = newItem.copy();
                    player.setItemSlot(event.getSlot(), ItemStack.EMPTY);
                    player.getInventory().placeItemBackInInventory(rejected);
                    player.sendSystemMessage(Component.translatable("message.minewinx.tecno_armor.equip_denied"));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            // Pass the server level from the event
            SunAndMoonPowers.onServerTick(event.getServer().overworld());
            StormPowers.onServerTick(event.getServer().overworld());
            WaterPowers.onServerTick(event.getServer().overworld());

            net.juli2kapo.minewinx.util.TransientLights.tick(event.getServer().overworld());

            // Órdenes de ilusiones: re-aplicarlas para que la IA vanilla no las pise
            for (net.minecraft.server.level.ServerLevel lvl : event.getServer().getAllLevels()) {
                net.juli2kapo.minewinx.powers.DarkPowers.tickOrders(lvl);
            }

            // Pasivas + HUD por acá: este handler está VERIFICADO en juego
            // (los géiseres corren por él); PlayerTickEvent no nos llega.
            boolean hudTick = event.getServer().getTickCount() % 20 == 0;
            for (net.minecraft.server.level.ServerPlayer sp : event.getServer().getPlayerList().getPlayers()) {
                tickPlayer(sp);
                if (hudTick) {
                    syncHudNow(sp);
                    // Estado de alas: paquete chico, se reenvía siempre para cubrir
                    // a quien recién empieza a ver al jugador (StartTracking no nos llega).
                    net.juli2kapo.minewinx.network.PacketHandler.sendToTracking(
                            new net.juli2kapo.minewinx.network.WingStateS2CPacket(sp), sp);
                }
            }
        }
    }

    private static void syncHudNow(net.minecraft.server.level.ServerPlayer serverPlayer) {
        String element = PlayerDataProvider.getElement(serverPlayer);
        int stage = PlayerDataProvider.getStage(serverPlayer);
        String plant = "Nature".equalsIgnoreCase(element)
                ? net.juli2kapo.minewinx.powers.NaturePowers.getSelectedPlant(serverPlayer).name() : "";
        String state = element + "|" + stage + "|" + plant;
        if (!state.equals(lastSyncedHud.get(serverPlayer.getUUID()))) {
            lastSyncedHud.put(serverPlayer.getUUID(), state);
            MineWinx.LOGGER.info("[HUD] sync a {}: {}", serverPlayer.getName().getString(), state);
            net.juli2kapo.minewinx.network.PacketHandler.sendToPlayer(
                    new net.juli2kapo.minewinx.network.HudStateS2CPacket(element, stage, plant), serverPlayer);
        }
    }

    private static void applyNatureEffects(Player player, int stage) {
        NaturePowers.applyPassiveNatureGrowth(player);
    }
}