package net.juli2kapo.factoryascent.capsule;

import net.juli2kapo.factoryascent.mobs.CapturedMob;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

/** Size Chamber screen: the capsule slot, the target size buttons, energy and progress. */
public class SizeChamberMenu extends DeviceMenu<SizeChamberBlockEntity> {
    public static final int WIDTH = 176, HEIGHT = 186, INV_Y = 104;
    public static final int SLOT_X = 21, SLOT_Y = 22;
    public static final int D_ENERGY_LO = 0, D_ENERGY_HI = 1, D_CAP_LO = 2, D_CAP_HI = 3, D_TARGET = 4, D_STATUS = 5,
            D_PROGRESS = 6, D_SIZE = 7, D_HEALTH = 8, D_MAX_HEALTH = 9, D_MIN = 10, D_MAX = 11, D_COUNT = 12;
    /** Buttons: ÷2, ×0.8, reset to 1, ×1.25, ×2. */
    public static final int B_HALF = 0, B_LESS = 1, B_RESET = 2, B_MORE = 3, B_DOUBLE = 4;
    public static final double[] STEPS = {0.5, 0.8, 0, 1.25, 2.0};

    SizeChamberMenu(int id, Inventory inventory, SizeChamberBlockEntity be) {
        super(CapsuleContent.SIZE_CHAMBER_MENU.get(), id, inventory, be, be.getBlockPos(), be.inventory,
                new int[][] {{SLOT_X, SLOT_Y}}, serverData(be), 8, INV_Y);
    }

    private SizeChamberMenu(int id, Inventory inventory, net.minecraft.core.BlockPos pos) {
        super(CapsuleContent.SIZE_CHAMBER_MENU.get(), id, inventory, clientDevice(inventory, pos, SizeChamberBlockEntity.class), pos,
                clientHandler(inventory, pos, 1), new int[][] {{SLOT_X, SLOT_Y}}, new SimpleContainerData(D_COUNT), 8, INV_Y);
    }

    public static SizeChamberMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new SizeChamberMenu(id, inventory, buf.readBlockPos());
    }

    private static ContainerData serverData(SizeChamberBlockEntity be) {
        return new ContainerData() {
            @Override
            public int get(int i) {
                CapturedMob mob = be.mob();
                return switch (i) {
                    case D_ENERGY_LO -> be.energyStored() & 0xFFFF;
                    case D_ENERGY_HI -> be.energyStored() >>> 16;
                    case D_CAP_LO -> be.energyCapacity() & 0xFFFF;
                    case D_CAP_HI -> be.energyCapacity() >>> 16;
                    case D_TARGET -> be.target();
                    case D_STATUS -> be.status();
                    case D_PROGRESS -> be.resizeTicks() * 1000 / Math.max(1, SizeChamberBlockEntity.resizeDuration());
                    case D_SIZE -> mob == null ? 1000 : (int) Math.round(CapsuleOps.scaleOf(mob) * 1000);
                    case D_HEALTH -> mob == null ? 0 : Mth.clamp(Math.round(mob.health() * 10), 0, 32000);
                    case D_MAX_HEALTH -> mob == null ? 0 : Mth.clamp(Math.round(mob.maxHealth() * 10), 0, 32000);
                    case D_MIN -> SizeChamberBlockEntity.minPermille();
                    case D_MAX -> SizeChamberBlockEntity.maxPermille();
                    default -> 0;
                };
            }

            @Override
            public void set(int i, int v) {}

            @Override
            public int getCount() {
                return D_COUNT;
            }
        };
    }

    public int energy() {
        return (get(D_ENERGY_LO) & 0xFFFF) | (get(D_ENERGY_HI) << 16);
    }

    public int capacity() {
        return (get(D_CAP_LO) & 0xFFFF) | (get(D_CAP_HI) << 16);
    }

    /** The target after pressing a step button (what the server will apply). */
    public static int stepped(int target, int button, int min, int max) {
        if (button == B_RESET) return Mth.clamp(1000, min, max);
        if (button < 0 || button >= STEPS.length) return target;
        return Mth.clamp((int) Math.round(target * STEPS[button]), min, max);
    }

    @Override
    protected boolean button(ServerPlayer player, SizeChamberBlockEntity device, int id) {
        if (id < 0 || id >= STEPS.length) return false;
        device.setTarget(stepped(device.target(), id, SizeChamberBlockEntity.minPermille(), SizeChamberBlockEntity.maxPermille()));
        return true;
    }
}
