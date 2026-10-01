package net.juli2kapo.factoryascent.phone;

import java.util.function.Consumer;
import net.juli2kapo.factoryascent.gear.PoweredItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Factory Phone: right-click to open it (a phone-shaped screen with apps, see {@link PhoneApps}).
 * Sneak-use it on a device to link or unlink it: a Storage Terminal or Controller (Storage app),
 * a machine, generator or reactor controller (Machines app: status and alerts), or a power cable
 * (Power app: that energy network). Runs on an FE battery, charged in a Charger or an Energy Cell.
 */
public class FactoryPhoneItem extends PoweredItem {
    public static final int CAPACITY = 100_000;

    public FactoryPhoneItem(Properties properties) {
        super(CAPACITY, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) return InteractionResult.PASS;
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockEntity be = level.getBlockEntity(pos);
        BlockState state = level.getBlockState(pos);
        int kind = be != null ? PhoneDevices.linkKind(be, state) : PhoneDevices.linkKind(null, state);
        if (kind < 0) return InteractionResult.PASS;
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            Component message = toggleLink(sp, context.getItemInHand(), server, pos, kind);
            sp.sendOverlayMessage(message);
        }
        return InteractionResult.SUCCESS;
    }

    /** Links the device at {@code pos} (or unlinks it if it was linked); returns the message to show. */
    public static Component toggleLink(ServerPlayer player, ItemStack phone, ServerLevel level, BlockPos pos, int kind) {
        PhoneMemory memory = PhoneMemory.of(phone);
        BlockEntity be = level.getBlockEntity(pos);
        if (kind == PhoneMemory.STORAGE) {
            BlockPos terminal = be == null ? null : PhoneDevices.terminalFor(level, be);
            if (terminal == null) {
                return Component.translatable("message.factoryascent.phone.no_terminal").withStyle(ChatFormatting.RED);
            }
            pos = terminal;
        }
        GlobalPos where = GlobalPos.of(level.dimension(), pos);
        String block = level.getBlockState(pos).getBlock().getDescriptionId();
        Component name = Component.translatable(block);
        if (memory.find(where) != null) {
            memory.without(where).store(phone);
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.6f, 0.8f);
            return Component.translatable("message.factoryascent.phone.unlinked", name).withStyle(ChatFormatting.YELLOW);
        }
        if (kind == PhoneMemory.MACHINE && memory.of(PhoneMemory.MACHINE).size() >= PhoneConfig.MAX_MACHINES.get()) {
            return Component.translatable("message.factoryascent.phone.too_many_machines", PhoneConfig.MAX_MACHINES.get())
                    .withStyle(ChatFormatting.RED);
        }
        if (kind == PhoneMemory.POWER) {
            // one link per network: a second cable of an already linked network replaces nothing
            var manager = net.juli2kapo.factoryascent.energy.EnergyNetworkManager.get(level);
            var net = manager.networkAt(pos);
            for (PhoneMemory.Link l : memory.of(PhoneMemory.POWER)) {
                if (net != null && l.pos().dimension().equals(level.dimension()) && net.cables().contains(l.pos().pos())) {
                    return Component.translatable("message.factoryascent.phone.network_already").withStyle(ChatFormatting.YELLOW);
                }
            }
            if (memory.of(PhoneMemory.POWER).size() >= PhoneConfig.MAX_POWER.get()) {
                return Component.translatable("message.factoryascent.phone.too_many_networks", PhoneConfig.MAX_POWER.get())
                        .withStyle(ChatFormatting.RED);
            }
        }
        memory.with(new PhoneMemory.Link(kind, where, block)).store(phone);
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.7f, 1.5f);
        String key = switch (kind) {
            case PhoneMemory.STORAGE -> "message.factoryascent.phone.linked_storage";
            case PhoneMemory.POWER -> "message.factoryascent.phone.linked_power";
            default -> "message.factoryascent.phone.linked_machine";
        };
        return Component.translatable(key, name).withStyle(ChatFormatting.AQUA);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp) {
            Component problem = PhoneService.open(sp, hand);
            if (problem != null) {
                sp.sendOverlayMessage(problem);
                return InteractionResult.FAIL;
            }
            level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.3f, 1.8f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        PhoneMemory memory = PhoneMemory.of(stack);
        int machines = memory.of(PhoneMemory.MACHINE).size(), power = memory.of(PhoneMemory.POWER).size();
        boolean storage = memory.storage() != null;
        if (machines + power > 0 || storage) {
            tooltip.accept(Component.translatable("tooltip.factoryascent.phone.links", machines, power,
                    Component.translatable(storage ? "gui.yes" : "gui.no")).withStyle(ChatFormatting.DARK_AQUA));
        }
        tooltip.accept(Component.translatable("tooltip.factoryascent.phone.open").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.phone.link").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.phone.signal").withStyle(ChatFormatting.DARK_GRAY));
    }
}
