package net.juli2kapo.factoryascent.trains;

import java.util.function.Consumer;
import java.util.function.Supplier;
import net.juli2kapo.factoryascent.fluid.FluidContent;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/**
 * A locomotive or wagon in item form: right-click a rail to set it on the track, its front toward
 * where you look. Its inventory, tank and charge come back out of the item.
 */
public class TrainItem extends Item {
    private final Supplier<? extends EntityType<? extends RollingStock>> type;

    public TrainItem(Supplier<? extends EntityType<? extends RollingStock>> type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public EntityType<? extends RollingStock> entityType() {
        return type.get();
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        RailShape shape = TrackWalker.shapeAt(level, pos, null);
        if (shape == null) return InteractionResult.FAIL;
        TrackWalker.Spot spot = new TrackWalker.Spot(pos, shape, 0.5);
        Vec3 at = spot.pos();
        if (!level.getEntitiesOfClass(RollingStock.class, new net.minecraft.world.phys.AABB(pos).inflate(0.4)).isEmpty()) {
            Player p = context.getPlayer();
            if (p != null && !level.isClientSide()) {
                p.sendOverlayMessage(Component.translatable("message.factoryascent.train.occupied").withStyle(ChatFormatting.YELLOW));
            }
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel server) {
            RollingStock stock = type.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
            if (stock == null) return InteractionResult.FAIL;
            stock.setInitialPos(at.x, at.y, at.z);
            ItemStack stack = context.getItemInHand();
            stock.readFromItem(stack);
            Player player = context.getPlayer();
            Vec3 look = player != null ? player.getLookAngle() : new Vec3(0, 0, 1);
            stock.face(look);
            server.addFreshEntity(stock);
            level.gameEvent(player, GameEvent.ENTITY_PLACE, at);
            stack.consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
                                TooltipFlag flag) {
        String id = BuiltInRegistries.ITEM.getKey(this).getPath();
        tooltip.accept(Component.translatable("tooltip.factoryascent." + id).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.train.place").withStyle(ChatFormatting.DARK_GRAY));
        var fluid = stack.get(FluidContent.TANK_CONTENTS.get());
        if (fluid != null && !fluid.isEmpty()) {
            var fs = fluid.copy();
            tooltip.accept(Component.translatable("tooltip.factoryascent.train.fluid", fs.getAmount(), fs.getHoverName())
                    .withStyle(ChatFormatting.AQUA));
        }
        Integer fe = stack.get(TrainContent.TRAIN_ENERGY.get());
        if (fe != null && fe > 0) {
            tooltip.accept(Component.translatable("tooltip.factoryascent.train.energy", EnergyUtil.format(fe)).withStyle(ChatFormatting.YELLOW));
        }
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) {
            long stacks = contents.nonEmptyItemCopyStream().count();
            if (stacks > 0) tooltip.accept(Component.translatable("tooltip.factoryascent.train.cargo", stacks).withStyle(ChatFormatting.AQUA));
        }
        if (stack.getItem() instanceof TrainItem t && Locomotive.class.isAssignableFrom(entityClass(t))) {
            tooltip.accept(Component.translatable("tooltip.factoryascent.train.controls").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static Class<?> entityClass(TrainItem item) {
        EntityType<?> t = item.entityType();
        return t == TrainContent.STEAM_LOCOMOTIVE.get() ? SteamLocomotive.class
                : t == TrainContent.DIESEL_LOCOMOTIVE.get() ? DieselLocomotive.class : RollingStock.class;
    }
}
