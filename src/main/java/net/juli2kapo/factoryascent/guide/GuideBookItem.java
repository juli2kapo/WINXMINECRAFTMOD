package net.juli2kapo.factoryascent.guide;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * The Factory Ascent Manual. Use: opens the book (client side). While a hologram is projected,
 * using it on a block moves the hologram there and sneak-using it in the air turns it 90°.
 */
public class GuideBookItem extends Item {
    /** Set by the client entry point; no-ops on a dedicated server. */
    public interface ClientHooks {
        /** True if the click was used by the hologram (re-anchor). */
        boolean useOn(UseOnContext context);

        void use(Player player, boolean sneaking);
    }

    public static ClientHooks hooks = new ClientHooks() {
        @Override
        public boolean useOn(UseOnContext context) {
            return false;
        }

        @Override
        public void use(Player player, boolean sneaking) {}
    };

    public GuideBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide() && hooks.useOn(context)) return InteractionResult.SUCCESS;
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) hooks.use(player, player.isShiftKeyDown());
        return InteractionResult.SUCCESS;
    }
}
