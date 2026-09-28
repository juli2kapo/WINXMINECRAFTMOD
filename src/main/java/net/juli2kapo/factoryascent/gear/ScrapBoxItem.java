package net.juli2kapo.factoryascent.gear;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * Scrap Box (nine scrap from the Recycler): open it for a random material, rolled from the loot
 * table {@code factoryascent:gameplay/scrap_box} (datapacks can change the odds). Sneak to open
 * the whole stack at once.
 */
public class ScrapBoxItem extends Item {
    public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "gameplay/scrap_box"));

    public ScrapBoxItem(Properties properties) {
        super(properties);
    }

    /** What one box gives. */
    public static List<ItemStack> roll(ServerLevel level, Player player) {
        LootTable table = level.getServer().reloadableRegistries().getLootTable(LOOT);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, player.position())
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .create(LootContextParamSets.GIFT);
        List<ItemStack> out = new ArrayList<>();
        table.getRandomItems(params, out::add);
        return out;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            int boxes = player.isShiftKeyDown() ? stack.getCount() : 1;
            for (int i = 0; i < boxes; i++) {
                for (ItemStack loot : roll(server, player)) {
                    if (!player.getInventory().add(loot)) player.drop(loot, false);
                }
            }
            stack.consume(boxes, player);
            level.playSound(null, player.blockPosition(), SoundEvents.CHEST_OPEN, SoundSource.PLAYERS, 0.5f, 1.4f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.factoryascent.scrap_box").withStyle(ChatFormatting.GRAY));
    }
}
