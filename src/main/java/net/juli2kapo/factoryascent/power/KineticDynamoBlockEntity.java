package net.juli2kapo.factoryascent.power;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Bronze age: the first FE. A Water Wheel or Windmill touching the dynamo turns its armature
 * (the rotor splits its work between everything it drives), and every work point per tick makes
 * {@code kineticDynamoFePerPoint} FE per tick: about 20 FE/t from a wheel in flowing water.
 */
public class KineticDynamoBlockEntity extends PowerBlockEntity implements KineticSink {
    private float received;
    private int drivers;
    private float points;
    private int rotors;

    public KineticDynamoBlockEntity(BlockPos pos, BlockState state) {
        super(PowerContent.generatorType(Generator.KINETIC_DYNAMO).get(), pos, state, 0);
    }

    @Override
    public int maxOutput() {
        return (int) Math.ceil(Generator.KINETIC_DYNAMO.peak() * PowerConfig.generatorMultiplier());
    }

    @Override
    public List<SlotSpec> slotLayout() {
        return List.of();
    }

    @Override
    public void driveKinetic(float points) {
        received += points;
        drivers++;
    }

    @Override
    protected boolean tickGenerator(ServerLevel level) {
        points = received;
        rotors = drivers;
        received = 0;
        drivers = 0;
        float out = (float) (points * PowerConfig.get(PowerConfig.DYNAMO_FE_PER_POINT) * PowerConfig.generatorMultiplier());
        if (out > 0) generate(out);
        else lastRate = 0;
        status = points <= 0 ? ST_NO_ROTATION : energy.space() <= 0 ? ST_FULL : ST_RUNNING;
        return points > 0;
    }

    /** Work points received last tick. */
    public float points() {
        return points;
    }

    @Override
    protected void writeExtra(int[] extra) {
        extra[0] = Math.round(points * 1000);
        extra[1] = rotors;
    }
}
