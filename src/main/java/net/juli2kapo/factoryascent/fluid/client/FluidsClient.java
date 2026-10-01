package net.juli2kapo.factoryascent.fluid.client;

import java.util.Map;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.fluid.FluidContent;
import net.juli2kapo.factoryascent.fluid.ModFluids;
import net.juli2kapo.factoryascent.fluid.machine.FluidMachine;
import net.juli2kapo.factoryascent.fluid.pipe.FluidPipePayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** Client side of the fluids: fluid models (own still/flowing textures), screens, tank/pipe/derrick/turbine renderers. */
@Mod(value = FactoryAscent.MOD_ID, dist = Dist.CLIENT)
public final class FluidsClient {
    public FluidsClient(IEventBus modBus) {
        modBus.addListener(FluidsClient::fluidModels);
        modBus.addListener((RegisterMenuScreensEvent e) -> e.register(FluidContent.MACHINE_MENU.get(), FluidMachineScreen::new));
        modBus.addListener((ModelEvent.RegisterStandalone e) -> {
            for (var entry : Map.of(MachinePartRenderer.TURBINE_ROTOR, "steam_turbine_rotor", MachinePartRenderer.DERRICK_FRAME, "oil_derrick_frame",
                    MachinePartRenderer.DERRICK_BEAM, "oil_derrick_beam", MachinePartRenderer.DERRICK_CRANK, "oil_derrick_crank",
                    MachinePartRenderer.DERRICK_ROD, "oil_derrick_rod").entrySet()) {
                e.register(entry.getKey(), SimpleUnbakedStandaloneModel.simpleModelWrapper(
                        Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "block/" + entry.getValue())));
            }
        });
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> {
            e.registerBlockEntityRenderer(FluidContent.FLUID_TANK_BE.get(), TankRenderer::new);
            e.registerBlockEntityRenderer(FluidContent.FLUID_PIPE_BE.get(), PipeFluidRenderer::new);
            e.registerBlockEntityRenderer(FluidContent.machineType(FluidMachine.STEAM_TURBINE).get(),
                    c -> new MachinePartRenderer(MachinePartRenderer.Kind.TURBINE, c));
            e.registerBlockEntityRenderer(FluidContent.machineType(FluidMachine.OIL_DERRICK).get(),
                    c -> new MachinePartRenderer(MachinePartRenderer.Kind.DERRICK, c));
        });
        modBus.addListener((RegisterClientPayloadHandlersEvent e) -> e.register(FluidPipePayloads.FluidPipeView.TYPE, (payload, context) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof FluidPipeScreen screen && screen.pos().equals(payload.pos())) screen.update(payload);
            else if (payload.open() && mc.level != null) {
                mc.gui.setScreen(new FluidPipeScreen(payload, mc.level.getBlockState(payload.pos()).getBlock().getName()));
            }
        }));
    }

    private static void fluidModels(RegisterFluidModelsEvent event) {
        for (ModFluids.Def d : ModFluids.all()) {
            event.register(new FluidModel.Unbaked(
                    new Material(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "block/fluid/" + d.id + "_still")),
                    new Material(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "block/fluid/" + d.id + "_flow")),
                    null, (net.neoforged.neoforge.client.fluid.FluidTintSource) null), d.source(), d.flowing());
        }
    }
}
