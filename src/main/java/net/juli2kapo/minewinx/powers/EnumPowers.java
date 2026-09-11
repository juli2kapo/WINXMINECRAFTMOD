package net.juli2kapo.minewinx.powers;

import net.minecraft.world.entity.player.Player;

import java.util.Arrays;
import java.util.function.Consumer;

public enum EnumPowers {

    // FIRE POWERS
    FIRE_BARRIER(Element.FIRE, 1, 25 * 10, FirePowers::activateFireBarrier),
    FIRE_LASER(Element.FIRE, 2, 15 * 10, FirePowers::fireLaser),
    DRAGON_PUNCH(Element.FIRE, 3, 20 * 10, FirePowers::dragonPunch),

    // WATER POWERS
    DROWN_TARGET(Element.WATER, 1, 10 * 10, WaterPowers::startDrowningTarget),
    SUMMON_TSUNAMI(Element.WATER, 2, 25 * 10, WaterPowers::summonTsunami),
    GEYSER_FIELD(Element.WATER, 3, 30 * 10, WaterPowers::summonGeyserField),

    // ICE POWERS
    ACTIVATE_ICE_RING(Element.ICE, 1, 20 * 10, IcePowers::activateIceRing),
    FIRE_ICE_VALLEY(Element.ICE, 2, 12 * 10, IcePowers::fireIceVolley),
    ENCAPSULE_IN_ICE(Element.ICE, 3, 25 * 10, IcePowers::encapsuleInIceCrystal),

    // MUSIC POWERS
    SUMMON_SPEAKERS(Element.MUSIC, 1, 20 * 10, MusicPowers::summonSpeakers),
    VOCAL_BLAST(Element.MUSIC, 2, 8 * 10, MusicPowers::vocalBlast),
    CONFUSION_SONG(Element.MUSIC, 3, 25 * 10, MusicPowers::confusionSong),

    // TECHNOLOGY POWERS
    FREEZE_TIME(Element.TECHNOLOGY, 1, 60 * 10, TechnologyPowers::freezeTime),
    ITEM_DROP(Element.TECHNOLOGY, 2, 15 * 10, TechnologyPowers::itemDrop),
    PISTON_SMASH(Element.TECHNOLOGY, 3, 15 * 10, TechnologyPowers::pistonSmash),

    // DARK POWERS
    COMMAND_ILLUSION(Element.DARK, 1, 3 * 10, DarkPowers::commandIllusions),
    SWAP_ILLUSION(Element.DARK, 2, 8 * 10, DarkPowers::swapWithIllusion),
    EXPLODE_ILLUSION(Element.DARK, 3, 20 * 10, DarkPowers::detonateIllusions),


    // NATURE POWERS
    SPORE_BOMB(Element.NATURE, 1, 6 * 10, NaturePowers::sporeBomb),
    CYCLE_PLANT(Element.NATURE, 2, 5, NaturePowers::cyclePlant),
    PLANT_SEED(Element.NATURE, 3, 3 * 10, NaturePowers::spawnPlant),

    // SUNANDMOON POWERS
    CAST_SUN_RAY(Element.SUNANDMOON, 1, 6 * 10, SunAndMoonPowers::castSunRay),
    LIGHTRAYS(Element.SUNANDMOON, 2, 20 * 10, SunAndMoonPowers::castLightBarrage),
    SOLAR_FLARE(Element.SUNANDMOON, 3, 30 * 10, SunAndMoonPowers::castSolarFlare),

    // STORM POWERS
    STORM_FIELD(Element.STORM, 1, 30 * 10, StormPowers::summonStormField),
    TORNADO(Element.STORM, 2, 25 * 10, StormPowers::summonTornado),
    RIDE_THE_STORM(Element.STORM, 3, 40 * 10, StormPowers::rideTheStorm),

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
     * EnumeraciÃ³n para los tipos de elementos.
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