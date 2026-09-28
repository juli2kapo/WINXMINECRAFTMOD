package net.juli2kapo.factoryascent.ui.client;

import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.ender.client.RecallCharmScreen;
import net.juli2kapo.factoryascent.item.drill.DrillScreen;
import net.juli2kapo.factoryascent.ui.ScreenPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** Opens or refreshes the payload-driven screens when their views arrive. */
@EventBusSubscriber(modid = FactoryAscent.MOD_ID, value = Dist.CLIENT)
public final class ScreenPayloadsClient {
    private ScreenPayloadsClient() {}

    private static Component blockName(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? Component.empty() : mc.level.getBlockState(pos).getBlock().getName();
    }

    @SubscribeEvent
    static void register(RegisterClientPayloadHandlersEvent event) {
        event.register(ScreenPayloads.OpenDrill.TYPE, (payload, context) -> {
            InteractionHand hand = ScreenPayloads.hand(payload.hand());
            if (hand != null) Minecraft.getInstance().gui.setScreen(new DrillScreen(hand));
        });
        event.register(ScreenPayloads.CharmView.TYPE, (payload, context) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof RecallCharmScreen screen) screen.update(payload);
            else if (payload.open()) mc.gui.setScreen(new RecallCharmScreen(payload));
        });
        event.register(ScreenPayloads.PipeView.TYPE, (payload, context) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof PipeScreen screen && screen.pos().equals(payload.pos())) screen.update(payload);
            else if (payload.open()) mc.gui.setScreen(new PipeScreen(payload, blockName(payload.pos())));
        });
        event.register(ScreenPayloads.CableView.TYPE, (payload, context) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof CableScreen screen && screen.pos().equals(payload.pos())) screen.update(payload);
            else if (payload.open()) mc.gui.setScreen(new CableScreen(payload, blockName(payload.pos())));
        });
        event.register(ScreenPayloads.StorageView.TYPE, (payload, context) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof StorageNetScreen screen && screen.pos().equals(payload.pos())) screen.update(payload);
            else if (payload.open()) mc.gui.setScreen(new StorageNetScreen(payload, blockName(payload.pos())));
        });
    }
}
