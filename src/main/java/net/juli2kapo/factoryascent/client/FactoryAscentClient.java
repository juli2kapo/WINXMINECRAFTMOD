package net.juli2kapo.factoryascent.client;

import java.util.List;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.registry.ModMenus;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@Mod(value = FactoryAscent.MOD_ID, dist = Dist.CLIENT)
public final class FactoryAscentClient {
    public FactoryAscentClient(IEventBus modBus) {
        modBus.addListener(FactoryAscentClient::registerScreens);
        net.juli2kapo.factoryascent.storagenet.StorageNetworkClient.register(modBus);
        // Before JEI reads the recipes on the same event.
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST, ClientRecipes::onRecipesReceived);
        NeoForge.EVENT_BUS.addListener(FactoryAscentClient::onTooltip);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.MACHINE.get(), MachineScreen::new);
    }

    /** "Used in: Quern, Burner Crusher, Crusher" on every item a machine can process. */
    private static void onTooltip(ItemTooltipEvent event) {
        List<MachineType> machines = ClientRecipes.usedIn(event.getItemStack());
        if (machines.isEmpty()) return;
        if (!net.minecraft.client.Minecraft.getInstance().hasShiftDown()) {
            event.getToolTip().add(Component.translatable("tooltip.factoryascent.hold_shift_uses").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        MutableComponent line = Component.translatable("tooltip.factoryascent.used_in").withStyle(ChatFormatting.GRAY);
        for (int i = 0; i < machines.size(); i++) {
            if (i > 0) line.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            line.append(Component.translatable("block.factoryascent." + machines.get(i).id()).withStyle(ChatFormatting.GOLD));
        }
        event.getToolTip().add(line);
    }
}
