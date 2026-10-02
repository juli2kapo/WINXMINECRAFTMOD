package net.juli2kapo.factoryascent.mobs;

import java.util.List;
import java.util.function.Supplier;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.registry.ModComponents;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Unit;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.energy.ItemAccessEnergyHandler;

/** Entry point of the mob tools (Mob Capsule, Minimizer and Maximizer rays). */
public final class MobContent {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FactoryAscent.MOD_ID);
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, FactoryAscent.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, FactoryAscent.MOD_ID);

    /** The mob stored in a full Mob Capsule. */
    public static final Supplier<DataComponentType<CapturedMob>> CAPTURED_MOB = COMPONENTS.registerComponentType("captured_mob",
            b -> b.persistent(CapturedMob.CODEC).networkSynchronized(CapturedMob.STREAM_CODEC));
    /** A Minimizer or Maximizer Ray with a scope: zooms while charging and reaches 50% farther. */
    public static final Supplier<DataComponentType<Unit>> SCOPED = COMPONENTS.registerComponentType("scoped",
            b -> b.persistent(Unit.CODEC).networkSynchronized(Unit.STREAM_CODEC));

    /** Scoped ray alone in the grid: bare ray back, spyglass left in the grid. */
    public static final Supplier<RecipeSerializer<SizeRayUnscopeRecipe>> SIZE_RAY_UNSCOPE =
            RECIPE_SERIALIZERS.register("size_ray_unscope", () -> SizeRayUnscopeRecipe.SERIALIZER);

    public static final DeferredItem<MobCapsuleItem> MOB_CAPSULE = ITEMS.registerItem("mob_capsule", MobCapsuleItem::new,
            p -> p.stacksTo(MobCapsuleItem.EMPTY_STACK));
    public static final DeferredItem<SizeRayItem> MINIMIZER_RAY = ITEMS.registerItem("minimizer_ray",
            p -> new SizeRayItem(p, 0.5, 0x33D6FF, "tooltip.factoryascent.minimizer_ray"), p -> p.stacksTo(1));
    public static final DeferredItem<SizeRayItem> MAXIMIZER_RAY = ITEMS.registerItem("maximizer_ray",
            p -> new SizeRayItem(p, 2.0, 0xFF5A1F, "tooltip.factoryascent.maximizer_ray"), p -> p.stacksTo(1));

    private MobContent() {}

    /** Called from the mod constructor. */
    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        modBus.addListener(MobContent::registerCapabilities);
        NeoForge.EVENT_BUS.addListener(MobCapsuleItem::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(MobCapsuleItem::onLogout);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(Capabilities.Energy.ITEM, (stack, access) -> new ItemAccessEnergyHandler(access,
                        ModComponents.ENERGY.get(), SizeRayItem.CAPACITY, SizeRayItem.CAPACITY / 50, 0),
                MINIMIZER_RAY.get(), MAXIMIZER_RAY.get());
        event.registerItem(Capabilities.Energy.ITEM, (stack, access) -> new ItemAccessEnergyHandler(access,
                ModComponents.ENERGY.get(), MobCapsuleItem.CAPACITY, MobCapsuleItem.CAPACITY / 20, 0), MOB_CAPSULE.get());
    }

    /** Items for the Utility creative tab, in order. */
    public static List<Supplier<? extends ItemLike>> creativeItems() {
        return List.of(MOB_CAPSULE, MINIMIZER_RAY, MAXIMIZER_RAY);
    }
}
