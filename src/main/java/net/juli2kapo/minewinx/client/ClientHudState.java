package net.juli2kapo.minewinx.client;

import net.minecraft.client.Minecraft;

/** Estado del HUD de poderes en el cliente (sincronizado desde el servidor). */
public final class ClientHudState {

    private static String element = "";
    private static int stage = 0;
    private static String selectedPlant = "";
    private static final long[] cooldownExpiry = new long[4];
    private static final int[] cooldownTotal = new int[4];

    private ClientHudState() {}

    public static void setState(String newElement, int newStage, String newSelectedPlant) {
        element = newElement == null ? "" : newElement;
        stage = newStage;
        selectedPlant = newSelectedPlant == null ? "" : newSelectedPlant;
        net.juli2kapo.minewinx.MineWinx.LOGGER.info("[HUD] estado recibido: element='{}' stage={} plant='{}'", element, stage, selectedPlant);
    }

    public static String getSelectedPlant() {
        return selectedPlant;
    }

    public static void setCooldown(int slot, int ticks) {
        if (slot < 1 || slot > 3) return;
        long now = currentTime();
        cooldownExpiry[slot] = now + ticks;
        cooldownTotal[slot] = ticks;
    }

    public static String getElement() {
        return element;
    }

    public static int getStage() {
        return stage;
    }

    /** @return fracción de cooldown restante en [0,1] para el slot */
    public static float cooldownFraction(int slot) {
        if (slot < 1 || slot > 3 || cooldownTotal[slot] <= 0) return 0;
        long remaining = cooldownExpiry[slot] - currentTime();
        if (remaining <= 0) return 0;
        return Math.min(1.0F, (float) remaining / cooldownTotal[slot]);
    }

    public static int cooldownSecondsLeft(int slot) {
        if (slot < 1 || slot > 3) return 0;
        long remaining = cooldownExpiry[slot] - currentTime();
        return remaining <= 0 ? 0 : (int) Math.ceil(remaining / 20.0);
    }

    private static long currentTime() {
        return Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
    }
}
