package net.juli2kapo.minewinx.powers;

import net.minecraft.world.entity.player.Player;

import java.util.Arrays;
import java.util.function.Consumer;

public enum EnumPowers {

    FIRE_BARRIER(Element.FIRE, 1, FirePowers::activateFireBarrier),
    FIRE_LASER(Element.FIRE, 2, FirePowers::fireLaser),
    DROWN_TARGET(Element.WATER, 1, WaterPowers::startDrowningTarget),
    SUMMON_TSUNAMI(Element.WATER, 2, WaterPowers::summonTsunami),
    THROW_SNOWBALL(Element.ICE, 1, player -> {}), // TODO: Implementar
    SPORE_BOMB(Element.NATURE, 1, NaturePowers::sporeBomb),

    UNKNOWN(Element.UNKNOWN, 0, player -> {});

    private final Element element;
    private final int slot;
    private final Consumer<Player> action;

    EnumPowers(Element element, int slot, Consumer<Player> action) {
        this.element = element;
        this.slot = slot;
        this.action = action;
    }

    public Element getElement() {
        return element;
    }

    public int getSlot() {
        return slot;
    }

    public void execute(Player player) {
        this.action.accept(player);
    }

    /**
     * Obtiene un poder basado en el elemento y el slot.
     * @param element El elemento del poder.
     * @param slot El slot del poder (ej: 1 para habilidad primaria, 2 para secundaria).
     * @return El poder correspondiente o UNKNOWN si no se encuentra.
     */
    public static EnumPowers getPower(Element element, int slot) {
        return Arrays.stream(values())
                .filter(power -> power.getElement() == element && power.getSlot() == slot)
                .findFirst()
                .orElse(UNKNOWN);
    }

    /**
     * Enumeración para los tipos de elementos.
     */
    public enum Element {
        UNKNOWN(0, "unknown"),
        FIRE(1, "fire"),
        WATER(2, "water"),
        NATURE(3, "nature"),
        ICE(4, "ice");

        private final int id;
        private final String name;

        Element(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public static Element fromId(int id) {
            return Arrays.stream(values())
                    .filter(element -> element.getId() == id)
                    .findFirst()
                    .orElse(UNKNOWN);
        }

        public static Element fromName(String name) {
            return Arrays.stream(values())
                    .filter(element -> element.getName().equalsIgnoreCase(name))
                    .findFirst()
                    .orElse(UNKNOWN);
        }
    }
}