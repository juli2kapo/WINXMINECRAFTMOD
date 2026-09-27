package net.juli2kapo.factoryascent.machine;

/** Slot index layout of a machine inventory: inputs, mould, fuel, outputs, upgrades, in that order. */
public record MachineSlots(int inputs, int mold, int fuel, int outputs, int upgrades) {
    public static MachineSlots of(MachineType type) {
        return new MachineSlots(type.inputSlots(), type.moldSlots(), type.fuelSlots(), type.outputSlots(), type.upgradeSlots());
    }

    public int size() {
        return inputs + mold + fuel + outputs + upgrades;
    }

    public int firstInput() { return 0; }
    public int firstMold() { return inputs; }
    public int firstFuel() { return inputs + mold; }
    public int firstOutput() { return inputs + mold + fuel; }
    public int firstUpgrade() { return inputs + mold + fuel + outputs; }

    public SlotRole role(int index) {
        if (index < firstMold()) return SlotRole.INPUT;
        if (index < firstFuel()) return SlotRole.MOLD;
        if (index < firstOutput()) return SlotRole.FUEL;
        if (index < firstUpgrade()) return SlotRole.OUTPUT;
        return SlotRole.UPGRADE;
    }
}
