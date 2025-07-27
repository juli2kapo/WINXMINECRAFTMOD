package net.juli2kapo.minewinx.event;

import net.juli2kapo.minewinx.MineWinx;
import net.juli2kapo.minewinx.effect.ModEffects;
import net.juli2kapo.minewinx.powers.EnumPowers;
import net.juli2kapo.minewinx.powers.NaturePowers;
import net.juli2kapo.minewinx.util.PlayerDataProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MineWinx.MOD_ID)
public class ServerEvents {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player != null && !event.player.level().isClientSide()) {
            Player player = event.player;

            String elementStr = PlayerDataProvider.getElement(player);
            int stage = PlayerDataProvider.getStage(player);
            EnumPowers.Element element = EnumPowers.Element.fromName(elementStr);

            switch (element){
                case FIRE -> applyFireEffects(player, stage);
//                case EARTH -> applyEarthEffects(player);
//                case AIR -> applyAirEffects(player);
                case WATER -> applyWaterEffects(player, stage);
                case NATURE -> applyNatureEffects(player, stage);

            }
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.level().isClientSide() && entity.hasEffect(ModEffects.SLEEP.get())) {
            entity.removeEffect(ModEffects.SLEEP.get());
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

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        ItemStack crafted = event.getCrafting();
        if (crafted.getItem() == Items.DIAMOND_CHESTPLATE) {
            String element = PlayerDataProvider.getElement(player);
            if (!"Technology".equalsIgnoreCase(element)) {
                crafted.setCount(0); // Elimina el ítem
                // Opcional: mensaje al jugador
                player.sendSystemMessage(Component.literal("A casa."));
            }
        }
    }
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            ItemStack newItem = event.getTo();
            if (newItem.getItem() == Items.DIAMOND_CHESTPLATE) {
                String element = PlayerDataProvider.getElement(player);
                if (!"Technology".equalsIgnoreCase(element)) {
                    event.setCanceled(true); // Cancela el equipamiento
                    // Opcional: mensaje al jugador
                    player.sendSystemMessage(Component.literal("Solo puedes equipar la TecnoArmor si tienes el elemento Tecnología."));
                }
            }
        }
    }

    private static void applyNatureEffects(Player player, int stage) {
        NaturePowers.applyPassiveNatureGrowth(player);
    }
}