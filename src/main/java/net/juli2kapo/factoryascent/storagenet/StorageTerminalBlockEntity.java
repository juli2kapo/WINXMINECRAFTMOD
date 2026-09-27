package net.juli2kapo.factoryascent.storagenet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Opens the {@link TerminalMenu}: a searchable view of everything in the network. */
public class StorageTerminalBlockEntity extends StorageNodeBlockEntity implements MenuProvider {
    public StorageTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(StorageContent.TERMINAL_BE.get(), pos, state);
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new TerminalMenu(id, inventory, this);
    }
}
