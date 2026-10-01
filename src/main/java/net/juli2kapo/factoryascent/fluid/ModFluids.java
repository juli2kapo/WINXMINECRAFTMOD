package net.juli2kapo.factoryascent.fluid;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The real fluids of Factory Ascent. Liquids (crude oil, diesel, rocket fuel, coolant) have a
 * source/flowing pair, a liquid block and a bucket; gases (steam, biogas, deuterium, tritium,
 * helium-3) only ever live in tanks, pipes and machines, so they have neither. Every fluid has
 * its own still/flowing texture ({@code block/fluid/<id>_still|_flow}), registered as its fluid
 * model on the client (see {@code FluidsClient}).
 */
public final class ModFluids {
    public static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, FactoryAscent.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, FactoryAscent.MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FactoryAscent.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FactoryAscent.MOD_ID);

    /** One fluid: what it is and how it looks (the colour is used where no texture can be: maps, particles, GUI text). */
    public static final class Def {
        public final String id;
        public final boolean gas;
        public final int color;
        final DeferredHolder<FluidType, FluidType> type;
        final DeferredHolder<Fluid, BaseFlowingFluid.Source> source;
        final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing;
        final DeferredBlock<LiquidBlock> block;
        final DeferredItem<BucketItem> bucket;

        Def(String id, boolean gas, int color, FluidType.Properties typeProps, int slope, int decrease, int tickRate, MapColor mapColor, int light) {
            this.id = id;
            this.gas = gas;
            this.color = color;
            this.type = TYPES.register(id, () -> new FluidType(typeProps.descriptionId("fluid_type." + FactoryAscent.MOD_ID + "." + id)));
            Supplier<BaseFlowingFluid.Properties> props = new Supplier<>() {
                private BaseFlowingFluid.Properties cached;

                @Override
                public BaseFlowingFluid.Properties get() {
                    if (cached == null) {
                        cached = new BaseFlowingFluid.Properties(type, ModFluids.Def.this::source, ModFluids.Def.this::flowing)
                                .slopeFindDistance(slope).levelDecreasePerBlock(decrease).tickRate(tickRate).explosionResistance(100f);
                        if (!gas) cached.block(ModFluids.Def.this::block).bucket(ModFluids.Def.this::bucket);
                    }
                    return cached;
                }
            };
            this.source = FLUIDS.register(id, () -> new BaseFlowingFluid.Source(props.get()));
            this.flowing = FLUIDS.register("flowing_" + id, () -> new BaseFlowingFluid.Flowing(props.get()));
            if (gas) {
                this.block = null;
                this.bucket = null;
            } else {
                this.block = BLOCKS.registerBlock(id, p -> new LiquidBlock((FlowingFluid) source.get(), p),
                        () -> BlockBehaviour.Properties.of().mapColor(mapColor).replaceable().noCollision().strength(100f)
                                .pushReaction(PushReaction.DESTROY).noLootTable().liquid().sound(SoundType.EMPTY)
                                .lightLevel(s -> light));
                this.bucket = ITEMS.registerItem(id + "_bucket", p -> new BucketItem(source.get(), p),
                        p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
            }
        }

        public FluidType type() {
            return type.get();
        }

        public Fluid source() {
            return source.get();
        }

        public Fluid flowing() {
            return flowing.get();
        }

        public LiquidBlock block() {
            return block == null ? null : block.get();
        }

        public Item bucket() {
            return bucket == null ? Items.AIR : bucket.get();
        }

        public boolean hasBucket() {
            return bucket != null;
        }
    }

    private static final Map<String, Def> ALL = new LinkedHashMap<>();

    private static FluidType.Properties liquid(int density, int viscosity, int temperature) {
        return FluidType.Properties.create().density(density).viscosity(viscosity).temperature(temperature)
                .canSwim(true).canDrown(true).canExtinguish(false).canConvertToSource(false).supportsBoating(false)
                .canHydrate(false).fallDistanceModifier(0.5f)
                .sound(SoundActions.BUCKET_FILL, net.minecraft.sounds.SoundEvents.BUCKET_FILL)
                .sound(SoundActions.BUCKET_EMPTY, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY);
    }

    private static FluidType.Properties gas(int density, int temperature) {
        return FluidType.Properties.create().density(density).viscosity(200).temperature(temperature)
                .canSwim(false).canDrown(false).canExtinguish(false).canConvertToSource(false).supportsBoating(false)
                .canHydrate(false).canPushEntity(false);
    }

    private static Def liquidDef(String id, int color, FluidType.Properties p, int slope, int decrease, int tickRate, MapColor map, int light) {
        Def d = new Def(id, false, color, p, slope, decrease, tickRate, map, light);
        ALL.put(id, d);
        return d;
    }

    private static Def gasDef(String id, int color, FluidType.Properties p) {
        Def d = new Def(id, true, color, p, 4, 1, 5, MapColor.NONE, 0);
        ALL.put(id, d);
        return d;
    }

    // ---------------------------------------------------------------- liquids

    /** Black, thick and slow: from oil pockets deep underground and tar-pit lakes in dry biomes. */
    public static final Def CRUDE_OIL = liquidDef("crude_oil", 0xFF1A1612,
            liquid(1100, 4000, 300).motionScale(0.002).fallDistanceModifier(0.3f), 2, 2, 25, MapColor.COLOR_BLACK, 0);
    /** Amber refinery product burnt in the Diesel Generator. */
    public static final Def DIESEL = liquidDef("diesel", 0xFFD8A531, liquid(850, 1400, 300), 3, 1, 8, MapColor.COLOR_YELLOW, 0);
    /** Red-orange refined kerosene for launches and shuttles. */
    public static final Def ROCKET_FUEL = liquidDef("rocket_fuel", 0xFFE8562A, liquid(800, 900, 300).rarity(Rarity.UNCOMMON),
            4, 1, 5, MapColor.COLOR_ORANGE, 4);
    /** Cyan reactor coolant: carries four times the heat of water. */
    public static final Def COOLANT = liquidDef("coolant", 0xFF33D6E0, liquid(1050, 1100, 260), 4, 1, 5, MapColor.COLOR_CYAN, 2);

    // ---------------------------------------------------------------- gases

    public static final Def STEAM = gasDef("steam", 0xFFE6ECEF, gas(-500, 400));
    public static final Def BIOGAS = gasDef("biogas", 0xFF9BC24A, gas(-300, 310));
    public static final Def DEUTERIUM = gasDef("deuterium", 0xFF6FA8FF, gas(-1000, 300).rarity(Rarity.UNCOMMON));
    public static final Def TRITIUM = gasDef("tritium", 0xFF66FF8A, gas(-1000, 300).rarity(Rarity.RARE).lightLevel(6));
    public static final Def HELIUM_3 = gasDef("helium_3", 0xFFD7B8FF, gas(-1200, 300).rarity(Rarity.RARE));

    private ModFluids() {}

    public static List<Def> all() {
        return new ArrayList<>(ALL.values());
    }

    public static Def byId(String id) {
        return ALL.get(id);
    }

    static void register(IEventBus modBus) {
        TYPES.register(modBus);
        FLUIDS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }
}
