package net.juli2kapo.factoryascent.guide;

import net.neoforged.neoforge.common.ModConfigSpec;

/** The Manual's server options ({@code factoryascent-guide-server.toml}). */
public final class GuideConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue GIVE_ON_FIRST_JOIN;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("guide");
        GIVE_ON_FIRST_JOIN = b.comment("Give every player a Factory Ascent Manual the first time they join the world.")
                .define("giveManualOnFirstJoin", true);
        b.pop();
        SPEC = b.build();
    }

    private GuideConfig() {}

    static boolean giveOnFirstJoin() {
        try {
            return GIVE_ON_FIRST_JOIN.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }
}
