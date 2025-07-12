//package net.juli2kapo.minewinx.event;
//
//import net.juli2kapo.minewinx.MineWinx;
//import net.juli2kapo.minewinx.powers.EnumPowers;
//import net.juli2kapo.minewinx.powers.FirePowers;
//import net.juli2kapo.minewinx.util.PlayerDataProvider;
//import net.minecraft.network.chat.Component;
//import net.minecraftforge.event.TickEvent;
//import net.minecraftforge.eventbus.api.SubscribeEvent;
//import net.minecraftforge.fml.common.Mod;
//
//
//@Mod.EventBusSubscriber(modid = MineWinx.MOD_ID)
//public class ServerEvents {
//
//    @SubscribeEvent
//    public static void onServerTick(TickEvent.ServerTickEvent event) {
//        if (event.phase == TickEvent.Phase.END) {
//            // Itera sobre todos los jugadores en el servidor
//            event.getServer().getPlayerList().getPlayers().forEach(player -> {
//                int activeSpellSlot = PlayerDataProvider.getActiveSpellId(player);
//                if (activeSpellSlot != 0) {
//                    player.sendSystemMessage(Component.literal("activeSpellSlot: " + activeSpellSlot));
//                }
//
//                int activeSpellDuration = PlayerDataProvider.getActiveSpellDuration(player);
//                player.sendSystemMessage(Component.literal("activeSpellDuration: " + activeSpellDuration));
//
//                String elementStr = PlayerDataProvider.getElement(player);
//                player.sendSystemMessage(Component.literal("elementStr: " + elementStr));
//
//                EnumPowers.Element element = EnumPowers.Element.fromName(elementStr);
//                player.sendSystemMessage(Component.literal("element: " + (element != null ? element.name() : "null")));
//
//                EnumPowers power = EnumPowers.getPower(element, activeSpellSlot);
//                player.sendSystemMessage(Component.literal("power: " + (power != null ? power.name() : "null")));
//
//
//                if (power != EnumPowers.UNKNOWN) {
//                    if (activeSpellDuration == 0) {
//                        // Es el primer tick del hechizo, lo ejecutamos para obtener la duración.
//                        int duration = power.execute(player);
//                        PlayerDataProvider.setActiveSpellDuration(player, duration);
//                    } else {
//                        // El hechizo ya está activo, solo ejecutamos su lógica de tick.
//                        power.execute(player);
//                    }
//                }
//
//                FirePowers.handleContinuousLaser(player);
//
//                PlayerDataProvider.tickActiveSpell(player);
//            });
//        }
//    }
//}