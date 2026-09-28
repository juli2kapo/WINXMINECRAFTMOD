package net.juli2kapo.factoryascent.ships;

import java.util.function.Consumer;
import java.util.function.Supplier;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A ship in item form. Sea ships are launched on water, the shuttle is set down on the ground (or a
 * Launch Pad). The hold, the fuel and the name come back out of the item's components
 * ({@link AbstractShip#readFromItem}).
 */
public class ShipItem extends Item {
    private final Supplier<? extends EntityType<? extends AbstractShip>> type;
    private final boolean sea;

    public ShipItem(Supplier<? extends EntityType<? extends AbstractShip>> type, boolean sea, Properties properties) {
        super(properties);
        this.type = type;
        this.sea = sea;
    }

    public EntityType<? extends AbstractShip> entityType() {
        return type.get();
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockHitResult hit = getPlayerPOVHitResult(level, player, sea ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE);
        if (hit.getType() != HitResult.Type.BLOCK) return InteractionResult.PASS;
        BlockPos pos = hit.getBlockPos();
        Vec3 at = hit.getLocation();
        if (sea) {
            if (!level.getFluidState(pos).is(FluidTags.WATER)) {
                if (!level.isClientSide()) player.sendOverlayMessage(Component.translatable("message.factoryascent.ship.need_water").withStyle(ChatFormatting.YELLOW));
                return InteractionResult.FAIL;
            }
            at = new Vec3(at.x, pos.getY() + level.getFluidState(pos).getHeight(level, pos) - 0.25, at.z);
        } else if (hit.getDirection() != net.minecraft.core.Direction.UP) {
            return InteractionResult.PASS;
        }
        AbstractShip ship = type.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (ship == null) return InteractionResult.FAIL;
        ship.snapTo(at.x, at.y, at.z, player.getYRot(), 0f);
        ship.setYHeadRot(player.getYRot());
        if (!level.noCollision(ship, ship.getBoundingBox().deflate(0.05))) {
            if (!level.isClientSide()) player.sendOverlayMessage(Component.translatable("message.factoryascent.ship.no_room").withStyle(ChatFormatting.YELLOW));
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel server) {
            ship.readFromItem(stack);
            server.addFreshEntity(ship);
            level.gameEvent(player, GameEvent.ENTITY_PLACE, at);
            stack.consume(1, player);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
                                TooltipFlag flag) {
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(this).getPath();
        tooltip.accept(Component.translatable("tooltip.factoryascent." + id).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable(sea ? "tooltip.factoryascent.ship.place_water" : "tooltip.factoryascent.ship.place_ground")
                .withStyle(ChatFormatting.DARK_GRAY));
        Integer fuel = stack.get(ShipContent.SHIP_FUEL.get());
        if (fuel != null && fuel > 0) {
            tooltip.accept(sea ? Component.translatable("tooltip.factoryascent.ship.energy", EnergyUtil.format(fuel)).withStyle(ChatFormatting.YELLOW)
                    : Component.translatable("tooltip.factoryascent.ship.fuel", fuel).withStyle(ChatFormatting.GOLD));
        }
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) {
            long stacks = contents.nonEmptyItemCopyStream().count();
            if (stacks > 0) tooltip.accept(Component.translatable("tooltip.factoryascent.ship.cargo", stacks).withStyle(ChatFormatting.AQUA));
        }
        tooltip.accept(Component.translatable(sea ? "tooltip.factoryascent.ship.controls" : "tooltip.factoryascent.shuttle.controls")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
