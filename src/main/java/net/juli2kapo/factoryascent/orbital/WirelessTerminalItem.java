package net.juli2kapo.factoryascent.orbital;

import java.util.function.Consumer;
import net.juli2kapo.factoryascent.storagenet.StorageTerminalBlockEntity;
import net.juli2kapo.factoryascent.storagenet.TerminalMenu;
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
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Wireless Terminal: sneak-use it on a Storage Terminal to link it, then use it anywhere in the
 * same dimension to open that terminal, as long as your team has uplink coverage there
 * ({@link OrbitalSignal}) and the terminal's chunk is loaded (an Ender Anchor can keep it loaded).
 * The session stays open while you hold the linked terminal and keep the signal. Sneak-use it in
 * the air to open the Team screen ({@link OrbitalConsole}).
 *
 * <p>Survey map: using an unlinked terminal (or the Team screen's Map button while carrying any
 * Wireless Terminal) opens your team's survey map of this dimension centred on you, over the uplink
 * ({@link SurveyService#openRemote}).
 */
public class WirelessTerminalItem extends Item {
    public WirelessTerminalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (player == null || !player.isShiftKeyDown() || !(level.getBlockEntity(pos) instanceof StorageTerminalBlockEntity)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            context.getItemInHand().set(OrbitalContent.LINKED_TERMINAL.get(), GlobalPos.of(level.dimension(), pos));
            player.sendOverlayMessage(Component.translatable("message.factoryascent.wireless_linked").withStyle(ChatFormatting.AQUA));
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.5f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        if (sp.isShiftKeyDown()) {
            OrbitalConsole.open(sp);
            return InteractionResult.SUCCESS;
        }
        if (stack.get(OrbitalContent.LINKED_TERMINAL.get()) == null) {
            Component problem = SurveyService.openRemote(sp);
            if (problem != null) {
                sp.sendOverlayMessage(problem.copy().withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            return InteractionResult.SUCCESS;
        }
        Component problem = open(sp, stack);
        if (problem != null) {
            sp.sendOverlayMessage(problem.copy().withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        return InteractionResult.SUCCESS;
    }

    /** Opens the linked terminal's screen; null on success, else why not. */
    public static @Nullable Component open(ServerPlayer player, ItemStack stack) {
        StorageTerminalBlockEntity terminal = reachable(player, stack);
        if (terminal == null) return whyNot(player, stack);
        BlockPos pos = terminal.getBlockPos();
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new TerminalMenu(id, inv, terminal, true), terminal.getDisplayName()),
                buf -> buf.writeBlockPos(pos));
        return null;
    }

    /** The linked terminal if it can be used remotely right now, else null. */
    static @Nullable StorageTerminalBlockEntity reachable(ServerPlayer player, ItemStack stack) {
        GlobalPos link = stack.get(OrbitalContent.LINKED_TERMINAL.get());
        if (link == null || !link.dimension().equals(player.level().dimension()) || !OrbitalSignal.hasCoverage(player)) return null;
        ServerLevel level = player.level();
        BlockPos pos = link.pos();
        if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) return null;
        return level.getBlockEntity(pos) instanceof StorageTerminalBlockEntity terminal ? terminal : null;
    }

    private static Component whyNot(ServerPlayer player, ItemStack stack) {
        GlobalPos link = stack.get(OrbitalContent.LINKED_TERMINAL.get());
        if (link == null) return Component.translatable("message.factoryascent.wireless_unlinked");
        if (!link.dimension().equals(player.level().dimension())) return Component.translatable("message.factoryascent.wireless_other_dimension");
        if (!OrbitalSignal.hasCoverage(player)) return Component.translatable("message.factoryascent.no_signal");
        BlockPos pos = link.pos();
        if (player.level().getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) {
            return Component.translatable("message.factoryascent.wireless_unloaded");
        }
        return Component.translatable("message.factoryascent.wireless_gone");
    }

    /**
     * The remote-access rule plugged into {@link TerminalMenu#remoteAccess}: the player holds a
     * Wireless Terminal linked to this terminal and still has coverage.
     */
    static boolean allowsRemote(Player player, StorageTerminalBlockEntity terminal) {
        if (!(player instanceof ServerPlayer sp) || terminal.getLevel() == null) return false;
        GlobalPos here = GlobalPos.of(terminal.getLevel().dimension(), terminal.getBlockPos());
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = sp.getItemInHand(hand);
            if (stack.getItem() instanceof WirelessTerminalItem && here.equals(stack.get(OrbitalContent.LINKED_TERMINAL.get()))) {
                return reachable(sp, stack) == terminal;
            }
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        GlobalPos link = stack.get(OrbitalContent.LINKED_TERMINAL.get());
        if (link == null) {
            tooltip.accept(Component.translatable("tooltip.factoryascent.wireless_unlinked").withStyle(ChatFormatting.GRAY));
        } else {
            BlockPos p = link.pos();
            tooltip.accept(Component.translatable("tooltip.factoryascent.wireless_linked", p.getX(), p.getY(), p.getZ(),
                    OrbitalText.dimensionName(link.dimension())).withStyle(ChatFormatting.GRAY));
        }
        tooltip.accept(Component.translatable("tooltip.factoryascent.wireless_needs_uplink").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.accept(Component.translatable("tooltip.factoryascent.wireless_map").withStyle(ChatFormatting.DARK_GREEN));
        tooltip.accept(Component.translatable("tooltip.factoryascent.wireless_console").withStyle(ChatFormatting.DARK_GRAY));
    }
}
