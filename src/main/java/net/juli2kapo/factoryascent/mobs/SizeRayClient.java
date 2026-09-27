package net.juli2kapo.factoryascent.mobs;

import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

/**
 * Client side of the Minimizer and Maximizer rays: both arms come up to aim while charging, and a scoped ray
 * looking through its scope (first person, charging) zooms in like a spyglass, with the spyglass vignette, a
 * slower mouse, the hand hidden and a thin charge bar under the crosshair.
 */
public final class SizeRayClient {
    /** FOV multiplier while looking through the scope (about 3.3× zoom; the spyglass uses 0.1). */
    public static final float SCOPE_FOV = 0.3f;
    private static final float SCOPE_SENSITIVITY = 0.55f;
    private static final Identifier SCOPE_LAYER = Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "size_ray_scope");
    private static final Identifier SCOPE_TEXTURE = Identifier.withDefaultNamespace("textures/misc/spyglass_scope.png");

    private static float scopeScale = 0.5f;

    private SizeRayClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(SizeRayClient::registerExtensions);
        modBus.addListener(SizeRayClient::registerGuiLayers);
        NeoForge.EVENT_BUS.addListener(SizeRayClient::onComputeFov);
        NeoForge.EVENT_BUS.addListener(SizeRayClient::onPlayerTurn);
        NeoForge.EVENT_BUS.addListener(SizeRayClient::onRenderHand);
    }

    /** The ray the local player is looking through right now, or null. */
    private static @Nullable ItemStack scopedRayInUse() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !mc.options.getCameraType().isFirstPerson() || !player.isUsingItem()) return null;
        ItemStack stack = player.getUseItem();
        return stack.getItem() instanceof SizeRayItem && SizeRayItem.isScoped(stack) ? stack : null;
    }

    // ---------------------------------------------------------------- arm pose

    private static void registerExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public HumanoidModel.@Nullable ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                // Aim with both arms while charging; the "charging" item model is posed for raised arms.
                boolean charging = entity.isUsingItem() && entity.getUsedItemHand() == hand && entity.getUseItemRemainingTicks() > 0;
                return charging ? HumanoidModel.ArmPose.BOW_AND_ARROW : null;
            }
        }, MobContent.MINIMIZER_RAY.get(), MobContent.MAXIMIZER_RAY.get());
    }

    // ---------------------------------------------------------------- scope

    private static void onComputeFov(ComputeFovModifierEvent event) {
        if (event.getPlayer() == Minecraft.getInstance().player && scopedRayInUse() != null) {
            event.setNewFovModifier(event.getNewFovModifier() * SCOPE_FOV);
        }
    }

    private static void onPlayerTurn(CalculatePlayerTurnEvent event) {
        if (scopedRayInUse() != null) event.setMouseSensitivity(event.getMouseSensitivity() * SCOPE_SENSITIVITY);
    }

    private static void onRenderHand(RenderHandEvent event) {
        if (scopedRayInUse() != null) event.setCanceled(true);
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, SCOPE_LAYER, SizeRayClient::renderScope);
    }

    /** The vanilla spyglass vignette (same growth animation), plus the charge bar in the ray's colour. */
    private static void renderScope(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        ItemStack stack = scopedRayInUse();
        if (stack == null) {
            scopeScale = 0.5f;
            return;
        }
        scopeScale = Mth.lerp(0.5f * delta.getGameTimeDeltaTicks(), scopeScale, 1.125f);
        int guiW = graphics.guiWidth();
        int guiH = graphics.guiHeight();
        int size = Mth.floor(Math.min(guiW, guiH) * scopeScale);
        int left = (guiW - size) / 2;
        int top = (guiH - size) / 2;
        int right = left + size;
        int bottom = top + size;
        graphics.blit(RenderPipelines.GUI_TEXTURED, SCOPE_TEXTURE, left, top, 0f, 0f, size, size, size, size);
        graphics.fill(RenderPipelines.GUI, 0, bottom, guiW, guiH, 0xFF000000);
        graphics.fill(RenderPipelines.GUI, 0, 0, guiW, top, 0xFF000000);
        graphics.fill(RenderPipelines.GUI, 0, top, left, bottom, 0xFF000000);
        graphics.fill(RenderPipelines.GUI, right, top, guiW, bottom, 0xFF000000);

        LocalPlayer player = Minecraft.getInstance().player;
        float charge = Math.min(1f, player.getTicksUsingItem(delta.getGameTimeDeltaPartialTick(false)) / SizeRayItem.CHARGE_TICKS);
        int color = stack.getItem() instanceof SizeRayItem ray ? ray.color() : 0xFFFFFF;
        int barW = 32;
        int x0 = guiW / 2 - barW / 2;
        int y0 = guiH / 2 + 10;
        graphics.fill(RenderPipelines.GUI, x0 - 1, y0 - 1, x0 + barW + 1, y0 + 2, 0x90000000);
        int filled = Math.round(barW * charge);
        if (filled > 0) {
            int alpha = charge >= 1f ? 0xFF000000 : 0xC0000000;
            graphics.fill(RenderPipelines.GUI, x0, y0, x0 + filled, y0 + 1, alpha | color);
        }
    }
}
