package net.juli2kapo.minewinx.powers;

import net.minecraft.world.entity.player.Player;

import java.util.Arrays;
import java.util.function.Consumer;

public enum EnumPowers {

    // FIRE POWERS
    FIRE_BARRIER(Element.FIRE, 1, 25 * 20, FirePowers::activateFireBarrier),
    FIRE_LASER(Element.FIRE, 2, 15 * 20, FirePowers::fireLaser),
    DRAGON_PUNCH(Element.FIRE, 3, 12 * 20, FirePowers::dragonPunch),

    // WATER POWERS
    DROWN_TARGET(Element.WATER, 1, 10 * 20, WaterPowers::startDrowningTarget),
    SUMMON_TSUNAMI(Element.WATER, 2, 25 * 20, WaterPowers::summonTsunami),
    GEYSER_FIELD(Element.WATER, 3, 30 * 20, WaterPowers::summonGeyserField),

    // ICE POWERS
    ACTIVATE_ICE_RING(Element.ICE, 1, 20 * 20, IcePowers::activateIceRing),
    FIRE_ICE_VALLEY(Element.ICE, 2, 12 * 20, IcePowers::fireIceVolley),
    ENCAPSULE_IN_ICE(Element.ICE, 3, 25 * 20, IcePowers::encapsuleInIceCrystal),

    // MUSIC POWERS
    SUMMON_SPEAKERS(Element.MUSIC, 1, 20 * 20, MusicPowers::summonSpeakers),
    VOCAL_BLAST(Element.MUSIC, 2, 8 * 20, MusicPowers::vocalBlast),
    CONFUSION_SONG(Element.MUSIC, 3, 25 * 20, MusicPowers::confusionSong),

    // TECHNOLOGY POWERS
    FREEZE_TIME(Element.TECHNOLOGY, 1, 60 * 20, TechnologyPowers::freezeTime),
    ITEM_DROP(Element.TECHNOLOGY, 2, 15 * 20, TechnologyPowers::itemDrop),
    PISTON_SMASH(Element.TECHNOLOGY, 3, 15 * 20, TechnologyPowers::pistonSmash),

    // DARK POWERS
    COMMAND_ILLUSION(Element.DARK, 1, 3 * 20, DarkPowers::commandIllusions),
    SWAP_ILLUSION(Element.DARK, 2, 8 * 20, DarkPowers::swapWithIllusion),
    EXPLODE_ILLUSION(Element.DARK, 3, 20 * 20, DarkPowers::detonateIllusions),


    // NATURE POWERS
    SPORE_BOMB(Element.NATURE, 1, 6 * 20, NaturePowers::sporeBomb),
    CYCLE_PLANT(Element.NATURE, 2, 10, NaturePowers::cyclePlant),
    PLANT_SEED(Element.NATURE, 3, 3 * 20, NaturePowers::spawnPlant),

    // SUNANDMOON POWERS
    CAST_SUN_RAY(Element.SUNANDMOON, 1, 6 * 20, SunAndMoonPowers::castSunRay),
    LIGHTRAYS(Element.SUNANDMOON, 2, 20 * 20, SunAndMoonPowers::castLightBarrage),
    LIGHT_PRISM(Element.SUNANDMOON, 3, 30 * 20, SunAndMoonPowers::castLightPrism),

    // STORM POWERS
    STORM_FIELD(Element.STORM, 1, 30 * 20, StormPowers::summonStormField),
    TORNADO(Element.STORM, 2, 25 * 20, StormPowers::summonTornado),

    UNKNOWN(Element.UNKNOWN, 0, 0, player -> {});

    private final Element element;
    private final int slot;
    private final int cooldownTicks;
    private final Consumer<Player> action;

    EnumPowers(Element element, int slot, int cooldownTicks, Consumer<Player> action) {
        this.element = element;
        this.slot = slot;
        this.cooldownTicks = cooldownTicks;
        this.action = action;
    }

    public int getCooldownTicks() {
        return cooldownTicks;
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
        ICE(4, "ice"),
        MUSIC(5, "music"),
        TECHNOLOGY(6, "technology"),
        DARK(7, "dark"),
        SUNANDMOON(8, "sunandmoon"),
        STORM(9, "storm");

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