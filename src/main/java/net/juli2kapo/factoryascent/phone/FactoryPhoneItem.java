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
import org.jspecify.annotations.Nullable;

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
        int kind = PhoneDevices.linkKind(be, state);
        if (kind < 0) return InteractionResult.PASS;
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            Component message = toggleLink(sp, context.getItemInHand(), server, pos, kind);
            sp.sendOverlayMessage(message);
        }
        return InteractionResult.SUCCESS;
    }

    /** What linking a block gives: the link, or why it can't be linked. */
    public record Resolved(PhoneMemory.@Nullable Link link, @Nullable Component error) {}

    /**
     * The link the block at {@code pos} makes (phone or Link Card): storage blocks resolve to their
     * network's terminal, Ender Beacons must be the player's own. {@code player} may be null (no owner check).
     */
    public static Resolved resolve(@Nullable ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        int kind = PhoneDevices.linkKind(be, level.getBlockState(pos));
        if (kind < 0) return new Resolved(null, Component.translatable("message.factoryascent.link_card.not_linkable").withStyle(ChatFormatting.RED));
        if (kind == PhoneMemory.STORAGE) {
            BlockPos terminal = be == null ? null : PhoneDevices.terminalFor(level, be);
            if (terminal == null) {
                return new Resolved(null, Component.translatable("message.factoryascent.phone.no_terminal").withStyle(ChatFormatting.RED));
            }
            pos = terminal;
        }
        if (kind == PhoneMemory.BEACON && player != null && be instanceof net.juli2kapo.factoryascent.ender.EnderBeaconBlockEntity beacon
                && beacon.owner() != null && !beacon.owner().equals(player.getUUID())) {
            return new Resolved(null, Component.translatable("message.factoryascent.beacon_not_yours").withStyle(ChatFormatting.RED));
        }
        return new Resolved(new PhoneMemory.Link(kind, GlobalPos.of(level.dimension(), pos),
                level.getBlockState(pos).getBlock().getDescriptionId()), null);
    }

    /**
     * Adds {@code link} to the phone's memory (phone sneak-use or Phone Dock). Returns null when it
     * was added (or was already there), else why not: the per-kind limits and one link per energy network.
     */
    public static @Nullable Component addLink(net.minecraft.server.MinecraftServer server, ItemStack phone, PhoneMemory.Link link) {
        PhoneMemory memory = PhoneMemory.of(phone);
        if (memory.find(link.pos()) != null) return null;
        int kind = link.kind();
        if (kind == PhoneMemory.MACHINE && memory.of(PhoneMemory.MACHINE).size() >= PhoneConfig.MAX_MACHINES.get()) {
            return Component.translatable("message.factoryascent.phone.too_many_machines", PhoneConfig.MAX_MACHINES.get())
                    .withStyle(ChatFormatting.RED);
        }
        if (kind == PhoneMemory.BEACON && memory.of(PhoneMemory.BEACON).size() >= PhoneConfig.MAX_BEACONS.get()) {
            return Component.translatable("message.factoryascent.phone.too_many_beacons", PhoneConfig.MAX_BEACONS.get())
                    .withStyle(ChatFormatting.RED);
        }
        if (kind == PhoneMemory.POWER) {
            // one link per network: a second cable of an already linked network adds nothing
            ServerLevel level = server.getLevel(link.pos().dimension());
            var net = level == null ? null : net.juli2kapo.factoryascent.energy.EnergyNetworkManager.get(level).networkAt(link.pos().pos());
            for (PhoneMemory.Link l : memory.of(PhoneMemory.POWER)) {
                if (net != null && l.pos().dimension().equals(link.pos().dimension()) && net.cables().contains(l.pos().pos())) {
                    return Component.translatable("message.factoryascent.phone.network_already").withStyle(ChatFormatting.YELLOW);
                }
            }
            if (memory.of(PhoneMemory.POWER).size() >= PhoneConfig.MAX_POWER.get()) {
                return Component.translatable("message.factoryascent.phone.too_many_networks", PhoneConfig.MAX_POWER.get())
                        .withStyle(ChatFormatting.RED);
            }
        }
        memory.with(link).store(phone);
        return null;
    }

    /** The message for a link that was just added. */
    public static Component linkedMessage(PhoneMemory.Link link) {
        Component name = Component.translatable(link.block());
        String key = switch (link.kind()) {
            case PhoneMemory.STORAGE -> "message.factoryascent.phone.linked_storage";
            case PhoneMemory.POWER -> "message.factoryascent.phone.linked_power";
            case PhoneMemory.BEACON -> "message.factoryascent.phone.linked_beacon";
            default -> "message.factoryascent.phone.linked_machine";
        };
        return Component.translatable(key, name).withStyle(ChatFormatting.AQUA);
    }

    /** Links the device at {@code pos} (or unlinks it if it was linked); returns the message to show. */
    public static Component toggleLink(ServerPlayer player, ItemStack phone, ServerLevel level, BlockPos pos, int kind) {
        Resolved resolved = resolve(player, level, pos);
        if (resolved.link() == null) return resolved.error();
        PhoneMemory.Link link = resolved.link();
        PhoneMemory memory = PhoneMemory.of(phone);
        if (memory.find(link.pos()) != null) {
            memory.without(link.pos()).store(phone);
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.6f, 0.8f);
            return Component.translatable("message.factoryascent.phone.unlinked", Component.translatable(link.block()))
                    .withStyle(ChatFormatting.YELLOW);
        }
        Component error = addLink(level.getServer(), phone, link);
        if (error != null) return error;
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.7f, 1.5f);
        return linkedMessage(link);
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
        int beacons = memory.of(PhoneMemory.BEACON).size();
        boolean storage = memory.storage() != null;
        if (machines + power > 0 || storage) {
            tooltip.accept(Component.translatable("tooltip.factoryascent.phone.links", machines, power,
                    Component.translatable(storage ? "gui.yes" : "gui.no")).withStyle(ChatFormatting.DARK_AQUA));
        }
        if (beacons > 0) {
            tooltip.accept(Component.translatable("tooltip.factoryascent.phone.beacons", beacons).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        tooltip.accept(Component.translatable("tooltip.factoryascent.phone.open").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.phone.link").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.phone.signal").withStyle(ChatFormatting.DARK_GRAY));
    }
}
