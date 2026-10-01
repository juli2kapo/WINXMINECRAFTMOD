package net.juli2kapo.factoryascent.phone.client;

import net.juli2kapo.factoryascent.phone.PhonePayloads;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The client half of a phone app: draws the app's page from the data its server half sent
 * ({@link #data}) and turns clicks into actions ({@link #send}). One instance per opened app.
 *
 * <p>Coordinates are the app area's own: (0, 0) is its top-left corner, {@link #W} x {@link #H}
 * pixels below the app's title bar. The phone clips drawing to that area and scrolls nothing by
 * itself; views that list things handle the wheel in {@link #scroll}.
 */
public abstract class PhoneAppView {
    /** Size of the app area. */
    public static final int W = 132, H = 171;

    protected final String app;
    protected CompoundTag data = new CompoundTag();
    /** True once the server's first answer arrived. */
    protected boolean loaded;
    /** The phone state at the time of drawing (battery, signal, memory). */
    protected PhonePayloads.PhoneState state;

    protected PhoneAppView(String app) {
        this.app = app;
    }

    public String app() {
        return app;
    }

    /** New data from the server. */
    public void setData(CompoundTag data) {
        this.data = data;
        this.loaded = true;
        dataChanged();
    }

    void setState(PhonePayloads.PhoneState state) {
        this.state = state;
    }

    /** Called after {@link #data} changed (rebuild cached lists, clamp scrolling). */
    protected void dataChanged() {}

    /** Draws the app. {@code mx/my} are in app coordinates (outside the area when the mouse is elsewhere). */
    public abstract void render(GuiGraphicsExtractor g, int mx, int my, float partial);

    /** A left/right click inside the app area; true if handled. */
    public boolean click(double mx, double my, int button) {
        return false;
    }

    public boolean scroll(double mx, double my, double amount) {
        return false;
    }

    public boolean key(KeyEvent event) {
        return false;
    }

    public boolean character(CharacterEvent event) {
        return false;
    }

    /** True while the view wants every key (a text field has focus): Escape still goes back. */
    public boolean capturesKeys() {
        return false;
    }

    /** Called every client tick while the app is showing. */
    public void tick() {}

    /** Sends an action of this app to the server. */
    protected void send(String action, CompoundTag args) {
        ClientPacketDistributor.sendToServer(new PhonePayloads.Action(app, action, args));
    }

    protected void send(String action) {
        send(action, new CompoundTag());
    }

    /**
     * Sends an action that opens another screen (map, terminal, team screen): when that screen is
     * closed, the phone comes back on this app.
     */
    protected void sendLeaving(String action) {
        PhoneClient.expectChildScreen(app);
        send(action);
    }

    /** A title bar subtitle (right side), e.g. "3/8"; empty for none. */
    public Component subtitle() {
        return Component.empty();
    }
}
