package net.juli2kapo.factoryascent.pipe;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** What an item pipe does on one face: nothing, deliver/connect, or pull from the inventory there. */
public enum PipeConnection implements StringRepresentable {
    NONE, CONNECTED, EXTRACT;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
