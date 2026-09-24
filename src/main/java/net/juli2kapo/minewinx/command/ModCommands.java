package net.juli2kapo.minewinx.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.juli2kapo.minewinx.Config;
import net.juli2kapo.minewinx.MineWinx;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Comandos del mod: /minewinx cooldown [multiplicador] */
@Mod.EventBusSubscriber(modid = MineWinx.MOD_ID)
public class ModCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("minewinx")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("cooldown")
                        .executes(context -> {
                            context.getSource().sendSuccess(() -> Component.translatable(
                                    "commands.minewinx.cooldown.current", String.valueOf(Config.cooldownMultiplier)), false);
                            return 1;
                        })
                        .then(Commands.argument("multiplicador", DoubleArgumentType.doubleArg(0.0, 10.0))
                                .executes(context -> {
                                    double value = DoubleArgumentType.getDouble(context, "multiplicador");
                                    Config.setCooldownMultiplier(value);
                                    context.getSource().sendSuccess(() -> Component.translatable(value == 0
                                                    ? "commands.minewinx.cooldown.set_none" : "commands.minewinx.cooldown.set",
                                            String.valueOf(value)), true);
                                    return 1;
                                }))));
    }
}
