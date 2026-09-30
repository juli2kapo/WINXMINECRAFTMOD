package net.juli2kapo.factoryascent.power;

/**
 * Something a Water Wheel or Windmill can drive besides the hand-cranked machines (the Kinetic
 * Dynamo). The rotor splits its work points between everything it drives, every tick.
 */
public interface KineticSink {
    /** Receives {@code points} work points of rotation this tick. */
    void driveKinetic(float points);
}
