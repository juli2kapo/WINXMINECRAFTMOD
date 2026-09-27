package net.juli2kapo.factoryascent.item.drill;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.item.ElectricDrillItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.neoforged.neoforge.client.event.RegisterConditionalItemModelPropertyEvent;
import org.jspecify.annotations.Nullable;

/**
 * Client side of the Electric Drill: the {@code factoryascent:drill_charged} and {@code factoryascent:drilling}
 * item-model properties (glowing band, spinning bit) and the outline of the extra 3x3 blocks in Area mode.
 */
@EventBusSubscriber(modid = FactoryAscent.MOD_ID, value = Dist.CLIENT)
public final class DrillClient {
    /** Outline colour of the extra Area-mode blocks (translucent amber). */
    private static final int AREA_OUTLINE = 0x70FFA420;

    private DrillClient() {}

    @SubscribeEvent
    public static void registerProperties(RegisterConditionalItemModelPropertyEvent event) {
        event.register(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "drill_charged"), Charged.MAP_CODEC);
        event.register(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "drilling"), Drilling.MAP_CODEC);
    }

    /** True while the drill holds enough charge to mine a block. */
    public record Charged() implements ConditionalItemModelProperty {
        public static final MapCodec<Charged> MAP_CODEC = MapCodec.unit(new Charged());

        @Override
        public boolean get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed,
                           ItemDisplayContext context) {
            return ElectricDrillItem.hasCharge(stack);
        }

        @Override
        public MapCodec<Charged> type() {
            return MAP_CODEC;
        }
    }

    /**
     * True while the holder is digging with this drill: the local player holding attack on a block, or another
     * player swinging their main hand. Drives the spinning-bit model.
     */
    public record Drilling() implements ConditionalItemModelProperty {
        public static final MapCodec<Drilling> MAP_CODEC = MapCodec.unit(new Drilling());

        @Override
        public boolean get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed,
                           ItemDisplayContext context) {
            if (owner == null || !ElectricDrillItem.hasCharge(stack) || !owner.getMainHandItem().is(stack.getItem())) {
                return false;
            }
            Minecraft mc = Minecraft.getInstance();
            if (owner == mc.player) return mc.gameMode != null && mc.gameMode.isDestroying();
            return owner.swinging && owner.swingingArm == InteractionHand.MAIN_HAND;
        }

        @Override
        public MapCodec<Drilling> type() {
            return MAP_CODEC;
        }
    }

    @SubscribeEvent
    public static void outlineArea(ExtractBlockOutlineRenderStateEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack drill = player.getMainHandItem();
        if (!(drill.getItem() instanceof ElectricDrillItem) || ElectricDrillItem.mode(drill) != DrillMode.AREA
                || !ElectricDrillItem.hasCharge(drill)) return;
        ClientLevel level = event.getLevel();
        List<BlockPos> extra = DrillMining.areaTargets(level, event.getBlockPos(), event.getBlockState(),
                event.getHitResult().getDirection());
        if (extra.isEmpty()) return;
        List<BlockPos> positions = new ArrayList<>(extra.size());
        List<VoxelShape> shapes = new ArrayList<>(extra.size());
        for (BlockPos pos : extra) {
            VoxelShape shape = level.getBlockState(pos).getShape(level, pos, event.getCollisionContext());
            if (shape.isEmpty()) continue;
            positions.add(pos);
            shapes.add(shape);
        }
        event.addCustomRenderer((state, collector, poseStack, levelState) -> {
            Vec3 cam = levelState.cameraRenderState.pos;
            float width = Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;
            for (int i = 0; i < positions.size(); i++) {
                BlockPos pos = positions.get(i);
                poseStack.pushPose();
                poseStack.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
                collector.submitShapeOutline(poseStack, shapes.get(i), RenderTypes.lines(), AREA_OUTLINE, width,
                        state.isTranslucent());
                poseStack.popPose();
            }
            return false; // keep the vanilla outline on the centre block
        });
    }
}
