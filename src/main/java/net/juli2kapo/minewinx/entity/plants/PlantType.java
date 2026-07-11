package net.juli2kapo.minewinx.entity.plants;

import java.util.List;
import java.util.Locale;

/**
 * Tipos de planta PvZ. El roster por stage es de reemplazo (upgrade), no
 * acumulativo.
 */
public enum PlantType {
    PEASHOOTER(12.0F, "peashooter", "peashooter"),
    REPEATER(12.0F, "repeater", "repeater"),
    GATLING_PEA(12.0F, "gatling_pea", "gatling_pea"),
    SNOW_PEA(12.0F, "snowpea", "snowpea"),
    CHOMPER(30.0F, "chomper", "chomper"),
    CHERRY_BOMB(8.0F, "cherry_bomb", "cherry_bomb"),
    DOOM_SHROOM(8.0F, "doomshroom", "doomshroom"),
    TORCHWOOD(20.0F, "torchwood", "torchwood"),
    COB_CANNON(25.0F, "cobcannon", "cobcannon");

    public static final List<PlantType> STAGE_1 = List.of(PEASHOOTER, CHOMPER, CHERRY_BOMB);
    public static final List<PlantType> STAGE_2 = List.of(REPEATER, SNOW_PEA, CHOMPER, DOOM_SHROOM);
    public static final List<PlantType> STAGE_3 = List.of(GATLING_PEA, SNOW_PEA, TORCHWOOD, DOOM_SHROOM, CHOMPER, COB_CANNON);

    public final float maxHealth;
    public final String geoName;
    public final String textureName;

    PlantType(float maxHealth, String geoName, String textureName) {
        this.maxHealth = maxHealth;
        this.geoName = geoName;
        this.textureName = textureName;
    }

    public static List<PlantType> rosterForStage(int stage) {
        return switch (stage) {
            case 1 -> STAGE_1;
            case 2 -> STAGE_2;
            default -> STAGE_3;
        };
    }

    public static int plantCapForStage(int stage) {
        return switch (stage) {
            case 1 -> 3;
            case 2 -> 5;
            default -> 7;
        };
    }

    public String translationKey() {
        return "plant.minewinx." + name().toLowerCase(Locale.ROOT);
    }

    public static PlantType byName(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return PEASHOOTER;
        }
    }

    public boolean isShooter() {
        return this == PEASHOOTER || this == REPEATER || this == GATLING_PEA || this == SNOW_PEA;
    }

    public boolean isBomb() {
        return this == CHERRY_BOMB || this == DOOM_SHROOM;
    }
}
